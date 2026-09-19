-- =============================================================================
-- V9 — ĐƯỜNG ỐNG NHẬP LIỆU HÀNG LOẠT (bounded context `dataimport`)
-- Nguồn: plan/02-nhap-lieu/index.html — N3 (khu vực chờ), N4 (bộ kiểm),
--        N5 (bất biến khi tải lại), §14 (mẫu Excel chưa tả nổi).
--
-- LỆCH SO VỚI KẾ HOẠCH — ĐỌC TRƯỚC:
--   Kế hoạch §1 ràng buộc 4 ghi "migration mới đánh số tiếp V8". Thực tế V8 đã bị một
--   luồng khác chiếm, nên đường ống nhập liệu nằm ở V9. Đây là lệch về ĐÁNH SỐ, không
--   phải lệch về nội dung: toàn bộ 5 bảng của §5.2 vẫn ở đây, cùng lược đồ `public`,
--   cùng tiền tố `import_`.
--
-- BẤT BIẾN LỚN NHẤT CỦA TỆP NÀY:
--   Không một bảng nào dưới đây được ghi vào `person` / `relationship` / đồ thị AGE.
--   Chúng là KHU VỰC CHỜ. Ranh giới ghi nằm ở đúng một bước (COMMITTING) và bước đó
--   thuộc nửa sau của đường ống, chưa hiện thực ở đợt này.
-- =============================================================================

-- =============================================================================
-- 9.1 place_division — DANH MỤC MÃ TỈNH / QUỐC GIA
-- =============================================================================
-- VÌ SAO BẢNG NÀY PHẢI CÓ NGAY BÂY GIỜ, KHÔNG ĐỂ ĐỢT SAU:
--   `person.native_place` và `person.current_place` là VARCHAR(255) TỰ DO. Không có
--   mã nào cả. Hệ quả là báo cáo dân số (FR-4.3) KHÔNG DỰNG ĐƯỢC: "Hà Nội",
--   "TP. Hà Nội", "Tp Hà Nội", "Hanoi", "HN" là năm nhóm khác nhau với mọi phép
--   GROUP BY, và không có cách nào gộp lại sau khi cả dòng họ đã gõ xong.
--   Quyết muộn = cả dòng họ phải nhập lại nơi ở. Vì vậy cột mã vào ĐỢT NÀY, cùng lúc
--   với mẫu Excel, chứ không phải vào đợt báo cáo.
--
-- VÌ SAO BẢNG NÀY ĐƯỢC TẠO GẦN NHƯ RỖNG:
--   Danh mục 34 tỉnh/thành sau sáp nhập 01/7/2025 là dữ liệu pháp quy
--   (Quyết định 19/2025/QĐ-TTg). Chép lại từ trí nhớ thì sai ở đúng những chỗ khó
--   thấy — tên giữ lại sau sáp nhập KHÔNG phải lúc nào cũng là tên của tỉnh cho mã số.
--   Một danh mục sai làm báo cáo dân số sai một cách IM LẶNG, tệ hơn hẳn không có
--   danh mục, vì bộ kiểm sẽ báo "mã hợp lệ" cho một mã trỏ nhầm tỉnh.
--   Vì vậy: LƯỢC ĐỒ vào V9, NỘI DUNG do quản trị nạp từ văn bản gốc. Bộ kiểm tự tắt
--   luật mã tỉnh khi danh mục còn rỗng (xem PlaceCodeRule) nên không ai bị chặn.
CREATE TABLE place_division (
    code             VARCHAR(12)  PRIMARY KEY,
    kind             VARCHAR(12)  NOT NULL,
    country_code     CHAR(2)      NOT NULL DEFAULT 'VN',
    name             VARCHAR(120) NOT NULL,
    name_unaccented  TEXT         GENERATED ALWAYS AS (vn_unaccent(name)) STORED,
    -- Hiệu lực hành chính: gia phả chép nguyên quán theo tỉnh của THỜI ĐIỂM ĐÓ
    -- ("quê Hà Tây"), nên danh mục phải giữ được cả đơn vị đã giải thể.
    valid_from       DATE,
    valid_to         DATE,
    -- Đơn vị đã sáp nhập trỏ về đơn vị kế thừa; báo cáo dân số đi theo chuỗi này để
    -- gộp "Hà Tây" vào "Hà Nội" mà KHÔNG phải sửa dữ liệu người dùng đã nhập.
    merged_into_code VARCHAR(12)  REFERENCES place_division (code),
    sort_order       INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_place_kind CHECK (kind IN ('COUNTRY','PROVINCE')),
    CONSTRAINT ck_place_country_code CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_place_not_own_successor CHECK (merged_into_code IS NULL OR merged_into_code <> code),
    CONSTRAINT ck_place_validity CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);

CREATE INDEX ix_place_kind        ON place_division (kind);
CREATE INDEX ix_place_unaccented  ON place_division USING gin (name_unaccented gin_trgm_ops);
CREATE INDEX ix_place_merged_into ON place_division (merged_into_code) WHERE merged_into_code IS NOT NULL;

CREATE TRIGGER tg_place_division_touch BEFORE UPDATE ON place_division
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE place_division IS
    'Danh muc ma tinh/quoc gia — nen cua bao cao dan so FR-4.3. Noi dung do quan tri nap tu QD 19/2025/QD-TTg, KHONG chep tay vao migration.';
COMMENT ON COLUMN place_division.merged_into_code IS
    'Don vi ke thua sau sap nhap. Bao cao di theo chuoi nay de gop don vi cu, khong sua du lieu nguoi dung da nhap.';

-- Chỉ gieo phần CHẮC CHẮN ĐÚNG và ổn định: mã quốc gia ISO 3166-1 alpha-2 cho các
-- nước có kiều bào đông. 'VN' bắt buộc phải có vì nó là country_code mặc định.
INSERT INTO place_division (code, kind, country_code, name, sort_order) VALUES
    ('VN', 'COUNTRY', 'VN', 'Việt Nam',      0),
    ('US', 'COUNTRY', 'US', 'Hoa Kỳ',       10),
    ('AU', 'COUNTRY', 'AU', 'Úc',           20),
    ('CA', 'COUNTRY', 'CA', 'Canada',       30),
    ('FR', 'COUNTRY', 'FR', 'Pháp',         40),
    ('DE', 'COUNTRY', 'DE', 'Đức',          50),
    ('CZ', 'COUNTRY', 'CZ', 'Séc',          60),
    ('PL', 'COUNTRY', 'PL', 'Ba Lan',       70),
    ('RU', 'COUNTRY', 'RU', 'Nga',          80),
    ('JP', 'COUNTRY', 'JP', 'Nhật Bản',     90),
    ('KR', 'COUNTRY', 'KR', 'Hàn Quốc',    100),
    ('TW', 'COUNTRY', 'TW', 'Đài Loan',    110),
    ('GB', 'COUNTRY', 'GB', 'Anh',         120);

-- =============================================================================
-- 9.2 person — thêm cột mã địa danh
-- =============================================================================
-- Cột chữ tự do GIỮ NGUYÊN, không bỏ: nó là thứ người trong họ thật sự viết ("làng
-- Đông Ngạc, huyện Từ Liêm") và mang thông tin mà một mã tỉnh không chở nổi. Cột mã
-- là thứ ĐỨNG CẠNH nó để gộp nhóm được, không phải thứ thay thế nó.
ALTER TABLE person
    ADD COLUMN native_place_code  VARCHAR(12) REFERENCES place_division (code),
    ADD COLUMN current_place_code VARCHAR(12) REFERENCES place_division (code);

CREATE INDEX ix_person_native_place_code ON person (native_place_code)
    WHERE native_place_code IS NOT NULL AND is_deleted = FALSE;
CREATE INDEX ix_person_current_place_code ON person (current_place_code)
    WHERE current_place_code IS NOT NULL AND is_deleted = FALSE;

COMMENT ON COLUMN person.native_place_code IS
    'Ma tinh/quoc gia cua nguyen quan — dau vao duy nhat gop nhom duoc cho bao cao dan so FR-4.3. native_place van giu nguyen van chu nguoi dung viet.';
COMMENT ON COLUMN person.current_place_code IS
    'Ma tinh/quoc gia noi o hien tai. RIENG TU: voi nguoi con song day la du lieu Tang 2 va CHI duoc lay tu ho tu khai — mau Excel co y KHONG co cot nay.';

-- =============================================================================
-- 9.3 import_batch — MỘT LẦN TẢI LÊN
-- =============================================================================
-- Máy trạng thái (kế hoạch §2):
--   DRAFT -> PARSED -> VALIDATING -> VALIDATED -> COMMITTING -> COMMITTED
--                          \-> FAILED  (còn lỗi chặn; sửa tệp rồi tải lại -> lô mới)
--   bất kỳ trạng thái nào -> SUPERSEDED khi cùng chi có lô mới hơn.
-- VALIDATED = "chờ duyệt": 0 lỗi chặn, người nhập được phép bấm ghi.
CREATE TABLE import_batch (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id          UUID         NOT NULL REFERENCES branch (id),
    uploaded_by        UUID         REFERENCES app_user (id),
    -- PHẢI CÓ TỪ NGÀY ĐẦU dù đợt này chỉ dùng EXCEL: đây là toàn bộ lý do bộ nhập
    -- GEDCOM ở đợt sau chỉ phải viết một bộ phân tích chứ không phải viết lại đường ống.
    source_kind        VARCHAR(12)  NOT NULL DEFAULT 'EXCEL',
    status             VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    original_filename  VARCHAR(255) NOT NULL,
    object_key         VARCHAR(512),
    file_sha256        CHAR(64)     NOT NULL,
    file_size_bytes    BIGINT       NOT NULL,
    person_row_count   INT          NOT NULL DEFAULT 0,
    marriage_row_count INT          NOT NULL DEFAULT 0,
    blocking_count     INT          NOT NULL DEFAULT 0,
    warning_count      INT          NOT NULL DEFAULT 0,
    create_count       INT          NOT NULL DEFAULT 0,
    update_count       INT          NOT NULL DEFAULT 0,
    warnings_acknowledged_at TIMESTAMPTZ,
    validated_at       TIMESTAMPTZ,
    committed_at       TIMESTAMPTZ,
    failure_reason     TEXT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_import_batch_source CHECK (source_kind IN ('EXCEL','GEDCOM')),
    CONSTRAINT ck_import_batch_status CHECK (status IN (
        'DRAFT','PARSED','VALIDATING','VALIDATED','FAILED','COMMITTING','COMMITTED','SUPERSEDED')),
    CONSTRAINT ck_import_batch_sha CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
    -- Chỉ được đánh COMMITTED khi thật sự đã ghi
    CONSTRAINT ck_import_batch_committed CHECK (status <> 'COMMITTED' OR committed_at IS NOT NULL)
);

CREATE INDEX ix_import_batch_branch ON import_batch (branch_id, created_at DESC);
CREATE INDEX ix_import_batch_status ON import_batch (status);
-- Chặn bấm-hai-lần: một tệp y hệt không được ghi vào phả hai lần cho cùng một chi.
-- CỐ Ý chỉ phủ COMMITTED — sửa một ô rồi tải lại là bước 4 của quy trình, phải cho phép.
CREATE UNIQUE INDEX ux_import_batch_committed_file
    ON import_batch (branch_id, file_sha256) WHERE status = 'COMMITTED';

CREATE TRIGGER tg_import_batch_touch BEFORE UPDATE ON import_batch
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE import_batch IS
    'Mot lan tai len. Moi trang thai TRUOC COMMITTING chi cham bang import_*; khong dong nao duoc vao person/relationship/AGE.';
COMMENT ON COLUMN import_batch.file_sha256 IS
    'Chi lam DUNG MOT VIEC: chan bam hai lan. Tinh dung dan KHONG den tu ma bam — sua mot o la doi ma bam.';
COMMENT ON COLUMN import_batch.warnings_acknowledged_at IS
    'Nguoi nhap da tick "toi da xem canh bao". Loi chan thi khong cho bam duyet; canh bao thi cho — sau khi tick.';

-- =============================================================================
-- 9.4 import_person_row — MỘT DÒNG TRANG "NHÂN KHẨU"
-- =============================================================================
CREATE TABLE import_person_row (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id           UUID         NOT NULL REFERENCES import_batch (id) ON DELETE CASCADE,
    row_no             INT          NOT NULL,
    external_code      VARCHAR(64)  NOT NULL,
    -- GIỮ CẢ HAI: raw là nguyên văn ô gốc, normalized là bản đã NFC + dọn khoảng trắng.
    -- Khi tranh cãi "tôi gõ đúng mà" thì raw là bằng chứng duy nhất.
    raw                JSONB        NOT NULL,
    normalized         JSONB        NOT NULL,
    -- Các trường đã tách ra cột thật: bộ kiểm phải LỌC, ĐẾM và NỐI theo mã cha.
    -- Làm việc đó trên JSONB là tự trói tay.
    full_name          VARCHAR(255),
    taboo_name         VARCHAR(255),
    posthumous_name    VARCHAR(255),
    han_nom_name       VARCHAR(255),
    gender             VARCHAR(10),
    generation         INT,
    father_code        VARCHAR(64),
    mother_code        VARCHAR(64),
    parent_rel         VARCHAR(16),
    is_alive           BOOLEAN,
    birth_year         INT,
    death_lunar_day    INT,
    death_lunar_month  INT,
    death_lunar_leap   BOOLEAN      NOT NULL DEFAULT FALSE,
    death_lunar_year   INT,
    native_place       VARCHAR(255),
    native_place_code  VARCHAR(12),
    heir_of_code       VARCHAR(64),
    heir_kind          VARCHAR(16),
    -- Kết quả bước đối soát, ghi lại mỗi lần kiểm
    resolved_person_id UUID         REFERENCES person (id),
    planned_action     VARCHAR(8)   NOT NULL DEFAULT 'CREATE',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_import_row_action CHECK (planned_action IN ('CREATE','UPDATE','SKIP')),
    CONSTRAINT ck_import_row_gender CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','UNKNOWN')),
    CONSTRAINT ck_import_row_heir   CHECK (heir_kind IS NULL OR heir_kind IN ('DICH_TON','THUA_TU','KE_TU')),
    CONSTRAINT ck_import_row_no     CHECK (row_no >= 1)
);

-- CỐ Ý KHÔNG ĐẶT UNIQUE (batch_id, external_code) — ĐỌC KỸ TRƯỚC KHI "SỮA"
--   Hai dòng cùng một Mã trong một tệp là lỗi chặn IMP_DUP_CODE. Nhưng khu vực chờ
--   phải GIỮ ĐƯỢC chính cái tệp sai ấy thì mới báo được cho người nhập biết nó sai ở
--   đâu. Đặt UNIQUE ở đây thì lệnh chèn chết giữa chừng với một SQLException, cả lô cuộn
--   lại, và Trưởng chi nhận được "tải lên thất bại" mà không biết dòng nào gây ra —
--   đúng cái trải nghiệm mà cả khu vực chờ sinh ra để tránh.
--   Tính duy nhất của Mã được canh ở HAI nơi đúng chỗ hơn: bộ kiểm (IMP_DUP_CODE, báo
--   cả hai dòng bằng một câu tiếng Việt), và ux_person_external_ref ở bước ghi.
CREATE INDEX        ix_import_person_row_code ON import_person_row (batch_id, external_code);
-- Số dòng thì duy nhất thật: một dòng Excel chỉ đọc ra đúng một lần.
CREATE UNIQUE INDEX ux_import_person_row_no   ON import_person_row (batch_id, row_no);
CREATE INDEX ix_import_person_row_father ON import_person_row (batch_id, father_code) WHERE father_code IS NOT NULL;
CREATE INDEX ix_import_person_row_mother ON import_person_row (batch_id, mother_code) WHERE mother_code IS NOT NULL;
CREATE INDEX ix_import_person_row_action ON import_person_row (batch_id, planned_action);

COMMENT ON COLUMN import_person_row.raw IS
    'Nguyen van o goc, truoc moi phep chuan hoa. Bang chung khi tranh cai "toi go dung ma".';
COMMENT ON COLUMN import_person_row.death_lunar_year IS
    'Nam am cua ngay gio — thuong RONG trong so cu ("mat thang 8, khong ro nam"). NULL la hop le.';

-- =============================================================================
-- 9.5 import_marriage_row — MỘT DÒNG TRANG "HÔN PHỐI"
-- =============================================================================
CREATE TABLE import_marriage_row (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id        UUID         NOT NULL REFERENCES import_batch (id) ON DELETE CASCADE,
    row_no          INT          NOT NULL,
    husband_code    VARCHAR(64),
    wife_code       VARCHAR(64),
    -- "Bậc" = vợ cả / vợ hai / vợ ba. Không có cột này thì ux_relationship_spouse_order
    -- không có dữ liệu và danh xưng con của các bà không tính đúng.
    spouse_order    INT,
    valid_from_year INT,
    valid_to_year   INT,
    end_reason      VARCHAR(12),
    raw             JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_import_marriage_end CHECK (
        end_reason IS NULL OR end_reason IN ('DIVORCE','DEATH','ANNULLED','OTHER')),
    CONSTRAINT ck_import_marriage_order CHECK (spouse_order IS NULL OR spouse_order >= 1),
    CONSTRAINT ck_import_marriage_row_no CHECK (row_no >= 1)
);

CREATE UNIQUE INDEX ux_import_marriage_row_no ON import_marriage_row (batch_id, row_no);
CREATE INDEX ix_import_marriage_husband ON import_marriage_row (batch_id, husband_code);
CREATE INDEX ix_import_marriage_wife    ON import_marriage_row (batch_id, wife_code);

-- =============================================================================
-- 9.6 import_issue — LỖI CHẶN VÀ CẢNH BÁO
-- =============================================================================
-- HAI NHÓM TÁCH BẠCH HOÀN TOÀN. Khác nhau ở đúng một điểm: BLOCKING thì không cho
-- bấm duyệt, WARNING thì cho — sau khi người nhập tick "tôi đã xem". Không có nhóm
-- thứ ba. Gộp chung hai nhóm thì "4 lỗi phải sửa" biến thành "15 vấn đề", và người
-- nhập chuyển từ "làm được" sang "hỏng cả tệp rồi" — rồi bấm bừa.
CREATE TABLE import_issue (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id   UUID         NOT NULL REFERENCES import_batch (id) ON DELETE CASCADE,
    severity   VARCHAR(8)   NOT NULL,
    code       VARCHAR(40)  NOT NULL,
    message    TEXT         NOT NULL,
    sheet      VARCHAR(24)  NOT NULL DEFAULT 'NHAN_KHAU',
    row_no     INT,
    field      VARCHAR(40),
    -- Ngữ cảnh máy đọc được: chuỗi mã của vòng lặp, danh sách mã gợi ý, điểm nghi trùng.
    context    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    resolution VARCHAR(16),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_import_issue_severity CHECK (severity IN ('BLOCKING','WARNING')),
    CONSTRAINT ck_import_issue_resolution CHECK (
        resolution IS NULL OR resolution IN ('ACKNOWLEDGED','FIXED','IGNORED'))
);

-- Bộ kiểm SINH LẠI TOÀN BỘ mỗi lần chạy, không tích luỹ qua các lần — nếu tích luỹ
-- thì người nhập sửa xong vẫn thấy y nguyên danh sách cũ và sẽ mất lòng tin vào bộ kiểm.
CREATE INDEX ix_import_issue_batch ON import_issue (batch_id, severity);
-- Bộ kiểm phải tất định: sắp theo (row_no, code) cho ra danh sách giống hệt nhau
-- giữa hai lần chạy trên cùng một tệp.
CREATE INDEX ix_import_issue_order ON import_issue (batch_id, row_no, code);

COMMENT ON TABLE import_issue IS
    'Hai nhom TACH BACH: BLOCKING chan bam duyet, WARNING thi khong. Sinh lai toan bo moi lan kiem.';

-- =============================================================================
-- 9.7 person_external_ref — KHOÁ BẤT BIẾN CỦA CẢ ĐƯỜNG ỐNG
-- =============================================================================
-- Đây là bảng QUAN TRỌNG NHẤT trong bảy bảng, và là bảng duy nhất KHÔNG thuộc về
-- một lô: nó sống lâu hơn mọi lô.
--
-- Cơ chế: cột `Mã` trong tệp Excel là khoá. Mỗi lần kiểm, tra external_code:
--   có    -> planned_action = UPDATE (không sinh người mới)
--   không -> planned_action = CREATE
-- Tải lại nguyên tệp cũ ra 400 dòng UPDATE, không đổi gì, không sinh một ai. Bước
-- đối soát lặp nhiều lần là HÀNH VI BÌNH THƯỜNG của quy trình, không phải ngoại lệ.
--
-- VÌ SAO KHOÁ PHẢI KÈM branch_id:
--   `Mã` chỉ duy nhất trong một chi. AT-02-001 và GI-02-001 là hai người khác nhau, và
--   không có gì bảo đảm hai chi không cùng đánh số kiểu "01, 02, 03". Bỏ branch_id
--   khỏi khoá thì bốn chi nhập song song sẽ đè lên nhau — và lỗi đó KHÔNG LỘ RA cho
--   tới khi chi thứ hai nộp bài.
--
-- KHÔNG SỬA DÒNG NÀY KHI NGƯỜI ĐÓ CHUYỂN CHI:
--   external_code là TOẠ ĐỘ TRONG TÀI LIỆU GỐC (số mấy của sổ chi nào), không phải
--   trạng thái hiện tại của người ấy.
CREATE TABLE person_external_ref (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code_system    VARCHAR(16)  NOT NULL DEFAULT 'EXCEL_MA',
    branch_id      UUID         NOT NULL REFERENCES branch (id),
    external_code  VARCHAR(64)  NOT NULL,
    person_id      UUID         NOT NULL REFERENCES person (id),
    -- Một cột, và nó trả lời câu hỏi "cho tôi mọi người sinh ra từ lô X" bằng một
    -- truy vấn có chỉ mục thay vì một buổi chiều đoán mò. Rẻ bây giờ, đắt gấp bội
    -- vào tháng thứ ba khi mới phát hiện lô đó nhập sai.
    first_batch_id UUID         REFERENCES import_batch (id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_person_external_ref_system CHECK (code_system IN ('EXCEL_MA','GEDCOM_XREF'))
);

CREATE UNIQUE INDEX ux_person_external_ref
    ON person_external_ref (code_system, branch_id, external_code);
CREATE INDEX ix_person_external_ref_person ON person_external_ref (person_id);
CREATE INDEX ix_person_external_ref_batch  ON person_external_ref (first_batch_id);

CREATE TRIGGER tg_person_external_ref_touch BEFORE UPDATE ON person_external_ref
    FOR EACH ROW EXECUTE FUNCTION giapha_touch_updated_at();

COMMENT ON TABLE person_external_ref IS
    'Khoa bat bien cua duong ong nhap lieu: (code_system, branch_id, external_code) -> person_id. KHONG thuoc ve mot lo, song lau hon moi lo.';
COMMENT ON COLUMN person_external_ref.external_code IS
    'Toa do trong tai lieu goc, KHONG phai trang thai hien tai. Nguoi chuyen chi thi dong nay giu nguyen chi cu.';
