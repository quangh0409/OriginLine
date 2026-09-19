package vn.giapha.dataimport;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.dataimport.application.StageImportBatchService;
import vn.giapha.dataimport.application.ValidateImportBatchService;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.integration.AbstractIntegrationTest;

/**
 * Lỗ rò riêng tư của bộ kiểm nhập liệu, đo <b>tại chỗ dữ liệu thật sự nằm</b>: bảng
 * {@code import_issue} trên PostgreSQL thật.
 *
 * <h2>Vì sao bài kiểm này phải đọc từ CSDL, không phải từ đối tượng trong bộ nhớ</h2>
 * Lỗ rò ban đầu <b>không</b> nằm ở chỗ endpoint trả gì ra — nó nằm ở chỗ câu thông báo mang tên và
 * năm sinh người trong phả <b>đã được ghi xuống bảng</b>. Một chốt chặn ở tầng api che được endpoint
 * nhưng không che được ai truy vấn thẳng bảng, không che được bản sao lưu, không che được báo cáo.
 * Vì vậy phép kiểm đúng là: chạy trọn đường ống thật (đọc tệp → khu vực chờ → bộ kiểm → ghi
 * {@code import_issue}), rồi <b>đọc lại chuỗi từ CSDL</b> và soi từng ký tự.
 *
 * <h2>Quét chuỗi thật, không kiểm kiểu</h2>
 * Gieo vào phả những giá trị <b>không thể tình cờ xuất hiện</b> — một cái tên có dấu, một năm sinh,
 * một năm mất, một nguyên quán, một số điện thoại — rồi khẳng định chúng không có mặt ở bất kỳ đâu
 * trong {@code message} lẫn {@code context}. Kiểm "trường X không có trong DTO" thì bỏ sót đúng cái
 * lớp lỗi đã xảy ra: giá trị bị nối vào một <b>câu tiếng Việt</b>.
 *
 * <p>Ca đối chứng đi kèm là bắt buộc: hai dòng trùng nhau <b>trong cùng một tệp</b> thì tên
 * <b>vẫn phải hiện</b>. Thiếu ca ấy, mọi khẳng định "không thấy tên" ở trên có thể xanh vì bộ dò
 * không kêu chứ không phải vì thông báo đã sạch.</p>
 */
@DisplayName("import_issue trong CSDL không mang một trường nhân khẩu nào của người trong phả")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class ImportIssuePrivacyIT extends AbstractIntegrationTest {

    /** Người <b>còn sống</b> — ca nguy hiểm nhất: guest không được thấy, member chi khác cũng không. */
    private static final String TEN_NGUOI_CON_SONG = "Nguyễn Văn Tuân";
    private static final String TEN_HUY_NGUOI_CON_SONG = "Nguyễn Đình Lựu";
    private static final String NGUYEN_QUAN_RIENG = "Kinh Bắc Thượng";
    private static final String DIEN_THOAI = "0900000001";
    private static final String NGHE_NGHIEP = "Giao vien lang Dong";

    /** Người <b>đã khuất</b> — để soi năm sinh và năm mất, hai con số chỉ có ở phía phả. */
    private static final String TEN_NGUOI_DA_KHUAT = "Nguyễn Văn Sửu";
    private static final int NAM_SINH_CHI_CO_TRONG_PHA = 1937;
    private static final int NAM_MAT_CHI_CO_TRONG_PHA = 1993;

    /** Bậc trên bị trùng huý. Tên huý lưu trong phả dài hơn hẳn ô người nhập gõ ("Đệ"). */
    private static final String TEN_BAC_TREN = "Nguyễn Phúc Thuỷ Tổ";
    private static final String TEN_HUY_BAC_TREN = "Nguyễn Phúc Đệ";

    @Autowired
    private StageImportBatchService stage;

    @Autowired
    private ValidateImportBatchService validate;

    private UUID chiAt;

    @BeforeEach
    void setUpDongHo() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");
    }

    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Nghi trùng: không tên, không năm sinh, không năm mất, không nguyên quán nào của người trong phả")
    void nghiTrung_khongMotTruongNaoLotXuongCsdl() {
        UUID conSong = gieoNguoiConSong();
        UUID daKhuat = gieoNguoiDaKhuat();

        byte[] tep = ImportWorkbooks.builder()
                // Dong nay nghi trung voi NGUOI CON SONG: trung ten sau khi bo dau + trung nam sinh.
                .nhanKhau("AT-04-001", "Nguyen Van Tuan", "", "Nam", "4", "", "", "", "Có",
                        String.valueOf(NAM_SINH_CHI_CO_TRONG_PHA), "")
                // Dong nay nghi trung voi NGUOI DA KHUAT: trung ten bo dau + TRUNG NGAY GIO.
                // Nam sinh de trong va nam mat khac han, nen 1937/1993 chi ton tai o phia pha.
                .nhanKhau("AT-03-001", "Nguyen Van Suu", "", "Nam", "3", "", "", "", "Không", "",
                        "23/11/1990")
                .build();

        List<Map<String, Object>> issues = kiem(tep);
        Map<String, Object> canhBao = canhBaoDuyNhat(issues, "IMP_SUSPECT_DUPLICATE", "AT-04-001");
        String tatCa = tatCa(canhBao);

        // --- Khong mot manh du lieu nhan khau nao cua nguoi trong pha ---
        assertThat(tatCa)
                .as("ten that co dau cua nguoi con song")
                .doesNotContain(TEN_NGUOI_CON_SONG)
                .doesNotContain(TEN_HUY_NGUOI_CON_SONG)
                .doesNotContain(NGUYEN_QUAN_RIENG)
                .doesNotContain(DIEN_THOAI)
                .doesNotContain(NGHE_NGHIEP);

        // --- ...nhung KHOA thi co, vi giao dien phai cam khoa di hoi GET /persons/{id} ---
        assertThat(tatCa).contains(conSong.toString());

        // --- ...va canh bao van dung duoc ---
        assertThat(canhBao.get("message").toString())
                .contains("AT-04-001", "Nguyen Van Tuan")   // du lieu cua CHINH nguoi nhap
                .contains("hồ sơ đã có trong phả")
                .contains("trùng họ tên khi bỏ dấu")        // vi sao nghi, ma khong lo ai
                .contains("trùng năm sinh")
                .contains("Hội đồng Tộc biểu");             // phai lam gi tiep

        // --- Nguoi da khuat: nam sinh va nam mat cung khong duoc lot ---
        Map<String, Object> canhBaoKhuat =
                canhBaoDuyNhat(issues, "IMP_SUSPECT_DUPLICATE", "AT-03-001");
        String tatCaKhuat = tatCa(canhBaoKhuat);
        assertThat(tatCaKhuat)
                .doesNotContain(TEN_NGUOI_DA_KHUAT)
                .doesNotContain(String.valueOf(NAM_SINH_CHI_CO_TRONG_PHA))
                .doesNotContain(String.valueOf(NAM_MAT_CHI_CO_TRONG_PHA));
        assertThat(tatCaKhuat).contains(daKhuat.toString());
        assertThat(canhBaoKhuat.get("message").toString()).contains("trùng ngày giỗ");
    }

    /**
     * Đối chứng. Không có ca này thì bài kiểm trên có thể xanh vì bộ dò im lặng chứ không phải vì
     * thông báo đã sạch — và ta sẽ tưởng mình đã vá xong một lỗ còn nguyên.
     */
    @Test
    @DisplayName("Đối chứng: hai dòng trùng nhau TRONG CÙNG TỆP thì tên vẫn hiện đầy đủ")
    void doiChung_trungTrongCungTepThiVanNeuTen() {
        byte[] tep = ImportWorkbooks.builder()
                .nhanKhau("AT-05-010", "Nguyễn Văn Kỷ", "", "Nam", "5", "", "", "", "Không",
                        "1922", "7/4/1981")
                .nhanKhau("AT-05-011", "Nguyễn Văn Kỷ", "", "Nam", "5", "", "", "", "Không",
                        "1922", "7/4/1981")
                .build();

        List<Map<String, Object>> canhBao = kiem(tep).stream()
                .filter(i -> "IMP_SUSPECT_DUPLICATE".equals(i.get("code")))
                .toList();

        assertThat(canhBao)
                .as("bo do phai keu, neu khong thi ca doi chung nay khong doi chung gi ca")
                .isNotEmpty();
        String tatCa = canhBao.stream().map(ImportIssuePrivacyIT::tatCa)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(tatCa)
                .contains("Nguyễn Văn Kỷ")
                .contains("AT-05-010")
                .contains("trong chính tệp này");
    }

    @Test
    @DisplayName("Kỵ húy: tên huý trong thông báo là ô người nhập gõ, không phải tên huý đọc từ phả")
    void kyHuy_khongNeuTenVaDoiCuaBacTren() {
        UUID bacTren = gieoBacTrenCoTenHuy();

        byte[] tep = ImportWorkbooks.builder()
                .nhanKhau("AT-07-001", "Nguyễn Văn Cẩn", "Đệ", "Nam", "7", "", "", "", "Không",
                        "1955", "9/2/2010")
                .build();

        Map<String, Object> canhBao = canhBaoDuyNhat(kiem(tep), "IMP_TABOO_COLLISION", "AT-07-001");
        String tatCa = tatCa(canhBao);

        assertThat(tatCa)
                .as("ten hien thi cua bac tren")
                .doesNotContain(TEN_BAC_TREN)
                // Bay: KetQua.tabooName() la person_name.full_name DOC TU PHA, khong phai o nguoi
                // nhap go — phep do khop ca o muc ten chinh, nen "De" khop "Nguyen Phuc De".
                .as("ten huy day du luu trong pha")
                .doesNotContain(TEN_HUY_BAC_TREN);
        assertThat(tatCa).contains(bacTren.toString());
        assertThat(canhBao.get("message").toString()).contains("Tên huý Đệ", "AT-07-001");
    }

    // -------------------------------------------------------------------------------------
    // Chạy đường ống và đọc lại từ CSDL
    // -------------------------------------------------------------------------------------

    /** Đọc tệp → khu vực chờ → bộ kiểm → {@code import_issue}, rồi đọc thẳng bảng ấy lên. */
    private List<Map<String, Object>> kiem(byte[] tep) {
        ImportBatch batch = stage.stage(chiAt, null, "rieng-tu.xlsx", tep, false);
        validate.validate(batch.id());
        return jdbc.queryForList(
                "SELECT code, row_no, message, context::text AS context FROM import_issue"
                        + " WHERE batch_id = ? ORDER BY row_no, code", batch.id());
    }

    private static Map<String, Object> canhBaoDuyNhat(List<Map<String, Object>> issues, String code,
                                                      String maDong) {
        List<Map<String, Object>> khop = issues.stream()
                .filter(i -> code.equals(i.get("code")))
                .filter(i -> String.valueOf(i.get("message")).contains(maDong))
                .toList();
        assertThat(khop)
                .as("khong tim thay %s cho dong %s trong %s", code, maDong, issues)
                .hasSize(1);
        return khop.get(0);
    }

    /** Toàn bộ chuỗi đã ghi xuống CSDL cho một cảnh báo: câu thông báo cộng ngữ cảnh JSONB. */
    private static String tatCa(Map<String, Object> issue) {
        return issue.get("message") + "\n" + issue.get("context");
    }

    // -------------------------------------------------------------------------------------
    // Gieo phả
    // -------------------------------------------------------------------------------------

    /**
     * Cố ý dựng bằng SQL thay vì qua use case: các ca này cần một hồ sơ <i>đã nằm sẵn trong phả</i>
     * làm mồi cho bộ dò, chứ không kiểm luồng thêm người.
     */
    private UUID gieoNguoiConSong() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO person (id, gender, generation, birth_solar, is_alive,"
                        + " native_place, primary_branch_id, lineage_status, attributes)"
                        + " VALUES (?, 'MALE', 4, ?, TRUE, ?, ?, 'NORMAL', CAST(? AS jsonb))",
                id, Date.valueOf(LocalDate.of(NAM_SINH_CHI_CO_TRONG_PHA, 3, 20)),
                NGUYEN_QUAN_RIENG, chiAt,
                "{\"_profile\":{\"occupation\":\"" + NGHE_NGHIEP + "\",\"contact\":{\"phone\":\""
                        + DIEN_THOAI + "\"}}}");
        themTen(id, "THUONG_GOI", TEN_NGUOI_CON_SONG, true);
        themTen(id, "HUY", TEN_HUY_NGUOI_CON_SONG, false);
        graph.createPersonNode(id, "MALE", 4);
        return id;
    }

    private UUID gieoNguoiDaKhuat() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO person (id, gender, generation, birth_solar, is_alive,"
                        + " death_lunar, native_place, primary_branch_id, lineage_status)"
                        + " VALUES (?, 'MALE', 3, ?, FALSE, CAST(? AS jsonb), ?, ?, 'NORMAL')",
                id, Date.valueOf(LocalDate.of(NAM_SINH_CHI_CO_TRONG_PHA, 5, 2)),
                "{\"year\":" + NAM_MAT_CHI_CO_TRONG_PHA + ",\"month\":11,\"day\":23,"
                        + "\"leap\":false}",
                NGUYEN_QUAN_RIENG, chiAt);
        themTen(id, "THUONG_GOI", TEN_NGUOI_DA_KHUAT, true);
        graph.createPersonNode(id, "MALE", 3);
        return id;
    }

    private UUID gieoBacTrenCoTenHuy() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO person (id, gender, generation, is_alive, primary_branch_id,"
                + " lineage_status) VALUES (?, 'MALE', 2, TRUE, ?, 'NORMAL')", id, chiAt);
        themTen(id, "THUONG_GOI", TEN_BAC_TREN, true);
        themTen(id, "HUY", TEN_HUY_BAC_TREN, false);
        graph.createPersonNode(id, "MALE", 2);
        return id;
    }

    private void themTen(UUID personId, String loai, String hoTen, boolean chinh) {
        jdbc.update("INSERT INTO person_name (id, person_id, name_type, full_name, is_primary)"
                + " VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), personId, loai, hoTen, chinh);
    }
}
