-- =============================================================================
-- V8__privacy_consent.sql — Mô hình riêng tư theo TỪNG NHÓM TRƯỜNG
-- Nguồn: BA v2.0 §10 · Nghị định 13/2023 · design/ màn "Hồ sơ người sống"
--
-- VẤN ĐỀ ĐANG SỬA
--   person.privacy_level là MỘT mức áp cho CẢ CON NGƯỜI: nới hoặc siết toàn bộ
--   Tầng 3 một lượt. Bản thiết kế đã duyệt hứa người dùng chọn được từng mục —
--   nghề nghiệp "cả họ xem", điện thoại "cùng chi", ngày sinh đầy đủ "riêng tư".
--   Vẽ năm công tắc rồi ánh xạ ngược về một enum là nói dối người dùng về quyền
--   riêng tư của chính họ, nên không có lựa chọn "làm gần đúng".
--
-- CÁCH LÀM
--   Thêm cột JSONB person.privacy_consent: 5 khoá (nhóm trường) × 3 giá trị
--   (PRIVATE | BRANCH | CLAN). Chọn JSONB thay vì bảng phụ vì bộ lọc riêng tư
--   chạy cho TỪNG nhân khẩu trên một projection cây hàng trăm node — một JOIN
--   thêm ở đó là biến chính lớp bảo vệ dữ liệu thành thứ làm chậm phả đồ.
--   Ràng buộc khoá/giá trị được CHECK ép, nên "JSONB tự do" không thành cửa sau.
--
-- MẶC ĐỊNH LÀ KÍN
--   DEFAULT '{}'::jsonb — object rỗng đọc ra là "cả năm nhóm đều PRIVATE"
--   (PrivacyConsent.fromJson). Nhờ vậy hàng mới, và cả nhóm trường được THÊM về
--   sau, tự động bắt đầu ở mức kín mà không cần migration và không có cửa sổ lộ.
-- =============================================================================

SET search_path = public, ag_catalog;

-- -----------------------------------------------------------------------------
-- 8.1 Cột mới
-- -----------------------------------------------------------------------------

ALTER TABLE person
    ADD COLUMN IF NOT EXISTS privacy_consent JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN person.privacy_consent IS
    'Ban dong thuan rieng tu theo tung nhom truong: {occupation, residenceProvince, residenceFull, contact, birthDetailAndPhoto} -> PRIVATE|BRANCH|CLAN. Khoa vang mat = PRIVATE (mac dinh la KIN). Day la Y CHI CUA CHU THE, khong phai ket qua cuoi: khach van khong thay nguoi con song, tre vi thanh nien van an toi da, nguoi da khuat van cong khai.';

-- Cột cũ ở lại làm dấu vết, KHÔNG còn quyết định gì. Không drop ở migration này:
-- dữ liệu trước V8 là căn cứ duy nhất để đối chiếu nếu có khiếu nại về riêng tư.
COMMENT ON COLUMN person.privacy_level IS
    'DI SAN (truoc V8), KHONG con quyet dinh hien thi. Nguon chan ly la person.privacy_consent. Giu lai de doi chieu khi co khieu nai; se drop o mot phien ban sau.';

-- -----------------------------------------------------------------------------
-- 8.2 Hàm hợp lệ hoá — dùng cho CHECK
--
-- CHECK của Postgres không chứa được subquery, mà việc "duyệt mọi khoá của một
-- jsonb" thì cần một. Bọc vào hàm IMMUTABLE là lối đi hợp lệ duy nhất.
-- Schema-qualify tường minh: search_path của session backend có ag_catalog đứng
-- trước (bẫy AGE, README §4) nên CREATE FUNCTION không ghi rõ schema có thể rơi
-- nhầm chỗ.
-- -----------------------------------------------------------------------------

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
                                'contact', 'birthDetailAndPhoto')
                OR kv.v NOT IN ('PRIVATE', 'BRANCH', 'CLAN')
       );
$$;

COMMENT ON FUNCTION public.is_valid_privacy_consent(JSONB) IS
    'Khoa la hoac gia tri la trong privacy_consent bi tu choi ngay o tang CSDL. Mot khoa go sai lot qua se doc thanh PRIVATE (fail-closed) va nguoi dung tuong minh da mo — im lang va sai huong.';

ALTER TABLE person
    ADD CONSTRAINT ck_person_privacy_consent
    CHECK (public.is_valid_privacy_consent(privacy_consent));

-- -----------------------------------------------------------------------------
-- 8.3 Di trú dữ liệu cũ — LỆCH VỀ PHÍA KÍN
--
-- Bảng ánh xạ phải khớp từng dòng với PrivacyLevel.toConsent() trong Java. Lệch
-- nhau là hai người xem cùng một hồ sơ thấy hai kết quả khác nhau tuỳ đường đi.
--
--   DEFAULT (TIER_0 'DEFAULT') -> PRIVATE  : KHONG phai lua chon cua nguoi dung,
--        ma la su VANG MAT cua lua chon. Viec luat cu mo Tang 2 cho nguoi cung
--        chi la quyet dinh cua he thong, khong phai dong thuan cua chu the.
--        Mo hinh moi mac dinh kin, nen "chua chon" phai thanh "rieng tu".
--   RESTRICTED ('TIER_1')      -> PRIVATE  : chu the da chu dong siet.
--   BRANCH_OPT_IN ('TIER_2')   -> BRANCH   : dong thuan ro rang "cho nguoi cung
--        chi xem". Luat cu cho nguoi cung chi thay tron Tang 3, nen nam nhom o
--        muc BRANCH KHONG lo them gi.
--   CLAN_OPT_IN ('TIER_3')     -> CLAN     : dong thuan ro rang "cho ca ho xem".
--        Luat cu cho moi thanh vien da dang nhap thay tron Tang 3.
--
-- Phep di tru nay DON DIEU THEO HUONG KIN: voi bon gia tri cu, tap truong ma mot
-- nguoi xem bat ky nhin thay sau di tru luon la tap con cua tap truoc do.
-- PrivacyConsentMigrationIT canh bat bien nay.
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
        'birthDetailAndPhoto', scope)
      FROM (SELECT CASE legacy
                       WHEN 'TIER_2' THEN 'BRANCH'   -- BRANCH_OPT_IN
                       WHEN 'TIER_3' THEN 'CLAN'     -- CLAN_OPT_IN
                       ELSE 'PRIVATE'                -- DEFAULT, TIER_1, va moi gia tri la
                   END AS scope) AS s;
$$;

COMMENT ON FUNCTION public.privacy_consent_from_legacy(TEXT) IS
    'Doi mot gia tri person.privacy_level cu sang ban dong thuan tuong duong. Phai khop tung dong voi PrivacyLevel.toConsent() ben Java. Tien ich nay con de cac bo gieo du lieu (demo, import) tu dat privacy_consent thay vi de mac dinh kin hoan toan.';

UPDATE person
   SET privacy_consent = public.privacy_consent_from_legacy(privacy_level)
 WHERE privacy_consent = '{}'::jsonb;

-- -----------------------------------------------------------------------------
-- 8.4 Chỉ mục
--
-- KHÔNG đánh index trên privacy_consent. Không truy vấn nào lọc theo nó: bộ lọc
-- riêng tư chạy trong tiến trình trên những hàng ĐÃ nạp, còn "tìm người đã mở
-- liên hệ cho cả họ" là đúng loại truy vấn mà hệ thống này không nên làm cho dễ.
-- -----------------------------------------------------------------------------
