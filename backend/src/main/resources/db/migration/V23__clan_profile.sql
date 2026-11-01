-- =============================================================================
-- V23__clan_profile.sql — Hồ sơ dòng họ: "Khởi tạo dòng họ" bằng màn hình (26/09/2026)
--
-- VÌ SAO CÓ BẢNG NÀY
-- Tới V22 hệ thống là "một bản cài = một dòng họ" nhưng KHÔNG có chỗ nào nói dòng họ ấy
-- là ai: tên "Họ Nguyễn Đình" chỉ sống trong dữ liệu mẫu (tên của chi gốc DONG_HO), vùng
-- miền nằm rải ở cột branch.region, và muốn dựng một dòng họ mới thì phải gõ SQL. Bảng này
-- là nguồn chân lý DUY NHẤT cho: tên dòng họ, nhãn ltree gốc, vùng miền (quyết định bộ luật
-- danh xưng cấp CLAN kế thừa từ đâu), từ đường, thuỷ tổ, ngày giỗ tổ.
--
-- ĐÂY LÀ DÒNG ĐẦU TIÊN CỦA BẢNG `clan` TƯƠNG LAI (bước 2 — nhiều dòng họ trên một bản cài)
-- Hôm nay bảng bị ép MỘT DÒNG bằng cột `singleton` (UNIQUE + CHECK). Bước 2 sẽ:
--   * đổi tên bảng thành `clan` và BỎ cột `singleton` (id của dòng này trở thành clan_id
--     của mọi dữ liệu đang có — vì thế cột id là UUID ổn định ngay từ bây giờ);
--   * thêm `clan_id` (FK → clan.id) vào: person, branch (hoặc suy từ gốc ltree),
--     relationship, event, post, honour, media_asset, kinship_rule_set (tầng CLAN/BRANCH),
--     invitation, clan_invite_code, person_claim, import_batch, app_user/branch_assignment
--     (một tài khoản có thể thuộc nhiều họ), audit_log;
--   * `root_branch_path` vẫn là UNIQUE — hai dòng họ không bao giờ chung một gốc ltree, và
--     phạm vi RBAC `ltree` của mỗi họ vẫn là cây con dưới gốc của chính nó.
-- Không cột nào ở đây giả định "chỉ có một dòng" ngoài chính cột `singleton`.
--
-- KHÔNG FK tới branch(id): hồ sơ nói "gốc ltree của họ là nhãn X", còn chi gốc DONG_HO là
-- dữ liệu của context genealogy và được ứng dụng tạo/đồng bộ khi lưu hồ sơ. Một FK ở đây
-- cũng sẽ làm `TRUNCATE branch ... CASCADE` của bộ test xoá mất hồ sơ.
-- =============================================================================

SET search_path = public, ag_catalog;

CREATE TABLE clan_profile (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    singleton              BOOLEAN       NOT NULL DEFAULT TRUE,
    name                   VARCHAR(120)  NOT NULL,
    short_name             VARCHAR(60),
    root_branch_path       LTREE         NOT NULL,
    region                 VARCHAR(10)   NOT NULL,
    ancestral_hall_name    VARCHAR(200),
    ancestral_hall_address VARCHAR(500),
    ancestor_name          VARCHAR(200),
    founded_note           VARCHAR(2000),
    gio_to_lunar_day       SMALLINT,
    gio_to_lunar_month     SMALLINT,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                BIGINT        NOT NULL DEFAULT 0,

    -- MỘT DÒNG: UNIQUE trên một cột chỉ nhận TRUE. Bước 2 bỏ cả cột lẫn hai ràng buộc này.
    CONSTRAINT ux_clan_profile_singleton UNIQUE (singleton),
    CONSTRAINT ck_clan_profile_singleton CHECK (singleton),
    CONSTRAINT ck_clan_profile_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_clan_profile_region CHECK (region IN ('BAC','TRUNG','NAM')),
    -- Gốc là MỘT nhãn, cùng bảng chữ với branch.slug (ck_branch_slug_ltree_safe): nhãn này sẽ
    -- thành slug của chi gốc DONG_HO, nên hai luật phải là một.
    CONSTRAINT ck_clan_profile_root_one_label CHECK (
        nlevel(root_branch_path) = 1
        AND root_branch_path::text ~ '^[a-z0-9_]{1,64}$'
    ),
    -- Có địa chỉ mà không có tên từ đường là dữ liệu mồ côi — giao diện không có chỗ hiện nó.
    CONSTRAINT ck_clan_profile_hall_shape CHECK (
        ancestral_hall_address IS NULL OR ancestral_hall_name IS NOT NULL
    ),
    -- Giỗ tổ theo âm lịch: ngày 1..30, tháng 1..12, và đi thành cặp.
    CONSTRAINT ck_clan_profile_gio_to CHECK (
        (gio_to_lunar_day IS NULL AND gio_to_lunar_month IS NULL)
        OR (gio_to_lunar_day BETWEEN 1 AND 30 AND gio_to_lunar_month BETWEEN 1 AND 12)
    )
);

CREATE UNIQUE INDEX ux_clan_profile_root ON clan_profile (root_branch_path);

CREATE TRIGGER tg_clan_profile_touch BEFORE UPDATE ON clan_profile
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE clan_profile IS
    'Ho so dong ho (mot dong). Dong dau tien cua bang clan o buoc 2 (nhieu dong ho tren mot ban cai).';
COMMENT ON COLUMN clan_profile.singleton IS
    'Luon TRUE; UNIQUE + CHECK ep bang chi co mot dong. Buoc 2 bo cot nay.';
COMMENT ON COLUMN clan_profile.root_branch_path IS
    'Nhan ltree goc cua dong ho = slug cua chi DONG_HO. Khong doi duoc khi da co nhan khau.';
COMMENT ON COLUMN clan_profile.region IS
    'BAC/TRUNG/NAM — bo luat danh xung cap CLAN ke thua tu bo REGION tuong ung (hoac DEFAULT).';

-- =============================================================================
-- TỰ ĐIỀN TỪ DỮ LIỆU ĐANG CHẠY
--
-- Bản cài đang chạy (dữ liệu mẫu họ Nguyễn Đình, gốc `ho_nguyen_dinh`) KHÔNG được rơi vào
-- `initialized = false` chỉ vì vừa nâng cấp: màn "Khởi tạo dòng họ" sẽ mời quản trị khởi tạo
-- lại một dòng họ đã có 1.500 người. Nên nếu bảng `branch` đã có gốc thì dựng sẵn một dòng từ
-- gốc ấy: tên lấy từ chi gốc (ưu tiên kind DONG_HO), vùng lấy từ chi gốc (thiếu thì BAC — đúng
-- vùng của bộ DEFAULT, tức không đổi danh xưng của ai).
--
-- Viết thành HÀM thay vì một câu INSERT trần để test tích hợp gọi lại được trên dữ liệu nó tự
-- dựng: Flyway chạy migration một lần trên CSDL rỗng, nên một câu INSERT trần sẽ không bao giờ
-- được kiểm trên dữ liệu thật. Hàm không làm gì nếu hồ sơ đã có (idempotent).
-- =============================================================================
CREATE OR REPLACE FUNCTION clan_profile_seed_from_branches()
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $fn$
DECLARE
    so_goc INT;
BEGIN
    IF EXISTS (SELECT 1 FROM clan_profile) THEN
        RETURN FALSE;
    END IF;

    SELECT count(DISTINCT subpath(b.path, 0, 1)) INTO so_goc
      FROM branch b
     WHERE NOT b.is_deleted;

    IF so_goc = 0 THEN
        RETURN FALSE;
    END IF;
    IF so_goc > 1 THEN
        RAISE NOTICE 'clan_profile: co % goc ltree; chon goc co chi DONG_HO va nhieu chi nhat.', so_goc;
    END IF;

    INSERT INTO clan_profile (name, root_branch_path, region)
    SELECT left(btrim(COALESCE(NULLIF(btrim(r.name), ''), g.nhan::text)), 120),
           g.nhan,
           COALESCE(r.region, 'BAC')
      FROM (SELECT subpath(b.path, 0, 1) AS nhan, count(*) AS so_chi
              FROM branch b
             WHERE NOT b.is_deleted
             GROUP BY 1) g
      LEFT JOIN branch r ON r.path = g.nhan AND NOT r.is_deleted
     -- Nhãn gốc phải hợp lệ với ck_clan_profile_root_one_label; slug của branch đã cùng luật
     -- nên điều kiện này chỉ loại dữ liệu từng bị sửa tay.
     WHERE g.nhan::text ~ '^[a-z0-9_]{1,64}$'
     ORDER BY (r.branch_kind = 'DONG_HO') DESC NULLS LAST, g.so_chi DESC, g.nhan
     LIMIT 1;

    RETURN FOUND;
END
$fn$;

COMMENT ON FUNCTION clan_profile_seed_from_branches() IS
    'Dung ho so dong ho tu goc ltree dang co trong branch (neu chua co ho so). Tra TRUE neu vua tao.';

SELECT clan_profile_seed_from_branches();
