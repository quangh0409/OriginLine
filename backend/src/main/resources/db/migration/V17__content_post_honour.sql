-- =============================================================================
-- V17__content_post_honour.sql — Bounded context `content`
--   post (bài viết: nháp → chờ duyệt → đã đăng → gỡ) · honour (vinh danh)
--   + nhóm trường riêng tư THỨ SÁU: `honour`
-- Nguồn: design/07-checklist §2 (Trang chủ và bài viết), BA v2 §10, NĐ 13/2023
--
-- HAI BẢNG, KHÔNG PHẢI MỘT
-- Vinh danh KHÔNG phải một loại bài viết — quyết định đã chốt ở §2. Bài viết là
-- văn bản tự do của một người; vinh danh là BẢN GHI GẮN VỚI NHÂN KHẨU, nên nó
-- phải tra cứu và thống kê được ("chi nào có bao nhiêu người đỗ đạt"). Nhồi hai
-- thứ ấy vào một bảng thì person_id phải nullable, kind phải nullable, và ngay
-- lúc đó cả hai câu thống kê trên đều phải bọc thêm một mệnh đề WHERE mà không
-- ai nhớ.
--
-- CHỖ BẤT ĐỐI XỨNG CÓ CHỦ Ý: post CÓ branch_id, honour KHÔNG
--   post.branch_id  — CHỤP LẠI lúc tạo nháp, y hệt change_request.target_branch_id
--        (V5). Nó phục vụ hàng đợi duyệt: lọc phạm vi bằng MỘT phép ltree trong
--        SQL, không phải JOIN sang person cho từng dòng. Bài viết là tiếng nói
--        của người viết tại thời điểm viết, nên ảnh chụp là đúng ngữ nghĩa.
--   honour          — KHÔNG có cột chi. Chi của một vinh danh luôn suy từ
--        person.primary_branch_id tại lúc đọc. Lý do: vinh danh là dữ liệu VỀ
--        một con người, và câu hỏi nghiệp vụ là "chi nào có bao nhiêu người đỗ
--        đạt". Chụp lại chi thì sau một lần MoveBranch, con số ấy SAI MÀ KHÔNG
--        AI THẤY — bảng vẫn đầy đủ, tổng vẫn khớp, chỉ phân bổ theo chi là sai.
--
-- KHÔNG CÓ CỘT ẢNH, VÀ ĐÓ LÀ MỘT QUYẾT ĐỊNH CHỨ KHÔNG PHẢI MỘT CHỖ SÓT
-- Bản nháp đầu của bảng này có cover_image_key VARCHAR(512) (khoá đối tượng
-- MinIO, đúng quy ước person.avatar_key). Đã bỏ, vì backend KHÔNG CÓ SDK
-- S3/MinIO nào: tìm MinioClient / S3Client / software.amazon trên cả cây nguồn
-- trả 0 kết quả, và dataimport đã hoãn việc lưu tệp gốc vì đúng lý do đó
-- (NoopImportFileStore). Không có SDK thì không có lối tải ảnh lên, tức cột ấy
-- chỉ nhận được giá trị do client tự bịa.
--
-- Một trường trỏ vào hư không tệ hơn một trường vắng mặt: giao diện dựng xong
-- ô đính ảnh, ai cũng tưởng phần ảnh đã có, và nó không bao giờ chạy. Ảnh minh
-- hoạ cho bài viết là việc của đợt sau, và đợt ấy có một điều kiện tiên quyết
-- gọi được tên: thêm SDK MinIO + một bucket + chính sách URL đã ký. Khi đó
-- thêm cột bằng một migration riêng — rẻ hơn nhiều so với việc gỡ một hợp đồng
-- mà frontend đã tin.
--
-- XOÁ MỀM TUYỆT ĐỐI
--   post   — gỡ bài là status = 'WITHDRAWN'. Không có cột is_deleted và không có
--            lối DELETE nào: một bài đã lên trang chủ rồi bị gỡ là dữ kiện phải
--            tra lại được.
--   honour — có is_deleted vì hợp đồng REST có DELETE /honours/{id}. Cột status
--            giữ nguyên nghĩa "trạng thái duyệt"; trộn "đã gỡ" vào status sẽ làm
--            mất thông tin bản ghi ấy từng được duyệt hay chưa.
-- =============================================================================

SET search_path = public, ag_catalog;

-- =============================================================================
-- 17.1 post — bài viết
-- =============================================================================
CREATE TABLE post (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    title            VARCHAR(250) NOT NULL,

    -- Thân bài ở dạng văn bản/markdown. Giới hạn ở tầng ứng dụng, không ở đây:
    -- một cụ 70 tuổi chép lại gia phả giấy có thể gõ rất dài, và cắt ngắn bằng
    -- một CHECK ở CSDL nghĩa là bài bị từ chối sau khi đã gõ xong.
    body             TEXT         NOT NULL,

    status           VARCHAR(12)  NOT NULL DEFAULT 'DRAFT',

    -- NHÂN KHẨU của người viết, NOT NULL. Đây là quyết định đã chốt của chủ dự
    -- án đặt thẳng vào lược đồ: "viết bài là tiếng nói của người trong họ", nên
    -- một tài khoản chưa được duyệt vào phả (app_user.person_id IS NULL) không
    -- dựng nổi một hàng ở bảng này. Kiểm ở tầng ứng dụng trả 403 để người dùng
    -- hiểu chuyện gì xảy ra; ràng buộc ở đây là lưới cuối.
    author_person_id UUID         NOT NULL REFERENCES person (id),

    -- TÀI KHOẢN đã bấm nút. Tách khỏi author_person_id vì hai câu hỏi khác nhau:
    -- "bài này là tiếng nói của ai trong họ" và "ai đăng nhập vào lúc ấy". Khi
    -- một nhân khẩu đổi tài khoản (mất máy, lập lại), câu thứ nhất không đổi.
    author_user_id   UUID         NOT NULL REFERENCES app_user (id),

    -- Chi của tác giả, chụp lúc tạo nháp. NULL khi tác giả chưa gắn chi nào —
    -- và khi đó CHỈ vai toàn dòng họ duyệt được, đúng luật "chi rỗng không phải
    -- chi công cộng" của BranchScopeGuard.
    branch_id        UUID         REFERENCES branch (id),

    published_at     TIMESTAMPTZ,

    -- app_user.id của người duyệt — cùng quy ước với change_request.reviewer_id.
    reviewed_by      UUID         REFERENCES app_user (id),
    reviewed_at      TIMESTAMPTZ,

    -- Lý do trả lại. BẮT BUỘC khi trả về nháp (ép ở tầng domain, xem Post.java):
    -- người viết có quyền biết vì sao, nếu không họ sẽ gửi lại y nguyên.
    reject_reason    TEXT,

    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_post_status CHECK (status IN ('DRAFT', 'PENDING', 'PUBLISHED', 'WITHDRAWN')),

    -- Đã đăng thì phải có mốc thời gian đăng. Trang chủ sắp xếp theo cột này;
    -- một hàng PUBLISHED mà published_at rỗng sẽ rơi khỏi mọi thứ tự.
    CONSTRAINT ck_post_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),

    -- Đã qua tay người duyệt thì phải ghi ĐỦ CẢ HAI: ai và lúc nào. Cùng tinh
    -- thần với ck_change_request_reviewed của V5 — "bài lên trang chủ là tiếng
    -- nói của cả dòng họ" nên trách nhiệm phải có tên.
    CONSTRAINT ck_post_reviewed CHECK ((reviewed_by IS NULL) = (reviewed_at IS NULL)),

    CONSTRAINT ck_post_title_not_blank CHECK (btrim(title) <> '')
);

COMMENT ON TABLE post IS
    'Bai viet cua dong ho. May trang thai: DRAFT -> PENDING -> PUBLISHED; PUBLISHED -> WITHDRAWN; PENDING -> DRAFT (tra lai kem ly do). Khong bao gio DELETE: go bai la doi trang thai.';
COMMENT ON COLUMN post.author_person_id IS
    'Nhan khau cua nguoi viet. NOT NULL: nguoi chua duoc duyet vao pha KHONG viet duoc bai (quyet dinh da chot, design/07-checklist §2).';
COMMENT ON COLUMN post.branch_id IS
    'Chi cua tac gia, CHUP lai luc tao nhap — de hang doi duyet loc pham vi bang mot phep ltree trong SQL. NULL => chi vai toan dong ho duyet duoc.';

CREATE TRIGGER tg_post_touch BEFORE UPDATE ON post
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

-- Trang chu: bai da dang, moi nhat truoc. Index mot phan vi 100% truy van feed
-- deu kem status = 'PUBLISHED'.
CREATE INDEX ix_post_feed ON post (published_at DESC)
    WHERE status = 'PUBLISHED';

-- Hang doi duyet, loc theo chi.
CREATE INDEX ix_post_pending_branch ON post (branch_id, created_at DESC)
    WHERE status = 'PENDING';

-- "Bai cua toi" — nhap chi chinh tac gia thay.
CREATE INDEX ix_post_author ON post (author_person_id, created_at DESC);

-- =============================================================================
-- 17.2 honour — vinh danh
-- =============================================================================
CREATE TABLE honour (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Khoa cung: vinh danh KHONG ton tai roi khoi mot nhan khau.
    person_id     UUID         NOT NULL REFERENCES person (id),

    kind          VARCHAR(16)  NOT NULL,

    title         VARCHAR(250) NOT NULL,

    -- INTEGER chu KHONG phai SMALLINT, du khoang 1000-2200 thua suc vua int2.
    -- Ly do la o tang tren: kieu trong domain va trong JSON deu la Integer, va
    -- Hibernate schema-validation TU CHOI khoi dong khi entity khai Integer ma
    -- cot la int2 ("wrong column type ... found int2, expecting integer"). Hai
    -- byte tiet kiem duoc khong dang doi lay mot cot phai nho ep kieu o moi
    -- lop. Ca duoi 1000 / tren 2200 da bi CHECK chan.
    year          INTEGER,

    issuer        VARCHAR(250),

    description   TEXT,

    status        VARCHAR(12)  NOT NULL DEFAULT 'PENDING',

    created_by    UUID         NOT NULL REFERENCES app_user (id),

    reviewed_by   UUID         REFERENCES app_user (id),
    reviewed_at   TIMESTAMPTZ,
    reject_reason TEXT,

    -- XOA MEM. Hop dong REST co DELETE /honours/{id}; no dat co nay chu khong
    -- bao gio xoa hang.
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_honour_kind CHECK (kind IN ('DO_DAT', 'CHUC_TUOC', 'THANH_TICH', 'KHEN_THUONG')),

    -- Vinh danh khong co giai doan "dang viet": no sinh ra la da cho duyet.
    -- PUBLISHED/WITHDRAWN la hai ket qua cua mot lan duyet.
    CONSTRAINT ck_honour_status CHECK (status IN ('PENDING', 'PUBLISHED', 'WITHDRAWN')),

    CONSTRAINT ck_honour_reviewed CHECK ((reviewed_by IS NULL) = (reviewed_at IS NULL)),

    CONSTRAINT ck_honour_title_not_blank CHECK (btrim(title) <> ''),

    CONSTRAINT ck_honour_year CHECK (year IS NULL OR (year BETWEEN 1000 AND 2200))
);

COMMENT ON TABLE honour IS
    'Vinh danh gan voi nhan khau: do dat / chuc tuoc / thanh tich / khen thuong. KHONG phai mot loai bai viet — no tra cuu va thong ke duoc theo chi. Can duyet nhu bai viet: mot danh hieu TU KHAI ma len thang trang chu la chuyen khac han.';
COMMENT ON COLUMN honour.person_id IS
    'Chu the cua vinh danh. Chi/nganh KHONG duoc chup lai o day — no luon suy tu person.primary_branch_id luc doc, de thong ke theo chi khong sai sau mot lan chuyen chi.';
COMMENT ON COLUMN honour.is_deleted IS
    'Xoa mem. Khong co lenh DELETE nao tren bang nay.';

CREATE TRIGGER tg_honour_touch BEFORE UPDATE ON honour
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

-- Vinh danh cua mot nguoi — loi doc thuong gap nhat (mo ho so).
CREATE INDEX ix_honour_person ON honour (person_id, status)
    WHERE is_deleted = FALSE;

-- Thong ke & loc theo loai.
CREATE INDEX ix_honour_kind ON honour (kind, year DESC)
    WHERE is_deleted = FALSE AND status = 'PUBLISHED';

CREATE INDEX ix_honour_pending ON honour (created_at DESC)
    WHERE is_deleted = FALSE AND status = 'PENDING';

-- =============================================================================
-- 17.3 NHÓM TRƯỜNG RIÊNG TƯ THỨ SÁU — `honour`
--
-- Vinh danh của NGƯỜI CÒN SỐNG là dữ liệu cá nhân (NĐ 13/2023). Mô hình V8 cho
-- phép chính chủ tự quyết từng nhóm, mặc định KÍN, nên nhóm mới không cần di
-- trú dữ liệu và không có cửa sổ lộ: hàng nào chưa có khoá `honour` đọc ra
-- PRIVATE (PrivacyConsent.fromJson).
--
-- NHƯNG "không cần migration" chỉ đúng với DỮ LIỆU, không đúng với RÀNG BUỘC.
-- ck_person_privacy_consent gọi is_valid_privacy_consent(), và hàm ấy LIỆT KÊ
-- TƯỜNG MINH năm khoá hợp lệ. Không thay hàm thì mọi lượt ghi privacy_consent
-- sau khi Java thêm nhóm thứ sáu đều bị CHECK từ chối — vì
-- PrivacyConsent.toJson() ghi ĐỦ mọi khoá, kể cả khoá ở mức PRIVATE.
--
-- Đó là cái giá của việc ép danh sách khoá ở tầng CSDL, và nó đáng: một khoá gõ
-- sai lọt qua sẽ đọc thành PRIVATE (fail-closed) và người dùng tưởng mình đã
-- mở — im lặng và sai hướng.
--
-- CHỈ THAY HÀM, KHÔNG ĐỤNG RÀNG BUỘC. CREATE OR REPLACE FUNCTION không kích
-- hoạt lại CHECK trên dữ liệu cũ, mà cũng không cần: tập khoá chỉ RỘNG THÊM,
-- nên mọi hàng đang hợp lệ vẫn hợp lệ.
-- =============================================================================

CREATE OR REPLACE FUNCTION public.is_valid_privacy_consent(p JSONB)
RETURNS BOOLEAN
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT p IS NOT NULL
       AND jsonb_typeof(p) = 'object'
       AND NOT EXISTS (
            SELECT 1
              FROM jsonb_each_text(p) AS kv(k, v)
             WHERE kv.k NOT IN ('occupation', 'residenceProvince', 'residenceFull',
                                'contact', 'birthDetailAndPhoto', 'honour')
                OR kv.v NOT IN ('PRIVATE', 'BRANCH', 'CLAN')
       );
$$;

COMMENT ON FUNCTION public.is_valid_privacy_consent(JSONB) IS
    'Khoa la hoac gia tri la trong privacy_consent bi tu choi ngay o tang CSDL. Danh sach khoa phai khop TUNG CHU voi enum PrivacyFieldGroup ben Java. Tu V17 co sau nhom: them `honour` (vinh danh cua nguoi con song la du lieu ca nhan).';

-- KHÔNG cập nhật privacy_consent của hàng nào. Nhóm mới bắt đầu ở PRIVATE cho
-- toàn bộ dữ liệu đang có — đó chính là điều mô hình V8 được thiết kế để làm,
-- và là lý do một nhóm trường mới không tạo ra cửa sổ lộ nào.

-- -----------------------------------------------------------------------------
-- 17.4 privacy_consent_from_legacy() — thêm khoá thứ sáu, GÁN CỨNG 'PRIVATE'
--
-- Hàm này dựng bản đồng thuận tương đương từ person.privacy_level trước V8.
-- Mức cũ ấy chưa bao giờ nói gì về vinh danh, nên ánh xạ 'honour' theo `scope`
-- (tức cho TIER_3 cũ thành CLAN) là SUY DIỄN một sự đồng ý chưa từng được đưa
-- ra. Gán cứng PRIVATE.
--
-- Vậy vì sao phải đụng tới hàm, nếu giá trị luôn là PRIVATE? Vì bất biến của dự
-- án là "hàm SQL khớp TỪNG DÒNG với PrivacyLevel.toConsent() bên Java", và
-- PrivacyConsent.toJson() ghi ĐỦ mọi khoá kể cả khoá ở mức PRIVATE — có chủ ý,
-- để một hàng đọc được bằng mắt trong psql khi đi điều tra sự cố riêng tư. Bỏ
-- khoá ở một bên là để hai bản lệch nhau đúng ở chỗ khó phát hiện nhất.
-- PrivacyConsentMigrationIT canh bất biến này.
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.privacy_consent_from_legacy(legacy TEXT)
RETURNS JSONB
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT jsonb_build_object(
        'occupation',          scope,
        'residenceProvince',   scope,
        'residenceFull',       scope,
        'contact',             scope,
        'birthDetailAndPhoto', scope,
        -- KHONG theo `scope`: xem khoi ghi chu o tren.
        'honour',              'PRIVATE')
      FROM (SELECT CASE legacy
                       WHEN 'TIER_2' THEN 'BRANCH'   -- BRANCH_OPT_IN
                       WHEN 'TIER_3' THEN 'CLAN'     -- CLAN_OPT_IN
                       ELSE 'PRIVATE'                -- DEFAULT, TIER_1, va moi gia tri la
                   END AS scope) AS s;
$$;

COMMENT ON FUNCTION public.privacy_consent_from_legacy(TEXT) IS
    'Doi mot gia tri person.privacy_level cu sang ban dong thuan tuong duong. Phai khop tung dong voi PrivacyLevel.toConsent() ben Java. Tu V17 co them khoa `honour`, LUON PRIVATE: muc cu chua bao gio noi gi ve vinh danh nen khong duoc suy dien mot su dong y chua tung duoc dua ra.';

-- KHONG chay UPDATE tren bang person: hang da di tru o V8 van dung, va nhom moi
-- vang mat trong JSON doc ra la PRIVATE. Chay lai phep di tru o day se ghi de
-- lua chon ma nguoi dung da thao tac trong khoang tu V8 toi nay.
