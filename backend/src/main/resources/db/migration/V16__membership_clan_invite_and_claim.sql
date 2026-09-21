-- =============================================================================
-- V16__membership_clan_invite_and_claim.sql — Mã mời DÒNG HỌ + đơn tự nhận mình
--   clan_invite_code · clan_invite_redemption · person_claim
-- Nguồn: design/07-checklist §1 (luồng đã chốt), §1.2 (bốn chốt chặn bắt buộc),
--        §1.4 (sáu ca biên), §1.5 (năm ràng buộc của lối "Tôi chưa có trong phả")
--
-- VÌ SAO ĐÂY LÀ CƠ CHẾ MỚI, KHÔNG PHẢI BIẾN THỂ CỦA V15
-- invitation.person_id là NOT NULL: một lời mời cá nhân LUÔN trỏ đích danh một
-- nhân khẩu, và đó chính là thứ làm người nhận không phải chờ duyệt. Mã dòng họ
-- thì ngược hẳn: nó không trỏ vào ai, dùng được NHIỀU LẦN, và chỉ mở đúng hai
-- cửa — đăng ký tài khoản và xem phả đồ. Dùng xong vẫn phải tự nhận mình và chờ
-- Trưởng chi duyệt. Nhồi hai nghiệp vụ ấy vào một bảng thì person_id phải thành
-- nullable, và ngay lúc đó bất biến "lời mời cá nhân không bao giờ rơi vào hàng
-- chờ duyệt" mất chỗ đứng ở CSDL.
--
-- HỆ QUẢ CỦA VIỆC CẤP MÃ CHO CẢ HỌ (§1.2)
-- Ai cầm được mã là đăng ký được và xem được danh sách người đang sống của dòng
-- họ. Mã ấy SẼ lan: dán vào nhóm Zalo, chuyển tiếp, chụp màn hình. Nghĩa là mã
-- mời mới là ranh giới an toàn thật sự, không phải bước duyệt — nên bốn chốt
-- dưới đây là BẮT BUỘC, không phải tuỳ chọn:
--   (1) expires_at  — có hạn dùng
--   (2) status      — thu hồi được
--   (3) use_count   — ĐẾM LƯỢT DÙNG, chốt quan trọng nhất và dễ bỏ qua nhất
--   (4) giới hạn tần suất — dùng lại invitation_attempt của V15, xem cuối tệp
-- =============================================================================

SET search_path = public, ag_catalog;

-- =============================================================================
-- 16.1 clan_invite_code — Mã mời dòng họ, dùng nhiều lần
-- =============================================================================
CREATE TABLE clan_invite_code (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- BĂM SHA-256 CỦA MÃ ĐÃ CHUẨN HOÁ, hex thường — cùng thuật toán, cùng bảng
    -- chữ Crockford Base32, cùng phép chuẩn hoá với mã cá nhân (InvitationCode).
    -- Mã thô KHÔNG bao giờ được lưu ở bất cứ đâu: nó rời hệ thống đúng một lần,
    -- trong phản hồi của lệnh phát mã. Một bản sao CSDL bị lộ không mở được mã
    -- nào.
    --
    -- VARCHAR chứ không CHAR(64), y như V15 và vì đúng hai lý do đã từng làm đổ
    -- một vòng build: Postgres trả kiểu CHAR về dưới tên bpchar và Hibernate
    -- schema-validation từ chối entity khai varchar lên nó; và CHAR đệm khoảng
    -- trắng khi so sánh, nên một băm bị cắt ngắn vẫn "bằng" băm đầy đủ.
    code_hash      VARCHAR(64)  NOT NULL,

    -- Nhãn để Hội đồng nhận ra mình đã phát mã nào cho kênh nào:
    -- "Nhóm Zalo họ Nguyễn 2026", "Phiếu phát tại lễ giỗ tổ". KHÔNG phải bí mật.
    -- Nó là thứ biến bộ đếm thành thông tin dùng được: "mã dán nhóm Zalo đã
    -- dùng 400 lần" nói được điều gì đó, "mã #3 đã dùng 400 lần" thì không.
    label          VARCHAR(160),

    issued_by      UUID         NOT NULL REFERENCES app_user (id),

    status         VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE',

    -- CHỐT 1 — CÓ HẠN DÙNG. NOT NULL, không có giá trị "vô hạn": một mã không
    -- hạn là một mã vĩnh viễn, và một tờ giấy bỏ quên năm 2026 vẫn mở được phả
    -- năm 2030.
    expires_at     TIMESTAMPTZ  NOT NULL,

    -- CHỐT 3 — ĐẾM LƯỢT DÙNG. Đây là cột quan trọng nhất của cả bảng.
    -- Hội đồng thấy mã đã dùng 400 lần trong khi dòng họ có 600 người thì BIẾT
    -- mà thu hồi. Không có bộ đếm thì mã rò ra và mọi thứ trông vẫn bình thường
    -- — không ai phát hiện được gì.
    --
    -- Tăng bằng một câu UPDATE ... SET use_count = use_count + 1 có điều kiện,
    -- KHÔNG bằng đọc-rồi-ghi ở tầng ứng dụng: hai người bấm cùng lúc thì lối
    -- đọc-rồi-ghi đếm thành một, và một bộ đếm đếm thiếu còn tệ hơn không có
    -- bộ đếm vì nó tạo cảm giác an toàn giả.
    use_count      INTEGER      NOT NULL DEFAULT 0,

    -- Trần lượt dùng, tuỳ chọn. NULL = không giới hạn số lượt (vẫn có hạn thời
    -- gian và vẫn thu hồi được). Đây là chốt thứ năm, không bắt buộc, nhưng rẻ:
    -- Hội đồng biết chi mình có bao nhiêu người thì đặt trần theo con số ấy.
    max_uses       INTEGER,

    revoked_at     TIMESTAMPTZ,
    revoked_reason TEXT,

    note           TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,

    -- CHỦ Ý: chỉ hai trạng thái. "Hết hạn" và "hết lượt" KHÔNG nằm ở đây — cả
    -- hai là phép so sánh (với đồng hồ, với use_count), không phải dòng dữ liệu.
    -- Nếu chúng là trạng thái lưu trữ thì phải có job đi lật cờ, và cho tới khi
    -- job ấy chạy thì mọi mã quá hạn vẫn dùng được: một lỗ hổng có lịch chạy.
    CONSTRAINT ck_clan_invite_status CHECK (status IN ('ACTIVE','REVOKED')),

    CONSTRAINT ck_clan_invite_code_hash CHECK (code_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_clan_invite_use_count CHECK (use_count >= 0),
    CONSTRAINT ck_clan_invite_max_uses  CHECK (max_uses IS NULL OR max_uses >= 1),
    CONSTRAINT ck_clan_invite_revoked   CHECK (
        status <> 'REVOKED' OR revoked_at IS NOT NULL
    )
);

-- Mã là bí mật duy nhất: hai mã cùng băm là một va chạm, phải nổ ngay.
CREATE UNIQUE INDEX ux_clan_invite_code_hash ON clan_invite_code (code_hash);

-- Màn của Hội đồng: mã còn mở trước, mã cũ sau.
CREATE INDEX ix_clan_invite_open ON clan_invite_code (expires_at DESC) WHERE status = 'ACTIVE';

CREATE TRIGGER tg_clan_invite_touch BEFORE UPDATE ON clan_invite_code
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  clan_invite_code IS
    'Ma moi DONG HO: dung nhieu lan, khong tro vao nhan khau nao, chi mo cua dang ky + xem pha.';
COMMENT ON COLUMN clan_invite_code.code_hash IS
    'SHA-256 hex cua ma da chuan hoa. MA THO KHONG BAO GIO duoc luu — no roi he thong dung mot lan.';
COMMENT ON COLUMN clan_invite_code.use_count IS
    'CHOT 3: khong co bo dem thi khong ai phat hien duoc ma da ro ri. Tang bang UPDATE co dieu kien.';
COMMENT ON COLUMN clan_invite_code.expires_at IS
    'CHOT 1: NOT NULL co chu y — ma khong han la ma vinh vien.';

-- =============================================================================
-- 16.2 clan_invite_redemption — Ai đã dùng mã nào
-- =============================================================================
-- VÌ SAO PHẢI CÓ (§1.2, "hai việc nên có thêm").
-- use_count nói CÓ BAO NHIÊU lượt; bảng này nói AI. Khi mã lan ra ngoài, đây là
-- thứ duy nhất truy được người đưa mã ra — và 1.500 người thì "ai đã vào bằng
-- mã nào" là câu hỏi Hội đồng sẽ hỏi, không phải có thể hỏi.
--
-- KHÔNG lưu email, KHÔNG lưu tên tự khai: app_user_id đã trỏ tới đủ mọi thứ ấy
-- và bản thân nó đi qua bộ lọc riêng tư khi hiển thị. Chép lại ở đây là tạo một
-- bản sao dữ liệu cá nhân nằm ngoài mọi bộ lọc.
CREATE TABLE clan_invite_redemption (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    code_id     UUID        NOT NULL REFERENCES clan_invite_code (id),
    app_user_id UUID        NOT NULL REFERENCES app_user (id),

    -- BĂM SHA-256 của định danh người gọi (địa chỉ IP), cùng khoá với
    -- invitation_attempt. Băm chứ không lưu thô: IP là dữ liệu cá nhân theo
    -- Nghị định 13/2023, và câu hỏi duy nhất cần trả lời là "có phải cùng một
    -- người gọi không".
    client_key  VARCHAR(64),

    at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_clan_redemption_key CHECK (
        client_key IS NULL OR client_key ~ '^[0-9a-f]{64}$'
    )
);

-- MỘT TÀI KHOẢN ĐẾM MỘT LƯỢT TRÊN MỘT MÃ.
-- Bấm hai lần, mạng chập chờn, tải lại trang — tất cả phải ra cùng một kết quả.
-- Không có chỉ mục này thì bộ đếm phồng lên vì những lần bấm lại vô hại, và
-- Hội đồng sẽ thu hồi một mã lành vì tưởng nó đã rò.
CREATE UNIQUE INDEX ux_clan_redemption_once ON clan_invite_redemption (code_id, app_user_id);

CREATE INDEX ix_clan_redemption_code ON clan_invite_redemption (code_id, at DESC);
CREATE INDEX ix_clan_redemption_user ON clan_invite_redemption (app_user_id);

COMMENT ON TABLE clan_invite_redemption IS
    'Ai da dung ma nao. use_count noi BAO NHIEU; bang nay noi AI — de truy nguoi dua ma ra ngoai.';

-- =============================================================================
-- 16.3 person_claim — "Tôi là ai trong phả" và "Tôi chưa có trong phả"
-- =============================================================================
-- MỘT BẢNG, HAI LOẠI ĐƠN, VÀ VÌ SAO KHÔNG TÁCH LÀM HAI.
-- Cả hai loại đều là: một tài khoản chưa ghép + một số điện thoại + vài dòng tự
-- giới thiệu + một chi đích quyết định ai duyệt + một máy trạng thái duyệt/từ
-- chối/rút. Khác nhau đúng ở chỗ đơn trỏ vào đâu. Tách làm hai bảng là chép lại
-- máy trạng thái và phép kiểm phạm vi hai lần, rồi để chúng lệch nhau.
--
-- VÌ SAO KHÔNG DÙNG LẠI change_request.
-- change_request.payload là hợp đồng đóng của luồng đính chính (CorrectionPayload,
-- sáu trường, có bộ áp dụng tự động ở genealogy). Nhét đơn nhận mình vào đó thì
-- payload phải mở ra, và cửa kiểm nội dung của luồng đính chính mất nghĩa. Quan
-- trọng hơn: người gửi đơn nhận mình là người CHƯA có person_id, trong khi cả
-- luồng đính chính giả định người gửi đã ở trong phả.
CREATE TABLE person_claim (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- EXISTING   = "tôi là người này trong phả"
    -- NEW_PERSON = "tôi chưa có trong phả" (§1.5)
    kind            VARCHAR(16)  NOT NULL,

    requested_by    UUID         NOT NULL REFERENCES app_user (id),

    -- EXISTING: nhân khẩu được nhận. NEW_PERSON: LUÔN NULL lúc gửi.
    -- RÀNG BUỘC 1 CỦA §1.5 — KHÔNG TẠO NHÂN KHẨU LÚC GỬI ĐƠN.
    -- Xoá mềm là luật tuyệt đối của dự án (không bao giờ xoá cứng một nhân khẩu
    -- vì nó phá liên kết cây), nên tạo trước rồi xoá sau để lại một NODE MA
    -- trong phả cho mỗi đơn bị từ chối. Cột created_person_id bên dưới chỉ được
    -- ghi lúc DUYỆT.
    person_id       UUID         REFERENCES person (id),

    -- RÀNG BUỘC 2 CỦA §1.5 — BẮT BUỘC CHỈ RA NGƯỜI THÂN ĐÃ CÓ TRONG PHẢ.
    -- Không có nó thì nhân khẩu mới thành node mồ côi: không gắn vào cây, không
    -- tính được đời, không tra được danh xưng. Người thân ấy cũng là thứ quyết
    -- định AI DUYỆT, vì người mới chưa thuộc chi nào.
    relative_person_id UUID      REFERENCES person (id),
    relative_kind   VARCHAR(8),

    -- Khai báo của người gửi cho loại NEW_PERSON. Chỉ ba trường, đúng những gì
    -- §1.5 nêu: họ tên, năm sinh, và (thêm) giới tính để dựng được node.
    declared_name   VARCHAR(160),
    declared_birth_year INTEGER,
    declared_gender VARCHAR(8),

    -- Chi đích — ảnh chụp lúc gửi, dùng để so phạm vi ltree khi duyệt.
    -- EXISTING: chi của nhân khẩu được nhận. NEW_PERSON: chi của NGƯỜI THÂN.
    target_branch_id UUID        REFERENCES branch (id),

    -- SỐ ĐIỆN THOẠI NGƯỜI KHAI (§1.4, đã chốt).
    -- Ở ĐÂY nó là dữ liệu của ĐƠN, để Trưởng chi gọi kiểm chứng — không phải là
    -- một bản sao thứ hai của person.contact. Việc ghi nó vào hồ sơ nhân khẩu
    -- khi duyệt cần một bề mặt ghi của genealogy mà hôm nay chưa có; xem javadoc
    -- PersonClaimService#duyet. Hiện hay không thì do chính chủ quyết, vì số
    -- điện thoại thuộc nhóm "contact" của mô hình V8 và nhóm ấy mặc định KÍN.
    phone           VARCHAR(32)  NOT NULL,

    -- Vài dòng tự giới thiệu: "con ông nào, bà nào, quê quán". Đây mới là thứ
    -- Trưởng chi dùng để đối chiếu; số điện thoại chỉ giúp gọi kiểm chứng.
    introduction    TEXT,

    status          VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
    reviewer_id     UUID         REFERENCES app_user (id),
    review_note     TEXT,
    reviewed_at     TIMESTAMPTZ,

    -- Nhân khẩu ĐƯỢC TẠO khi duyệt một đơn NEW_PERSON. NULL ở mọi lúc khác.
    -- Đây là bằng chứng kiểm được của ràng buộc 1: đơn bị từ chối thì cột này
    -- rỗng và trong phả không có thêm một dòng nào.
    created_person_id UUID       REFERENCES person (id),

    -- RÀNG BUỘC 3 CỦA §1.5 — ẢNH CHỤP KẾT QUẢ DÒ TRÙNG, chạy lúc GỬI.
    -- Người khai rất có thể ĐÃ có trong phả dưới một tên khác (tên huý, tên
    -- thường gọi), và đây đúng là kịch bản bộ dò sinh ra để phục vụ. Lưu ảnh
    -- chụp thay vì dò lại lúc duyệt: Trưởng chi phải thấy cùng một thứ mà hệ
    -- thống đã thấy, chứ không phải một kết quả khác vì phả đã đổi trong lúc chờ.
    --
    -- CHỈ chứa khoá + điểm + tín hiệu, KHÔNG chứa tên/năm sinh đọc từ phả: bên
    -- bị nghi có thể là một người CÒN SỐNG ở chi khác mà người gửi đơn không
    -- được xem. Giao diện cầm khoá rồi gọi GET /persons/{id}, nơi bộ lọc phân
    -- tầng riêng tư thật sự chạy. Xem javadoc DuplicateMatch.
    screening       JSONB        NOT NULL DEFAULT '{}'::jsonb,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_person_claim_kind   CHECK (kind IN ('EXISTING','NEW_PERSON')),
    CONSTRAINT ck_person_claim_status CHECK (
        status IN ('PENDING','APPROVED','REJECTED','CANCELLED')
    ),
    CONSTRAINT ck_person_claim_relative_kind CHECK (
        relative_kind IS NULL OR relative_kind IN ('FATHER','MOTHER','SPOUSE')
    ),
    CONSTRAINT ck_person_claim_gender CHECK (
        declared_gender IS NULL OR declared_gender IN ('MALE','FEMALE','UNKNOWN')
    ),

    -- Hình dạng của hai loại đơn, phát biểu ở CSDL chứ không chỉ ở Java.
    -- EXISTING phải trỏ vào một nhân khẩu và không được mang khai báo nào;
    -- NEW_PERSON thì ngược lại và BẮT BUỘC có người thân (ràng buộc 2 của §1.5).
    CONSTRAINT ck_person_claim_shape CHECK (
        (kind = 'EXISTING'
             AND person_id IS NOT NULL
             AND relative_person_id IS NULL
             AND declared_name IS NULL)
        OR
        (kind = 'NEW_PERSON'
             AND person_id IS NULL
             AND relative_person_id IS NOT NULL
             AND relative_kind IS NOT NULL
             AND declared_name IS NOT NULL)
    ),

    -- Chỉ đơn NEW_PERSON đã DUYỆT mới được mang nhân khẩu vừa tạo.
    -- Chặn ở CSDL để một lệnh UPDATE viết tay không tạo ra dòng "đã từ chối mà
    -- vẫn sinh người".
    CONSTRAINT ck_person_claim_created CHECK (
        created_person_id IS NULL
        OR (kind = 'NEW_PERSON' AND status = 'APPROVED')
    ),

    CONSTRAINT ck_person_claim_reviewed CHECK (
        status = 'PENDING' OR (reviewer_id IS NOT NULL AND reviewed_at IS NOT NULL)
    )
);

-- MỘT TÀI KHOẢN CHỈ CÓ MỘT ĐƠN ĐANG CHỜ.
-- Không có nó thì màn này thành cách dò đúng người bằng cách gửi hàng loạt đơn
-- rồi xem đơn nào được duyệt (§1.4, ca "bị từ chối rồi").
CREATE UNIQUE INDEX ux_person_claim_open_requester
    ON person_claim (requested_by) WHERE status = 'PENDING';

-- CỐ Ý KHÔNG CÓ chỉ mục duy nhất trên (person_id) WHERE status='PENDING'.
-- §1.4: hai người cùng nhận một nhân khẩu thì để Trưởng chi thấy CẢ HAI rồi
-- chọn, không ưu tiên người gửi trước — trùng tên trong dòng họ là chuyện
-- thường, và người gửi trước chưa chắc là người đúng. Một chỉ mục duy nhất ở
-- đây sẽ âm thầm biến "ai gửi trước thắng" thành luật.
CREATE INDEX ix_person_claim_person   ON person_claim (person_id) WHERE status = 'PENDING';
CREATE INDEX ix_person_claim_branch   ON person_claim (target_branch_id) WHERE status = 'PENDING';
CREATE INDEX ix_person_claim_requester ON person_claim (requested_by, created_at DESC);
CREATE INDEX ix_person_claim_relative ON person_claim (relative_person_id);

CREATE TRIGGER tg_person_claim_touch BEFORE UPDATE ON person_claim
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  person_claim IS
    'Don tu nhan minh trong pha (EXISTING) va don xin duoc them vao pha (NEW_PERSON).';
COMMENT ON COLUMN person_claim.person_id IS
    'CHI don EXISTING. Don NEW_PERSON KHONG tao nhan khau luc gui — xem created_person_id.';
COMMENT ON COLUMN person_claim.relative_person_id IS
    'Nguoi than da co trong pha. Bat buoc voi NEW_PERSON: no chong node mo coi VA quyet dinh ai duyet.';
COMMENT ON COLUMN person_claim.created_person_id IS
    'Nhan khau duoc tao LUC DUYET. Rong o moi don bi tu choi — bang chung rang khong co node ma.';
COMMENT ON COLUMN person_claim.screening IS
    'Anh chup ket qua do trung luc gui. Chi khoa + diem + tin hieu, KHONG co gia tri doc tu pha.';
COMMENT ON COLUMN person_claim.phone IS
    'So dien thoai nguoi khai, de Truong chi goi kiem chung. Khong phai ban sao cua person.contact.';

-- =============================================================================
-- 16.4 CHỐT 4 — GIỚI HẠN TẦN SUẤT: dùng lại invitation_attempt của V15
-- =============================================================================
-- KHÔNG dựng bảng đếm thứ hai, và đó là quyết định chứ không phải tiết kiệm.
-- Câu hỏi mà cả hai luồng hỏi là một: "người gọi này đã thử sai mã bao nhiêu
-- lần trong một giờ qua?". Hai bộ đếm tách rời nghĩa là kẻ dò được cấp ngưỡng
-- gấp đôi, chỉ bằng cách xen kẽ hai endpoint — và không ai nhìn ra điều đó khi
-- đọc riêng từng bảng.
--
-- Mã dòng họ ngắn (10 ký tự, ~50 bit) để đọc qua điện thoại, nên nó cũng ngắn
-- để đoán. Khác mã cá nhân ở một điểm làm mọi thứ nặng hơn: mã cá nhân chết sau
-- một lần dùng, còn mã dòng họ SỐNG SUỐT HẠN — dò trúng là mở được cửa cho tới
-- khi Hội đồng thu hồi. Vì vậy giới hạn tần suất ở đây không phải lớp phòng thủ
-- thứ ba mà là lớp phòng thủ thứ nhất.
--
-- Thêm hai kết quả để phân biệt được lượt thử của luồng nào khi đọc nhật ký.
-- CHỈ 'FAILED' được đếm vào ngưỡng, y như V15: một người gõ sai vài lần rồi gõ
-- đúng không phải là người đang tấn công.
ALTER TABLE invitation_attempt DROP CONSTRAINT ck_invitation_attempt_outcome;
ALTER TABLE invitation_attempt ADD CONSTRAINT ck_invitation_attempt_outcome CHECK (
    outcome IN ('FAILED','ACCEPTED_OK','PREVIEW_OK','CLAN_PREVIEW_OK','CLAN_REGISTER_OK')
);

COMMENT ON COLUMN invitation_attempt.outcome IS
    'FAILED = ma khong dung duoc (ca ma ca nhan lan ma dong ho). CHI FAILED duoc dem vao nguong.';
