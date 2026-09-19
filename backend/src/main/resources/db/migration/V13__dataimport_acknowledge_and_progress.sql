-- =============================================================================
-- V13 — XÁC NHẬN CẢNH BÁO CÓ TRÁCH NHIỆM, VÀ MÀN TIẾN ĐỘ THEO CHI
--
-- V9 đã có `import_batch.warnings_acknowledged_at`, nhưng một cái dấu thời gian
-- trơ trọi trả lời được đúng một câu hỏi ("đã tick chưa") và bỏ ngỏ hai câu hỏi
-- quan trọng hơn:
--
--   1. AI tick? Xác nhận đã xem cảnh báo là một hành vi CÓ TRÁCH NHIỆM — nó mở
--      khoá nút ghi 400 người vào phả. Không ghi người thì nhật ký kiểm toán
--      chỉ nói "lúc 21:14 có ai đó đồng ý", và đó là câu vô dụng đúng vào lúc
--      cần nó nhất.
--   2. Tick cho NHỮNG CẢNH BÁO NÀO? Đối soát là một vòng lặp: sửa tệp, kiểm
--      lại, kiểm lại nữa. Nếu lần kiểm sau sinh ra cảnh báo MỚI mà cái tick cũ
--      vẫn còn hiệu lực thì người duyệt đang xác nhận những thứ họ chưa từng
--      thấy — và hệ thống đang nói dối thay họ.
--
-- Cách chốt câu hỏi 2: lưu VÂN TAY của tập cảnh báo ở hai thời điểm.
--   `warnings_digest`              — vân tay của tập cảnh báo ở lần kiểm gần nhất
--   `warnings_acknowledged_digest` — vân tay của tập cảnh báo lúc người ấy tick
-- Hai vân tay khác nhau ⇒ tập cảnh báo đã đổi ⇒ tick cũ HẾT hiệu lực, không cần
-- ai phải nhớ đi xoá nó. Hai vân tay bằng nhau ⇒ kiểm lại mà cảnh báo y nguyên
-- thì KHÔNG bắt tick lại — bắt tick lại cho một danh sách không đổi là cách
-- chắc chắn để lần thứ ba người ta tick mà không đọc.
-- =============================================================================

ALTER TABLE import_batch
    ADD COLUMN warnings_acknowledged_by     UUID REFERENCES app_user (id),
    ADD COLUMN warnings_digest              CHAR(64),
    ADD COLUMN warnings_acknowledged_digest CHAR(64);

-- Dấu thời gian và người xác nhận đi liền một cặp: có cái này mà thiếu cái kia
-- nghĩa là bản ghi trách nhiệm đã khuyết, và khuyết lặng lẽ.
ALTER TABLE import_batch
    ADD CONSTRAINT ck_import_batch_ack_pair CHECK (
        (warnings_acknowledged_at IS NULL AND warnings_acknowledged_by IS NULL)
        OR (warnings_acknowledged_at IS NOT NULL AND warnings_acknowledged_by IS NOT NULL));

COMMENT ON COLUMN import_batch.warnings_acknowledged_by IS
    'AI tick "da xem canh bao". Tick nay mo khoa nut ghi vao pha nen no phai co chu, khong phai mot dau thoi gian vo danh.';
COMMENT ON COLUMN import_batch.warnings_digest IS
    'Van tay (SHA-256 hex) cua tap canh bao o LAN KIEM GAN NHAT. Tinh tu (code, sheet, row_no, field), khong tu cau chu.';
COMMENT ON COLUMN import_batch.warnings_acknowledged_digest IS
    'Van tay cua tap canh bao NGUOI XAC NHAN DA DOC. Lech voi warnings_digest ⇒ kiem lai da sinh canh bao moi ⇒ tick cu het hieu luc.';

-- =============================================================================
-- import_branch_target — SỐ NGƯỜI HỘI ĐỒNG ĐẾM ĐƯỢC TRÊN BẢN PHẢ GỐC
-- =============================================================================
-- Màn tiến độ theo chi phải trả lời "còn thiếu bao nhiêu", và câu ấy chỉ có
-- nghĩa khi biết mẫu số. Mẫu số KHÔNG suy ra được từ dữ liệu đã nhập — nó là
-- kết quả của việc một người ngồi đếm cuốn sổ giấy.
--
-- VÌ SAO LÀ MỘT BẢNG RIÊNG, KHÔNG PHẢI MỘT CỘT TRÊN `branch`:
--   `branch` thuộc context `genealogy`. Con số này là dữ liệu của quy trình
--   NHẬP LIỆU: nó sinh ra lúc khởi động đợt nhập và hết ý nghĩa khi đợt đóng.
--   Treo nó lên `branch` là để một context ghi vào bảng của context khác, đúng
--   thứ ranh giới module dựng ra để chặn.
--
-- VẮNG MẶT MỘT DÒNG = "CHƯA AI ĐẾM", và đó là một câu trả lời thật. Đặt mặc
-- định 0 thì màn tiến độ sẽ báo mọi chi đã xong 100% ngay khi chưa ai nhập gì.
-- =============================================================================
CREATE TABLE import_branch_target (
    branch_id        UUID        PRIMARY KEY REFERENCES branch (id) ON DELETE CASCADE,
    expected_persons INT         NOT NULL,
    counted_by       UUID        REFERENCES app_user (id),
    counted_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    note             TEXT,

    CONSTRAINT ck_import_branch_target_positive CHECK (expected_persons >= 0)
);

COMMENT ON TABLE import_branch_target IS
    'So nguoi Hoi dong Toc bieu dem duoc tren ban pha GIAY cua mot chi. Mau so cua man tien do; vang mat = chua ai dem.';
COMMENT ON COLUMN import_branch_target.expected_persons IS
    'Dem tay tren so giay, KHONG suy ra duoc tu du lieu da nhap. Vi vay no khong bao gio duoc tinh lai tu dong.';
