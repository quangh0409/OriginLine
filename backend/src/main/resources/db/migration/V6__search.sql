-- =============================================================================
-- V6__search.sql — Tìm kiếm tên tiếng Việt: có dấu / không dấu
-- Nguồn: TDD v1.0 §5.2 · BA v2.0 §9 (FTS + unaccent, KHÔNG dùng Elasticsearch)
--
-- Yêu cầu: kiều bào gõ "nguyen van tuan" phải tìm ra "Nguyễn Văn Tuấn", và
-- ngược lại gõ có dấu cũng ra kết quả. Cách làm:
--   1. vn_unaccent(text) — hàm IMMUTABLE bỏ dấu + hạ chữ thường (đ -> d).
--   2. person_name.name_unaccented — GENERATED ALWAYS ... STORED, luôn khớp với
--      full_name, không thể quên cập nhật.
--   3. GIN + pg_trgm trên cột không dấu — tìm gần đúng, chịu được lỗi gõ.
--   4. Text search configuration vi_unaccent (simple + từ điển unaccent) và cột
--      tsvector generated — tìm theo từ, hoạt động với cả hai kiểu gõ vì cả lúc
--      lập chỉ mục lẫn lúc truy vấn đều đi qua cùng một từ điển bỏ dấu.
-- =============================================================================

SET search_path = public, ag_catalog;

-- -----------------------------------------------------------------------------
-- 6.1 vn_unaccent — bỏ dấu tiếng Việt, IMMUTABLE
-- -----------------------------------------------------------------------------
-- unaccent(text) một tham số là STABLE nên KHÔNG dùng được trong generated column
-- hay index. Dạng hai tham số unaccent(regdictionary, text) là IMMUTABLE — đây là
-- cách chính thống để bọc lại.
-- Vẫn thêm translate('đĐ') phòng trường hợp bản unaccent.rules của bản dựng
-- Postgres đang chạy không xử lý ký tự đ có gạch ngang; translate chạy sau
-- unaccent nên vô hại nếu unaccent đã xử lý rồi (idempotent).
CREATE OR REPLACE FUNCTION vn_unaccent(txt TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
STRICT
AS $fn$
    SELECT lower(
        translate(
            unaccent('public.unaccent'::regdictionary, txt),
            'đĐ', 'dD'
        )
    );
$fn$;

COMMENT ON FUNCTION vn_unaccent(TEXT) IS
    'Bo dau tieng Viet + ha chu thuong. IMMUTABLE de dung duoc trong generated column va index.';

-- -----------------------------------------------------------------------------
-- 6.2 vn_slugify — sinh nhãn ltree an toàn từ tên có dấu
-- -----------------------------------------------------------------------------
-- ############################################################################
-- # ltree CHỈ chấp nhận nhãn [A-Za-z0-9_]. KHÔNG BAO GIỜ ghép branch.path từ  #
-- # branch.name (có dấu tiếng Việt + khoảng trắng) — luôn ghép từ slug.       #
-- #   'Chi Thượng'  ->  'chi_thuong'                                          #
-- #   'Nhánh Cả'    ->  'nhanh_ca'                                            #
-- ############################################################################
CREATE OR REPLACE FUNCTION vn_slugify(txt TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
STRICT
AS $fn$
    SELECT left(
        btrim(
            regexp_replace(vn_unaccent(txt), '[^a-z0-9]+', '_', 'g'),
            '_'
        ),
        64
    );
$fn$;

COMMENT ON FUNCTION vn_slugify(TEXT) IS
    'Sinh nhan ltree khong dau [a-z0-9_] tu ten hien thi co dau. Dung cho branch.slug / branch.path.';

-- -----------------------------------------------------------------------------
-- 6.3 Text search configuration cho tiếng Việt (bỏ dấu)
-- -----------------------------------------------------------------------------
-- Dựa trên 'simple' (không stemming — tiếng Việt không chia thì/biến hình) và
-- ghép thêm từ điển unaccent để cả chỉ mục lẫn truy vấn đều được bỏ dấu.
DO $do$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_ts_config c
        JOIN pg_namespace n ON n.oid = c.cfgnamespace
        WHERE c.cfgname = 'vi_unaccent' AND n.nspname = 'public'
    ) THEN
        CREATE TEXT SEARCH CONFIGURATION public.vi_unaccent (COPY = pg_catalog.simple);
        ALTER TEXT SEARCH CONFIGURATION public.vi_unaccent
            ALTER MAPPING FOR hword, hword_part, word, asciiword, asciihword, hword_asciipart
            WITH unaccent, simple;
    END IF;
END
$do$;

COMMENT ON TEXT SEARCH CONFIGURATION public.vi_unaccent IS
    'FTS tieng Viet: simple + unaccent. Truy van phai dung dung config nay thi go co dau va khong dau moi cho cung ket qua.';

-- -----------------------------------------------------------------------------
-- 6.4 person_name.name_unaccented (generated) + chỉ mục
-- -----------------------------------------------------------------------------
ALTER TABLE person_name
    ADD COLUMN name_unaccented VARCHAR(255)
        GENERATED ALWAYS AS (vn_unaccent(full_name)) STORED;

ALTER TABLE person_name
    ADD COLUMN name_tsv TSVECTOR
        GENERATED ALWAYS AS (to_tsvector('public.vi_unaccent'::regconfig, full_name)) STORED;

-- Tìm gần đúng / chứa chuỗi con, chịu được lỗi gõ: ILIKE '%tuan%', similarity()
CREATE INDEX ix_person_name_unaccented_trgm
    ON person_name USING gin (name_unaccented gin_trgm_ops);
-- Tìm theo tiền tố và sắp xếp
CREATE INDEX ix_person_name_unaccented
    ON person_name (name_unaccented varchar_pattern_ops);
-- Tìm theo từ (FTS)
CREATE INDEX ix_person_name_tsv
    ON person_name USING gin (name_tsv);
-- Kỵ húy (FR-1.6): so tên mới với tên huý của bậc trên, so ở dạng KHÔNG DẤU để
-- "Nguyễn Văn Tuân" và "Nguyen Van Tuan" đều bị cảnh báo.
CREATE INDEX ix_person_name_huy_unaccented
    ON person_name (name_unaccented) WHERE name_type = 'HUY';

COMMENT ON COLUMN person_name.name_unaccented IS
    'Ten khong dau, GENERATED ALWAYS tu full_name — khong bao gio ghi tay, khong the lech voi full_name.';
COMMENT ON COLUMN person_name.name_tsv IS
    'tsvector sinh tu full_name qua config public.vi_unaccent. Truy van: name_tsv @@ to_tsquery(''public.vi_unaccent'', :q).';

-- Ví dụ truy vấn (để người sau khỏi tự chế biến thể khác):
--   -- gõ không dấu, khớp gần đúng
--   SELECT pn.person_id, pn.full_name
--   FROM person_name pn JOIN person p ON p.id = pn.person_id
--   WHERE p.is_deleted = FALSE
--     AND pn.name_unaccented LIKE vn_unaccent(:q) || '%'
--   ORDER BY similarity(pn.name_unaccented, vn_unaccent(:q)) DESC
--   LIMIT 20;
--
--   -- tìm theo từ, gõ có dấu hay không dấu đều được
--   SELECT pn.person_id, pn.full_name
--   FROM person_name pn
--   WHERE pn.name_tsv @@ websearch_to_tsquery('public.vi_unaccent', :q);

-- -----------------------------------------------------------------------------
-- 6.5 Chỉ mục tìm kiếm bổ trợ trên person
-- -----------------------------------------------------------------------------
CREATE INDEX ix_person_native_place_trgm
    ON person USING gin (vn_unaccent(native_place) gin_trgm_ops)
    WHERE native_place IS NOT NULL;
CREATE INDEX ix_person_current_place_trgm
    ON person USING gin (vn_unaccent(current_place) gin_trgm_ops)
    WHERE current_place IS NOT NULL;
