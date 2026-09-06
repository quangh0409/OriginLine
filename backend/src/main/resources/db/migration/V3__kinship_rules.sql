-- =============================================================================
-- V3__kinship_rules.sql — Rule engine danh xưng (FR-1.3a · TDD §5.5)
--
-- NGUYÊN TẮC: DANH XƯNG LÀ DỮ LIỆU, KHÔNG PHẢI CODE.
-- Không được hard-code bất kỳ danh xưng nào trong Java. KinshipResolver chỉ so
-- khớp RelationFacts với các dòng của bảng kinship_rule.
--
-- Kế thừa & ghi đè:  DEFAULT -> REGION -> CLAN -> BRANCH
-- Hội đồng Tộc biểu sửa được qua API /api/v1/kinship-rules (quyền COUNCIL).
--
-- -----------------------------------------------------------------------------
-- ĐỊNH NGHĨA CÁC CHIỀU SO KHỚP (W3 phải hiện thực đúng như mô tả này)
-- -----------------------------------------------------------------------------
-- Cho ego = A (người hỏi) và alter = B (người được gọi). AGE trả về LCA:
--     dist_a = số đời từ A đi lên tới LCA
--     dist_b = số đời từ B đi lên tới LCA
--
--   gen_delta         = dist_a - dist_b
--                       > 0 : B thuộc đời TRÊN A (bố, bác, ông...)
--                       = 0 : cùng đời (anh/chị/em)
--                       < 0 : B thuộc đời DƯỚI A (con, cháu, chắt...)
--
--   collateral_degree = min(dist_a, dist_b)  — "bậc bàng hệ"
--                       0 = trực hệ (bố, ông, cụ / con, cháu, chắt)
--                       1 = bàng hệ bậc 1, chung bố mẹ với đường của A
--                           => anh/chị/em RUỘT, bác/chú/cô/cậu/dì RUỘT, cháu ruột
--                       2 = bàng hệ bậc 2, chung ông bà (con chú con bác)
--                           => anh/chị/em HỌ, bác/chú/cô HỌ, cháu họ
--                       >=3 = họ xa
--       *** ĐÂY LÀ CHIỀU BẮT BUỘC PHẢI CÓ MÀ TDD §5.5 THIẾU. ***
--       Chỉ với (gen_delta, side, gender, is_elder) thì KHÔNG THỂ phân biệt
--       "bác ruột" với "bác họ", cũng không phân biệt "anh ruột" với "anh họ".
--
--   side              PATERNAL = bên nội · MATERNAL = bên ngoại
--                     BLOOD    = huyết thống, KHÔNG phân biệt nội/ngoại
--                                (đóng vai trò ký tự đại diện; luật khớp side
--                                 chính xác luôn thắng nhờ priority nhỏ hơn)
--                     IN_LAW   = quan hệ qua hôn nhân (dâu/rể/thông gia)
--       Cách xác định side (chốt ở đây để W3 làm đúng):
--         * gen_delta >= 0 : đi từ A lên tới LCA — bước đầu tiên qua CHA thì
--           PATERNAL, qua MẸ thì MATERNAL.
--         * gen_delta < 0  : đi từ B lên tới LCA — nhánh nối vào A qua CON TRAI
--           của A thì PATERNAL (cháu nội), qua CON GÁI thì MATERNAL (cháu ngoại).
--
--   gender            giới tính của B (người được gọi). NULL = mọi giới.
--
--   is_elder          TRUE  = đường của B là VAI TRÊN so với đường của A
--                     FALSE = vai dưới ·  NULL = không xét
--       * gen_delta = 0 : B sinh trước A (so birth_order rồi tới birth_solar).
--       * gen_delta > 0 : so tại đời ngay dưới LCA — tổ tiên của B ở đời đó
--         sinh trước hay sau tổ tiên của A ở đời đó.
--         (anh của bố => bác · em trai của bố => chú)
--       * gen_delta < 0 : lấy nghịch đảo của trường hợp trên.
--
--   link_side / link_gender / in_law_direction  — chỉ dùng khi side = 'IN_LAW'.
--       Quan hệ dâu/rể được tính GIÁN TIẾP: trước hết giải quan hệ huyết thống
--       tới NGƯỜI NỐI (người vừa là ruột thịt của A, vừa là vợ/chồng của B),
--       rồi mới ánh xạ sang danh xưng cho B.
--         link_gender      = giới tính của NGƯỜI NỐI
--         link_side        = bên nội/ngoại của NGƯỜI NỐI
--         in_law_direction = ALTER_IS_SPOUSE : B là vợ/chồng của người ruột
--                                              thịt của A  (thím, mợ, chị dâu,
--                                              con dâu, con rể...)
--                          = EGO_IS_SPOUSE   : A là dâu/rể; B là người ruột
--                                              thịt của vợ/chồng A
--                                              (bố chồng, mẹ vợ...)
--       Ví dụ "thím": người nối = em trai của bố (gen_delta 1, collateral 1,
--       link_side PATERNAL, link_gender MALE, is_elder FALSE), B là FEMALE
--       => title 'thím'.
--
--   direct_link       Khớp trực tiếp theo CẠNH thay vì theo LCA — dùng cho các
--                     quan hệ mà LCA vô nghĩa hoặc cần ưu tiên tuyệt đối:
--                     SPOUSE (vợ/chồng), PARENT_ADOPT (bố/mẹ nuôi, con nuôi),
--                     HEIR (đích tôn/thừa tự/kế tự).
--                     Luật có direct_link luôn được xét TRƯỚC (priority nhỏ).
--                     direct_link_reversed = TRUE nghĩa là cạnh đi từ B sang A.
--                     direct_link_subtype khớp thêm phân loại cạnh: HEIR ->
--                     DICH_TON/THUA_TU/KE_TU, PARENT_* -> BIO/ADOPT.
--
-- THỨ TỰ ƯU TIÊN: priority TĂNG DẦN — priority NHỎ HƠN được chọn trước.
-- Luật ở rule set con (BRANCH/CLAN/REGION) ghi đè luật cha khi trùng
-- relation_code hoặc khi khớp với priority nhỏ hơn.
-- =============================================================================

SET search_path = public, ag_catalog;

-- =============================================================================
-- 3.1 kinship_rule_set — Bộ quy tắc (TDD §5.5)
-- =============================================================================
CREATE TABLE kinship_rule_set (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    scope       VARCHAR(10)  NOT NULL,
    parent_id   UUID         REFERENCES kinship_rule_set (id),
    region      VARCHAR(10),
    branch_id   UUID         REFERENCES branch (id),
    description TEXT,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_rule_set_scope  CHECK (scope IN ('DEFAULT','REGION','CLAN','BRANCH')),
    CONSTRAINT ck_rule_set_region CHECK (region IS NULL OR region IN ('BAC','TRUNG','NAM')),
    CONSTRAINT ck_rule_set_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id),
    -- DEFAULT là gốc: không cha, không vùng, không chi.
    CONSTRAINT ck_rule_set_default_shape CHECK (
        scope <> 'DEFAULT' OR (parent_id IS NULL AND region IS NULL AND branch_id IS NULL)
    ),
    -- REGION phải nêu rõ vùng miền và phải kế thừa từ một bộ cha.
    CONSTRAINT ck_rule_set_region_shape CHECK (
        scope <> 'REGION' OR (region IS NOT NULL AND parent_id IS NOT NULL)
    ),
    -- CLAN/BRANCH phải gắn với một branch: CLAN gắn branch gốc (kind = DONG_HO),
    -- BRANCH gắn đúng chi/nhánh được áp dụng.
    CONSTRAINT ck_rule_set_branch_shape CHECK (
        scope NOT IN ('CLAN','BRANCH') OR (branch_id IS NOT NULL AND parent_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_kinship_rule_set_code ON kinship_rule_set (code);
-- Chỉ được có duy nhất một bộ DEFAULT đang hoạt động (index trên chính cột scope
-- vì mọi dòng lọt qua WHERE đều có scope = 'DEFAULT')
CREATE UNIQUE INDEX ux_kinship_rule_set_default
    ON kinship_rule_set (scope) WHERE scope = 'DEFAULT' AND is_active;
-- Mỗi vùng miền chỉ một bộ REGION đang hoạt động
CREATE UNIQUE INDEX ux_kinship_rule_set_region
    ON kinship_rule_set (region) WHERE scope = 'REGION' AND is_active;
-- Mỗi chi/nhánh chỉ một bộ đang hoạt động ở mỗi cấp
CREATE UNIQUE INDEX ux_kinship_rule_set_branch
    ON kinship_rule_set (branch_id, scope) WHERE branch_id IS NOT NULL AND is_active;
CREATE INDEX ix_kinship_rule_set_parent ON kinship_rule_set (parent_id);

CREATE TRIGGER tg_kinship_rule_set_touch BEFORE UPDATE ON kinship_rule_set
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

-- Chuỗi kế thừa phải là cây — chặn chu trình parent_id ngay ở CSDL, vì một chu
-- trình sẽ làm resolveFor() lặp vô hạn khi hợp nhất DEFAULT->REGION->CLAN->BRANCH.
CREATE OR REPLACE FUNCTION kinship_rule_set_no_cycle()
RETURNS trigger
LANGUAGE plpgsql
AS $fn$
DECLARE
    cur   UUID := NEW.parent_id;
    hops  INT  := 0;
BEGIN
    WHILE cur IS NOT NULL LOOP
        IF cur = NEW.id THEN
            RAISE EXCEPTION 'kinship_rule_set: chu trinh ke thua tai % (code=%)', NEW.id, NEW.code;
        END IF;
        hops := hops + 1;
        IF hops > 32 THEN
            RAISE EXCEPTION 'kinship_rule_set: chuoi ke thua qua sau (>32) tai %', NEW.id;
        END IF;
        SELECT parent_id INTO cur FROM kinship_rule_set WHERE id = cur;
    END LOOP;
    RETURN NEW;
END
$fn$;

CREATE TRIGGER tg_kinship_rule_set_no_cycle
    BEFORE INSERT OR UPDATE OF parent_id ON kinship_rule_set
    FOR EACH ROW EXECUTE FUNCTION kinship_rule_set_no_cycle();

COMMENT ON TABLE  kinship_rule_set IS
    'Bo quy tac danh xung (FR-1.3a). Ke thua & ghi de: DEFAULT -> REGION -> CLAN -> BRANCH. Hoi dong Toc bieu chinh sua duoc.';
COMMENT ON COLUMN kinship_rule_set.code      IS 'Ma on dinh, vd DEFAULT_BAC, REGION_TRUNG, CLAN_NGUYEN_DINH.';
COMMENT ON COLUMN kinship_rule_set.scope     IS 'DEFAULT / REGION / CLAN / BRANCH — cap ap dung.';
COMMENT ON COLUMN kinship_rule_set.parent_id IS 'Bo cha de ke thua; luat cua bo con ghi de luat cha.';
COMMENT ON COLUMN kinship_rule_set.branch_id IS
    'CLAN: tro toi branch goc (branch_kind = DONG_HO). BRANCH: tro toi chi/nhanh cu the.';

-- =============================================================================
-- 3.2 kinship_rule — Từng luật danh xưng (TDD §5.5 + các chiều bổ sung)
-- =============================================================================
CREATE TABLE kinship_rule (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_set_id            UUID        NOT NULL REFERENCES kinship_rule_set (id) ON DELETE CASCADE,
    relation_code          VARCHAR(48) NOT NULL,

    -- ---- Chiều so khớp: NULL luôn có nghĩa "không xét / mọi giá trị" --------
    gen_delta              INT,
    gen_delta_min          INT,
    gen_delta_max          INT,
    collateral_degree      INT,
    collateral_degree_min  INT,
    collateral_degree_max  INT,
    side                   VARCHAR(10),
    gender                 VARCHAR(10),
    is_elder               BOOLEAN,

    -- ---- Chiều dành riêng cho quan hệ hôn nhân (side = 'IN_LAW') -----------
    link_side              VARCHAR(10),
    link_gender            VARCHAR(10),
    in_law_direction       VARCHAR(16),

    -- ---- Khớp theo cạnh trực tiếp, bỏ qua LCA -----------------------------
    direct_link            VARCHAR(20),
    direct_link_subtype    VARCHAR(16),
    direct_link_reversed   BOOLEAN     NOT NULL DEFAULT FALSE,

    -- ---- Kết quả ----------------------------------------------------------
    title                  VARCHAR(64) NOT NULL,
    title_short            VARCHAR(32),
    ego_self_term          VARCHAR(32),
    description            VARCHAR(255),
    priority               INT         NOT NULL DEFAULT 100,
    is_active              BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_kinship_rule_side CHECK (
        side IS NULL OR side IN ('PATERNAL','MATERNAL','BLOOD','IN_LAW')
    ),
    CONSTRAINT ck_kinship_rule_gender CHECK (
        gender IS NULL OR gender IN ('MALE','FEMALE','UNKNOWN')
    ),
    CONSTRAINT ck_kinship_rule_link_side CHECK (
        link_side IS NULL OR link_side IN ('PATERNAL','MATERNAL','BLOOD')
    ),
    CONSTRAINT ck_kinship_rule_link_gender CHECK (
        link_gender IS NULL OR link_gender IN ('MALE','FEMALE')
    ),
    CONSTRAINT ck_kinship_rule_in_law_direction CHECK (
        in_law_direction IS NULL OR in_law_direction IN ('ALTER_IS_SPOUSE','EGO_IS_SPOUSE')
    ),
    -- Các cột link_* chỉ có nghĩa khi side = 'IN_LAW'
    CONSTRAINT ck_kinship_rule_in_law_shape CHECK (
        side = 'IN_LAW'
        OR (link_side IS NULL AND link_gender IS NULL AND in_law_direction IS NULL)
    ),
    CONSTRAINT ck_kinship_rule_direct_link CHECK (
        direct_link IS NULL
        OR direct_link IN ('SPOUSE','PARENT_BIO','PARENT_ADOPT','HEIR')
    ),
    -- Phân loại con của cạnh: HEIR có heir_type (đích tôn/thừa tự/kế tự)
    CONSTRAINT ck_kinship_rule_direct_subtype CHECK (
        direct_link_subtype IS NULL
        OR (direct_link = 'HEIR' AND direct_link_subtype IN ('DICH_TON','THUA_TU','KE_TU'))
        OR (direct_link IN ('PARENT_BIO','PARENT_ADOPT') AND direct_link_subtype IN ('BIO','ADOPT'))
    ),
    CONSTRAINT ck_kinship_rule_collateral CHECK (
        (collateral_degree     IS NULL OR collateral_degree     >= 0) AND
        (collateral_degree_min IS NULL OR collateral_degree_min >= 0) AND
        (collateral_degree_max IS NULL OR collateral_degree_max >= 0)
    ),
    CONSTRAINT ck_kinship_rule_ranges CHECK (
        (gen_delta_min IS NULL OR gen_delta_max IS NULL OR gen_delta_min <= gen_delta_max) AND
        (collateral_degree_min IS NULL OR collateral_degree_max IS NULL
             OR collateral_degree_min <= collateral_degree_max)
    ),
    CONSTRAINT ck_kinship_rule_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_kinship_rule_priority CHECK (priority >= 0)
);

CREATE INDEX ix_kinship_rule_set       ON kinship_rule (rule_set_id, priority) WHERE is_active;
CREATE INDEX ix_kinship_rule_gen_delta ON kinship_rule (gen_delta)             WHERE is_active;
-- Trong một bộ luật, mỗi relation_code chỉ xuất hiện một lần (khoá ghi đè khi
-- bộ con override bộ cha).
CREATE UNIQUE INDEX ux_kinship_rule_code ON kinship_rule (rule_set_id, relation_code);

CREATE TRIGGER tg_kinship_rule_touch BEFORE UPDATE ON kinship_rule
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE  kinship_rule IS
    'Mot luat danh xung. KinshipResolver (POJO thuan, khong Spring/DB) so khop RelationFacts voi cac dong nay theo priority tang dan.';
COMMENT ON COLUMN kinship_rule.relation_code IS
    'Ma quan he on dinh (RelationCode) — vd BAC_RUOT_NOI, CHU_RUOT, THIM, CHAU_HO. Khoa de bo con ghi de bo cha.';
COMMENT ON COLUMN kinship_rule.gen_delta IS
    'dist_a - dist_b. >0: B doi tren A · =0: cung doi · <0: B doi duoi A. NULL = moi gia tri.';
COMMENT ON COLUMN kinship_rule.gen_delta_min IS 'Can duoi cua gen_delta (dung cho luat mo, vd to tien tu doi 6 tro len).';
COMMENT ON COLUMN kinship_rule.gen_delta_max IS 'Can tren cua gen_delta.';
COMMENT ON COLUMN kinship_rule.collateral_degree IS
    'min(dist_a, dist_b) — bac bang he. 0 = truc he · 1 = ruot · 2 = ho (con chu con bac) · >=3 = ho xa. CHIEU BO SUNG SO VOI TDD §5.5, bat buoc de phan biet "bac ruot" voi "bac ho".';
COMMENT ON COLUMN kinship_rule.side IS
    'PATERNAL (noi) / MATERNAL (ngoai) / BLOOD (huyet thong, khong phan biet noi-ngoai) / IN_LAW (qua hon nhan).';
COMMENT ON COLUMN kinship_rule.gender   IS 'Gioi tinh cua NGUOI DUOC GOI (B). NULL = moi gioi.';
COMMENT ON COLUMN kinship_rule.is_elder IS
    'TRUE = duong cua B la vai tren duong cua A (anh cua bo -> bac); FALSE = vai duoi (em cua bo -> chu); NULL = khong xet.';
COMMENT ON COLUMN kinship_rule.link_side IS
    'Chi khi side=IN_LAW: ben noi/ngoai cua NGUOI NOI (nguoi vua la ruot thit cua A vua la vo/chong cua B).';
COMMENT ON COLUMN kinship_rule.link_gender IS
    'Chi khi side=IN_LAW: gioi tinh cua NGUOI NOI. Vd thim = vo cua em trai bo -> link_gender MALE, gender FEMALE.';
COMMENT ON COLUMN kinship_rule.in_law_direction IS
    'ALTER_IS_SPOUSE: B la dau/re cua ho A (thim, mo, con dau). EGO_IS_SPOUSE: A la dau/re, B la ruot thit ben vo/chong (bo chong, me vo).';
COMMENT ON COLUMN kinship_rule.direct_link IS
    'Khop theo canh truc tiep thay vi LCA: SPOUSE / PARENT_BIO / PARENT_ADOPT / HEIR. Luat co direct_link duoc xet truoc.';
COMMENT ON COLUMN kinship_rule.direct_link_subtype IS
    'Phan loai canh: HEIR -> DICH_TON/THUA_TU/KE_TU (khop relationship.heir_type); PARENT_* -> BIO/ADOPT.';
COMMENT ON COLUMN kinship_rule.direct_link_reversed IS
    'TRUE = canh di tu B sang A (vd con nuoi: canh PARENT_ADOPT tu B la bo nuoi sang A).';
COMMENT ON COLUMN kinship_rule.title IS
    'Danh xung ket qua — A goi B bang gi. Vd "bac ho", "co ruot", "cu ong noi".';
COMMENT ON COLUMN kinship_rule.ego_self_term IS
    'A tu xung la gi khi noi voi B (vd goi "bac" xung "chau"). NULL = tra cuu nguoc lai bang chinh rule engine.';
COMMENT ON COLUMN kinship_rule.priority IS
    'TANG DAN — priority NHO HON duoc chon truoc. Luat cu the (khop chinh xac) dat 10–40, luat khai quat 50–90, luat vet 900+.';
