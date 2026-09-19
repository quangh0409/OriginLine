package vn.giapha.dataimport.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.giapha.dataimport.ImportWorkbooks;
import vn.giapha.integration.AbstractIntegrationTest;

/**
 * {@code GET /api/v1/import/batches/&#123;id&#125;/issues.xlsx} chạy qua <b>toàn bộ chuỗi HTTP
 * thật</b> trên PostgreSQL + Apache AGE thật.
 *
 * <h2>Bốn câu hỏi bộ này trả lời</h2>
 * <ol>
 *   <li><b>Tệp có đủ cả hai nhóm, và có tách bạch không?</b> Một lô thật luôn có cả lỗi chặn lẫn
 *       cảnh báo. Nếu bản xuất chỉ chở một nhóm thì người nhập sửa xong vòng một rồi sững lại ở
 *       vòng hai mà không hiểu vì sao.</li>
 *   <li><b>Có mẩu dữ liệu nào của người trong phả rời khỏi tiến trình không?</b> Ca chính gieo vào
 *       phả một tên có dấu, một năm sinh và một tên huý <b>không thể tình cờ xuất hiện</b>, rồi quét
 *       <b>mọi ô của mọi trang</b> của tệp sinh ra. Không soi một trường cụ thể — rò rỉ thật xuất
 *       hiện ở chỗ không ai nghĩ tới.</li>
 *   <li><b>Đối chứng: {@code personId} có mặt chứ?</b> Thiếu khẳng định này, câu trên có thể xanh
 *       vì bộ dò trùng không kêu chứ không phải vì bản xuất đã sạch.</li>
 *   <li><b>Phạm vi chi có phải hạng nhất không?</b> Trưởng chi Giáp tải bản xuất của lô chi Ất phải
 *       là {@code 403 BRANCH_SCOPE_VIOLATION}. Tệp này rời khỏi hệ thống và không có đường thu hồi,
 *       nên đây là bề mặt cuối cùng đáng nới lỏng phân quyền.</li>
 * </ol>
 */
@DisplayName("Xuất danh sách lỗi ra Excel — tách bạch hai nhóm, phạm vi chi, và rò rỉ riêng tư")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class ImportIssueExportIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "/api/v1/import/batches";
    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final String TRANG_TONG_QUAN = "Tổng quan";
    private static final String TRANG_LOI_CHAN = "Lỗi phải sửa";
    private static final String TRANG_CANH_BAO = "Nên xem lại";

    /** Ba mảnh dữ liệu chỉ tồn tại ở phía phả, không có trong tệp người nhập nộp. */
    private static final String TEN_TRONG_PHA = "Nguyễn Văn Tuân";
    private static final String TEN_HUY_TRONG_PHA = "Nguyễn Đình Lựu";
    private static final int NAM_SINH_TRONG_PHA = 1937;
    private static final String NGUYEN_QUAN_TRONG_PHA = "Kinh Bắc Thượng";

    @Autowired
    private MockMvc mockMvc;

    private UUID chiAt;
    private UUID chiGiap;

    @BeforeEach
    void setUpDongHo() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");

        assignBranchRole(insertAppUser("sub-truong-chi-at", null), "BRANCH_HEAD", chiAt);
        assignBranchRole(insertAppUser("sub-truong-chi-giap", null), "BRANCH_HEAD", chiGiap);
    }

    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Lô có cả lỗi chặn lẫn cảnh báo: tệp có đủ cả hai, ở hai trang tách bạch")
    void caLoiChanLanCanhBaoDeuCoMat_oHaiTrangRieng() throws Exception {
        UUID batchId = taiLen(chiAt, tepCoCaHaiNhom());

        XSSFWorkbook wb = tai(batchId);

        List<String> ten = new ArrayList<>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            ten.add(wb.getSheetName(i));
        }
        assertThat(ten).containsExactly(TRANG_TONG_QUAN, TRANG_LOI_CHAN, TRANG_CANH_BAO);

        List<String> maChan = maLoi(wb, TRANG_LOI_CHAN);
        List<String> maCanhBao = maLoi(wb, TRANG_CANH_BAO);

        assertThat(maChan)
                .as("mot lo that luon co loi chan; thieu la ban xuat khong dung duoc")
                .isNotEmpty()
                .contains("IMP_PARENT_NOT_FOUND")
                .allMatch(m -> m.startsWith("IMP_"));
        assertThat(maCanhBao)
                .as("va luon co canh bao — thuy to lan nao cung sinh IMP_LONE_NODE")
                .isNotEmpty();

        // Tach bach: khong mot ma nao nam ca hai trang.
        assertThat(maChan).doesNotContainAnyElementsOf(maCanhBao);

        // Va hai con so o trang Tong quan phai khop so dong that cua tung trang.
        String tatCa = moiO(wb);
        assertThat(tatCa).contains(maChan.size() + " lỗi phải sửa");
        assertThat(tatCa).contains(maCanhBao.size() + " điều nên xem lại");
    }

    @Test
    @DisplayName("Toạ độ đủ để tìm lại dòng trong tệp gốc: trang, dòng, mã, cột")
    void moiDongDeuCoDuToaDo() throws Exception {
        UUID batchId = taiLen(chiAt, tepCoCaHaiNhom());
        XSSFWorkbook wb = tai(batchId);

        Sheet sheet = wb.getSheet(TRANG_LOI_CHAN);
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            List<String> o = dong(sheet, r);
            assertThat(o.get(0))
                    .as("dong %d phai chi ro trang nao trong tep gia pha", r)
                    .isIn("Nhân khẩu", "Hôn phối", "Cả lô");
            if ("Cả lô".equals(o.get(0))) {
                continue;
            }
            assertThat(o.get(1)).as("so dong").isNotEmpty();
            assertThat(o.get(2)).as("ma cua dong %d", r).isNotEmpty();
            assertThat(o.get(4)).as("cau tieng Viet cua dong %d", r).isNotEmpty();
        }
    }

    @Test
    @DisplayName("Gợi ý mã gần giống đi ra tệp — thứ duy nhất sửa được lỗi mà không phải mở sổ giấy")
    void goiYMaGanGiongDiRaTep() throws Exception {
        // AT-01-OO3 (hai chu O) thay vi AT-01-003 — loi pho bien nhat cua ca duong ong.
        byte[] tep = ImportWorkbooks.builder()
                .nhanKhau("AT-01-003", "Nguyễn Văn Gốc", "", "Nam", "1", "", "", "", "Không",
                        "1880", "3/2/1940")
                .nhanKhau("AT-02-001", "Nguyễn Văn Đức", "", "Nam", "2", "AT-01-OO3", "", "",
                        "Không", "1915", "15/8/1945")
                .build();

        XSSFWorkbook wb = tai(taiLen(chiAt, tep));
        Sheet sheet = wb.getSheet(TRANG_LOI_CHAN);

        List<String> dongParent = null;
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            if ("IMP_PARENT_NOT_FOUND".equals(dong(sheet, r).get(7))) {
                dongParent = dong(sheet, r);
            }
        }
        assertThat(dongParent).as("phai co IMP_PARENT_NOT_FOUND").isNotNull();
        assertThat(dongParent.get(5)).as("cot Goi y ma gan giong").contains("AT-01-003");
    }

    // -------------------------------------------------------------------------------------
    // Rieng tu
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Không một trường nhân khẩu nào của người trong phả lọt vào tệp; personId thì có")
    void khongMotTruongNaoCuaPhaLotVaoTep() throws Exception {
        UUID conSong = gieoNguoiConSongCoTenHuy();

        byte[] tep = ImportWorkbooks.builder()
                // Nghi trung voi nguoi CON SONG o tren: trung ho ten sau khi bo dau + trung nam
                // sinh. Nam sinh 1937 co mat o CA HAI ben, nen no khong dung de do ro ri — ba
                // hang duoi moi la nhung manh CHI ton tai o phia pha.
                .nhanKhau("AT-04-001", "Nguyen Van Tuan", "", "Nam", "4", "", "", "", "Có",
                        String.valueOf(NAM_SINH_TRONG_PHA), "")
                // Trung ten huy cua mot bac tren trong pha — canh bao ky huy.
                .nhanKhau("AT-07-001", "Nguyễn Văn Cẩn", "Lựu", "Nam", "7", "", "", "", "Không",
                        "1955", "9/2/2010")
                .build();

        XSSFWorkbook wb = tai(taiLen(chiAt, tep));
        String tatCa = moiO(wb);

        // --- Khong mot manh du lieu nhan khau nao doc tu pha ---
        assertThat(tatCa)
                .as("ten that co dau cua nguoi con song trong pha")
                .doesNotContain(TEN_TRONG_PHA)
                .as("ten huy day du luu trong pha — bay: phep do khop ca o muc ten chinh")
                .doesNotContain(TEN_HUY_TRONG_PHA)
                .as("nguyen quan chi co o phia pha")
                .doesNotContain(NGUYEN_QUAN_TRONG_PHA);

        // --- ...nhung KHOA thi co, vi khong co no thi khong ai mo duoc ho so de doi chieu ---
        assertThat(tatCa)
                .as("personId cua ho so nghi trung phai co mat, neu khong bai kiem tren xanh vi"
                        + " ly do sai")
                .contains(conSong.toString());

        // --- ...va du lieu cua CHINH nguoi nhap thi giu nguyen ---
        assertThat(tatCa).contains("AT-04-001").contains("Nguyen Van Tuan");
    }

    @Test
    @DisplayName("Trưởng chi Giáp không tải được bản xuất của lô chi Ất")
    void ngoaiPhamViChiThiBiChan() throws Exception {
        UUID batchId = taiLen(chiAt, tepCoCaHaiNhom());

        MvcResult ketQua = mockMvc.perform(get(BASE + "/" + batchId + "/issues.xlsx")
                .with(truongChi("sub-truong-chi-giap"))).andReturn();

        assertThat(ketQua.getResponse().getStatus()).isEqualTo(403);
        JsonNode than = JSON.readTree(
                ketQua.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(than.get("code").asText()).isEqualTo("BRANCH_SCOPE_VIOLATION");
    }

    @Test
    @DisplayName("Mỗi lần tải để lại một dòng EXPORT trong audit_log — tệp này không có đường thu hồi")
    void moiLanTaiDeLaiMotDongAudit() throws Exception {
        UUID batchId = taiLen(chiAt, tepCoCaHaiNhom());

        tai(batchId);

        List<java.util.Map<String, Object>> dong = jdbc.queryForList(
                "SELECT entity_type, entity_id, action, note FROM audit_log"
                        + " WHERE entity_type = 'ImportBatch' AND action = 'EXPORT'");
        assertThat(dong).hasSize(1);
        assertThat(dong.get(0).get("entity_id")).isEqualTo(batchId.toString());
        // Ghi tach bach ca trong audit: mot dong "15 van de" thi sau nay khong ai tra lai duoc lo
        // do co chan duyet hay khong.
        assertThat(String.valueOf(dong.get(0).get("note")))
                .contains("loi chan").contains("canh bao");
    }

    @Test
    @DisplayName("Tên tệp có dấu đi qua HTTP nguyên vẹn ở dạng RFC 5987")
    void tenTepCoDauSongSotQuaHttp() throws Exception {
        UUID batchId = taiLen(chiAt, tepCoCaHaiNhom());

        MvcResult ketQua = mockMvc.perform(get(BASE + "/" + batchId + "/issues.xlsx")
                .with(truongChi("sub-truong-chi-at"))).andReturn();

        assertThat(ketQua.getResponse().getStatus()).isEqualTo(200);
        assertThat(ketQua.getResponse().getContentType()).isEqualTo(XLSX);

        String cd = ketQua.getResponse().getHeader("Content-Disposition");
        assertThat(cd).isNotNull();
        // Ban du phong thuan ASCII cho trinh duyet cu...
        assertThat(cd).contains("filename=\"Danh sach can sua - Chi At - ");
        // ...va ban that, UTF-8 phan tram-hoa. "Ấ" = E1 BA A4.
        assertThat(cd).contains("filename*=UTF-8''").contains("%E1%BA%A4");
    }

    // -------------------------------------------------------------------------------------
    // Tien ich
    // -------------------------------------------------------------------------------------

    /** Tệp mang chắc chắn cả lỗi chặn (mã cha sai, trùng mã) lẫn cảnh báo (thiếu giỗ, đơn côi). */
    private static byte[] tepCoCaHaiNhom() {
        return ImportWorkbooks.builder()
                .nhanKhau("AT-02-001", "Nguyễn Văn Đức", "", "Nam", "2", "AT-01-OO3", "", "",
                        "Không", "1915", "15/8/1945")
                .nhanKhau("AT-01-003", "Nguyễn Văn Gốc", "", "Nam", "1", "", "", "", "Không",
                        "1880", "3/2/1940")
                // Da mat ma trong Ngay mat am -> IMP_MISSING_GIO, canh bao dang gia nhat cua bang.
                .nhanKhau("AT-03-001", "Nguyễn Văn Bảo", "", "Nam", "3", "AT-02-001", "", "",
                        "Không", "1940", "")
                .build();
    }

    private UUID taiLen(UUID branchId, byte[] noiDung) throws Exception {
        MvcResult ketQua = mockMvc.perform(multipart(BASE)
                        .file(new MockMultipartFile("file", "gia-pha.xlsx", XLSX, noiDung))
                        .param("branchId", branchId.toString())
                        .with(truongChi("sub-truong-chi-at")))
                .andReturn();
        assertThat(ketQua.getResponse().getStatus())
                .as("than phan hoi: %s",
                        ketQua.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(201);
        JsonNode than = JSON.readTree(
                ketQua.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return UUID.fromString(than.get("id").asText());
    }

    private XSSFWorkbook tai(UUID batchId) throws Exception {
        MvcResult ketQua = mockMvc.perform(get(BASE + "/" + batchId + "/issues.xlsx")
                .with(truongChi("sub-truong-chi-at"))).andReturn();
        assertThat(ketQua.getResponse().getStatus())
                .as("than phan hoi: %s",
                        ketQua.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(200);
        return new XSSFWorkbook(
                new ByteArrayInputStream(ketQua.getResponse().getContentAsByteArray()));
    }

    private static RequestPostProcessor truongChi(String sub) {
        return jwt().jwt(builder -> builder.subject(sub).claim("preferred_username", sub))
                .authorities(new SimpleGrantedAuthority("ROLE_BRANCH_HEAD"));
    }

    /** Tên mọi trang cộng nội dung mọi ô của mọi trang — xem javadoc lớp, ca số 2. */
    private static String moiO(XSSFWorkbook wb) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            sb.append(wb.getSheetName(i)).append('\n');
            for (Row row : wb.getSheetAt(i)) {
                for (Cell cell : row) {
                    sb.append(doc(cell)).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static List<String> dong(Sheet sheet, int chiSoDong) {
        Row row = sheet.getRow(chiSoDong);
        List<String> o = new ArrayList<>(8);
        for (int c = 0; c < 8; c++) {
            o.add(row == null ? "" : doc(row.getCell(c)));
        }
        return o;
    }

    private static List<String> maLoi(XSSFWorkbook wb, String trang) {
        Sheet sheet = wb.getSheet(trang);
        List<String> ma = new ArrayList<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            ma.add(dong(sheet, r).get(7));
        }
        return ma;
    }

    private static String doc(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    // -------------------------------------------------------------------------------------
    // Gieo pha
    // -------------------------------------------------------------------------------------

    /**
     * Một người <b>còn sống</b> trong phả, có tên huý — ca nguy hiểm nhất.
     *
     * <p>Cố ý dựng bằng SQL thay vì qua use case: ca test cần một hồ sơ <i>đã nằm sẵn trong phả</i>
     * làm mồi cho bộ dò trùng và bộ dò kỵ húy, chứ không kiểm luồng thêm người.</p>
     */
    private UUID gieoNguoiConSongCoTenHuy() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO person (id, gender, generation, birth_solar, is_alive,"
                        + " native_place, primary_branch_id, lineage_status, attributes)"
                        + " VALUES (?, 'MALE', 4, ?, TRUE, ?, ?, 'NORMAL', '{}'::jsonb)",
                id, Date.valueOf(LocalDate.of(NAM_SINH_TRONG_PHA, 3, 20)), NGUYEN_QUAN_TRONG_PHA,
                chiAt);
        themTen(id, "THUONG_GOI", TEN_TRONG_PHA, true);
        themTen(id, "HUY", TEN_HUY_TRONG_PHA, false);
        graph.createPersonNode(id, "MALE", 4);
        return id;
    }

    private void themTen(UUID personId, String loai, String hoTen, boolean chinh) {
        jdbc.update("INSERT INTO person_name (id, person_id, name_type, full_name, is_primary)"
                + " VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), personId, loai, hoTen, chinh);
    }
}
