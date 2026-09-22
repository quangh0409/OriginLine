-- =============================================================================
-- V21__membership_password_reset_grant.sql — Trưởng chi cấp lại liên kết đặt
-- mật khẩu cho một thành viên trong phạm vi của mình
--   password_reset_grant
--
-- VÌ SAO BẢNG NÀY TỒN TẠI
-- Realm bật resetPasswordAllowed:true, nhưng đường phục hồi duy nhất Keycloak
-- biết làm là GỬI THƯ. Sản phẩm lại cho lập tài khoản bằng email HOẶC số điện
-- thoại, và luồng số điện thoại sinh ra chính vì các cụ không có email. Hệ quả
-- trước V21: một cụ đăng ký bằng số máy, quên mật khẩu, thì KHÔNG có đường nào
-- tự lấy lại — messages/vi.json phải bảo họ "gọi Trưởng chi", tức là mỗi lần
-- quên mật khẩu là một cuộc điện thoại, với 1.500 người là một gánh nặng thật.
--
-- ĐÂY LÀ MỘT LỐI CHIẾM TÀI KHOẢN NẾU LÀM ẨU, VÀ BẢNG NÀY LÀ VẾT CỦA NÓ
-- Cấp lại liên kết đặt mật khẩu cho người khác NGHĨA LÀ đặt lại được mật khẩu
-- của người ấy. Không có cách nào làm cho việc ấy vô hại; chỉ có cách làm cho
-- nó KHÔNG IM LẶNG. Vì vậy mỗi lượt cấp để lại đúng ba thứ:
--   1. một dòng ở đây — ai cấp, cho ai, lúc nào, hạn tới bao giờ, dùng chưa;
--   2. một dòng audit_log (bảng chỉ ghi thêm);
--   3. một thông báo gửi thẳng cho CHÍNH CHỦ qua hộp thư trong ứng dụng.
-- Thiếu (3) thì một lượt cấp mà chủ tài khoản không biết là một lượt chiếm tài
-- khoản im lặng, và (1)+(2) chỉ giúp điều tra SAU KHI đã mất.
--
-- KHÔNG LƯU TOKEN, KỂ CẢ BĂM
-- Token của liên kết là chuỗi ký HMAC do SetPasswordTokenCodec đúc, mang đúng
-- id của dòng này và một hạn. Nó không nằm trong CSDL vì không cần: chữ ký đã
-- chứng minh token do máy chủ này phát, còn "một lần" thì do cột used_at dưới
-- đây canh. Một bản sao CSDL bị lộ vì thế không đặt lại được mật khẩu của ai.
-- =============================================================================

SET search_path = public, ag_catalog;

-- =============================================================================
-- 21.1 password_reset_grant — Một lượt cấp lại liên kết đặt mật khẩu
-- =============================================================================
CREATE TABLE password_reset_grant (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Tài khoản ĐƯỢC cấp lại. Cố ý KHÔNG lưu keycloak_sub kèm theo: sub đọc từ
    -- app_user lúc đổi mật khẩu, nên chỉ có một nguồn chân lý. Lưu ảnh chụp sub
    -- ở đây sẽ đẻ ra câu hỏi "khi hai bên lệch thì tin bên nào", và mọi câu trả
    -- lời cho câu hỏi ấy đều là một lỗ hổng.
    target_app_user_id UUID        NOT NULL REFERENCES app_user (id),

    -- Nhân khẩu của tài khoản ấy — khoá để gửi thông báo cho chính chủ và để
    -- tra soát "ai đã cấp cho người chi nào".
    target_person_id   UUID        NOT NULL REFERENCES person (id),

    -- Ảnh chụp chi/ngành tại thời điểm cấp, CHỈ để tra soát. Phép kiểm phạm vi
    -- đọc ltree hiện hành của person, không đọc cột này — y như invitation.
    branch_id          UUID        REFERENCES branch (id),

    issued_by          UUID        NOT NULL REFERENCES app_user (id),
    issued_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ NOT NULL,

    -- MỘT LẦN. Đây là nguồn chân lý duy nhất của tính chất ấy, và nó là một
    -- lệnh UPDATE ... WHERE used_at IS NULL có điều kiện (xem
    -- PasswordResetGrantJdbcAdapter.claim) chứ không phải một câu if trong Java.
    --
    -- Luồng đặt mật khẩu lần ĐẦU (invitation) không cần cột nào như thế này: ở
    -- đó "một lần" suy ra được từ trạng thái thật của Keycloak — tài khoản đã
    -- có mật khẩu chưa. Ở đây tài khoản GẦN NHƯ LUÔN đã có mật khẩu (người ta
    -- quên nó, chứ không phải chưa từng đặt), nên nguồn chân lý ấy biến mất và
    -- phải có một cái thay thế. Đó là lý do duy nhất bảng này tồn tại.
    used_at            TIMESTAMPTZ,

    revoked_at         TIMESTAMPTZ,
    revoked_reason     TEXT,

    note               TEXT,

    -- KHÔNG AI TỰ CẤP CHO CHÍNH MÌNH. Chặn ở CSDL chứ không chỉ ở tầng ứng
    -- dụng: đây là ràng buộc phân quyền, và một lệnh INSERT viết tay (kịch bản
    -- dựng dữ liệu, một script vận hành) không được phép lách qua nó.
    -- "Quên mật khẩu của chính mình" là một luồng khác, đi đường khác.
    CONSTRAINT ck_password_reset_grant_not_self CHECK (issued_by <> target_app_user_id),

    CONSTRAINT ck_password_reset_grant_window CHECK (expires_at > issued_at),

    -- Đã dùng thì không thể đồng thời đã thu hồi: hai cột ấy kể hai câu chuyện
    -- khác nhau về cùng một dòng và người đọc nhật ký sẽ không biết tin cái nào.
    CONSTRAINT ck_password_reset_grant_outcome CHECK (used_at IS NULL OR revoked_at IS NULL)
);

-- MỘT TÀI KHOẢN CHỈ CÓ MỘT LIÊN KẾT ĐANG MỞ.
-- Cấp lại (Trưởng chi đọc nhầm, tin nhắn không tới) phải thu hồi liên kết cũ
-- trước. Nếu không thì hai liên kết cùng mở được một tài khoản và "thu hồi"
-- mất nghĩa — y hệt lập luận của ux_invitation_open_person ở V15.
--
-- Hết hạn KHÔNG gỡ dòng khỏi chỉ mục này (chủ ý: hết hạn là một phép so sánh,
-- không phải một trạng thái lưu trữ), nên lượt cấp sau vẫn phải thu hồi lượt
-- trước. Đó là việc PasswordResetGrantWriter làm, trong cùng transaction.
CREATE UNIQUE INDEX ux_password_reset_grant_open
    ON password_reset_grant (target_app_user_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;

-- Bộ đếm giới hạn tần suất đọc chỉ mục này: "người này đã cấp bao nhiêu lượt
-- trong một giờ qua". Xem javadoc PasswordResetGrantService về việc vì sao
-- KHÔNG dùng lại InviteThrottle cho câu hỏi ấy.
CREATE INDEX ix_password_reset_grant_issuer ON password_reset_grant (issued_by, issued_at DESC);
CREATE INDEX ix_password_reset_grant_target ON password_reset_grant (target_app_user_id, issued_at DESC);

COMMENT ON TABLE  password_reset_grant IS
    'Mot luot Truong chi/Hoi dong cap lai lien ket dat mat khau cho thanh vien trong pham vi. KHONG luu token, ke ca bam.';
COMMENT ON COLUMN password_reset_grant.used_at IS
    'Nguon chan ly cua tinh chat MOT LAN. Dat bang UPDATE ... WHERE used_at IS NULL, khong bang mot cau if.';
COMMENT ON COLUMN password_reset_grant.target_app_user_id IS
    'Tai khoan duoc cap lai. keycloak_sub doc tu app_user luc doi mat khau — mot nguon chan ly, khong luu anh chup.';
COMMENT ON COLUMN password_reset_grant.branch_id IS
    'Anh chup chi luc cap, chi de tra soat. Kiem pham vi doc ltree hien hanh cua person.';
