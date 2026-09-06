-- =============================================================================
-- V2__core.sql — Lõi phả hệ: branch · person · person_name · relationship
-- Nguồn: TDD v1.0 §5.1–§5.4 · BA v2.0 §8, §10, §12
--
-- BẤT BIẾN NGHIỆP VỤ (không migration/đoạn code nào sau này được vi phạm):
--  1. XOÁ MỀM. Không bao giờ DELETE cứng một person — node phải ở lại trong đồ
--     thị AGE để cây không đứt. Xoá = person.is_deleted = true.
--  2. Bảng relationship là BẢN CHIẾU (mirror) của cạnh trong graph AGE.
--     NGUỒN CHÂN LÝ LÀ GRAPH. Bảng này tồn tại để có FK, audit và truy vấn SQL
--     thuần. Mọi thao tác ghi phải ghi CẢ HAI trong CÙNG MỘT TRANSACTION.
--  3. Con gái / bên ngoại được ghi nhận đầy đủ ngang bằng con trai (BA v2 §12).
--     Schema không chứa bất kỳ ràng buộc nào ưu tiên giới tính.
-- =============================================================================

SET search_path = public, ag_catalog;

-- -----------------------------------------------------------------------------
-- Hàm tiện ích dùng chung cho mọi bảng chịu mutation
-- -----------------------------------------------------------------------------
-- CHỈ chạm updated_at. TUYỆT ĐỐI KHÔNG tăng cột version trong trigger:
-- Hibernate @Version tự tăng version trong câu UPDATE và so khớp giá trị cũ;
-- nếu trigger tăng thêm một lần nữa thì lần update kế tiếp sẽ ném
-- OptimisticLockException giả.
CREATE OR REPLACE FUNCTION giapha_touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $fn$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END
$fn$;

COMMENT ON FUNCTION giapha_touch_updated_at() IS
    'Trigger BEFORE UPDATE: cap nhat updated_at. Khong dung toi version (do JPA @Version quan ly).';

-- =============================================================================
-- 2.1 branch — Chi / Ngành / Cành / Nhánh (TDD §5.3)
-- =============================================================================
-- CẢNH BÁO ltree: nhãn (label) của ltree CHỈ nhận [A-Za-z0-9_] — KHÔNG nhận dấu
-- tiếng Việt, không nhận khoảng trắng, không nhận dấu gạch ngang.
--   SAI :  root.chi_thượng.nhánh_cả
--   ĐÚNG:  root.chi_thuong.nhanh_ca
-- Vì vậy:
--   * branch.name = tên hiển thị, CÓ dấu tiếng Việt ("Chi Thượng", "Nhánh Cả").
--   * branch.slug = nhãn ltree, sinh từ name bằng unaccent + lower + thay ký tự
--                   lạ thành '_' (hàm public.vn_slugify ở V6__search.sql; ở tầng
--                   ứng dụng là BranchPath VO).
--   * branch.path = ltree ghép từ slug của tổ tiên, KHÔNG BAO GIỜ ghép từ name.
-- =============================================================================
CREATE TABLE branch (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(200) NOT NULL,
    slug              VARCHAR(64)  NOT NULL,
    path              LTREE        NOT NULL,
    parent_id         UUID         REFERENCES branch (id),
    branch_kind       VARCHAR(10)  NOT NULL DEFAULT 'CHI',
    region            VARCHAR(10),
    head_person_id    UUID,
    founded_year      INT,
    note              TEXT,
    sort_order        INT          NOT NULL DEFAULT 0,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_branch_slug_ltree_safe   CHECK (slug ~ '^[a-z0-9_]{1,64}$'),
    CONSTRAINT ck_branch_path_tail_is_slug CHECK (subpath(path, -1)::text = slug),
    CONSTRAINT ck_branch_kind   CHECK (branch_kind IN ('DONG_HO','CHI','NGANH','CANH','NHANH')),
    CONSTRAINT ck_branch_region CHECK (region IS NULL OR region IN ('BAC','TRUNG','NAM')),
    CONSTRAINT ck_branch_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE UNIQUE INDEX ux_branch_path        ON branch USING btree (path);
CREATE INDEX        ix_branch_path_gist   ON branch USING gist  (path);
CREATE INDEX        ix_branch_parent      ON branch (parent_id);
CREATE INDEX        ix_branch_region      ON branch (region) WHERE region IS NOT NULL;
CREATE INDEX        ix_branch_head_person ON branch (head_person_id);

CREATE TRIGGER tg_branch_touch BEFORE UPDATE ON branch
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  branch IS
    'Chi/Nganh/Canh/Nhanh cua dong ho. path (ltree) la pham vi RBAC — xem @RequiresBranch.';
COMMENT ON COLUMN branch.name        IS 'Ten hien thi CO dau tieng Viet.';
COMMENT ON COLUMN branch.slug        IS 'Nhan ltree KHONG DAU [a-z0-9_]. Sinh tu name, khong sua tay.';
COMMENT ON COLUMN branch.path        IS 'Duong dan phan cap ltree, vd root.chi_thuong.nhanh_ca. Nhan cuoi luon = slug.';
COMMENT ON COLUMN branch.branch_kind IS 'DONG_HO (goc) / CHI / NGANH / CANH / NHANH.';
COMMENT ON COLUMN branch.region      IS 'BAC/TRUNG/NAM — chon bo quy tac danh xung theo vung (FR-1.3a).';
COMMENT ON COLUMN branch.head_person_id IS
    'Truong chi — CHUC DANH DONG TOC theo huyet thong/dich ton, tach biet hoan toan voi vai tro ky thuat trong bang role.';

-- =============================================================================
-- 2.2 person — Nhân khẩu, aggregate root (TDD §5.1)
-- =============================================================================
CREATE TABLE person (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    gender            VARCHAR(10)  NOT NULL DEFAULT 'UNKNOWN',
    generation        INT,
    birth_order       INT,
    birth_solar       DATE,
    birth_lunar       JSONB,
    death_solar       DATE,
    death_lunar       JSONB,
    is_alive          BOOLEAN      NOT NULL DEFAULT TRUE,
    native_place      VARCHAR(255),
    current_place     VARCHAR(255),
    primary_branch_id UUID         REFERENCES branch (id),
    lineage_status    VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    attributes        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    privacy_level     VARCHAR(12)  NOT NULL DEFAULT 'DEFAULT',
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMPTZ,
    is_anonymized     BOOLEAN      NOT NULL DEFAULT FALSE,
    anonymized_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_person_gender      CHECK (gender IN ('MALE','FEMALE','UNKNOWN')),
    CONSTRAINT ck_person_generation  CHECK (generation IS NULL OR generation >= 1),
    CONSTRAINT ck_person_birth_order CHECK (birth_order IS NULL OR birth_order >= 1),
    CONSTRAINT ck_person_privacy     CHECK (privacy_level IN ('DEFAULT','TIER_1','TIER_2','TIER_3')),
    CONSTRAINT ck_person_lineage     CHECK (lineage_status IN ('NORMAL','TUYET_TU','KE_TU')),
    CONSTRAINT ck_person_death_order CHECK (
        death_solar IS NULL OR birth_solar IS NULL OR death_solar >= birth_solar
    ),
    CONSTRAINT ck_person_deleted_at  CHECK (is_deleted = FALSE OR deleted_at IS NOT NULL),
    -- Người đã có ngày mất thì không thể còn is_alive = true
    CONSTRAINT ck_person_alive_vs_death CHECK (
        NOT (is_alive AND (death_solar IS NOT NULL OR death_lunar IS NOT NULL))
    ),
    -- Âm lịch tối thiểu phải có day + month (leap mặc định false)
    -- Dùng jsonb_exists() thay cho toán tử ? để tránh bị JDBC hiểu nhầm là placeholder.
    CONSTRAINT ck_person_birth_lunar CHECK (
        birth_lunar IS NULL
        OR (jsonb_exists(birth_lunar, 'day') AND jsonb_exists(birth_lunar, 'month'))
    ),
    CONSTRAINT ck_person_death_lunar CHECK (
        death_lunar IS NULL
        OR (jsonb_exists(death_lunar, 'day') AND jsonb_exists(death_lunar, 'month'))
    )
);

ALTER TABLE branch
    ADD CONSTRAINT fk_branch_head_person FOREIGN KEY (head_person_id) REFERENCES person (id);

CREATE INDEX ix_person_generation  ON person (generation)        WHERE is_deleted = FALSE;
CREATE INDEX ix_person_is_alive    ON person (is_alive)          WHERE is_deleted = FALSE;
CREATE INDEX ix_person_branch      ON person (primary_branch_id) WHERE is_deleted = FALSE;
CREATE INDEX ix_person_is_deleted  ON person (is_deleted);
CREATE INDEX ix_person_death_solar ON person (death_solar)       WHERE death_solar IS NOT NULL;
CREATE INDEX ix_person_attributes  ON person USING gin (attributes jsonb_path_ops);
-- Truy vấn giỗ: lọc theo ngày/tháng âm của ngày mất
CREATE INDEX ix_person_death_lunar ON person USING gin (death_lunar jsonb_path_ops)
    WHERE death_lunar IS NOT NULL;

CREATE TRIGGER tg_person_touch BEFORE UPDATE ON person
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  person IS
    'Nhan khau — aggregate root. XOA MEM BAT BUOC: khong luong nao duoc DELETE dong nay; node AGE tuong ung cung phai giu nguyen.';
COMMENT ON COLUMN person.generation  IS 'Doi thu may tinh tu Thuy to = 1. Suy ra khi them quan he cha–con.';
COMMENT ON COLUMN person.birth_order IS
    'Thu tu sinh trong cac con cua cung nguoi cha (con ca = 1). Dau vao cho is_elder cua rule danh xung: anh cua bo = bac, em cua bo = chu.';
COMMENT ON COLUMN person.birth_lunar IS 'Am lich {year, month, day, leap}.';
COMMENT ON COLUMN person.death_lunar IS
    'Am lich ngay mat — NGUON CHAN LY de tinh gio (BA v2). death_solar chi la tham chieu.';
COMMENT ON COLUMN person.lineage_status IS
    'NORMAL / TUYET_TU (khong nguoi noi doi) / KE_TU (da lap nguoi ke tu). Chi tiet nguoi ke tu nam o canh HEIR.';
COMMENT ON COLUMN person.privacy_level IS
    'Chu the tu chon muc chia se (opt-in Tang 3). DEFAULT = theo chinh sach phan tang BA v2 §10.';
COMMENT ON COLUMN person.is_anonymized IS
    'PDPD/ND 13/2023: quyen xoa du lieu duoc phuc vu bang AN DANH HOA (xoa Tang 3), KHONG xoa node pha he.';
COMMENT ON COLUMN person.version IS 'Optimistic locking (@Version). Trigger khong duoc tu tang cot nay.';

-- =============================================================================
-- 2.3 person_name — Tên đa lớp (TDD §5.2 · FR-1.2, FR-1.6)
-- =============================================================================
-- Cột name_unaccented (generated) + FTS được thêm ở V6__search.sql.
CREATE TABLE person_name (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id    UUID         NOT NULL REFERENCES person (id) ON DELETE CASCADE,
    name_type    VARCHAR(16)  NOT NULL,
    full_name    VARCHAR(255) NOT NULL,
    name_hannom  VARCHAR(255),
    is_primary   BOOLEAN      NOT NULL DEFAULT FALSE,
    note         TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_person_name_type CHECK (
        name_type IN ('HUY','TU','HIEU','THUY','THUONG_GOI','PHAP_DANH')
    ),
    CONSTRAINT ck_person_name_not_blank CHECK (btrim(full_name) <> '')
);

CREATE INDEX ix_person_name_person ON person_name (person_id);
CREATE INDEX ix_person_name_type   ON person_name (name_type);
-- Kỵ húy (FR-1.6): tra nhanh tên huý của bậc trên
CREATE INDEX ix_person_name_huy    ON person_name (full_name) WHERE name_type = 'HUY';
-- Mỗi người chỉ có đúng một tên hiển thị mặc định
CREATE UNIQUE INDEX ux_person_name_primary ON person_name (person_id) WHERE is_primary;
CREATE UNIQUE INDEX ux_person_name_unique  ON person_name (person_id, name_type, full_name);

CREATE TRIGGER tg_person_name_touch BEFORE UPDATE ON person_name
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  person_name IS
    'Ten da lop: 1 person : N ten. ON DELETE CASCADE chi la luoi an toan — person khong bao gio bi xoa cung.';
COMMENT ON COLUMN person_name.name_type IS
    'HUY (ten huy — dung cho canh bao ky huy FR-1.6) / TU / HIEU / THUY (thuy hieu) / THUONG_GOI / PHAP_DANH.';
COMMENT ON COLUMN person_name.name_hannom IS
    'Ten chu Han/Nom, dung cho ban khac bia va OCR o giai doan AI.';

-- =============================================================================
-- 2.4 relationship — BẢN CHIẾU của cạnh graph AGE (TDD §5.4, §6)
-- =============================================================================
-- ###########################################################################
-- #  NGUỒN CHÂN LÝ CỦA QUAN HỆ LÀ CẠNH TRONG graph giapha_graph (Apache AGE).#
-- #  Bảng này CHỈ LÀ BẢN CHIẾU để có FK, audit và truy vấn SQL thuần.        #
-- #   - Không sửa bảng này mà không sửa cạnh AGE tương ứng, và ngược lại.    #
-- #   - Cả hai phải nằm trong CÙNG MỘT TRANSACTION (TreeGraphPort +          #
-- #     RelationshipRepository gọi trong cùng một @Transactional).           #
-- #   - Khi hai bên lệch nhau: GRAPH THẮNG. Job đối soát sửa lại bảng này,   #
-- #     không bao giờ sửa ngược lại graph theo bảng.                         #
-- ###########################################################################
--
-- QUY ƯỚC HƯỚNG CẠNH (chốt tại đây, W2/W3 phải tuân theo):
--   PARENT_BIO / PARENT_ADOPT : from_person_id = CHA/MẸ, to_person_id = CON,
--       tương ứng Cypher  (parent)-[:PARENT {type:'BIO'|'ADOPT'}]->(child)
--       đúng như ví dụ CREATE ở TDD §6.
--       => Truy vấn tổ tiên phải đi NGƯỢC cạnh:  (ego)<-[:PARENT*0..]-(anc)
--       LƯU Ý: đoạn LCA mẫu ở TDD §6 viết (a)-[:PARENT*0..]->(anc), mâu thuẫn
--       với chính ví dụ CREATE ngay phía trên nó. Ở đây chốt theo chiều CREATE
--       (cha -> con); AgeLcaAdapter phải dùng chiều đi vào (<-).
--   SPOUSE : from_person_id = người được ghi trước (thường là chồng),
--       to_person_id = người phối ngẫu. Ngữ nghĩa hai chiều; adapter đọc cả hai
--       hướng. spouse_order đánh số theo from_person_id (vợ cả = 1, vợ hai = 2).
--   HEIR   : from_person_id = NGƯỜI ĐỂ LẠI hương hoả (tổ/cha),
--       to_person_id = NGƯỜI KẾ TỰ / ĐÍCH TÔN.
-- =============================================================================
CREATE TABLE relationship (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    from_person_id UUID        NOT NULL REFERENCES person (id),
    to_person_id   UUID        NOT NULL REFERENCES person (id),
    rel_type       VARCHAR(20) NOT NULL,
    spouse_order   INT,
    heir_type      VARCHAR(16),
    valid_from     DATE,
    valid_to       DATE,
    end_reason     VARCHAR(16),
    note           TEXT,
    attributes     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    is_deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_relationship_type CHECK (
        rel_type IN ('PARENT_BIO','PARENT_ADOPT','SPOUSE','HEIR')
    ),
    CONSTRAINT ck_relationship_no_self CHECK (from_person_id <> to_person_id),
    CONSTRAINT ck_relationship_spouse_order CHECK (
        spouse_order IS NULL OR (rel_type = 'SPOUSE' AND spouse_order >= 1)
    ),
    CONSTRAINT ck_relationship_heir_type CHECK (
        (rel_type = 'HEIR' AND heir_type IN ('DICH_TON','THUA_TU','KE_TU'))
        OR (rel_type <> 'HEIR' AND heir_type IS NULL)
    ),
    CONSTRAINT ck_relationship_valid_range CHECK (
        valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from
    ),
    CONSTRAINT ck_relationship_end_reason CHECK (
        end_reason IS NULL OR end_reason IN ('DIVORCE','DEATH','ANNULLED','OTHER')
    ),
    CONSTRAINT ck_relationship_deleted_at CHECK (
        is_deleted = FALSE OR deleted_at IS NOT NULL
    )
);

CREATE INDEX ix_relationship_from     ON relationship (from_person_id, rel_type) WHERE is_deleted = FALSE;
CREATE INDEX ix_relationship_to       ON relationship (to_person_id,   rel_type) WHERE is_deleted = FALSE;
CREATE INDEX ix_relationship_type     ON relationship (rel_type)                 WHERE is_deleted = FALSE;
CREATE INDEX ix_relationship_valid_to ON relationship (valid_to)                 WHERE rel_type = 'SPOUSE';

-- Một cặp cha–con chỉ có một cạnh cùng loại. Tái hôn được phân biệt bằng
-- valid_from nên SPOUSE cho phép nhiều dòng cho cùng một cặp (kết hôn lại lần 2).
CREATE UNIQUE INDEX ux_relationship_parent
    ON relationship (from_person_id, to_person_id, rel_type)
    WHERE is_deleted = FALSE AND rel_type IN ('PARENT_BIO','PARENT_ADOPT','HEIR');
CREATE UNIQUE INDEX ux_relationship_spouse
    ON relationship (from_person_id, to_person_id, valid_from)
    WHERE is_deleted = FALSE AND rel_type = 'SPOUSE';
-- Thứ tự vợ/chồng (đa thê/đa phu) không được trùng trên cùng một người
CREATE UNIQUE INDEX ux_relationship_spouse_order
    ON relationship (from_person_id, spouse_order)
    WHERE is_deleted = FALSE AND rel_type = 'SPOUSE' AND spouse_order IS NOT NULL;

CREATE TRIGGER tg_relationship_touch BEFORE UPDATE ON relationship
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  relationship IS
    'BAN CHIEU cua canh AGE. NGUON CHAN LY LA GRAPH giapha_graph. Ghi dong bo trong CUNG MOT transaction; khi lech thi graph thang.';
COMMENT ON COLUMN relationship.from_person_id IS
    'PARENT_*: cha/me. SPOUSE: nguoi ghi truoc. HEIR: nguoi de lai huong hoa.';
COMMENT ON COLUMN relationship.to_person_id IS
    'PARENT_*: con. SPOUSE: nguoi phoi ngau. HEIR: nguoi ke tu/dich ton.';
COMMENT ON COLUMN relationship.rel_type IS
    'PARENT_BIO (con ruot) / PARENT_ADOPT (con nuoi) / SPOUSE / HEIR. Anh xa AGE: PARENT{type:BIO|ADOPT}, SPOUSE, HEIR.';
COMMENT ON COLUMN relationship.spouse_order IS
    'Thu tu vo/chong cho da the–da phu: vo ca = 1, vo hai = 2...';
COMMENT ON COLUMN relationship.heir_type IS
    'DICH_TON (dich ton) / THUA_TU (thua tu) / KE_TU (ke tu — noi doi cho nguoi tuyet tu).';
COMMENT ON COLUMN relationship.valid_from IS
    'Ngay bat dau hieu luc — dung cho tai hon, con rieng/con ke (step-children).';
COMMENT ON COLUMN relationship.valid_to IS
    'Ngay ket thuc hieu luc (ly hon, goa). NULL = con hieu luc.';
COMMENT ON COLUMN relationship.is_deleted IS
    'Xoa mem ban chieu. Khi set true thi canh AGE tuong ung phai bi xoa trong cung transaction.';
