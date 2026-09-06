-- =============================================================================
-- V4__events.sql — Giỗ/lễ, lịch nhắc, thông báo
--   event · reminder_job · notification_log · notification_inbox · push_subscription
-- Nguồn: TDD v1.0 §5.6, §8.3 · plan Giai đoạn 1 W5
--
-- Luồng: Scheduler chạy hằng đêm -> LunarConverter quy đổi ngày giỗ âm sang
-- dương của năm hiện tại -> sinh reminder_job cho mốc 7/3/1 ngày -> publish
-- RabbitMQ (exchange notify -> notify.inapp, notify.webpush; DLX notify.dlx)
-- -> consumer idempotent ghi notification_inbox / gửi Web Push -> notification_log.
--
-- MVP Giai đoạn 1 dùng kênh INAPP + WEBPUSH; ZALO là adapter thêm vào ở GĐ2 mà
-- không phải sửa luồng — vì vậy CHECK của channel đã liệt kê sẵn ZALO/SMS/EMAIL.
-- =============================================================================

SET search_path = public, ag_catalog;

-- =============================================================================
-- 4.1 event — Giỗ / lễ / sự kiện dòng họ
-- =============================================================================
CREATE TABLE event (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id        UUID         REFERENCES person (id),
    event_type       VARCHAR(24)  NOT NULL,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    lunar_date       JSONB,
    solar_date       DATE,
    is_lunar_based   BOOLEAN      NOT NULL DEFAULT TRUE,
    is_recurring     BOOLEAN      NOT NULL DEFAULT TRUE,
    target_branch_id UUID         REFERENCES branch (id),
    is_clan_level    BOOLEAN      NOT NULL DEFAULT FALSE,
    location         VARCHAR(255),
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_event_type CHECK (event_type IN (
        'GIO',            -- giỗ cá nhân (nguồn: person.death_lunar)
        'GIO_TO',         -- giỗ Tổ / tế Tổ toàn họ
        'TE_LE',          -- tế lễ, lễ tại từ đường
        'TAO_MO',         -- chạp mộ / tảo mộ
        'KHANH_THANH',    -- khánh thành từ đường, tu bổ
        'HOP_HO',         -- họp họ
        'SINH_NHAT',
        'CUOI_HOI',
        'KHAC'
    )),
    -- Sự kiện theo âm lịch bắt buộc có lunar_date {day, month}; theo dương lịch
    -- bắt buộc có solar_date.
    CONSTRAINT ck_event_date_source CHECK (
        (is_lunar_based AND lunar_date IS NOT NULL
             AND jsonb_exists(lunar_date, 'day') AND jsonb_exists(lunar_date, 'month'))
        OR (NOT is_lunar_based AND solar_date IS NOT NULL)
    ),
    -- Sự kiện cấp họ thì không gắn nhánh cụ thể
    CONSTRAINT ck_event_scope CHECK (NOT (is_clan_level AND target_branch_id IS NOT NULL)),
    CONSTRAINT ck_event_gio_has_person CHECK (event_type <> 'GIO' OR person_id IS NOT NULL)
);

CREATE INDEX ix_event_person     ON event (person_id)        WHERE is_deleted = FALSE;
CREATE INDEX ix_event_branch     ON event (target_branch_id) WHERE is_deleted = FALSE;
CREATE INDEX ix_event_type       ON event (event_type)       WHERE is_deleted = FALSE;
CREATE INDEX ix_event_lunar_date ON event USING gin (lunar_date jsonb_path_ops)
    WHERE lunar_date IS NOT NULL;

CREATE TRIGGER tg_event_touch BEFORE UPDATE ON event
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  event IS 'Gio/le. Voi event_type=GIO thi person.death_lunar la nguon chan ly cua ngay gio.';
COMMENT ON COLUMN event.lunar_date    IS 'Am lich {day, month, leap} (year rong = lap lai hang nam).';
COMMENT ON COLUMN event.is_lunar_based IS 'TRUE = tinh theo am lich (mac dinh cho gio/te le), FALSE = theo duong lich.';
COMMENT ON COLUMN event.is_clan_level IS 'TRUE = ca dong ho duoc nhac; FALSE = chi thanh vien cua target_branch_id (chi/nhanh).';

-- =============================================================================
-- 4.2 reminder_job — Lịch nhắc trước 7 / 3 / 1 ngày
-- =============================================================================
CREATE TABLE reminder_job (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID        NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    occurrence_year INT         NOT NULL,
    due_solar_date  DATE        NOT NULL,
    offset_days     INT         NOT NULL,
    fire_at         TIMESTAMPTZ NOT NULL,
    status          VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    attempt_count   INT         NOT NULL DEFAULT 0,
    last_error      TEXT,
    dispatched_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_reminder_job_status CHECK (
        status IN ('PENDING','QUEUED','SENT','FAILED','CANCELLED')
    ),
    CONSTRAINT ck_reminder_job_offset CHECK (offset_days >= 0),
    CONSTRAINT ck_reminder_job_attempt CHECK (attempt_count >= 0)
);

-- Chống trùng: mỗi sự kiện, mỗi năm, mỗi mốc chỉ sinh đúng một job.
-- Đây là chốt chặn để scheduler chạy lại (hoặc chạy trên 2 instance) không nhân đôi.
CREATE UNIQUE INDEX ux_reminder_job_occurrence
    ON reminder_job (event_id, occurrence_year, offset_days);
CREATE INDEX ix_reminder_job_due ON reminder_job (fire_at) WHERE status = 'PENDING';
CREATE INDEX ix_reminder_job_status ON reminder_job (status, fire_at);

CREATE TRIGGER tg_reminder_job_touch BEFORE UPDATE ON reminder_job
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  reminder_job IS 'Lich nhac sinh boi GenerateRemindersService chay hang dem.';
COMMENT ON COLUMN reminder_job.occurrence_year IS 'Nam duong lich cua lan gio/le nay — thanh phan khoa chong trung.';
COMMENT ON COLUMN reminder_job.due_solar_date  IS 'Ngay duong lich cua su kien nam nay, do LunarConverter (Ho Ngoc Duc, GMT+7) quy doi.';
COMMENT ON COLUMN reminder_job.offset_days     IS 'So ngay nhac truoc: 7 / 3 / 1 (BA v2). De mo de cau hinh them moc khac.';

-- =============================================================================
-- 4.3 notification_log — Nhật ký gửi (mọi kênh)
-- =============================================================================
CREATE TABLE notification_log (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    reminder_job_id     UUID         REFERENCES reminder_job (id) ON DELETE SET NULL,
    recipient_person_id UUID         NOT NULL REFERENCES person (id),
    channel             VARCHAR(16)  NOT NULL,
    provider            VARCHAR(32),
    provider_msg_id     VARCHAR(128),
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    retry_count         INT          NOT NULL DEFAULT 0,
    error               TEXT,
    payload_digest      VARCHAR(64),
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_notification_log_channel CHECK (
        channel IN ('INAPP','WEBPUSH','ZALO','SMS','EMAIL')
    ),
    CONSTRAINT ck_notification_log_status CHECK (
        status IN ('PENDING','SENT','FAILED','DEAD_LETTER','SKIPPED')
    ),
    CONSTRAINT ck_notification_log_retry CHECK (retry_count >= 0)
);

-- Khoá chống trùng của consumer = reminder_job_id + recipient + channel
-- (consumer phải idempotent: RabbitMQ giao ít nhất một lần).
CREATE UNIQUE INDEX ux_notification_log_idempotency
    ON notification_log (reminder_job_id, recipient_person_id, channel)
    WHERE reminder_job_id IS NOT NULL;
CREATE INDEX ix_notification_log_recipient ON notification_log (recipient_person_id, created_at DESC);
CREATE INDEX ix_notification_log_status    ON notification_log (status, channel);

CREATE TRIGGER tg_notification_log_touch BEFORE UPDATE ON notification_log
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  notification_log IS
    'Nhat ky gui thong bao moi kenh. Giu nguyen cau truc cho GD2 khi them ZaloZnsAdapter.';
COMMENT ON COLUMN notification_log.status IS
    'PENDING/SENT/FAILED/DEAD_LETTER (het retry, da day sang notify.dlq)/SKIPPED (nguoi nhan tat kenh).';
COMMENT ON COLUMN notification_log.payload_digest IS
    'Bam noi dung de doi soat trung lap. KHONG luu noi dung nhay cam o day.';

-- =============================================================================
-- 4.4 notification_inbox — Hộp thư trong ứng dụng (kênh INAPP của MVP)
-- =============================================================================
CREATE TABLE notification_inbox (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_person_id UUID         NOT NULL REFERENCES person (id),
    event_id            UUID         REFERENCES event (id) ON DELETE SET NULL,
    reminder_job_id     UUID         REFERENCES reminder_job (id) ON DELETE SET NULL,
    title               VARCHAR(200) NOT NULL,
    body                TEXT         NOT NULL,
    link_url            VARCHAR(500),
    category            VARCHAR(24)  NOT NULL DEFAULT 'REMINDER',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at             TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_notification_inbox_category CHECK (
        category IN ('REMINDER','APPROVAL','SYSTEM','NEWS')
    )
);

-- Danh sách chưa đọc theo người nhận (API GET /api/v1/notifications)
CREATE INDEX ix_notification_inbox_unread
    ON notification_inbox (recipient_person_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX ix_notification_inbox_recipient
    ON notification_inbox (recipient_person_id, created_at DESC);
-- Consumer idempotent: một job chỉ đẩy một tin vào hộp thư của một người
CREATE UNIQUE INDEX ux_notification_inbox_idempotency
    ON notification_inbox (reminder_job_id, recipient_person_id)
    WHERE reminder_job_id IS NOT NULL;

CREATE TRIGGER tg_notification_inbox_touch BEFORE UPDATE ON notification_inbox
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  notification_inbox IS
    'Hop thu trong ung dung — hien thuc InAppProvider cua MVP. read_at IS NULL = chua doc.';

-- =============================================================================
-- 4.5 push_subscription — Đăng ký Web Push (VAPID)
-- =============================================================================
-- app_user_id trỏ tới bảng app_user được tạo ở V5__membership_audit.sql; FK được
-- thêm ở cuối V5 (không thể khai báo ở đây vì bảng đích chưa tồn tại).
CREATE TABLE push_subscription (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    app_user_id   UUID         NOT NULL,
    endpoint      TEXT         NOT NULL,
    p256dh        VARCHAR(255) NOT NULL,
    auth          VARCHAR(255) NOT NULL,
    user_agent    VARCHAR(255),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    failure_count INT          NOT NULL DEFAULT 0,
    last_used_at  TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_push_subscription_failure CHECK (failure_count >= 0)
);

-- endpoint là định danh duy nhất của một subscription theo chuẩn Web Push.
-- Dùng md5 vì endpoint có thể dài hơn giới hạn khoá btree.
CREATE UNIQUE INDEX ux_push_subscription_endpoint ON push_subscription (md5(endpoint));
CREATE INDEX ix_push_subscription_user ON push_subscription (app_user_id) WHERE is_active;

CREATE TRIGGER tg_push_subscription_touch BEFORE UPDATE ON push_subscription
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  push_subscription IS
    'Dang ky Web Push (VAPID). Khi provider tra HTTP 404/410 (subscription het han hoac bi thu hoi) thi XOA dong nay, khong retry vo ich.';
COMMENT ON COLUMN push_subscription.p256dh IS 'Khoa cong khai cua client (base64url). Khoa VAPID cua server nam trong bien moi truong, KHONG luu trong CSDL.';
COMMENT ON COLUMN push_subscription.auth   IS 'Auth secret cua client (base64url).';
COMMENT ON COLUMN push_subscription.app_user_id IS 'FK toi app_user duoc them o cuoi V5__membership_audit.sql.';
