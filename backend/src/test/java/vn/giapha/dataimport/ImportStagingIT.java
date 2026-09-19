package vn.giapha.dataimport;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.dataimport.application.StageImportBatchService;
import vn.giapha.dataimport.application.ValidateImportBatchService;
import vn.giapha.dataimport.domain.BatchStatus;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.TextNormalizer;
import vn.giapha.integration.AbstractIntegrationTest;

/**
 * Bài kiểm <b>trực tiếp</b> cho bất biến lớn nhất của nửa đầu đường ống: khu vực chờ không rò rỉ
 * sang gia phả.
 *
 * <p>Không kiểm bằng lời hứa mà bằng ba con số đếm trước và sau — {@code person},
 * {@code relationship}, và số đỉnh {@code Person} trong đồ thị AGE. Đúng cách mà
 * {@code GraphRelationalConsistencyIT} canh bất biến của nó.</p>
 */
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class ImportStagingIT extends AbstractIntegrationTest {

    @Autowired
    private StageImportBatchService stage;

    @Autowired
    private ValidateImportBatchService validate;

    @Test
    @DisplayName("Tệp đầy lỗi: KHÔNG một dòng nào lọt vào person / relationship / AGE")
    void tepDayLoiKhongLotVaoPha() {
        UUID chi = insertBranch("Chi Ất", "goc.chi_at", null, "CHI");

        int personTruoc = countRows("person");
        int relTruoc = countRows("relationship");
        int dinhTruoc = graphNodeCount();

        byte[] file = ImportWorkbooks.builder()
                // Ma cha khong ton tai
                .nhanKhau("AT-02-001", "Nguyễn Văn Đức", "", "Nam", "2", "AT-01-XXX", "", "",
                        "Không", "1915", "15/8/1945")
                // Vong lap: hai dong lam cha cua nhau
                .nhanKhau("AT-05-012", "Nguyễn Văn E", "", "Nam", "5", "AT-04-003", "", "",
                        "Không", "1950", "2/3/1990")
                .nhanKhau("AT-04-003", "Nguyễn Văn F", "", "Nam", "4", "AT-05-012", "", "",
                        "Không", "1920", "5/6/1980")
                // Ma trung trong tep
                .nhanKhau("AT-09-001", "Nguyễn Văn G", "", "Nam", "9", "", "", "", "Không",
                        "1970", "1/1/2000")
                .nhanKhau("AT-09-001", "Nguyễn Văn H", "", "Nam", "9", "", "", "", "Không",
                        "1971", "2/2/2001")
                // Con sinh truoc cha
                .nhanKhau("AT-07-001", "Nguyễn Văn Cha", "", "Nam", "7", "", "", "", "Không",
                        "1960", "3/3/2010")
                .nhanKhau("AT-08-001", "Nguyễn Văn Con", "", "Nam", "8", "AT-07-001", "", "",
                        "Không", "1940", "4/4/2011")
                // Con song = Co nhung co ngay gio
                .nhanKhau("AT-10-001", "Nguyễn Văn Sống", "", "Nam", "10", "", "", "", "Có",
                        "1990", "6/6")
                .build();

        ImportBatch batch = stage.stage(chi, null, "chi-at-day-loi.xlsx", file, false);
        assertThat(batch.status()).isEqualTo(BatchStatus.PARSED);

        ValidateImportBatchService.KetQua ketQua = validate.validate(batch.id());

        // --- BAT BIEN: ba con so KHONG DOI ---
        assertThat(countRows("person")).isEqualTo(personTruoc);
        assertThat(countRows("relationship")).isEqualTo(relTruoc);
        assertThat(graphNodeCount()).isEqualTo(dinhTruoc);

        // --- Lo dung lai o FAILED, khong duoc bam duyet ---
        assertThat(ketQua.status()).isEqualTo(BatchStatus.FAILED);
        assertThat(ketQua.sach()).isFalse();

        List<String> maLoi = jdbc.queryForList(
                "SELECT DISTINCT code FROM import_issue WHERE batch_id = ? AND severity = 'BLOCKING'",
                String.class, batch.id());
        assertThat(maLoi).contains(
                IssueCode.IMP_PARENT_NOT_FOUND.name(),
                IssueCode.IMP_CYCLE.name(),
                IssueCode.IMP_DUP_CODE.name(),
                IssueCode.IMP_CHILD_BEFORE_PARENT.name(),
                IssueCode.IMP_ALIVE_WITH_DEATH.name());

        // --- Nhung du lieu VAN o khu vuc cho, de nguoi nhap doi soat ---
        assertThat(countRows("import_person_row")).isEqualTo(8);
    }

    @Test
    @DisplayName("Tên tiếng Việt có dấu đi trọn đường từ tệp tới khu vực chờ, nguyên vẹn và NFC")
    void tenCoDauDiTronDuong() {
        UUID chi = insertBranch("Chi Ất", "goc.chi_at", null, "CHI");

        // Co y tron NFC va NFD trong cung mot tep — dung nhu khi hai nguoi dung hai may khac nhau
        // cung go vao mot file.
        String nfc = "Nguyễn Thị Lựu";
        String nfd = Normalizer.normalize("Lê Văn Đễ", Normalizer.Form.NFD);

        byte[] file = ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", nfc, "Húy Lựu", "Nữ", "1", "", "", "", "Không", "1890", "15/8")
                .nhanKhau("AT-01-002", nfd, "Húy Đễ", "Nam", "1", "", "", "", "Không", "1888", "3/9")
                .build();

        ImportBatch batch = stage.stage(chi, null, "co-dau.xlsx", file, false);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT external_code, full_name, taboo_name, raw->>'Họ tên' AS raw_ten"
                        + " FROM import_person_row WHERE batch_id = ? ORDER BY row_no", batch.id());

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("full_name")).isEqualTo("Nguyễn Thị Lựu");
        assertThat(rows.get(0).get("taboo_name")).isEqualTo("Húy Lựu");
        // NFD trong tep phai thanh NFC trong khu vuc cho, neu khong thi ma cha se tro truot va
        // vn_unaccent sinh ra cot khong dau rac.
        assertThat(rows.get(1).get("full_name")).isEqualTo("Lê Văn Đễ");
        assertThat(TextNormalizer.laNfc((String) rows.get(1).get("full_name"))).isTrue();

        // raw giu nguyen van o goc — bang chung khi tranh cai "toi go dung ma".
        assertThat(rows.get(0).get("raw_ten")).isEqualTo(nfc);
    }

    @Test
    @DisplayName("Sửa file rồi tải lại KHÔNG sinh người trùng: mã cũ ra UPDATE, mã mới ra CREATE")
    void suaFileTaiLaiKhongSinhTrung() {
        UUID chi = insertBranch("Chi Ất", "goc.chi_at", null, "CHI");
        UUID cuTo = seedPerson(chi, "Nguyễn Văn Cẩn", 1);
        seedExternalRef(chi, "AT-01-001", cuTo, null);

        int personTruoc = countRows("person");

        // Lan 1
        byte[] lan1 = ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", "Nguyễn Văn Cẩn", "", "Nam", "1", "", "", "", "Không",
                        "1890", "15/8/1950")
                .nhanKhau("AT-02-001", "Nguyễn Thị Lựu", "", "Nữ", "2", "AT-01-001", "", "",
                        "Không", "1915", "3/9/1990")
                .build();
        ImportBatch b1 = stage.stage(chi, null, "lan1.xlsx", lan1, false);
        validate.validate(b1.id());
        assertThat(hanhDong(b1.id(), "AT-01-001")).isEqualTo("UPDATE");
        assertThat(hanhDong(b1.id(), "AT-02-001")).isEqualTo("CREATE");

        // Lan 2: sua mot o ho ten, giu nguyen ma — dung buoc doi soat cua quy trinh.
        byte[] lan2 = ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", "Nguyễn Văn Cẩn (đã sửa)", "", "Nam", "1", "", "", "",
                        "Không", "1890", "15/8/1950")
                .nhanKhau("AT-02-001", "Nguyễn Thị Lựu", "", "Nữ", "2", "AT-01-001", "", "",
                        "Không", "1915", "3/9/1990")
                .build();
        ImportBatch b2 = stage.stage(chi, null, "lan2.xlsx", lan2, false);
        validate.validate(b2.id());

        // Ma cu VAN tro ve dung nguoi cu — day la toan bo co che chong sinh trung.
        assertThat(hanhDong(b2.id(), "AT-01-001")).isEqualTo("UPDATE");
        assertThat(jdbc.queryForObject(
                "SELECT resolved_person_id FROM import_person_row"
                        + " WHERE batch_id = ? AND external_code = 'AT-01-001'",
                UUID.class, b2.id())).isEqualTo(cuTo);

        // Va khong ai duoc them vao pha o ca hai lan.
        assertThat(countRows("person")).isEqualTo(personTruoc);

        // Lo cu chuyen SUPERSEDED, khong bi xoa — dau vet doi soat phai giu lai.
        assertThat(jdbc.queryForObject("SELECT status FROM import_batch WHERE id = ?",
                String.class, b1.id())).isEqualTo("SUPERSEDED");
    }

    @Test
    @DisplayName("Tải lại y nguyên tệp cũ: 100% UPDATE, không một dòng CREATE nào")
    void taiLaiYNguyenTepCu() {
        UUID chi = insertBranch("Chi Ất", "goc.chi_at", null, "CHI");
        UUID p1 = seedPerson(chi, "Nguyễn Văn Cẩn", 1);
        UUID p2 = seedPerson(chi, "Nguyễn Thị Lựu", 2);
        seedExternalRef(chi, "AT-01-001", p1, null);
        seedExternalRef(chi, "AT-02-001", p2, null);

        byte[] file = ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", "Nguyễn Văn Cẩn", "", "Nam", "1", "", "", "", "Không",
                        "1890", "15/8/1950")
                .nhanKhau("AT-02-001", "Nguyễn Thị Lựu", "", "Nữ", "2", "AT-01-001", "", "",
                        "Không", "1915", "3/9/1990")
                .build();

        ImportBatch batch = stage.stage(chi, null, "y-nguyen.xlsx", file, false);
        ValidateImportBatchService.KetQua ketQua = validate.validate(batch.id());

        assertThat(ketQua.seTao()).isZero();
        assertThat(ketQua.seCapNhat()).isEqualTo(2);
        assertThat(countRows("person")).isEqualTo(2);
    }

    @Test
    @DisplayName("Bộ kiểm tất định: chạy hai lần trên cùng một lô cho danh sách lỗi giống hệt nhau")
    void boKiemTatDinh() {
        UUID chi = insertBranch("Chi Ất", "goc.chi_at", null, "CHI");
        byte[] file = ImportWorkbooks.builder()
                .nhanKhau("AT-02-001", "Nguyễn Văn A", "", "Nam", "2", "AT-01-XXX", "", "",
                        "Không", "1915", "15/8/1945")
                .nhanKhau("AT-03-001", "Nguyễn Văn B", "", "Nam", "3", "AT-02-YYY", "", "",
                        "Không", "1940", "")
                .build();

        ImportBatch batch = stage.stage(chi, null, "hai-lan.xlsx", file, false);
        validate.validate(batch.id());
        List<String> lan1 = danhSachLoi(batch.id());
        validate.validate(batch.id());
        List<String> lan2 = danhSachLoi(batch.id());

        assertThat(lan2).isEqualTo(lan1);
    }

    @Test
    @DisplayName("Mã đã đăng ký cho chi khác bị chặn — đây là ranh giới phân quyền, không chỉ dữ liệu")
    void maCuaChiKhacBiChan() {
        UUID chiAt = insertBranch("Chi Ất", "goc.chi_at", null, "CHI");
        UUID chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", null, "CHI");
        UUID nguoiChiGiap = seedPerson(chiGiap, "Nguyễn Văn Giáp", 3);
        seedExternalRef(chiGiap, "GI-03-001", nguoiChiGiap, null);

        byte[] file = ImportWorkbooks.builder()
                .nhanKhau("GI-03-001", "Nguyễn Văn Trộm", "", "Nam", "3", "", "", "", "Không",
                        "1930", "1/1/1990")
                .build();

        ImportBatch batch = stage.stage(chiAt, null, "lan-chi.xlsx", file, false);
        validate.validate(batch.id());

        List<String> maLoi = jdbc.queryForList(
                "SELECT code FROM import_issue WHERE batch_id = ? AND severity = 'BLOCKING'",
                String.class, batch.id());
        assertThat(maLoi).contains(IssueCode.IMP_BAD_CODE_FORMAT.name());
        assertThat(countRows("person")).isEqualTo(1);
    }

    @Test
    @DisplayName("Cột mã tỉnh có mặt từ lần nhập đầu tiên và đi được vào khu vực chờ")
    void maTinhDiVaoKhuVucCho() {
        UUID chi = insertBranch("Chi Ất", "goc.chi_at", null, "CHI");
        byte[] file = ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", "Nguyễn Văn Cẩn", "", "Nam", "1", "", "", "", "Không",
                        "1890", "15/8", "Làng Đông Ngạc", "VN")
                .build();

        ImportBatch batch = stage.stage(chi, null, "que.xlsx", file, false);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT native_place, native_place_code FROM import_person_row WHERE batch_id = ?",
                batch.id());
        assertThat(row.get("native_place")).isEqualTo("Làng Đông Ngạc");
        assertThat(row.get("native_place_code")).isEqualTo("VN");

        // Va cot tren bang person da ton tai — day moi la thu dat neu quyet muon.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_name = 'person' AND column_name IN ('native_place_code','current_place_code')
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("Hai cảnh báo mượn của genealogy thực sự sinh ra kết quả: nghi trùng và kỵ húy")
    void nghiTrungVaKyHuyThucSuChay() {
        UUID chi = insertBranch("Chi Ất", "goc.chi_at", null, "CHI");

        // Cụ đã có trong phả: đời 3, giỗ 15/8 âm năm 1950, tên huý là "Cẩn".
        UUID cuCan = seedPerson(chi, "Nguyễn Văn Cẩn", 3,
                "{\"year\":1950,\"month\":8,\"day\":15,\"leap\":false}", "Cẩn");

        byte[] file = ImportWorkbooks.builder()
                // Cùng tên, cùng giỗ, cùng chi, cùng đời — thừa sức vượt ngưỡng nghi trùng.
                .nhanKhau("AT-03-001", "Nguyễn Văn Cẩn", "", "Nam", "3", "", "", "", "Không",
                        "", "15/8/1950")
                // Tên huý trùng huý của bậc trên đời 3, trong khi dòng này ở đời 7.
                .nhanKhau("AT-07-001", "Nguyễn Văn Khác", "Cẩn", "Nam", "7", "", "", "", "Không",
                        "1930", "1/1/2000")
                .build();

        ImportBatch batch = stage.stage(chi, null, "nghi-trung.xlsx", file, false);
        validate.validate(batch.id());

        List<Map<String, Object>> canhBao = jdbc.queryForList("""
                SELECT code, row_no, message, context::text AS context
                  FROM import_issue
                 WHERE batch_id = ? AND severity = 'WARNING'
                 ORDER BY row_no, code
                """, batch.id());

        List<String> ma = canhBao.stream().map(r -> (String) r.get("code")).toList();
        assertThat(ma).contains(IssueCode.IMP_SUSPECT_DUPLICATE.name(),
                IssueCode.IMP_TABOO_COLLISION.name());

        // Nghi trùng phải trỏ đúng người đã có bằng KHOÁ, kèm điểm — đủ để người đọc tự đối chiếu.
        //
        // Ở ca này hai cái tên trùng nhau nên không thể kiểm "có lộ tên người trong phả không" —
        // tên vẫn hiện, nhưng nó đến từ dòng của chính người nhập. Phép kiểm rò rỉ thật, với tên
        // trong tệp và tên trong phả CỐ Ý KHÁC NHAU, nằm ở ImportIssuePrivacyIT.
        Map<String, Object> nghiTrung = canhBao.stream()
                .filter(r -> IssueCode.IMP_SUSPECT_DUPLICATE.name().equals(r.get("code")))
                .findFirst().orElseThrow();
        assertThat((String) nghiTrung.get("context")).contains(cuCan.toString());
        assertThat((String) nghiTrung.get("message"))
                .contains("AT-03-001", "điểm", "hồ sơ đã có trong phả");

        Map<String, Object> kyHuy = canhBao.stream()
                .filter(r -> IssueCode.IMP_TABOO_COLLISION.name().equals(r.get("code")))
                .findFirst().orElseThrow();
        assertThat(kyHuy.get("row_no")).isEqualTo(3);
        // Tên huý người nhập vừa gõ thì nêu; tên và đời của bậc trên thì không. Bậc trên được
        // chọn theo ĐỜI THỨ chứ không theo is_alive, nên "cụ" ấy hoàn toàn có thể còn sống.
        assertThat((String) kyHuy.get("message"))
                .contains("Tên huý Cẩn", "AT-07-001")
                .doesNotContain("Nguyễn Văn Cẩn")
                .doesNotContain("đời 3");

        // Và cả hai đều là CẢNH BÁO: không được chặn người nhập bấm duyệt.
        // Anh em ruột trùng tên, và tên huý chép đúng theo sổ cũ, đều là chuyện thường.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM import_issue WHERE batch_id = ? AND severity = 'BLOCKING'",
                Integer.class, batch.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM import_batch WHERE id = ?",
                String.class, batch.id())).isEqualTo(BatchStatus.VALIDATED.name());

        // Và vẫn không ai được thêm vào phả.
        assertThat(countRows("person")).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------
    // Tiện ích gieo dữ liệu
    // ---------------------------------------------------------------------------------------

    private UUID seedPerson(UUID branchId, String fullName, int generation) {
        return seedPerson(branchId, fullName, generation, null, null);
    }

    /**
     * Gieo một nhân khẩu đã khuất.
     *
     * @param gioJson ngày giỗ âm dạng {@code {"year":..,"month":..,"day":..,"leap":false}} — nguồn chân
     *        lý của giỗ, và là tín hiệu nặng điểm nhất của bộ dò trùng
     * @param tenHuy lớp tên HUY — lớp tên <b>duy nhất</b> kích hoạt cảnh báo kỵ húy
     */
    private UUID seedPerson(UUID branchId, String fullName, int generation, String gioJson,
                            String tenHuy) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO person (id, gender, generation, is_alive, primary_branch_id, death_lunar)
                VALUES (?, 'MALE', ?, FALSE, ?, CAST(? AS jsonb))
                """, id, generation, branchId, gioJson);
        jdbc.update("""
                INSERT INTO person_name (person_id, name_type, full_name, is_primary)
                VALUES (?, 'THUONG_GOI', ?, TRUE)
                """, id, fullName);
        if (tenHuy != null) {
            jdbc.update("""
                    INSERT INTO person_name (person_id, name_type, full_name, is_primary)
                    VALUES (?, 'HUY', ?, FALSE)
                    """, id, tenHuy);
        }
        return id;
    }

    private void seedExternalRef(UUID branchId, String code, UUID personId, UUID batchId) {
        jdbc.update("""
                INSERT INTO person_external_ref (code_system, branch_id, external_code, person_id,
                        first_batch_id)
                VALUES ('EXCEL_MA', ?, ?, ?, ?)
                """, branchId, code, personId, batchId);
    }

    private String hanhDong(UUID batchId, String code) {
        return jdbc.queryForObject(
                "SELECT planned_action FROM import_person_row"
                        + " WHERE batch_id = ? AND external_code = ?",
                String.class, batchId, code);
    }

    private List<String> danhSachLoi(UUID batchId) {
        return jdbc.queryForList("""
                SELECT sheet || '|' || coalesce(row_no::text, '-') || '|' || code || '|' || message
                  FROM import_issue WHERE batch_id = ?
                 ORDER BY sheet, row_no NULLS LAST, code, message
                """, String.class, batchId);
    }
}
