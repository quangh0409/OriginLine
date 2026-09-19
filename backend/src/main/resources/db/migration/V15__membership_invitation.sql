-- =============================================================================
-- V15__membership_invitation.sql — Lời mời vào hệ thống
--   invitation · invitation_attempt
-- Nguồn: design/06-dang-nhap §5 ("Đăng ký, hay đúng hơn: nhận lời mời")
--
-- VÌ SAO BẢNG NÀY TỒN TẠI
-- Dòng họ MỜI người vào; không ai tự ghi danh rồi vào xem phả nhà người khác
-- (realm Keycloak đặt registrationAllowed:false và đó là mặc định vĩnh viễn).
-- Trước V15, cửa duy nhất để đưa người thứ tư vào hệ thống là gõ tay một dòng
-- app_user rồi gọi linkToPerson() — tức là không có cửa nào dùng được thật.
--
-- ĐIỂM MẤU CHỐT: lời mời MANG SẴN person_id. Nhờ vậy người được mời không rơi
-- vào trạng thái "chờ duyệt": Trưởng chi đã chỉ đích danh người mình mời khi
-- phát mã, nên bắt họ chờ duyệt lần nữa là bắt hệ thống hỏi lại một câu đã có
-- đáp án (design 06 §5.2, hình 5 khung 3).
-- =============================================================================

SET search_path = public, ag_catalog;

-- =============================================================================
-- 15.1 invitation — Lời mời đích danh, mang sẵn nhân khẩu sẽ được ghép
-- =============================================================================
CREATE TABLE invitation (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- BĂM SHA-256 CỦA MÃ ĐÃ CHUẨN HOÁ, hex thường. Mã thô KHÔNG bao giờ được
    -- lưu ở bất cứ đâu trong CSDL: nó rời hệ thống đúng một lần, trong phản hồi
    -- của lệnh phát mã, rồi sống tiếp trên tờ phiếu giấy và trong tin nhắn của
    -- Trưởng chi. Một bản sao CSDL bị lộ vì thế không mở được lời mời nào.
    --
    -- Băm KHÔNG muối (unsalted), khác với mật khẩu: tra cứu phải đi qua chỉ mục
    -- nên phép băm buộc phải tất định. An toàn vì mã do CSPRNG sinh, ~50 bit
    -- entropy — không có từ điển nào để dò, khác hẳn mật khẩu do người tự chọn.
    -- VARCHAR chu khong phai CHAR(64), du do dai la co dinh. Hai ly do, ca hai deu tung
    -- lam do mot vong build: (1) Postgres tra kieu CHAR ve duoi ten bpchar va Hibernate
    -- schema-validation tu choi bat ky entity nao khai varchar len no — ca ung dung chet
    -- luc khoi dong, khong phai luc chay cau lenh; (2) CHAR dem khoang trang khi so sanh,
    -- nen mot bam bi cat ngan van "bang" bam day du sau khi padding.
    code_hash      VARCHAR(64)  NOT NULL,

    -- Nhân khẩu sẽ được ghép khi lời mời được nhận. NOT NULL: một lời mời không
    -- trỏ vào ai là một lời mời tạo ra đúng cái hàng đợi "chờ duyệt" mà cả
    -- thiết kế này sinh ra để xoá.
    person_id      UUID         NOT NULL REFERENCES person (id),

    -- Chi của nhân khẩu tại thời điểm phát — ảnh chụp, phục vụ tra soát
    -- "ai đã mời ai vào chi nào". Phép kiểm phạm vi lúc phát đọc ltree hiện
    -- hành của person, KHÔNG đọc cột này.
    branch_id      UUID         REFERENCES branch (id),

    invited_by     UUID         NOT NULL REFERENCES app_user (id),

    status         VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
    expires_at     TIMESTAMPTZ  NOT NULL,

    accepted_by    UUID         REFERENCES app_user (id),
    accepted_at    TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    revoked_reason TEXT,

    note           TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,

    -- CHỦ Ý: KHÔNG có trạng thái 'EXPIRED'. Hết hạn là một phép so sánh với
    -- expires_at, không phải một dòng dữ liệu. Nếu EXPIRED là trạng thái lưu
    -- trữ thì phải có một job đi lật cờ, và mọi lời mời quá hạn sẽ còn dùng
    -- được cho tới khi job ấy chạy — tức là một lỗ hổng có lịch.
    CONSTRAINT ck_invitation_status CHECK (status IN ('PENDING','ACCEPTED','REVOKED')),

    -- Do dai co dinh van la mot rang buoc, chi la mot rang buoc duoc phat bieu ro rang
    -- thay vi an trong kieu cot (xem ghi chu o code_hash).
    CONSTRAINT ck_invitation_code_hash CHECK (code_hash ~ '^[0-9a-f]{64}$'),

    CONSTRAINT ck_invitation_accepted CHECK (
        status <> 'ACCEPTED' OR (accepted_by IS NOT NULL AND accepted_at IS NOT NULL)
    ),
    CONSTRAINT ck_invitation_revoked CHECK (
        status <> 'REVOKED' OR revoked_at IS NOT NULL
    ),
    -- Chỉ ACCEPTED mới được mang người nhận. Chặn ở CSDL để một lệnh UPDATE
    -- viết tay không thể tạo ra dòng "đã có người nhận nhưng vẫn dùng được".
    CONSTRAINT ck_invitation_acceptor CHECK (
        status = 'ACCEPTED' OR (accepted_by IS NULL AND accepted_at IS NULL)
    )
);

-- Mã là bí mật duy nhất: hai lời mời cùng băm là một va chạm, phải nổ ngay.
CREATE UNIQUE INDEX ux_invitation_code_hash ON invitation (code_hash);

-- MỘT NHÂN KHẨU CHỈ CÓ MỘT LỜI MỜI ĐANG MỞ.
-- Phát lại cho cùng một người (mất phiếu, gửi nhầm số) phải thu hồi mã cũ
-- trước; nếu không thì hai mã cùng mở được một hồ sơ và "thu hồi" mất nghĩa.
CREATE UNIQUE INDEX ux_invitation_open_person
    ON invitation (person_id) WHERE status = 'PENDING';

CREATE INDEX ix_invitation_branch  ON invitation (branch_id) WHERE status = 'PENDING';
CREATE INDEX ix_invitation_inviter ON invitation (invited_by, created_at DESC);
CREATE INDEX ix_invitation_person  ON invitation (person_id);

CREATE TRIGGER tg_invitation_touch BEFORE UPDATE ON invitation
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  invitation IS
    'Loi moi dich danh vao he thong. Mang san person_id nen nguoi duoc moi khong phai cho duyet.';
COMMENT ON COLUMN invitation.code_hash IS
    'SHA-256 hex cua ma da chuan hoa. MA THO KHONG BAO GIO duoc luu — no roi he thong dung mot lan, luc phat.';
COMMENT ON COLUMN invitation.person_id IS
    'Nhan khau se duoc ghep. Rang buoc ux_app_user_person bao dam moi nhan khau chi gan mot tai khoan.';
COMMENT ON COLUMN invitation.branch_id IS
    'Anh chup chi luc phat, chi de tra soat. Kiem pham vi doc ltree hien hanh cua person.';
COMMENT ON COLUMN invitation.expires_at IS
    'Het han la phep so sanh, khong phai trang thai luu tru — xem ck_invitation_status.';

-- =============================================================================
-- 15.2 invitation_attempt — Nhật ký thử mã, phục vụ giới hạn tần suất
-- =============================================================================
-- VÌ SAO PHẢI CÓ.
-- Màn nhận lời mời ĐƯỢC hiện tên người sẽ được gắn (quyết định có chủ ý: không
-- hiện thì người nhận không xác nhận được "đúng là tôi" và cả luồng vô nghĩa —
-- design 06 §5.3 phương án (a)). Hệ quả: mã mời trở thành KHOÁ MỞ MỘT CÁI TÊN
-- CỦA NGƯỜI CÒN SỐNG, tức dữ liệu Tầng 1 theo BA v2 §10.
-- Ba lớp chống đỡ đi kèm, và cả ba đều bắt buộc: mã dùng một lần · hạn ngắn ·
-- GIỚI HẠN TẦN SUẤT. Thiếu lớp thứ ba thì một mã 50 bit vẫn không dò ra được,
-- nhưng ta mất khả năng PHÁT HIỆN người đang dò — và đó mới là thứ đáng giá.
--
-- Bảng, không phải Redis: giới hạn tần suất ở đây là một phép đếm trên đường
-- hiếm (vài chục lượt một ngày cho cả dòng họ), còn mất số đếm khi Redis khởi
-- động lại thì lại đúng là mất lớp phòng thủ. Redis vẫn là chỗ đúng nếu về sau
-- lưu lượng đổi bậc.
CREATE TABLE invitation_attempt (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- BĂM SHA-256 của định danh người gọi (địa chỉ IP). Băm chứ không lưu thô:
    -- IP là dữ liệu cá nhân theo Nghị định 13/2023, và ở đây ta chỉ cần biết
    -- "có phải cùng một người gọi không", không cần biết người ấy ở đâu.
    client_key VARCHAR(64) NOT NULL,

    outcome    VARCHAR(16) NOT NULL,
    at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_invitation_attempt_outcome CHECK (
        outcome IN ('FAILED','ACCEPTED_OK','PREVIEW_OK')
    ),
    CONSTRAINT ck_invitation_attempt_key CHECK (client_key ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_invitation_attempt_key ON invitation_attempt (client_key, at DESC);
CREATE INDEX ix_invitation_attempt_at  ON invitation_attempt USING brin (at);

COMMENT ON TABLE invitation_attempt IS
    'Nhat ky thu ma moi. CHI luu bam cua IP va ket qua — khong luu ma thu, ke ca ma sai.';
COMMENT ON COLUMN invitation_attempt.outcome IS
    'FAILED = ma khong dung duoc. Chi FAILED duoc dem vao gioi han tan suat.';
