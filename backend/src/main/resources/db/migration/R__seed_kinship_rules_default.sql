-- =============================================================================
-- R__seed_kinship_rules_default.sql
-- Bộ luật danh xưng MẶC ĐỊNH — CÁCH XƯNG HÔ MIỀN BẮC (rule set scope = DEFAULT)
-- FR-1.3a · TDD §5.5 · plan Giai đoạn 1 W3 ("Bộ luật DEFAULT = miền Bắc")
--
-- REPEATABLE MIGRATION: Flyway chạy lại mỗi khi checksum file này đổi.
-- Idempotent theo cách "xoá sạch rồi chèn lại" trong phạm vi ĐÚNG MỘT rule set
-- DEFAULT có id cố định, nên chạy bao nhiêu lần cũng không nhân đôi luật.
--
-- ############################################################################
-- # CẢNH BÁO CHO HỘI ĐỒNG TỘC BIỂU VÀ NGƯỜI VẬN HÀNH                         #
-- # Bộ DEFAULT này DO HỆ THỐNG SỞ HỮU. Mỗi lần triển khai bản mới có sửa file #
-- # này, toàn bộ luật trong bộ DEFAULT bị ghi đè.                             #
-- # => Tuỳ biến của dòng họ PHẢI đặt ở rule set con (scope CLAN hoặc BRANCH,  #
-- #    parent_id trỏ về bộ DEFAULT này) qua API /api/v1/kinship-rules.        #
-- #    Luật của bộ con ghi đè luật cha theo relation_code.                    #
-- ############################################################################
--
-- QUY ƯỚC (định nghĩa đầy đủ ở V3__kinship_rules.sql):
--   A = ego (người hỏi) · B = alter (người được gọi)
--   gen_delta         = dist_a - dist_b  (>0: B đời trên A)
--   collateral_degree = min(dist_a, dist_b)
--                       0 = trực hệ · 1 = ruột · 2 = họ (con chú con bác) · >=3 = họ xa
--   side              PATERNAL nội · MATERNAL ngoại · BLOOD huyết thống (không
--                     phân biệt nội/ngoại) · IN_LAW qua hôn nhân
--   is_elder          TRUE = đường của B là vai trên đường của A
--   priority          TĂNG DẦN — số NHỎ được chọn TRƯỚC
--   ego_self_term     A tự xưng gì khi nói với B (theo lối nói, không phải theo
--                     bậc phả hệ: gọi "cụ" vẫn xưng "cháu").
--                     NULL = phụ thuộc giới tính/vai của A, resolver tra ngược.
-- =============================================================================

SET search_path = public, ag_catalog;

-- -----------------------------------------------------------------------------
-- 1. Bộ luật DEFAULT (id cố định để repeatable migration bám vào)
-- -----------------------------------------------------------------------------
INSERT INTO kinship_rule_set (id, code, name, scope, parent_id, region, branch_id, description, is_active)
VALUES (
    '00000000-0000-0000-0000-0000000000b1',
    'DEFAULT_BAC',
    'Danh xưng mặc định — miền Bắc',
    'DEFAULT',
    NULL, NULL, NULL,
    'Bộ danh xưng gốc theo lối xưng hô miền Bắc. Do hệ thống sở hữu; dòng họ tuỳ biến ở rule set con (CLAN/BRANCH). Miền Trung và miền Nam bổ sung ở cấp REGION trong Giai đoạn 2–3.',
    TRUE
)
ON CONFLICT (code) DO UPDATE SET
    name        = EXCLUDED.name,
    scope       = EXCLUDED.scope,
    description = EXCLUDED.description,
    is_active   = TRUE,
    updated_at  = now();

-- Xoá sạch luật cũ của ĐÚNG bộ DEFAULT này rồi chèn lại (idempotent).
DELETE FROM kinship_rule
WHERE rule_set_id = '00000000-0000-0000-0000-0000000000b1'::uuid;

-- =============================================================================
-- NHÓM A — TRỰC HỆ, BẬC TRÊN (collateral_degree = 0, gen_delta > 0)
--   A gọi tổ tiên trực hệ của mình.
-- =============================================================================
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree, side, gender,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','CHA',           1,0,'BLOOD',   'MALE',  'bố',          'bố',   'con',  'Cha ruột. Miền Bắc: bố/thầy; chi họ dùng "thầy" thì ghi đè ở rule set con.', 10),
('00000000-0000-0000-0000-0000000000b1','ME',            1,0,'BLOOD',   'FEMALE','mẹ',          'mẹ',   'con',  'Mẹ ruột. Biến thể: u, bầm, mạ.', 10),
('00000000-0000-0000-0000-0000000000b1','ONG_NOI',       2,0,'PATERNAL','MALE',  'ông nội',     'ông',  'cháu', 'Bố của bố.', 10),
('00000000-0000-0000-0000-0000000000b1','BA_NOI',        2,0,'PATERNAL','FEMALE','bà nội',      'bà',   'cháu', 'Mẹ của bố.', 10),
('00000000-0000-0000-0000-0000000000b1','ONG_NGOAI',     2,0,'MATERNAL','MALE',  'ông ngoại',   'ông',  'cháu', 'Bố của mẹ.', 10),
('00000000-0000-0000-0000-0000000000b1','BA_NGOAI',      2,0,'MATERNAL','FEMALE','bà ngoại',    'bà',   'cháu', 'Mẹ của mẹ.', 10),
('00000000-0000-0000-0000-0000000000b1','CU_ONG_NOI',    3,0,'PATERNAL','MALE',  'cụ ông nội',  'cụ',   'cháu', 'Ông nội của bố (đời thứ 3 phía trên, bên nội).', 15),
('00000000-0000-0000-0000-0000000000b1','CU_BA_NOI',     3,0,'PATERNAL','FEMALE','cụ bà nội',   'cụ',   'cháu', 'Bà nội của bố.', 15),
('00000000-0000-0000-0000-0000000000b1','CU_ONG_NGOAI',  3,0,'MATERNAL','MALE',  'cụ ông ngoại','cụ',   'cháu', 'Ông ngoại của mẹ.', 15),
('00000000-0000-0000-0000-0000000000b1','CU_BA_NGOAI',   3,0,'MATERNAL','FEMALE','cụ bà ngoại', 'cụ',   'cháu', 'Bà ngoại của mẹ.', 15),
('00000000-0000-0000-0000-0000000000b1','KY_ONG',        4,0,'BLOOD',   'MALE',  'kỵ ông',      'kỵ',   'cháu', 'Đời thứ 4 phía trên. Miền Bắc gọi kỵ (nơi khác: cố).', 20),
('00000000-0000-0000-0000-0000000000b1','KY_BA',         4,0,'BLOOD',   'FEMALE','kỵ bà',       'kỵ',   'cháu', 'Đời thứ 4 phía trên.', 20),
('00000000-0000-0000-0000-0000000000b1','CAN_ONG',       5,0,'BLOOD',   'MALE',  'can ông',     'can',  'cháu', 'Đời thứ 5 phía trên (cụ can). CẦN HỘI ĐỒNG TỘC BIỂU XÁC NHẬN.', 25),
('00000000-0000-0000-0000-0000000000b1','CAN_BA',        5,0,'BLOOD',   'FEMALE','can bà',      'can',  'cháu', 'Đời thứ 5 phía trên. CẦN HỘI ĐỒNG TỘC BIỂU XÁC NHẬN.', 25);

INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta_min, collateral_degree, side, gender,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','TO_TIEN_TRUC_HE', 6,0,'BLOOD',NULL,'tổ tiên','tổ','cháu','Từ đời thứ 6 phía trên trở lên, trực hệ — gọi chung là tổ/tiên tổ.', 60);

-- =============================================================================
-- NHÓM B — TRỰC HỆ, BẬC DƯỚI (collateral_degree = 0, gen_delta < 0)
-- =============================================================================
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree, side, gender,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','CON_TRAI',   -1,0,'BLOOD',   'MALE',  'con trai',   'con',   NULL,'Con ruột nam. ego_self_term để trống vì phụ thuộc giới tính của A (bố/mẹ).', 10),
('00000000-0000-0000-0000-0000000000b1','CON_GAI',    -1,0,'BLOOD',   'FEMALE','con gái',    'con',   NULL,'Con ruột nữ — ghi nhận ngang bằng con trai (BA v2 §12).', 10),
('00000000-0000-0000-0000-0000000000b1','CHAU_NOI',   -2,0,'PATERNAL',NULL,    'cháu nội',   'cháu',  'ông/bà','Con của con trai.', 15),
('00000000-0000-0000-0000-0000000000b1','CHAU_NGOAI', -2,0,'MATERNAL',NULL,    'cháu ngoại', 'cháu',  'ông/bà','Con của con gái — ghi nhận đầy đủ ngang cháu nội (BA v2 §12).', 15),
('00000000-0000-0000-0000-0000000000b1','CHAT',       -3,0,'BLOOD',   NULL,    'chắt',       'chắt',  'cụ', 'Đời thứ 3 phía dưới.', 20),
('00000000-0000-0000-0000-0000000000b1','CHUT',       -4,0,'BLOOD',   NULL,    'chút',       'chút',  'kỵ', 'Đời thứ 4 phía dưới.', 20),
('00000000-0000-0000-0000-0000000000b1','CHIT',       -5,0,'BLOOD',   NULL,    'chít',       'chít',  'can','Đời thứ 5 phía dưới. CẦN HỘI ĐỒNG TỘC BIỂU XÁC NHẬN.', 25);

INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta_max, collateral_degree, side, gender,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','HAU_DUE_TRUC_HE', -6,0,'BLOOD',NULL,'hậu duệ','hậu duệ',NULL,'Từ đời thứ 6 phía dưới trở xuống, trực hệ.', 60);


-- =============================================================================
-- NHÓM B2 — TRỰC HỆ NHƯNG CHƯA RÕ BÊN NỘI/NGOẠI (collateral_degree = 0,
--            side = 'BLOOD')
--
--   QUYẾT ĐỊNH CỦA HỘI ĐỒNG TỘC BIỂU (khoảng trống 2): phả giấy chép lại thường
--   thiếu giới tính của người nối dòng, khi đó RelationFactsFactory trả
--   side = BLOOD đúng theo thiết kế. Vẫn phải gọi được — chỉ mất phần phân biệt
--   nội/ngoại:
--     · bậc ông/bà -> "ông" / "bà"; chưa rõ luôn giới của người được gọi thì
--       "ông/bà";
--     · bậc cụ     -> gọi chung là "cụ", KHÔNG tách cụ ông / cụ bà;
--     · đời dưới   -> "cháu".
--   Bậc 1 (CHA/ME), bậc 4 (KY_ONG/KY_BA) và bậc -1/-3 đã có biến thể BLOOD từ
--   trước, nên thiếu bậc 2/3/-2 là SÓT chứ không phải chủ ý. Nhóm này bịt nốt.
--
--   priority LỚN HƠN luật có ghi bên (10 và 15) nên luật ghi đúng nội/ngoại
--   luôn thắng — đúng cách nhóm D đã làm với các luật "chưa rõ vai".
-- =============================================================================
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree, side, gender,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','ONG_CHUA_RO_BEN',    2,0,'BLOOD','MALE',  'ông',   'ông',   'cháu',  'Đời trên 2 bậc, trực hệ, CHƯA RÕ bên nội hay bên ngoại vì thiếu giới tính người nối. Gọi ông, không kèm nội/ngoại.', 45),
('00000000-0000-0000-0000-0000000000b1','BA_CHUA_RO_BEN',     2,0,'BLOOD','FEMALE','bà',    'bà',    'cháu',  'Đời trên 2 bậc, trực hệ, chưa rõ bên nội/ngoại.', 45),
('00000000-0000-0000-0000-0000000000b1','ONG_BA_CHUA_RO_BEN', 2,0,'BLOOD',NULL,    'ông/bà','ông/bà','cháu',  'Đời trên 2 bậc, trực hệ, chưa rõ bên VÀ chưa rõ giới tính của chính người được gọi.', 46),
('00000000-0000-0000-0000-0000000000b1','CU_CHUA_RO_BEN',     3,0,'BLOOD',NULL,    'cụ',    'cụ',    'cháu',  'Đời trên 3 bậc, trực hệ, chưa rõ bên. Hội đồng chốt: bậc cụ gọi chung là cụ.', 45),
('00000000-0000-0000-0000-0000000000b1','CHAU_CHUA_RO_BEN',  -2,0,'BLOOD',NULL,    'cháu',  'cháu',  'ông/bà','Đời dưới 2 bậc, trực hệ, chưa rõ cháu nội hay cháu ngoại vì thiếu giới tính người nối.', 45);

-- =============================================================================
-- NHÓM C — CÙNG ĐỜI (gen_delta = 0)
--   collateral 1 = anh/chị/em ruột (chung bố mẹ, kể cả cùng cha khác mẹ)
--   collateral >= 2 = anh/chị/em họ (con chú con bác trở đi)
-- =============================================================================
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree, side, gender, is_elder,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','ANH_RUOT',    0,1,'BLOOD','MALE',  TRUE, 'anh trai','anh','em', 'Anh ruột. Đa thê: cùng cha khác mẹ vẫn là anh ruột, LCA là người cha.', 10),
('00000000-0000-0000-0000-0000000000b1','CHI_RUOT',    0,1,'BLOOD','FEMALE',TRUE, 'chị gái', 'chị','em', 'Chị ruột.', 10),
('00000000-0000-0000-0000-0000000000b1','EM_TRAI_RUOT',0,1,'BLOOD','MALE',  FALSE,'em trai', 'em', NULL,'Em ruột nam. A tự xưng anh hay chị tuỳ giới tính của A.', 10),
('00000000-0000-0000-0000-0000000000b1','EM_GAI_RUOT', 0,1,'BLOOD','FEMALE',FALSE,'em gái',  'em', NULL,'Em ruột nữ.', 10),
('00000000-0000-0000-0000-0000000000b1','ANH_EM_CHUA_RO_VAI', 0,1,'BLOOD','MALE',  NULL,'anh/em trai','anh/em',NULL,'Cùng đời, ruột, CHƯA RÕ thứ tự sinh (gia phả cổ thiếu birth_order). Hiển thị cả hai khả năng.', 45),
('00000000-0000-0000-0000-0000000000b1','CHI_EM_CHUA_RO_VAI', 0,1,'BLOOD','FEMALE',NULL,'chị/em gái', 'chị/em',NULL,'Cùng đời, ruột, CHƯA RÕ thứ tự sinh.', 45);

INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree_min, side, gender, is_elder,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','ANH_HO',    0,2,'BLOOD','MALE',  TRUE, 'anh họ',   'anh','em','Cùng đời, bàng hệ bậc 2 trở lên (con chú con bác).', 30),
('00000000-0000-0000-0000-0000000000b1','CHI_HO',    0,2,'BLOOD','FEMALE',TRUE, 'chị họ',   'chị','em',NULL, 30),
('00000000-0000-0000-0000-0000000000b1','EM_TRAI_HO',0,2,'BLOOD','MALE',  FALSE,'em trai họ','em',NULL,NULL, 30),
('00000000-0000-0000-0000-0000000000b1','EM_GAI_HO', 0,2,'BLOOD','FEMALE',FALSE,'em gái họ', 'em',NULL,NULL, 30),
('00000000-0000-0000-0000-0000000000b1','ANH_EM_HO_CHUA_RO_VAI',0,2,'BLOOD','MALE',  NULL,'anh/em họ','anh/em',NULL,'Cùng đời, bàng hệ, chưa rõ thứ tự sinh.', 50),
('00000000-0000-0000-0000-0000000000b1','CHI_EM_HO_CHUA_RO_VAI',0,2,'BLOOD','FEMALE',NULL,'chị/em họ','chị/em',NULL,'Cùng đời, bàng hệ, chưa rõ thứ tự sinh.', 50);

-- =============================================================================
-- NHÓM D — ĐỜI TRÊN 1 BẬC, BÀNG HỆ RUỘT (gen_delta = 1, collateral = 1)
--   Anh/chị/em ruột của bố hoặc của mẹ. ĐÂY LÀ NHÓM ĐẶC TRƯNG NHẤT CỦA MIỀN BẮC:
--   is_elder quyết định bác (vai trên) hay chú/cô/cậu/dì (vai dưới),
--   và miền Bắc dùng "bác" cho CẢ hai bên nội lẫn ngoại khi là vai trên.
-- =============================================================================
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree, side, gender, is_elder,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','BAC_TRAI_NOI',1,1,'PATERNAL','MALE',  TRUE, 'bác',     'bác','cháu','Anh trai của bố = bác (bác trai).', 10),
('00000000-0000-0000-0000-0000000000b1','CHU_RUOT',    1,1,'PATERNAL','MALE',  FALSE,'chú',     'chú','cháu','Em trai của bố = chú.', 10),
('00000000-0000-0000-0000-0000000000b1','BAC_GAI_NOI', 1,1,'PATERNAL','FEMALE',TRUE, 'bác gái', 'bác','cháu','Chị gái của bố = bác (miền Bắc), không gọi cô.', 10),
('00000000-0000-0000-0000-0000000000b1','CO_RUOT',     1,1,'PATERNAL','FEMALE',FALSE,'cô ruột', 'cô', 'cháu','Em gái của bố = cô.', 10),
('00000000-0000-0000-0000-0000000000b1','BAC_TRAI_NGOAI',1,1,'MATERNAL','MALE',  TRUE, 'bác',    'bác','cháu','Anh trai của mẹ = bác (miền Bắc), không gọi cậu.', 10),
('00000000-0000-0000-0000-0000000000b1','CAU_RUOT',      1,1,'MATERNAL','MALE',  FALSE,'cậu',    'cậu','cháu','Em trai của mẹ = cậu.', 10),
('00000000-0000-0000-0000-0000000000b1','BAC_GAI_NGOAI', 1,1,'MATERNAL','FEMALE',TRUE, 'bác gái','bác','cháu','Chị gái của mẹ = bác gái (một số nơi gọi "bá"/"già").', 10),
('00000000-0000-0000-0000-0000000000b1','DI_RUOT',       1,1,'MATERNAL','FEMALE',FALSE,'dì',     'dì', 'cháu','Em gái của mẹ = dì.', 10),
('00000000-0000-0000-0000-0000000000b1','BAC_CHU_NOI_CHUA_RO', 1,1,'PATERNAL','MALE',  NULL,'bác/chú',    'bác/chú','cháu','Anh em trai của bố, CHƯA RÕ ai lớn hơn.', 45),
('00000000-0000-0000-0000-0000000000b1','BAC_CO_NOI_CHUA_RO',  1,1,'PATERNAL','FEMALE',NULL,'bác gái/cô', 'bác/cô','cháu','Chị em gái của bố, chưa rõ thứ tự sinh.', 45),
('00000000-0000-0000-0000-0000000000b1','BAC_CAU_NGOAI_CHUA_RO',1,1,'MATERNAL','MALE',  NULL,'bác/cậu',   'bác/cậu','cháu','Anh em trai của mẹ, chưa rõ thứ tự sinh.', 45),
('00000000-0000-0000-0000-0000000000b1','BAC_DI_NGOAI_CHUA_RO', 1,1,'MATERNAL','FEMALE',NULL,'bác gái/dì','bác/dì','cháu','Chị em gái của mẹ, chưa rõ thứ tự sinh.', 45);

-- =============================================================================
-- NHÓM E — ĐỜI TRÊN 1 BẬC, BÀNG HỆ HỌ (gen_delta = 1, collateral >= 2)
--   Anh chị em họ của bố/mẹ => thêm chữ "họ": bác họ, chú họ, cô họ, cậu họ, dì họ.
-- =============================================================================
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree_min, side, gender, is_elder,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','BAC_HO_NOI', 1,2,'PATERNAL','MALE',  TRUE, 'bác họ',    'bác','cháu','Anh họ của bố. VÍ DỤ KINH ĐIỂN: A và B chung cụ (dist_a=3, dist_b=2) -> gen_delta 1, collateral 2 -> "bác họ".', 30),
('00000000-0000-0000-0000-0000000000b1','CHU_HO',     1,2,'PATERNAL','MALE',  FALSE,'chú họ',    'chú','cháu','Em họ (nam) của bố.', 30),
('00000000-0000-0000-0000-0000000000b1','BAC_GAI_HO_NOI',1,2,'PATERNAL','FEMALE',TRUE,'bác gái họ','bác','cháu','Chị họ của bố.', 30),
('00000000-0000-0000-0000-0000000000b1','CO_HO',      1,2,'PATERNAL','FEMALE',FALSE,'cô họ',     'cô', 'cháu','Em họ (nữ) của bố.', 30),
('00000000-0000-0000-0000-0000000000b1','BAC_HO_NGOAI',1,2,'MATERNAL','MALE', TRUE, 'bác họ',    'bác','cháu','Anh họ của mẹ.', 30),
('00000000-0000-0000-0000-0000000000b1','CAU_HO',      1,2,'MATERNAL','MALE', FALSE,'cậu họ',    'cậu','cháu','Em họ (nam) của mẹ.', 30),
('00000000-0000-0000-0000-0000000000b1','BAC_GAI_HO_NGOAI',1,2,'MATERNAL','FEMALE',TRUE,'bác gái họ','bác','cháu','Chị họ của mẹ.', 30),
('00000000-0000-0000-0000-0000000000b1','DI_HO',      1,2,'MATERNAL','FEMALE',FALSE,'dì họ',     'dì', 'cháu','Em họ (nữ) của mẹ.', 30),
('00000000-0000-0000-0000-0000000000b1','BAC_CHU_HO_CHUA_RO',1,2,'BLOOD','MALE',  NULL,'bác/chú họ','bác/chú','cháu','Đời trên 1 bậc, bàng hệ họ, chưa rõ thứ tự sinh.', 55),
('00000000-0000-0000-0000-0000000000b1','BAC_CO_HO_CHUA_RO', 1,2,'BLOOD','FEMALE',NULL,'bác gái/cô họ','bác/cô','cháu','Đời trên 1 bậc, bàng hệ họ, chưa rõ thứ tự sinh.', 55);

-- =============================================================================
-- NHÓM F — ĐỜI TRÊN 2 BẬC, BÀNG HỆ (gen_delta = 2)
--   Anh chị em của ông/bà: ông bác, ông chú (ông trẻ), bà bác, bà cô.
-- =============================================================================
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree, side, gender, is_elder,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','ONG_BAC',2,1,'BLOOD','MALE',  TRUE, 'ông bác','ông','cháu','Anh trai của ông (nội hoặc ngoại).', 20),
('00000000-0000-0000-0000-0000000000b1','ONG_CHU',2,1,'BLOOD','MALE',  FALSE,'ông chú','ông','cháu','Em trai của ông. Nhiều nơi gọi "ông trẻ".', 20),
('00000000-0000-0000-0000-0000000000b1','BA_BAC', 2,1,'BLOOD','FEMALE',TRUE, 'bà bác', 'bà', 'cháu','Chị gái của ông/bà.', 20),
('00000000-0000-0000-0000-0000000000b1','BA_CO',  2,1,'BLOOD','FEMALE',FALSE,'bà cô',  'bà', 'cháu','Em gái của ông. Nếu mất khi chưa lấy chồng thì trong cúng giỗ gọi "bà cô tổ".', 20),
('00000000-0000-0000-0000-0000000000b1','ONG_BANG_HE_CHUA_RO',2,1,'BLOOD','MALE',  NULL,'ông','ông','cháu','Đời trên 2 bậc, bàng hệ ruột, chưa rõ thứ tự sinh.', 45),
('00000000-0000-0000-0000-0000000000b1','BA_BANG_HE_CHUA_RO', 2,1,'BLOOD','FEMALE',NULL,'bà', 'bà', 'cháu','Đời trên 2 bậc, bàng hệ ruột, chưa rõ thứ tự sinh.', 45);

INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree_min, side, gender,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','ONG_HO',2,2,'BLOOD','MALE',  'ông họ','ông','cháu','Đời trên 2 bậc, bàng hệ họ.', 40),
('00000000-0000-0000-0000-0000000000b1','BA_HO', 2,2,'BLOOD','FEMALE','bà họ', 'bà', 'cháu','Đời trên 2 bậc, bàng hệ họ.', 40);

-- =============================================================================
-- NHÓM G — ĐỜI TRÊN >= 3 BẬC, BÀNG HỆ (cụ họ, kỵ họ)
-- =============================================================================
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree_min, side, gender,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','CU_ONG_HO',3,1,'BLOOD','MALE',  'cụ ông họ','cụ','cháu','Đời trên 3 bậc, bàng hệ — "cháu gọi bằng cụ họ".', 40),
('00000000-0000-0000-0000-0000000000b1','CU_BA_HO', 3,1,'BLOOD','FEMALE','cụ bà họ', 'cụ','cháu','Đời trên 3 bậc, bàng hệ.', 40),
('00000000-0000-0000-0000-0000000000b1','KY_HO',    4,1,'BLOOD',NULL,    'kỵ họ',    'kỵ','cháu','Đời trên 4 bậc, bàng hệ.', 50);

INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta_min, collateral_degree_min, side, gender,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','TO_HO',5,1,'BLOOD',NULL,'bậc tổ trong họ','tổ','cháu','Đời trên 5 bậc trở lên, bàng hệ. CẦN HỘI ĐỒNG TỘC BIỂU XÁC NHẬN cách gọi.', 70);

-- =============================================================================
-- NHÓM H — ĐỜI DƯỚI, BÀNG HỆ (cháu ruột, cháu họ, chắt họ)
--   Trên đường bàng hệ, thang bậc dịch một nấc so với trực hệ:
--   con của anh/chị/em ruột = cháu; cháu của họ = chắt.
-- =============================================================================
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree, side, gender,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','CHAU_TRAI_RUOT',-1,1,'BLOOD','MALE',  'cháu trai','cháu',NULL,'Con của anh/chị/em ruột. A tự xưng bác/chú/cô/cậu/dì tuỳ vai — resolver tra ngược.', 20),
('00000000-0000-0000-0000-0000000000b1','CHAU_GAI_RUOT', -1,1,'BLOOD','FEMALE','cháu gái', 'cháu',NULL,'Con của anh/chị/em ruột.', 20),
('00000000-0000-0000-0000-0000000000b1','CHAT_BANG_HE',  -2,1,'BLOOD',NULL,    'chắt',     'chắt',NULL,'Cháu của anh/chị/em ruột.', 30);

INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree_min, side, gender,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','CHAU_HO',-1,2,'BLOOD',NULL,'cháu họ','cháu',NULL,'Đời dưới 1 bậc, bàng hệ họ.', 35),
('00000000-0000-0000-0000-0000000000b1','CHAT_HO',-2,2,'BLOOD',NULL,'chắt họ','chắt',NULL,'Đời dưới 2 bậc, bàng hệ họ.', 40);

INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta_max, collateral_degree_min, side, gender,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','HAU_DUE_BANG_HE',-3,1,'BLOOD',NULL,'hậu duệ trong họ','hậu duệ',NULL,'Đời dưới 3 bậc trở xuống, bàng hệ.', 80);

-- =============================================================================
-- NHÓM I — HÔN NHÂN TRỰC TIẾP (khớp theo cạnh SPOUSE, không qua LCA)
--   Thứ tự vợ cả/vợ hai (đa thê) lấy từ relationship.spouse_order và được ghép
--   ở tầng hiển thị; rule engine chỉ trả danh xưng gốc "vợ"/"chồng".
-- =============================================================================
INSERT INTO kinship_rule
    (rule_set_id, relation_code, side, gender, direct_link, direct_link_reversed,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','CHONG','IN_LAW','MALE',  'SPOUSE',FALSE,'chồng','chồng','vợ',   'Có cạnh SPOUSE còn hiệu lực (valid_to IS NULL).', 5),
('00000000-0000-0000-0000-0000000000b1','VO',   'IN_LAW','FEMALE','SPOUSE',FALSE,'vợ',   'vợ',   'chồng','Đa thê/đa phu: mỗi cạnh SPOUSE một dòng, phân biệt bằng spouse_order.', 5);

-- =============================================================================
-- NHÓM J — DÂU / RỂ / THÔNG GIA (side = IN_LAW, tính gián tiếp qua NGƯỜI NỐI)
--   link_gender = giới tính của NGƯỜI NỐI (người vừa ruột thịt với A vừa là
--   vợ/chồng của B). in_law_direction cho biết ai là người "lấy vào họ".
-- =============================================================================
-- J1. A là dâu/rể — B là ruột thịt bên vợ/chồng của A (EGO_IS_SPOUSE)
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree, side, gender,
     link_gender, in_law_direction, title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','BO_CHONG',1,0,'IN_LAW','MALE',  'MALE',  'EGO_IS_SPOUSE','bố chồng','bố','con','A là con dâu; B là bố của chồng.', 15),
('00000000-0000-0000-0000-0000000000b1','ME_CHONG',1,0,'IN_LAW','FEMALE','MALE',  'EGO_IS_SPOUSE','mẹ chồng','mẹ','con','A là con dâu; B là mẹ của chồng.', 15),
('00000000-0000-0000-0000-0000000000b1','BO_VO',   1,0,'IN_LAW','MALE',  'FEMALE','EGO_IS_SPOUSE','bố vợ',   'bố','con','A là con rể; B là bố của vợ (nhạc phụ).', 15),
('00000000-0000-0000-0000-0000000000b1','ME_VO',   1,0,'IN_LAW','FEMALE','FEMALE','EGO_IS_SPOUSE','mẹ vợ',   'mẹ','con','A là con rể; B là mẹ của vợ (nhạc mẫu).', 15);

-- J2. B là dâu/rể của họ nhà A (ALTER_IS_SPOUSE), trực hệ
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree, side, gender,
     link_gender, in_law_direction, title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','CON_DAU', -1,0,'IN_LAW','FEMALE','MALE',  'ALTER_IS_SPOUSE','con dâu', 'con', NULL,'Vợ của con trai.', 15),
('00000000-0000-0000-0000-0000000000b1','CON_RE',  -1,0,'IN_LAW','MALE',  'FEMALE','ALTER_IS_SPOUSE','con rể',  'con', NULL,'Chồng của con gái.', 15),
('00000000-0000-0000-0000-0000000000b1','CHAU_DAU',-2,0,'IN_LAW','FEMALE','MALE',  'ALTER_IS_SPOUSE','cháu dâu','cháu',NULL,'Vợ của cháu trai.', 25),
('00000000-0000-0000-0000-0000000000b1','CHAU_RE', -2,0,'IN_LAW','MALE',  'FEMALE','ALTER_IS_SPOUSE','cháu rể', 'cháu',NULL,'Chồng của cháu gái.', 25);

-- J3. B là dâu/rể cùng đời với A (anh/chị/em dâu rể)
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree, side, gender, is_elder,
     link_gender, in_law_direction, title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','CHI_DAU',0,1,'IN_LAW','FEMALE',TRUE, 'MALE',  'ALTER_IS_SPOUSE','chị dâu','chị','em','Vợ của anh trai.', 15),
('00000000-0000-0000-0000-0000000000b1','EM_DAU', 0,1,'IN_LAW','FEMALE',FALSE,'MALE',  'ALTER_IS_SPOUSE','em dâu', 'em', NULL,'Vợ của em trai.', 15),
('00000000-0000-0000-0000-0000000000b1','ANH_RE', 0,1,'IN_LAW','MALE',  TRUE, 'FEMALE','ALTER_IS_SPOUSE','anh rể', 'anh','em','Chồng của chị gái.', 15),
('00000000-0000-0000-0000-0000000000b1','EM_RE',  0,1,'IN_LAW','MALE',  FALSE,'FEMALE','ALTER_IS_SPOUSE','em rể',  'em', NULL,'Chồng của em gái.', 15);

-- J4. Vợ/chồng của bác, chú, cô, cậu, dì  (đời trên 1 bậc, bàng hệ ruột)
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree, side, gender, is_elder,
     link_side, link_gender, in_law_direction, title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','BAC_GAI_DAU_NOI',1,1,'IN_LAW','FEMALE',TRUE, 'PATERNAL','MALE',  'ALTER_IS_SPOUSE','bác gái','bác','cháu','Vợ của bác trai (anh trai của bố).', 15),
('00000000-0000-0000-0000-0000000000b1','THIM',           1,1,'IN_LAW','FEMALE',FALSE,'PATERNAL','MALE',  'ALTER_IS_SPOUSE','thím',   'thím','cháu','Vợ của chú (em trai của bố).', 15),
('00000000-0000-0000-0000-0000000000b1','BAC_TRAI_RE_NOI',1,1,'IN_LAW','MALE',  TRUE, 'PATERNAL','FEMALE','ALTER_IS_SPOUSE','bác',    'bác','cháu','Chồng của bác gái (chị gái của bố).', 15),
('00000000-0000-0000-0000-0000000000b1','CHU_RE_CUA_CO',  1,1,'IN_LAW','MALE',  FALSE,'PATERNAL','FEMALE','ALTER_IS_SPOUSE','chú',    'chú','cháu','Chồng của cô. Miền Bắc gọi "chú"; miền Trung/Nam gọi "dượng" — ghi đè ở rule set REGION.', 15),
('00000000-0000-0000-0000-0000000000b1','BAC_GAI_DAU_NGOAI',1,1,'IN_LAW','FEMALE',TRUE, 'MATERNAL','MALE',  'ALTER_IS_SPOUSE','bác gái','bác','cháu','Vợ của bác trai bên ngoại (anh trai của mẹ).', 15),
('00000000-0000-0000-0000-0000000000b1','MO',               1,1,'IN_LAW','FEMALE',FALSE,'MATERNAL','MALE',  'ALTER_IS_SPOUSE','mợ',     'mợ', 'cháu','Vợ của cậu (em trai của mẹ).', 15),
('00000000-0000-0000-0000-0000000000b1','BAC_TRAI_RE_NGOAI',1,1,'IN_LAW','MALE',  TRUE, 'MATERNAL','FEMALE','ALTER_IS_SPOUSE','bác',    'bác','cháu','Chồng của bác gái bên ngoại (chị gái của mẹ).', 15),
('00000000-0000-0000-0000-0000000000b1','CHU_RE_CUA_DI',    1,1,'IN_LAW','MALE',  FALSE,'MATERNAL','FEMALE','ALTER_IS_SPOUSE','chú',    'chú','cháu','Chồng của dì. Miền Bắc gọi "chú"; nơi khác gọi "dượng".', 15);

-- J5. Vợ/chồng của bác/chú/cô/cậu/dì HỌ (đời trên 1 bậc, bàng hệ họ)
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree_min, side, gender, is_elder,
     link_gender, in_law_direction, title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','BAC_GAI_HO_DAU',1,2,'IN_LAW','FEMALE',TRUE, 'MALE',  'ALTER_IS_SPOUSE','bác gái họ','bác','cháu','Vợ của bác họ.', 35),
('00000000-0000-0000-0000-0000000000b1','THIM_HO',       1,2,'IN_LAW','FEMALE',FALSE,'MALE',  'ALTER_IS_SPOUSE','thím họ',   'thím','cháu','Vợ của chú họ.', 35),
('00000000-0000-0000-0000-0000000000b1','BAC_TRAI_HO_RE',1,2,'IN_LAW','MALE',  TRUE, 'FEMALE','ALTER_IS_SPOUSE','bác họ',    'bác','cháu','Chồng của bác gái họ.', 35),
('00000000-0000-0000-0000-0000000000b1','CHU_HO_RE',     1,2,'IN_LAW','MALE',  FALSE,'FEMALE','ALTER_IS_SPOUSE','chú họ',    'chú','cháu','Chồng của cô họ / dì họ.', 35);

-- =============================================================================
-- NHÓM J6 — NGƯỜI LÀM DÂU / LÀM RỂ GỌI HỌ NHÀ CHỒNG / NHÀ VỢ
--            (side = IN_LAW, in_law_direction = EGO_IS_SPOUSE)
--
--   QUYẾT ĐỊNH CỦA HỘI ĐỒNG TỘC BIỂU (khoảng trống 1):
--     "Người làm dâu xưng hô y như chồng mình; người làm rể xưng hô y như vợ
--      mình."
--   Trước đây chiều EGO_IS_SPOUSE chỉ được khai ở nhóm J1 (gen_delta = 1,
--   collateral_degree = 0 — bố/mẹ chồng, bố/mẹ vợ); mọi bậc khác rơi vào luật
--   vét, nên một người con dâu không gọi được gần hết họ nhà chồng.
--
--   QUY ƯỚC CỦA NHÓM NÀY — đọc kỹ trước khi sửa:
--     · NGƯỜI NỐI ở chiều EGO_IS_SPOUSE là VỢ/CHỒNG của A. Vì thế
--       link_gender = MALE   nghĩa là A là DÂU (họ nhà chồng),
--       link_gender = FEMALE nghĩa là A là RỂ (họ nhà vợ).
--     · gen_delta / collateral_degree / is_elder / link_side đều tính trên
--       đường huyết thống NGƯỜI NỐI -> B, tức đúng bằng dữ kiện mà bạn đời của
--       A nhận được. Nhờ vậy "xưng như bạn đời" chỉ là chép lại bảng danh xưng
--       huyết thống sang một bộ luật khác — hoàn toàn là DỮ LIỆU, không cần
--       một dòng code nào (FR-1.3a).
--     · Danh xưng ở đây là LỐI GỌI (tương đương title_short bên huyết thống),
--       không phải danh xưng mô tả: chồng gọi "ông nội" thì vợ gọi "ông";
--       chồng gọi "ông chú" thì vợ cũng gọi "ông".
--     · NGOẠI LỆ có thật của tiếng Việt, không suy máy móc từ bạn đời được:
--       em của bạn đời được gọi theo lối con cái gọi — em trai chồng là "chú",
--       em gái chồng là "cô", em trai vợ là "cậu", em gái vợ là "dì".
--
--   CỐ Ý BỎ TRỐNG: gen_delta = -1 / -2 với collateral_degree = 0 (con riêng của
--   bạn đời và con cháu của người con riêng đó). Đó là quan hệ con riêng/bố
--   dượng - mẹ kế, đã có quyết định ở ví dụ #8 cuối file: hệ thống KHÔNG tự suy
--   ra, dòng họ phải lập cạnh PARENT_ADOPT hoặc khai luật ở rule set con. Vì thế
--   mọi luật đời dưới của nhóm này đều ràng collateral_degree_min = 1.
-- =============================================================================
-- J6a. Bậc TRÊN bạn đời từ 2 đời trở lên — trực hệ lẫn bàng hệ gọi như nhau
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, side, gender, in_law_direction,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','ONG_BEN_BAN_DOI',   2,'IN_LAW','MALE',  'EGO_IS_SPOUSE','ông',   'ông',   'cháu','Ông nội/ngoại, ông bác, ông chú bên chồng hoặc bên vợ. Con dâu gọi ông nội của chồng là ông — đúng như chồng gọi.', 20),
('00000000-0000-0000-0000-0000000000b1','BA_BEN_BAN_DOI',    2,'IN_LAW','FEMALE','EGO_IS_SPOUSE','bà',    'bà',    'cháu','Bà nội/ngoại, bà bác, bà cô bên chồng hoặc bên vợ.', 20),
('00000000-0000-0000-0000-0000000000b1','ONG_BA_BEN_BAN_DOI',2,'IN_LAW',NULL,    'EGO_IS_SPOUSE','ông/bà','ông/bà','cháu','Đời trên 2 bậc bên bạn đời, chưa rõ giới tính người được gọi.', 26),
('00000000-0000-0000-0000-0000000000b1','CU_BEN_BAN_DOI',    3,'IN_LAW',NULL,    'EGO_IS_SPOUSE','cụ',    'cụ',    'cháu','Bậc cụ bên chồng/bên vợ — gọi chung là cụ, thống nhất với nhóm B2.', 20),
('00000000-0000-0000-0000-0000000000b1','KY_BEN_BAN_DOI',    4,'IN_LAW',NULL,    'EGO_IS_SPOUSE','kỵ',    'kỵ',    'cháu','Bậc kỵ bên chồng/bên vợ. Biến thể vùng miền (kĩnh) khai ở rule set REGION.', 20);

INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta_min, side, gender, in_law_direction,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','TO_TIEN_BEN_BAN_DOI',5,'IN_LAW',NULL,'EGO_IS_SPOUSE','tổ tiên bên chồng/bên vợ','tổ','cháu','Từ đời thứ 5 phía trên bạn đời trở lên.', 70);

-- J6b. Đời trên 1 bậc, bàng hệ — bác/chú/cô/cậu/dì của bạn đời.
--      link_side là bên nội/ngoại TÍNH TỪ BẠN ĐỜI (bên nội của chồng, bên ngoại
--      của vợ...), nên bảng này là bản sao đúng nghĩa của nhóm D.
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree_min, side, gender, is_elder,
     link_side, in_law_direction, title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','BAC_TRAI_BEN_BAN_DOI',1,1,'IN_LAW','MALE',  TRUE, NULL,      'EGO_IS_SPOUSE','bác',       'bác',    'cháu','Anh của bố/mẹ chồng (hoặc bố/mẹ vợ) — vai trên, gọi bác cả nội lẫn ngoại như nhóm D.', 20),
('00000000-0000-0000-0000-0000000000b1','BAC_GAI_BEN_BAN_DOI', 1,1,'IN_LAW','FEMALE',TRUE, NULL,      'EGO_IS_SPOUSE','bác gái',   'bác',    'cháu','Chị của bố/mẹ bạn đời — vai trên.', 20),
('00000000-0000-0000-0000-0000000000b1','CHU_BEN_BAN_DOI',     1,1,'IN_LAW','MALE',  FALSE,'PATERNAL','EGO_IS_SPOUSE','chú',       'chú',    'cháu','Em trai của bố chồng/bố vợ — bên nội của bạn đời.', 20),
('00000000-0000-0000-0000-0000000000b1','CO_BEN_BAN_DOI',      1,1,'IN_LAW','FEMALE',FALSE,'PATERNAL','EGO_IS_SPOUSE','cô',        'cô',     'cháu','Em gái của bố chồng/bố vợ.', 20),
('00000000-0000-0000-0000-0000000000b1','CAU_BEN_BAN_DOI',     1,1,'IN_LAW','MALE',  FALSE,'MATERNAL','EGO_IS_SPOUSE','cậu',       'cậu',    'cháu','Em trai của mẹ chồng/mẹ vợ — bên ngoại của bạn đời.', 20),
('00000000-0000-0000-0000-0000000000b1','DI_BEN_BAN_DOI',      1,1,'IN_LAW','FEMALE',FALSE,'MATERNAL','EGO_IS_SPOUSE','dì',        'dì',     'cháu','Em gái của mẹ chồng/mẹ vợ.', 20),
('00000000-0000-0000-0000-0000000000b1','CHU_CAU_BEN_BAN_DOI_CHUA_RO',1,1,'IN_LAW','MALE',  FALSE,NULL,'EGO_IS_SPOUSE','chú/cậu',   'chú/cậu','cháu','Vai dưới nhưng chưa rõ bên nội hay bên ngoại của bạn đời.', 44),
('00000000-0000-0000-0000-0000000000b1','CO_DI_BEN_BAN_DOI_CHUA_RO',  1,1,'IN_LAW','FEMALE',FALSE,NULL,'EGO_IS_SPOUSE','cô/dì',     'cô/dì',  'cháu','Vai dưới nhưng chưa rõ bên nội hay bên ngoại của bạn đời.', 44),
('00000000-0000-0000-0000-0000000000b1','BAC_CHU_BEN_BAN_DOI_CHUA_RO',1,1,'IN_LAW','MALE',  NULL, NULL,'EGO_IS_SPOUSE','bác/chú',   'bác/chú','cháu','Anh em trai của bố/mẹ bạn đời, chưa rõ thứ tự sinh.', 46),
('00000000-0000-0000-0000-0000000000b1','BAC_CO_BEN_BAN_DOI_CHUA_RO', 1,1,'IN_LAW','FEMALE',NULL, NULL,'EGO_IS_SPOUSE','bác gái/cô','bác/cô', 'cháu','Chị em gái của bố/mẹ bạn đời, chưa rõ thứ tự sinh.', 46);

-- J6c. Cùng đời với bạn đời — anh/chị/em của chồng hoặc của vợ.
--      NGOẠI LỆ đã nói ở đầu nhóm: em của bạn đời gọi theo lối con cái gọi
--      (chú/cô bên chồng, cậu/dì bên vợ), nên phải tách theo link_gender.
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree_min, side, gender, is_elder,
     link_gender, in_law_direction, title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','ANH_BEN_BAN_DOI',0,1,'IN_LAW','MALE',  TRUE, NULL,    'EGO_IS_SPOUSE','anh','anh','em', 'Anh của chồng hoặc của vợ. Em dâu/em rể gọi anh, xưng em.', 20),
('00000000-0000-0000-0000-0000000000b1','CHI_BEN_BAN_DOI',0,1,'IN_LAW','FEMALE',TRUE, NULL,    'EGO_IS_SPOUSE','chị','chị','em', 'Chị của chồng hoặc của vợ.', 20),
('00000000-0000-0000-0000-0000000000b1','CHU_EM_CHONG',   0,1,'IN_LAW','MALE',  FALSE,'MALE',  'EGO_IS_SPOUSE','chú','chú',NULL,'Em trai của chồng. Chị dâu gọi em chồng là chú — theo lối con cái gọi, không gọi em.', 20),
('00000000-0000-0000-0000-0000000000b1','CO_EM_CHONG',    0,1,'IN_LAW','FEMALE',FALSE,'MALE',  'EGO_IS_SPOUSE','cô', 'cô', NULL,'Em gái của chồng.', 20),
('00000000-0000-0000-0000-0000000000b1','CAU_EM_VO',      0,1,'IN_LAW','MALE',  FALSE,'FEMALE','EGO_IS_SPOUSE','cậu','cậu',NULL,'Em trai của vợ. Anh rể gọi cậu.', 20),
('00000000-0000-0000-0000-0000000000b1','DI_EM_VO',       0,1,'IN_LAW','FEMALE',FALSE,'FEMALE','EGO_IS_SPOUSE','dì', 'dì', NULL,'Em gái của vợ.', 20),
('00000000-0000-0000-0000-0000000000b1','ANH_CHU_BEN_CHONG_CHUA_RO',0,1,'IN_LAW','MALE',  NULL,'MALE',  'EGO_IS_SPOUSE','anh/chú','anh/chú','em','Anh em trai của chồng, chưa rõ thứ tự sinh.', 46),
('00000000-0000-0000-0000-0000000000b1','CHI_CO_BEN_CHONG_CHUA_RO', 0,1,'IN_LAW','FEMALE',NULL,'MALE',  'EGO_IS_SPOUSE','chị/cô', 'chị/cô', 'em','Chị em gái của chồng, chưa rõ thứ tự sinh.', 46),
('00000000-0000-0000-0000-0000000000b1','ANH_CAU_BEN_VO_CHUA_RO',   0,1,'IN_LAW','MALE',  NULL,'FEMALE','EGO_IS_SPOUSE','anh/cậu','anh/cậu','em','Anh em trai của vợ, chưa rõ thứ tự sinh.', 46),
('00000000-0000-0000-0000-0000000000b1','CHI_DI_BEN_VO_CHUA_RO',    0,1,'IN_LAW','FEMALE',NULL,'FEMALE','EGO_IS_SPOUSE','chị/dì', 'chị/dì', 'em','Chị em gái của vợ, chưa rõ thứ tự sinh.', 46);

-- J6d. Đời DƯỚI bạn đời, bàng hệ (collateral_degree >= 1 — xem "cố ý bỏ trống"
--      ở đầu nhóm). Thím gọi cháu của chồng là "cháu".
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree_min, side, gender, in_law_direction,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','CHAU_BEN_BAN_DOI',-1,1,'IN_LAW',NULL,'EGO_IS_SPOUSE','cháu','cháu',NULL,'Cháu của chồng/của vợ. Thím gọi cháu bên chồng là cháu; A tự xưng thím/mợ/bác gái tuỳ vai, resolver tra ngược.', 26),
('00000000-0000-0000-0000-0000000000b1','CHAT_BEN_BAN_DOI',-2,1,'IN_LAW',NULL,'EGO_IS_SPOUSE','chắt','chắt',NULL,'Chắt bên chồng/bên vợ.', 26),
('00000000-0000-0000-0000-0000000000b1','CHUT_BEN_BAN_DOI',-3,1,'IN_LAW',NULL,'EGO_IS_SPOUSE','chút','chút',NULL,'Chút bên chồng/bên vợ.', 30);

INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta_max, collateral_degree_min, side, gender, in_law_direction,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','HAU_DUE_BEN_BAN_DOI',-4,1,'IN_LAW',NULL,'EGO_IS_SPOUSE','hậu duệ bên chồng/bên vợ','hậu duệ',NULL,'Từ đời thứ 4 phía dưới bạn đời trở xuống.', 80);

-- =============================================================================
-- NHÓM K — CON NUÔI, CHA MẸ NUÔI, THỪA TỰ / KẾ TỰ / ĐÍCH TÔN
--   Khớp theo cạnh trực tiếp nên luôn thắng suy luận theo LCA.
--   LƯU Ý: cạnh PARENT {type:'ADOPT'} VẪN nằm trong đường đi phả hệ (con nuôi
--   được ghi nhận đầy đủ). Nếu một dòng họ muốn tách bạch, hãy ghi đè
--   relation_code tương ứng ở rule set con, KHÔNG xoá cạnh khỏi graph.
-- =============================================================================
-- K1. Nuôi dưỡng + vai trò nối dõi nhìn TỪ NGƯỜI ĐỂ LẠI HƯƠNG HOẢ sang người
--     nối dõi (direct_link_reversed = FALSE).
--     QUYẾT ĐỊNH CỦA HỘI ĐỒNG TỘC BIỂU (khoảng trống 3): người nối dõi được gọi
--     là "đích tôn" / "đích nữ", hoặc "con" / "cháu" / "chắt" tuỳ bậc — nên
--     đích tôn tách theo giới tính, còn thừa tự/kế tự tách theo gen_delta.
INSERT INTO kinship_rule
    (rule_set_id, relation_code, side, gender, direct_link, direct_link_subtype,
     direct_link_reversed, title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','BO_NUOI',    'BLOOD','MALE',  'PARENT_ADOPT',NULL,TRUE, 'bố nuôi', 'bố', 'con nuôi','Cạnh PARENT_ADOPT đi từ B (cha nuôi) sang A.', 8),
('00000000-0000-0000-0000-0000000000b1','ME_NUOI',    'BLOOD','FEMALE','PARENT_ADOPT',NULL,TRUE, 'mẹ nuôi', 'mẹ', 'con nuôi','Cạnh PARENT_ADOPT đi từ B (mẹ nuôi) sang A.', 8),
('00000000-0000-0000-0000-0000000000b1','CON_NUOI',   'BLOOD',NULL,    'PARENT_ADOPT',NULL,FALSE,'con nuôi','con',NULL,      'Cạnh PARENT_ADOPT đi từ A sang B.', 8),
('00000000-0000-0000-0000-0000000000b1','DICH_TON',   'BLOOD','MALE',  'HEIR','DICH_TON',FALSE,'đích tôn',       'đích tôn','cháu','Cháu đích tôn — cháu trai trưởng của con trai trưởng, người giữ hương hoả.', 6),
('00000000-0000-0000-0000-0000000000b1','DICH_NU',    'BLOOD','FEMALE','HEIR','DICH_TON',FALSE,'đích nữ',        'đích nữ', 'cháu','Người nối dõi giữ hương hoả là NỮ. Con gái được ghi nhận ngang bằng con trai (BA v2 §12), nên vai trò này phải có danh xưng riêng.', 6),
('00000000-0000-0000-0000-0000000000b1','DICH_TON_CHUA_RO_GIOI','BLOOD',NULL,'HEIR','DICH_TON',FALSE,'đích tôn/đích nữ','đích tôn','cháu','Cạnh HEIR DICH_TON nhưng phả chưa ghi giới tính người nối dõi.', 9),
('00000000-0000-0000-0000-0000000000b1','NGUOI_THUA_TU','BLOOD',NULL,  'HEIR','THUA_TU', FALSE,'người thừa tự','thừa tự',NULL,'Luật lui của thừa tự: có cạnh HEIR nhưng KHÔNG suy được bậc (người thừa tự lấy từ ngoài họ, hoặc phả thiếu đường huyết thống). Không được đánh mất vai trò nối dõi.', 9),
('00000000-0000-0000-0000-0000000000b1','NGUOI_KE_TU',  'BLOOD',NULL,  'HEIR','KE_TU',   FALSE,'người kế tự',  'kế tự',  NULL,'Luật lui của kế tự khi không suy được bậc.', 9);

-- K1b. Thừa tự / kế tự tách theo BẬC — "con" khi kém một đời, "cháu" khi kém
--      hai đời, "chắt" khi kém ba đời (đúng lời Hội đồng: con/cháu/chắt tuỳ
--      trường hợp). gen_delta lấy trên đường huyết thống nên khi hai người
--      không có tổ chung thì các luật này không khớp và rơi về K1 (priority 9).
INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, side, gender, direct_link, direct_link_subtype,
     direct_link_reversed, title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','CON_THUA_TU', -1,'BLOOD',NULL,'HEIR','THUA_TU',FALSE,'con thừa tự', 'thừa tự',NULL,'Người được lập để thừa tự hương hoả, kém A một đời.', 6),
('00000000-0000-0000-0000-0000000000b1','CHAU_THUA_TU',-2,'BLOOD',NULL,'HEIR','THUA_TU',FALSE,'cháu thừa tự','thừa tự',NULL,'Người thừa tự kém A hai đời.', 6),
('00000000-0000-0000-0000-0000000000b1','CHAT_THUA_TU',-3,'BLOOD',NULL,'HEIR','THUA_TU',FALSE,'chắt thừa tự','thừa tự',NULL,'Người thừa tự kém A ba đời.', 6),
('00000000-0000-0000-0000-0000000000b1','CON_KE_TU',   -1,'BLOOD',NULL,'HEIR','KE_TU',  FALSE,'con kế tự',   'kế tự',  NULL,'Người được lập nối dõi cho người TUYỆT TỰ (person.lineage_status = TUYET_TU/KE_TU), kém A một đời.', 6),
('00000000-0000-0000-0000-0000000000b1','CHAU_KE_TU',  -2,'BLOOD',NULL,'HEIR','KE_TU',  FALSE,'cháu kế tự',  'kế tự',  NULL,'Người kế tự kém A hai đời.', 6),
('00000000-0000-0000-0000-0000000000b1','CHAT_KE_TU',  -3,'BLOOD',NULL,'HEIR','KE_TU',  FALSE,'chắt kế tự',  'kế tự',  NULL,'Người kế tự kém A ba đời.', 6);

-- K2. CHIỀU NGƯỢC của vai trò KẾ TỰ (direct_link_reversed = TRUE) — người kế tự
--     gọi người mình nối dõi cho.
--     Vì sao chỉ khai cho KE_TU:
--       · KE_TU lập ra một dòng cha-con TRÊN DANH NGHĨA cho người tuyệt tự —
--         người kế tự cúng giỗ như con, nên chiều ngược phải là "cha/mẹ kế tự".
--         Trước đây chiều này rơi về suy luận huyết thống thuần ("chú"), đánh
--         mất hẳn vai trò vừa được lập.
--       · DICH_TON thì KHÔNG khai chiều ngược: đích tôn vốn đã là cháu nội
--         ruột, vẫn gọi ông là "ông nội". Cạnh HEIR không được phép xoá quan hệ
--         huyết thống có sẵn.
--       · THUA_TU cũng không khai chiều ngược: người thừa tự thường là con ruột,
--         vẫn gọi "bố"/"mẹ".
--     Dòng họ muốn khác thì khai đè theo relation_code ở rule set CLAN/BRANCH.
INSERT INTO kinship_rule
    (rule_set_id, relation_code, side, gender, direct_link, direct_link_subtype,
     direct_link_reversed, title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','CHA_KE_TU',   'BLOOD','MALE',  'HEIR','KE_TU',TRUE,'cha kế tự',   'cha','con kế tự','A là người kế tự; B là người tuyệt tự mà A nối dõi cho (nam).', 6),
('00000000-0000-0000-0000-0000000000b1','ME_KE_TU',    'BLOOD','FEMALE','HEIR','KE_TU',TRUE,'mẹ kế tự',    'mẹ', 'con kế tự','A là người kế tự; B là người tuyệt tự mà A nối dõi cho (nữ).', 6),
('00000000-0000-0000-0000-0000000000b1','CHA_ME_KE_TU','BLOOD',NULL,    'HEIR','KE_TU',TRUE,'cha/mẹ kế tự','cha/mẹ','con kế tự','Chiều ngược của kế tự khi phả chưa ghi giới tính người để lại hương hoả.', 9);

-- =============================================================================
-- NHÓM L — LUẬT VÉT (không bao giờ để người dùng nhận về kết quả rỗng)
-- =============================================================================
INSERT INTO kinship_rule
    (rule_set_id, relation_code, collateral_degree_min, side, gender,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','HO_HANG_NOI',  1,'PATERNAL',NULL,'họ hàng bên nội', 'họ nội', NULL,'Luật vét: xác định được là bên nội nhưng không khớp luật cụ thể nào.', 900),
('00000000-0000-0000-0000-0000000000b1','HO_HANG_NGOAI',1,'MATERNAL',NULL,'họ hàng bên ngoại','họ ngoại',NULL,'Luật vét bên ngoại.', 900),
('00000000-0000-0000-0000-0000000000b1','HO_HANG_XA',   1,'BLOOD',   NULL,'họ hàng xa',      'họ xa',  NULL,'Luật vét cuối cùng cho quan hệ huyết thống chưa phân loại được.', 950);

INSERT INTO kinship_rule
    (rule_set_id, relation_code, side, gender, title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b1','KHONG_XAC_DINH',NULL,NULL,'chưa xác định quan hệ','chưa rõ',NULL,'Luật vét cuối cùng: không tìm được LCA (khác dòng họ, hoặc dữ liệu thiếu). API trả về kèm gợi ý bổ sung dữ liệu.', 999);

-- =============================================================================
-- BỘ LUẬT CẤP REGION — BIẾN THỂ "KĨNH" TRÊN BẬC CỤ  (CHỜ HỘI ĐỒNG CHỐT)
--
--   Hội đồng Tộc biểu nói: "trên bậc cụ, một số nơi gọi là kĩnh". Bộ DEFAULT
--   giữ nguyên "kỵ" (nhất quán với KY_ONG/KY_BA/KY_HO đã seed từ đầu); biến thể
--   vùng miền được khai đúng chỗ của nó — một rule set scope = REGION kế thừa
--   bộ DEFAULT, theo cơ chế DEFAULT -> REGION -> CLAN -> BRANCH.
--
--   ############################################################################
--   # is_active = FALSE — CỐ Ý.                                                #
--   # Hội đồng CHƯA xác nhận hai điều: (1) chính tả "kĩnh" hay "kỵ"/"kính";    #
--   # (2) vùng áp dụng. Ở đây tạm ghi region = 'TRUNG' để dòng dữ liệu hợp lệ  #
--   # theo ck_rule_set_region_shape, KHÔNG phải kết luận của hệ thống.         #
--   # Khi Hội đồng chốt: sửa cột region cho đúng rồi bật is_active = TRUE      #
--   # (qua API /api/v1/kinship-rules, không cần sửa migration).                #
--   # Đang tắt nên RuleSetJpaRepository.pickByRegion() bỏ qua hoàn toàn:       #
--   # không dòng họ nào bị đổi danh xưng ngoài ý muốn.                          #
--   ############################################################################
-- =============================================================================
INSERT INTO kinship_rule_set (id, code, name, scope, parent_id, region, branch_id, description, is_active)
VALUES (
    '00000000-0000-0000-0000-0000000000b2',
    'REGION_KY_GOI_LA_KINH',
    'Biến thể vùng miền — trên bậc cụ gọi "kĩnh"',
    'REGION',
    '00000000-0000-0000-0000-0000000000b1',
    'TRUNG',
    NULL,
    'CHO HOI DONG TOC BIEU XAC NHAN chinh ta "kinh" va vung ap dung. Dang TAT (is_active=FALSE) nen khong anh huong dong ho nao. Bat len sau khi Hoi dong chot.',
    FALSE
)
ON CONFLICT (code) DO UPDATE SET
    name        = EXCLUDED.name,
    scope       = EXCLUDED.scope,
    parent_id   = EXCLUDED.parent_id,
    description = EXCLUDED.description,
    updated_at  = now();
-- CHÚ Ý: ON CONFLICT ở trên KHÔNG đụng vào region và is_active — nếu Hội đồng
-- đã chỉnh hai cột đó trên môi trường chạy thật thì lần deploy sau không ghi đè
-- mất quyết định của họ.

DELETE FROM kinship_rule
WHERE rule_set_id = '00000000-0000-0000-0000-0000000000b2'::uuid;

INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree, side, gender,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b2','KY_ONG',4,0,'BLOOD','MALE',  'kĩnh ông','kĩnh','cháu','Ghi đè KY_ONG của bộ DEFAULT: đời thứ 4 phía trên gọi là kĩnh thay vì kỵ.', 20),
('00000000-0000-0000-0000-0000000000b2','KY_BA', 4,0,'BLOOD','FEMALE','kĩnh bà', 'kĩnh','cháu','Ghi đè KY_BA của bộ DEFAULT.', 20);

INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, collateral_degree_min, side, gender,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b2','KY_HO',4,1,'BLOOD',NULL,'kĩnh họ','kĩnh','cháu','Ghi đè KY_HO của bộ DEFAULT — đời trên 4 bậc, bàng hệ.', 50);

INSERT INTO kinship_rule
    (rule_set_id, relation_code, gen_delta, side, gender, in_law_direction,
     title, title_short, ego_self_term, description, priority)
VALUES
('00000000-0000-0000-0000-0000000000b2','KY_BEN_BAN_DOI',4,'IN_LAW',NULL,'EGO_IS_SPOUSE','kĩnh','kĩnh','cháu','Ghi đè KY_BEN_BAN_DOI: dâu/rể cũng gọi kĩnh theo lối của vùng.', 20);

-- =============================================================================
-- KIỂM TRA SAU KHI SEED — thất bại thì migration dừng, không để bộ luật lỗi lọt
-- =============================================================================
DO $do$
DECLARE
    n_rules  INT;
    n_region INT;
    n_ego_spouse INT;
BEGIN
    SELECT count(*) INTO n_rules
    FROM kinship_rule
    WHERE rule_set_id = '00000000-0000-0000-0000-0000000000b1'::uuid;

    IF n_rules < 150 THEN
        RAISE EXCEPTION 'Seed danh xung DEFAULT_BAC bi thieu: chi co % luat (mong doi 157).', n_rules;
    END IF;

    -- Khoang trong 1: chieu EGO_IS_SPOUSE (dau/re goi ho nha chong/vo) phai phu
    -- ca bac tren, cung doi lan bac duoi — khong con chi rieng bo/me chong.
    SELECT count(*) INTO n_ego_spouse
    FROM kinship_rule
    WHERE rule_set_id = '00000000-0000-0000-0000-0000000000b1'::uuid
      AND in_law_direction = 'EGO_IS_SPOUSE';

    IF n_ego_spouse < 30 THEN
        RAISE EXCEPTION 'Nhom J1+J6 (dau/re) bi thieu: chi co % luat EGO_IS_SPOUSE.', n_ego_spouse;
    END IF;

    -- Khoang trong 2: bac ong/ba, cu va chau deu phai co bien the side = BLOOD.
    IF NOT EXISTS (SELECT 1 FROM kinship_rule
                   WHERE rule_set_id = '00000000-0000-0000-0000-0000000000b1'::uuid
                     AND side = 'BLOOD' AND collateral_degree = 0
                     AND gen_delta IN (2, 3, -2))
    THEN
        RAISE EXCEPTION 'Nhom B2 (chua ro ben noi/ngoai) chua duoc seed.';
    END IF;

    -- Khoang trong 3: chieu nguoc cua ke tu phai ton tai, khong de roi ve "chu".
    IF NOT EXISTS (SELECT 1 FROM kinship_rule
                   WHERE rule_set_id = '00000000-0000-0000-0000-0000000000b1'::uuid
                     AND direct_link = 'HEIR' AND direct_link_subtype = 'KE_TU'
                     AND direct_link_reversed)
    THEN
        RAISE EXCEPTION 'Nhom K2 (chieu nguoc cua ke tu) chua duoc seed.';
    END IF;

    SELECT count(*) INTO n_region
    FROM kinship_rule
    WHERE rule_set_id = '00000000-0000-0000-0000-0000000000b2'::uuid;

    RAISE NOTICE 'Seed danh xung DEFAULT_BAC: % luat (% luat chieu dau/re). Bo REGION "kinh": % luat, dang TAT cho Hoi dong chot.',
        n_rules, n_ego_spouse, n_region;
END
$do$;

-- =============================================================================
-- VÍ DỤ ĐỐI CHIẾU (dùng làm ca kiểm thử cho KinshipResolver ở W3)
-- -----------------------------------------------------------------------------
--  1. A hỏi về anh trai của bố:      dist_a=2, dist_b=1 -> gen_delta 1, coll 1,
--     side PATERNAL, gender MALE, is_elder TRUE          => "bác"
--  2. A hỏi về em trai của bố:       cùng trên, is_elder FALSE => "chú"
--  3. A hỏi về anh họ của bố (chung cụ): dist_a=3, dist_b=2 -> gen_delta 1,
--     coll 2, PATERNAL, MALE, elder TRUE                 => "bác họ"
--  4. A hỏi về em trai của mẹ:       gen_delta 1, coll 1, MATERNAL, MALE,
--     is_elder FALSE                                      => "cậu"
--  5. A hỏi về vợ của chú:           IN_LAW, gen_delta 1, coll 1,
--     link_side PATERNAL, link_gender MALE, elder FALSE, gender FEMALE => "thím"
--  6. ĐA THÊ: A và B cùng cha khác mẹ, B sinh trước       => "anh trai" (coll 1,
--     LCA là người cha; vợ cả hay vợ lẽ KHÔNG đổi danh xưng của con).
--  7. CON NUÔI: B nhận A làm con nuôi -> cạnh PARENT_ADOPT B->A => "bố nuôi";
--     đồng thời B vẫn là "bố" theo LCA nếu có cả cạnh BIO — luật direct_link
--     priority 8 thắng luật CHA priority 10.
--  8. TÁI HÔN: A là con riêng của vợ; chồng mới KHÔNG có cạnh PARENT tới A =>
--     LCA không có, rơi vào luật vét KHONG_XAC_DINH. Nếu dòng họ muốn ghi nhận
--     "bố dượng" thì tạo cạnh PARENT_ADOPT (hoặc thêm luật ở rule set con) —
--     đây là một quyết định của Hội đồng Tộc biểu, không phải mặc định hệ thống.
--  9. TUYỆT TỰ / KẾ TỰ: người tuyệt tự có lineage_status = TUYET_TU; sau khi lập
--     người kế tự thì tạo cạnh HEIR {heir_type:'KE_TU'} => "con kế tự".
-- 10. XOÁ MỀM: node của người đã xoá mềm vẫn nằm trên đường đi để nối các đời,
--     nhưng KHÔNG được chọn làm LCA và không xuất hiện trong kết quả cây.
--
-- ----- Ba quyết định mới của Hội đồng Tộc biểu (nhóm B2, J6, K1b/K2) ---------
-- 11. CON DÂU GỌI HỌ NHÀ CHỒNG (J6): A là vợ của ConTrai, B là ông nội của
--     ConTrai. NGƯỜI NỐI = ConTrai, in_law_direction = EGO_IS_SPOUSE,
--     đường huyết thống ConTrai -> B cho gen_delta 2, collateral 0
--     => ONG_BEN_BAN_DOI => "ông". Đúng bằng lối chồng gọi.
-- 12. CON DÂU GỌI ÔNG CHÚ BÊN CHỒNG (J6): cùng NGƯỜI NỐI, đường huyết thống cho
--     gen_delta 2, collateral 1 => vẫn ONG_BEN_BAN_DOI => "ông". Luật J6a cố ý
--     KHÔNG ràng collateral_degree, vì từ bậc ông trở lên lối gọi giống nhau.
-- 13. CHỊ DÂU GỌI EM CHỒNG (J6c): NGƯỜI NỐI = chồng (link_gender MALE), đường
--     huyết thống chồng -> em chồng cho gen_delta 0, collateral 1,
--     is_elder FALSE => CHU_EM_CHONG => "chú" (theo lối con cái gọi, KHÔNG phải
--     "em"). Nếu ego là rể thì link_gender FEMALE => CAU_EM_VO => "cậu".
-- 14. THÍM GỌI CHÁU BÊN CHỒNG (J6d): NGƯỜI NỐI = chú, đường huyết thống
--     chú -> cháu cho gen_delta -1, collateral 1 => CHAU_BEN_BAN_DOI => "cháu".
-- 15. ĐA THÊ + DÂU: mỗi cạnh SPOUSE còn hiệu lực sinh một NGƯỜI NỐI; analyzer
--     lấy đường NGẮN NHẤT, nên bà vợ hai vẫn gọi họ nhà chồng y như bà vợ cả —
--     spouse_order KHÔNG đổi danh xưng, chỉ đổi thứ tự hiển thị.
-- 16. LY HÔN + DÂU: cạnh SPOUSE hết hiệu lực (valid_to IS NULL sai) thì analyzer
--     bỏ qua, không còn NGƯỜI NỐI, quan hệ rơi về luật vét — dâu cũ không còn
--     danh xưng với họ nhà chồng cũ. Đó là hành vi mong muốn.
-- 17. PHẢ THIẾU GIỚI TÍNH NGƯỜI NỐI (B2): Kỵ -> Cụ -> Ông -> [chưa rõ giới] ->
--     Tôi. Bước đi lên đầu tiên của Tôi qua người chưa rõ giới nên side = BLOOD:
--     Tôi gọi Ông là "ông" (ONG_CHUA_RO_BEN), gọi Cụ là "cụ" (CU_CHUA_RO_BEN),
--     và Ông gọi Tôi là "cháu" (CHAU_CHUA_RO_BEN). Khi bổ sung được giới tính,
--     luật ONG_NOI/ONG_NGOAI (priority 10) lập tức thắng và trả lại nội/ngoại.
-- 18. NHẬN NUÔI + THIẾU GIỚI TÍNH: cạnh PARENT_ADOPT vẫn nằm trên đường đi nên
--     nhóm B2 áp dụng y hệt; luật BO_NUOI/ME_NUOI (priority 8) vẫn thắng ở bậc
--     kề vì khớp theo cạnh.
-- 19. KẾ TỰ, CHIỀU NGƯỢC (K2): NgườiTuyệtTự lập cháu ruột làm người kế tự
--     => cạnh HEIR{KE_TU}. Chiều thuận gen_delta -1 => "con kế tự"; chiều ngược
--     => CHA_KE_TU => "cha kế tự", KHÔNG còn trả về "chú" trống trơn.
-- 20. ĐÍCH TÔN, CHIỀU NGƯỢC: cố ý KHÔNG khai. Đích tôn vốn là cháu nội ruột nên
--     vẫn gọi "ông nội" — cạnh HEIR không được xoá quan hệ huyết thống có sẵn.
-- 21. ĐÍCH NỮ: cạnh HEIR{DICH_TON} trỏ tới người nữ => DICH_NU => "đích nữ"
--     (BA v2 §12: con gái ghi nhận ngang bằng con trai).
-- =============================================================================
