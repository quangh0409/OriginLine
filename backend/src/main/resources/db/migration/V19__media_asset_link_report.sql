-- =============================================================================
-- V19__media_asset_link_report.sql — Bounded context `media`
--   media_asset (một đối tượng trên MinIO) · media_link (nó thuộc về cái gì)
--   media_report (báo gỡ một tệp đính kèm)
-- Nguồn: BA v2 §9 (media ra object storage), §10 + NĐ 13/2023, V17 khối
--        "KHÔNG CÓ CỘT ẢNH, VÀ ĐÓ LÀ MỘT QUYẾT ĐỊNH CHỨ KHÔNG PHẢI MỘT CHỖ SÓT".
--
-- ĐÂY LÀ MIGRATION MÀ V17 ĐÃ HẸN TRƯỚC
-- V17 bỏ cột `cover_image_key` với một lý do đo được: backend không có SDK
-- S3/MinIO nào, nên cột ấy chỉ nhận được giá trị do client tự bịa. Điều kiện
-- tiên quyết V17 nêu đích danh — "thêm SDK MinIO + một bucket + chính sách URL
-- đã ký" — đã hoàn thành ở đợt này (io.minio:minio 8.5.17,
-- vn.giapha.media.infrastructure.minio). Nên bảng ảnh ra đời ở đây, ở một
-- migration riêng, đúng như V17 đã hẹn.
--
-- BA QUYẾT ĐỊNH CỦA CHỦ DỰ ÁN, GHI THẲNG VÀO LƯỢC ĐỒ
--  1. ẢNH TRONG BÀI ĐI THEO QUYỀN CỦA BÀI, không theo bộ lọc nhóm trường của
--     từng người có mặt trong ảnh. Vì vậy media_asset KHÔNG có cột riêng tư nào
--     và KHÔNG có khoá ngoại sang person: quyền xem một tệp suy ra từ media_link
--     (bài nào / hồ sơ nào), không suy từ nội dung tấm ảnh. Đây là lựa chọn có
--     giá: một tấm ảnh tập thể trong bài đã đăng thì mọi thành viên đều thấy,
--     kể cả người trong ảnh đang để `birthDetailAndPhoto` = PRIVATE. Đổi lại
--     phải có ĐƯỜNG BÁO GỠ — đó là bảng media_report dưới đây, và nếu bảng ấy
--     vắng mặt thì quyết định số 1 không an toàn.
--  2. VIDEO NHẬN TỆP GỐC, KHÔNG CHUYỂN MÃ. Không ffmpeg, không sinh ảnh bìa.
--     Hệ quả ở lược đồ: không có cột `poster_key`, không có cột `variant`, và
--     `duration_ms` được ĐO TỪ HEADER CONTAINER lúc xác nhận (mvhd của MP4 /
--     Info-Duration của WebM), không phải con số client khai.
--  3. ẢNH CHÂN DUNG ĐI QUA NHÓM TRƯỜNG `birthDetailAndPhoto` ĐÃ CÓ (V8/V17).
--     Không thêm khoá riêng tư thứ bảy, không sửa is_valid_privacy_consent().
--     Hệ quả: media_link.owner_type = 'PERSON_AVATAR' và phép kiểm quyền xem
--     nằm ở genealogy (PersonAvatarAccessAdapter), không ở đây. Không thêm khoá
--     đồng thuận thứ bảy, nên không sửa is_valid_privacy_consent() lần nữa.
--
-- MIGRATION NÀY KHÔNG ĐỤNG TỚI BẢNG `person`, VÀ ĐÓ LÀ MỘT TIN TỐT
-- Khoá ảnh chân dung KHÔNG phải một cột. Nó nằm trong JSONB:
--     person.attributes -> '_profile' ->> 'avatarKey'
-- (xem PersonMapper.K_AVATAR). Đây là điều dễ đoán sai nhất khi đọc mã ở đây —
-- cả V17 lẫn bản nháp đầu của chính tệp này đều viết nhầm là "cột
-- person.avatar_key", và một câu SQL dựa vào cái tên đó sẽ chết với "column
-- does not exist" ở đúng lúc đang đi truy một sự cố riêng tư.
--
-- Hệ quả thì có lợi: thêm ảnh chân dung KHÔNG cần một migration nào trên bảng
-- person. Và nó vẫn là NGUỒN CHÂN LÝ cho ô avatar, vì mọi bộ lọc riêng tư đang
-- soi đúng giá trị ấy (PrivacyTierService.toView + toSummary, DirectoryService).
--
-- Vì sao KHÔNG đổi nó thành một khoá ngoại sang media_asset: làm thế là sửa năm
-- chỗ lọc riêng tư cùng lúc để đổi lấy một phép nối — cách chắc chắn nhất để
-- một trong năm chỗ ấy bị bỏ sót và ảnh người còn sống lọt ra. Thay vào đó:
-- media_link ghi quan hệ sở hữu (để dọn tệp mồ côi và để ký URL), còn
-- `_profile.avatarKey` giữ nguyên vai trò cũ, và hai bên được ghi TRONG CÙNG
-- MỘT GIAO DỊCH bởi SetPersonAvatarService.
--
-- XOÁ MỀM, VÀ RANH GIỚI CỦA NÓ
-- Luật "xoá mềm tuyệt đối" của dự án nói về NODE PHẢ HỆ — xoá cứng một person
-- làm đứt liên kết cây. Một đối tượng trên MinIO không phải node phả hệ, và NĐ
-- 13/2023 đòi dữ liệu bị gỡ phải thực sự biến mất. Nên đường dọn XOÁ BYTE trên
-- MinIO nhưng GIỮ HÀNG media_asset với status = 'PURGED' + purged_at: sổ vẫn
-- ghi ai tải lên cái gì lúc nào và nó đã bị dọn khi nào, còn nội dung thì hết.
-- =============================================================================

SET search_path = public, ag_catalog;

-- =============================================================================
-- 19.1 media_asset — một đối tượng trên kho, và vòng đời của nó
--
-- BA TRẠNG THÁI, VÀ TRẠNG THÁI ĐẦU TIÊN LÀ TRẠNG THÁI QUAN TRỌNG NHẤT
--   PENDING — đã phát URL PUT đã ký, CHƯA thấy tệp trên MinIO. Một hàng PENDING
--             KHÔNG phải bằng chứng tệp tồn tại; nó là bằng chứng ai đó đã XIN
--             một chỗ để tải lên. Không một lối đọc nào được phép trả về nó.
--   READY   — backend đã tự statObject + đọc chữ ký byte và thấy tệp thật.
--   PURGED  — byte đã bị xoá khỏi MinIO (gỡ vi phạm, hoặc dọn mồ côi).
--
-- Bất biến: tệp chỉ CÓ THẬT sau khi backend nhìn thấy nó trên MinIO. Tin lời
-- client là cách một bài viết trỏ vào một tệp không tồn tại, và triệu chứng
-- xuất hiện ở máy người đọc chứ không ở log của người tải.
-- =============================================================================
CREATE TABLE media_asset (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Khoá đối tượng trên MinIO. DUY NHẤT: một khoá ↔ một hàng, nên
    -- POST /media/view-urls tra được từ khoá về hàng mà không cần khoá thứ hai,
    -- và nên hai bản ghi không bao giờ cùng trỏ vào một byte (dọn cái này làm
    -- hỏng cái kia).
    object_key         VARCHAR(512) NOT NULL UNIQUE,

    bucket             VARCHAR(63)  NOT NULL,

    kind               VARCHAR(8)   NOT NULL,      -- IMAGE | VIDEO
    status             VARCHAR(8)   NOT NULL DEFAULT 'PENDING',

    -- Kiểu MIME ĐÃ DÒ ĐƯỢC BẰNG CHỮ KÝ BYTE, không phải Content-Type client gửi.
    -- NULL khi còn PENDING: lúc ấy chưa ai nhìn thấy byte nào để mà dò.
    content_type       VARCHAR(100),

    -- Kích thước THẬT, lấy từ statObject lúc xác nhận. Không phải số client khai
    -- lúc xin URL (số ấy chỉ dùng để từ chối sớm, xem MediaLimits).
    size_bytes         BIGINT,

    -- Thời lượng video, ĐỌC TỪ HEADER CONTAINER (mvhd / EBML Info-Duration).
    -- NULL với ảnh. Với video thì NOT NULL khi READY — không đọc được thời lượng
    -- là từ chối, vì một trần thời lượng không đo được là một trần không tồn tại.
    duration_ms        INTEGER,

    -- Chữ thay ảnh (WCAG 2.2 AA, tiêu chí 1.1.1). BẮT BUỘC với IMAGE khi READY —
    -- ép bằng ck_media_alt_required ở dưới. Người dùng chính của sản phẩm này là
    -- các cụ cao niên, trong đó có người dùng trình đọc màn hình; một tấm ảnh
    -- không có chữ thay thế với họ là một khoảng trống câm.
    alt_text           VARCHAR(300),

    uploaded_by        UUID         NOT NULL REFERENCES app_user (id),

    -- KHONG co cot chi o day, va do la mot quyet dinh. Chi cua mot tep KHONG
    -- phai thuoc tinh cua tep — no la thuoc tinh cua BAN GHI MANG tep ay (bai
    -- viet, hoac nhan khau). Chup lai chi cua nguoi tai len se tao ra mot cau
    -- tra loi THU HAI cho cau hoi "ai duyet duoc don bao go tam anh nay", va hai
    -- cau tra loi ay lech nhau ngay lan dau mot bai duoc viet boi nguoi chi Giap
    -- nhung dang o hang doi cua chi At. Chi duy nhat co tham quyen suy tu
    -- media_link -> chu so huu -> MediaOwnerAccessPort.branchOf().

    -- Hạn của URL PUT đã ký. Sau mốc này mà vẫn PENDING thì đường dọn mang đi —
    -- đây là ca "người dùng xin URL rồi bỏ ngang".
    ticket_expires_at  TIMESTAMPTZ  NOT NULL,

    confirmed_at       TIMESTAMPTZ,
    purged_at          TIMESTAMPTZ,

    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_media_kind   CHECK (kind IN ('IMAGE', 'VIDEO')),
    CONSTRAINT ck_media_status CHECK (status IN ('PENDING', 'READY', 'PURGED')),

    -- READY thì phải có ĐỦ bằng chứng backend đã tự nhìn thấy tệp: mốc xác nhận,
    -- kiểu MIME đã dò, và kích thước thật. Thiếu một trong ba nghĩa là có một
    -- lối ghi nào đó đặt READY mà không đi qua ConfirmUploadService — chặn ở đây.
    CONSTRAINT ck_media_ready_evidence CHECK (
        status <> 'READY'
        OR (confirmed_at IS NOT NULL AND content_type IS NOT NULL AND size_bytes IS NOT NULL)),

    -- Trần thời lượng chỉ có nghĩa nếu thời lượng luôn đo được.
    CONSTRAINT ck_media_video_duration CHECK (
        kind <> 'VIDEO' OR status <> 'READY' OR duration_ms IS NOT NULL),
    CONSTRAINT ck_media_image_no_duration CHECK (kind <> 'IMAGE' OR duration_ms IS NULL),

    -- WCAG: ảnh đã sẵn sàng thì phải có chữ thay thế không rỗng.
    CONSTRAINT ck_media_alt_required CHECK (
        kind <> 'IMAGE' OR status <> 'READY' OR btrim(coalesce(alt_text, '')) <> ''),

    CONSTRAINT ck_media_purged CHECK ((status = 'PURGED') = (purged_at IS NOT NULL)),

    CONSTRAINT ck_media_size_positive CHECK (size_bytes IS NULL OR size_bytes > 0)
);

COMMENT ON TABLE media_asset IS
    'Mot doi tuong tren MinIO. PENDING (da phat URL ky, chua thay tep) -> READY (backend da statObject + doc chu ky byte) -> PURGED (byte da xoa, hang o lai lam so). Mot hang PENDING KHONG phai bang chung tep ton tai.';
COMMENT ON COLUMN media_asset.content_type IS
    'Kieu MIME DO BANG CHU KY BYTE luc xac nhan. KHONG phai Content-Type cua client va khong phai duoi tep — mot tep .jpg do nguoi dung dat ten khong noi len dieu gi.';
COMMENT ON COLUMN media_asset.duration_ms IS
    'Thoi luong video doc tu header container (mvhd cua MP4 / Info-Duration cua WebM). Khong chuyen ma, khong ffmpeg. Khong doc duoc thi tu choi.';
COMMENT ON COLUMN media_asset.alt_text IS
    'Chu thay anh — BAT BUOC voi IMAGE (WCAG 2.2 AA 1.1.1). Ep boi ck_media_alt_required.';

CREATE TRIGGER tg_media_asset_touch BEFORE UPDATE ON media_asset
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

-- Duong don #1: ve PENDING qua han (nguoi dung xin URL roi bo ngang).
CREATE INDEX ix_media_pending_expired ON media_asset (ticket_expires_at)
    WHERE status = 'PENDING';

-- Duong don #2: READY khong con media_link nao tro toi. Chi index phan READY;
-- phep NOT EXISTS sang media_link chay tren khoa ngoai co index rieng ben duoi.
CREATE INDEX ix_media_ready ON media_asset (confirmed_at)
    WHERE status = 'READY';

CREATE INDEX ix_media_uploader ON media_asset (uploaded_by, created_at DESC);

-- =============================================================================
-- 19.2 media_link — tệp này thuộc về cái gì
--
-- MỘT TỆP, MỘT CHỦ (ux_media_link_one_owner). Cho một tệp gắn vào hai bài nghe
-- tiện, nhưng nó phá hai thứ cùng lúc: "gỡ khỏi bài A" hoá ra vẫn còn ở bài B
-- nên người báo gỡ tưởng đã xong mà tấm ảnh vẫn hiện; và phép dò mồ côi phải
-- đếm tham chiếu thay vì hỏi một câu NOT EXISTS. Muốn dùng lại một tấm ảnh thì
-- tải lên lần nữa — vài trăm KB rẻ hơn nhiều so với một lỗ trong đường gỡ.
--
-- KHÔNG CÓ KHOÁ NGOẠI SANG post/person, VÀ ĐÓ LÀ CÓ CHỦ Ý. owner_id là khoá đa
-- hình: một module khác thêm một loại chủ sở hữu mới mà không phải sửa bảng
-- này. Cái giá: không có FK nên một hàng mồ côi về phía chủ sở hữu là có thể.
-- Đường dọn chính là thứ dọn nó, và ContentMediaLinker/SetPersonAvatarService
-- gỡ liên kết TRONG CÙNG GIAO DỊCH với thao tác trên chủ sở hữu.
-- =============================================================================
CREATE TABLE media_link (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    media_id    UUID        NOT NULL REFERENCES media_asset (id),

    owner_type  VARCHAR(16) NOT NULL,      -- POST | PERSON_AVATAR
    owner_id    UUID        NOT NULL,

    -- Thu tu hien thi trong bai. Anh chan dung luon 0.
    position    SMALLINT    NOT NULL DEFAULT 0,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_media_link_owner_type CHECK (owner_type IN ('POST', 'PERSON_AVATAR')),
    CONSTRAINT ck_media_link_position CHECK (position >= 0 AND position < 100)
);

-- MOT TEP, MOT CHU.
CREATE UNIQUE INDEX ux_media_link_one_owner ON media_link (media_id);

-- Doc theo chu so huu: "bai nay co nhung tep nao", dung thu tu.
CREATE INDEX ix_media_link_owner ON media_link (owner_type, owner_id, position);

-- Mot nhan khau co DUNG MOT anh chan dung.
CREATE UNIQUE INDEX ux_media_link_avatar ON media_link (owner_id)
    WHERE owner_type = 'PERSON_AVATAR';

COMMENT ON TABLE media_link IS
    'Tep nay thuoc ve cai gi. Mot tep chi co MOT chu (ux_media_link_one_owner): go khoi chu la tep thanh mo coi va duong don mang di. Khong co FK sang post/person — owner_id la khoa da hinh.';

-- =============================================================================
-- 19.3 media_report — ĐƯỜNG BÁO GỠ
--
-- Bảng này là ĐIỀU KIỆN của quyết định số 1, không phải một tính năng thêm.
-- Vì ảnh đi theo quyền của BÀI chứ không theo bộ lọc của từng người có mặt
-- trong ảnh, phải có một lối để người trong họ nói "tấm này gỡ giùm" và một
-- người có thẩm quyền trong phạm vi gỡ được. Không có bảng này thì quyết định
-- số 1 chỉ còn một nửa: nửa mở, không có nửa đóng.
--
-- `note` LÀ VĂN BẢN NGƯỜI DÙNG GÕ VÀ CÓ THỂ CHỨA DỮ LIỆU TẦNG 3
-- ("ảnh này có số điện thoại của mẹ tôi"). Nó KHÔNG BAO GIỜ được chép vào
-- audit_log — MediaReportService ghi audit không kèm `note`, và
-- SensitiveFieldRedactor là lưới cuối.
-- =============================================================================
CREATE TABLE media_report (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    media_id        UUID        NOT NULL REFERENCES media_asset (id),

    reason          VARCHAR(20) NOT NULL,
    note            TEXT,

    reported_by     UUID        NOT NULL REFERENCES app_user (id),

    status          VARCHAR(10) NOT NULL DEFAULT 'OPEN',

    reviewed_by     UUID        REFERENCES app_user (id),
    reviewed_at     TIMESTAMPTZ,
    resolution_note TEXT,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0,

    -- Nam ly do, deu la cau nguoi trong ho that su noi. KHAC bat buoc co `note`
    -- (ep o tang ung dung) — mot bao go khong noi ly do la mot bao go khong xu
    -- duoc, va nguoi duyet se bo qua.
    CONSTRAINT ck_media_report_reason CHECK (
        reason IN ('RIENG_TU', 'SAI_NGUOI', 'KHONG_PHU_HOP', 'BAN_QUYEN', 'KHAC')),

    CONSTRAINT ck_media_report_status CHECK (status IN ('OPEN', 'ACTIONED', 'DISMISSED')),

    CONSTRAINT ck_media_report_reviewed CHECK ((reviewed_by IS NULL) = (reviewed_at IS NULL)),

    -- Da dong thi phai co nguoi ky ten — cung tinh than voi ck_post_reviewed.
    CONSTRAINT ck_media_report_closed CHECK (status = 'OPEN' OR reviewed_by IS NOT NULL)
);

COMMENT ON TABLE media_report IS
    'Bao go mot tep dinh kem. Day la DIEU KIEN cua quyet dinh "anh di theo quyen cua bai": khong co duong go thi quyet dinh ay khong an toan. `note` la van ban nguoi dung go va co the chua du lieu Tang 3 — khong bao gio chep vao audit_log.';

CREATE TRIGGER tg_media_report_touch BEFORE UPDATE ON media_report
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

-- Hang doi cua nguoi duyet.
CREATE INDEX ix_media_report_open ON media_report (created_at DESC)
    WHERE status = 'OPEN';

CREATE INDEX ix_media_report_media ON media_report (media_id, status);

-- Mot nguoi bao mot tep MOT LAN thoi khi don con mo — khong thi mot nguoi buc
-- minh bam muoi lan se dim hang doi cua Truong chi.
CREATE UNIQUE INDEX ux_media_report_open_per_user ON media_report (media_id, reported_by)
    WHERE status = 'OPEN';
