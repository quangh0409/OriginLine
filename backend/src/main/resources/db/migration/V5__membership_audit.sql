-- =============================================================================
-- V5__membership_audit.sql — Tài khoản, vai trò, phân quyền theo chi/ngành,
--                            yêu cầu đính chính và nhật ký audit
--   app_user · role · branch_assignment · change_request · audit_log
-- Nguồn: TDD v1.0 §5.6, §8.4, §10 · BA v2.0 §10
--
-- RBAC HAI CHIỀU (TDD §10):
--   (1) Vai trò kỹ thuật: ADMIN / COUNCIL / BRANCH_HEAD / MEMBER / GUEST
--   (2) PHẠM VI CHI/NGÀNH: mọi kiểm tra quyền phải so ltree path của đối tượng
--       với branch_assignment.branch_id — KHÔNG được chỉ kiểm tra vai trò.
-- Chức danh dòng tộc (Tộc trưởng, Trưởng chi — theo huyết thống/đích tôn) nằm ở
-- branch.head_person_id, TÁCH BIỆT với vai trò kỹ thuật ở đây. Một người có thể
-- giữ cả hai, nhưng đó là hai dữ kiện độc lập.
-- =============================================================================

SET search_path = public, ag_catalog;

-- =============================================================================
-- 5.1 app_user — Tài khoản (ánh xạ keycloak_sub -> app_user -> person)
-- =============================================================================
CREATE TABLE app_user (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_sub  VARCHAR(64)  NOT NULL,
    person_id     UUID         REFERENCES person (id),
    email         VARCHAR(255),
    display_name  VARCHAR(200),
    status        VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
    locale        VARCHAR(5)   NOT NULL DEFAULT 'vi',
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_app_user_status CHECK (status IN ('PENDING','ACTIVE','SUSPENDED','DISABLED')),
    CONSTRAINT ck_app_user_locale CHECK (locale IN ('vi','en'))
);

CREATE UNIQUE INDEX ux_app_user_keycloak_sub ON app_user (keycloak_sub);
-- Một nhân khẩu chỉ gắn với một tài khoản
CREATE UNIQUE INDEX ux_app_user_person ON app_user (person_id) WHERE person_id IS NOT NULL;
CREATE INDEX ix_app_user_status ON app_user (status);

CREATE TRIGGER tg_app_user_touch BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  app_user IS 'Tai khoan he thong. AuthN o Keycloak; backend nhan JWT roi map keycloak_sub -> app_user -> person.';
COMMENT ON COLUMN app_user.keycloak_sub IS 'Claim "sub" cua JWT Keycloak — dinh danh duy nhat, khong doi.';
COMMENT ON COLUMN app_user.person_id    IS 'Nhan khau tuong ung trong pha he. NULL = tai khoan chua duoc ghep vao cay.';
COMMENT ON COLUMN app_user.email        IS 'Du lieu Tang 3 (BA v2 §10) — khong tra ve cho vai tro khong du quyen.';

-- =============================================================================
-- 5.2 role — Danh mục vai trò kỹ thuật
-- =============================================================================
-- TDD §5.6 gộp "role / branch_assignment" thành một dòng mô tả. Ở đây tách:
--   role              = DANH MỤC vai trò (tra cứu, hiển thị, mô tả quyền)
--   branch_assignment = GÁN (user x role x phạm vi chi/ngành)
-- Cách tách này cho phép một người vừa là BRANCH_HEAD của chi A, vừa là MEMBER
-- ở phạm vi toàn họ mà không phải nhân bản cột.
CREATE TABLE role (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    rank        INT          NOT NULL DEFAULT 0,
    is_system   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_role_code CHECK (code IN ('ADMIN','COUNCIL','BRANCH_HEAD','MEMBER','GUEST'))
);

CREATE UNIQUE INDEX ux_role_code ON role (code);

CREATE TRIGGER tg_role_touch BEFORE UPDATE ON role
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

INSERT INTO role (code, name, description, rank) VALUES
    ('ADMIN',       'Quản trị hệ thống',            'Vai trò KỸ THUẬT, phạm vi toàn hệ thống. Không phải chức danh dòng tộc.', 100),
    ('COUNCIL',     'Hội đồng Tộc biểu / Tộc trưởng','Quản trị nội dung toàn dòng họ; sửa được bộ quy tắc danh xưng.',          80),
    ('BRANCH_HEAD', 'Trưởng Chi/Ngành',             'Chỉ quản trị trong nhánh được giao (kiểm tra theo ltree path).',          60),
    ('MEMBER',      'Thành viên',                   'Xem theo phạm vi, sửa hồ sơ của mình, gửi yêu cầu đính chính.',           40),
    ('GUEST',       'Khách',                        'Chỉ xem thông tin công khai; KHÔNG thấy bất kỳ người còn sống nào.',      10)
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE  role IS 'Danh muc vai tro ky thuat. Chuc danh dong toc (Toc truong, Truong chi) nam o branch.head_person_id.';
COMMENT ON COLUMN role.rank IS 'Thu bac de so sanh nhanh; KHONG thay the cho kiem tra pham vi chi/nganh.';

-- =============================================================================
-- 5.3 branch_assignment — Gán vai trò theo phạm vi chi/ngành
-- =============================================================================
CREATE TABLE branch_assignment (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    app_user_id UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role_id     UUID        NOT NULL REFERENCES role (id),
    branch_id   UUID        REFERENCES branch (id),
    valid_from  DATE,
    valid_to    DATE,
    granted_by  UUID        REFERENCES app_user (id),
    note        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_branch_assignment_range CHECK (
        valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from
    )
);

CREATE UNIQUE INDEX ux_branch_assignment
    ON branch_assignment (app_user_id, role_id, COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid));
CREATE INDEX ix_branch_assignment_user   ON branch_assignment (app_user_id);
CREATE INDEX ix_branch_assignment_branch ON branch_assignment (branch_id);

CREATE TRIGGER tg_branch_assignment_touch BEFORE UPDATE ON branch_assignment
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  branch_assignment IS
    'Gan vai tro cho tai khoan kem PHAM VI chi/nganh. @RequiresBranch so ltree path cua doi tuong voi branch nay.';
COMMENT ON COLUMN branch_assignment.branch_id IS
    'NULL = pham vi toan dong ho (ADMIN/COUNCIL). Khac NULL = chi duoc thao tac tren branch do VA moi hau due cua no (path <@ branch.path).';

-- =============================================================================
-- 5.4 change_request — Yêu cầu đính chính chờ Trưởng chi duyệt (TDD §8.4)
-- =============================================================================
CREATE TABLE change_request (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    request_type     VARCHAR(24) NOT NULL,
    person_id        UUID        REFERENCES person (id),
    target_branch_id UUID        REFERENCES branch (id),
    payload          JSONB       NOT NULL,
    reason           TEXT,
    requested_by     UUID        NOT NULL REFERENCES app_user (id),
    status           VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    reviewer_id      UUID        REFERENCES app_user (id),
    review_note      TEXT,
    reviewed_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_change_request_type CHECK (request_type IN (
        'CREATE_PERSON','UPDATE_PERSON','SOFT_DELETE_PERSON',
        'ADD_RELATIONSHIP','REMOVE_RELATIONSHIP','ADD_NAME','MOVE_BRANCH','OTHER'
    )),
    CONSTRAINT ck_change_request_status CHECK (
        status IN ('PENDING','APPROVED','REJECTED','CANCELLED')
    ),
    -- Đã xử lý thì phải có người duyệt và thời điểm duyệt
    CONSTRAINT ck_change_request_reviewed CHECK (
        status = 'PENDING' OR (reviewer_id IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    -- Trừ CREATE_PERSON, mọi yêu cầu đều phải trỏ tới một nhân khẩu có sẵn
    CONSTRAINT ck_change_request_person CHECK (
        request_type = 'CREATE_PERSON' OR person_id IS NOT NULL
    )
);

CREATE INDEX ix_change_request_status   ON change_request (status, created_at DESC);
CREATE INDEX ix_change_request_person   ON change_request (person_id);
CREATE INDEX ix_change_request_branch   ON change_request (target_branch_id) WHERE status = 'PENDING';
CREATE INDEX ix_change_request_reporter ON change_request (requested_by, created_at DESC);

CREATE TRIGGER tg_change_request_touch BEFORE UPDATE ON change_request
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  change_request IS
    'Yeu cau dinh chinh cua thanh vien. Truong chi duyet trong pham vi nhanh cua minh (@RequiresBranch), ngoai nhanh -> 403.';
COMMENT ON COLUMN change_request.payload IS
    'Noi dung de nghi thay doi dang JSONB (chi cac truong duoc sua). Ap dung khi APPROVED, co optimistic lock theo person.version.';

-- =============================================================================
-- 5.5 audit_log — Nhật ký thay đổi (ai / khi / đổi gì)
-- =============================================================================
-- CHỦ Ý: audit_log KHÔNG có updated_at/version — đây là bảng CHỈ GHI THÊM
-- (append-only), là ngoại lệ duy nhất của quy ước "mọi bảng chịu mutation phải
-- có created_at/updated_at/version". Có trigger chặn UPDATE để không ai sửa
-- lịch sử. DELETE vẫn được phép để phục vụ lưu trữ/xoá dữ liệu cũ theo hạn.
CREATE TABLE audit_log (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type     VARCHAR(48)  NOT NULL,
    entity_id       VARCHAR(64)  NOT NULL,
    action          VARCHAR(24)  NOT NULL,
    actor_user_id   UUID         REFERENCES app_user (id),
    actor_person_id UUID         REFERENCES person (id),
    before          JSONB,
    after           JSONB,
    changed_fields  TEXT[],
    at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ip_address      INET,
    user_agent      VARCHAR(255),
    request_id      VARCHAR(64),
    note            TEXT,

    CONSTRAINT ck_audit_log_action CHECK (action IN (
        'CREATE','UPDATE','SOFT_DELETE','RESTORE','ANONYMIZE',
        'LINK_RELATIONSHIP','UNLINK_RELATIONSHIP','MOVE_BRANCH',
        'APPROVE','REJECT','GRANT_ROLE','REVOKE_ROLE',
        'LOGIN','READ_SENSITIVE','EXPORT'
    ))
);

CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id, at DESC);
CREATE INDEX ix_audit_log_actor  ON audit_log (actor_user_id, at DESC);
CREATE INDEX ix_audit_log_at     ON audit_log USING brin (at);

CREATE OR REPLACE FUNCTION audit_log_immutable()
RETURNS trigger
LANGUAGE plpgsql
AS $fn$
BEGIN
    RAISE EXCEPTION 'audit_log la bang chi ghi them (append-only): khong duoc UPDATE.';
END
$fn$;

CREATE TRIGGER tg_audit_log_immutable
    BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();

COMMENT ON TABLE  audit_log IS
    'Nhat ky thay doi (append-only, trigger chan UPDATE). Ghi boi AuditInterceptor. KHONG duoc ghi gia tri nhay cam Tang 3 vao before/after.';
COMMENT ON COLUMN audit_log.entity_id      IS 'Kieu VARCHAR de chua duoc ca khoa UUID lan khoa so cua cac bang khac.';
COMMENT ON COLUMN audit_log.before         IS 'Anh chup truoc khi doi. Voi hanh dong ANONYMIZE chi ghi ten truong bi xoa, KHONG ghi gia tri.';
COMMENT ON COLUMN audit_log.changed_fields IS 'Danh sach truong da doi — de truy van nhanh ma khong phai dien JSONB.';

-- =============================================================================
-- 5.6 Khoá ngoại hoãn lại từ V4 (push_subscription -> app_user)
-- =============================================================================
ALTER TABLE push_subscription
    ADD CONSTRAINT fk_push_subscription_user
    FOREIGN KEY (app_user_id) REFERENCES app_user (id) ON DELETE CASCADE;
