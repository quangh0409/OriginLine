package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.giapha.shared.vo.Gender;

/**
 * <b>Thân lỗi 409 của {@code POST /api/v1/persons} không được là một kênh đọc dữ liệu nhân khẩu.</b>
 * Chạy trọn chuỗi HTTP thật (Spring Security + MVC) trên PostgreSQL + Apache AGE thật.
 *
 * <h2>Câu hỏi duy nhất mà mọi ca ở đây trả lời</h2>
 * Khi máy nghi ngờ, nó có lỡ nói ra <b>một giá trị đọc từ phả</b> không? Cả hai bộ dò đứng sau 409
 * đều quét <b>toàn dòng họ</b> và <b>không biết người gọi là ai</b> — bộ dò trùng chấm điểm theo
 * tên / năm sinh / ngày giỗ, bộ dò kỵ húy chọn bậc trên theo <b>đời thứ</b> chứ không theo sống-mất
 * và không có một điều kiện chi/ngành nào. Thân lỗi thì đi thẳng ra HTTP, <b>không</b> qua
 * {@code PrivacyTierService}. Nên phép khẳng định ở đây soi <b>toàn bộ chuỗi JSON của phản hồi</b>,
 * không soi từng trường: lỗi đã xảy ra một lần rồi, và nó xảy ra ở chỗ giá trị bị nối vào một câu
 * tiếng Việt trong {@code detail}, không ở một trường DTO.
 *
 * <h2>Người gọi: Trưởng chi Giáp — vai có quyền ghi nhưng KHÔNG có quyền đọc khối kín</h2>
 * {@code PersonVisibility.ungroupedFieldsVisible()} là {@code deceased || self || clanWide}. Với
 * một người <b>còn sống</b>, khối "tên huý / tự / hiệu / thụy, năm sinh, nguyên quán, tiểu sử" chỉ
 * mở cho chính chủ, Hội đồng Tộc biểu và Admin — <b>Trưởng chi cũng không</b>, kể cả với người
 * trong chính chi mình. Vì vậy Trưởng chi là người gọi đúng để đo lỗ rò: họ được phép gọi
 * {@code POST /persons}, nhưng {@code GET /persons/&#123;id&#125;} cố ý không trả cho họ đúng những
 * giá trị mà thân lỗi từng phát ra.
 *
 * <h2>Vì sao ca nghi trùng dùng người cùng chi, còn ca kỵ húy dùng người CHI KHÁC</h2>
 * Không phải tuỳ tiện, mà là số học của bộ chấm điểm. Ngưỡng kêu là <b>70</b>; với hai người
 * <b>còn sống</b> ở <b>hai chi khác nhau</b>, tổng điểm tối đa lấy được là
 * {@code TEN_TRUNG_CO_DAU(40) + NAM_SINH_KHOP(22) + CUNG_DOI(6) = 68} — thiếu đúng hai điểm, vì
 * {@code GIO_TRUNG_KHIT} đòi cả hai bên đã khuất và {@code CUNG_CHI(18)} thì theo định nghĩa không
 * bật được. Nói cách khác, <b>hôm nay</b> một ứng viên còn sống chỉ vượt ngưỡng khi cùng chi với
 * người đang được thêm. Đó là một tai nạn của bộ trọng số, <b>không</b> phải một bảo đảm: nâng
 * {@code CUNG_DOI} lên 8 là ca chi khác sống lại ngay. Bảo đảm riêng tư ở đây vì thế không được
 * dựa vào con số ấy — và ca kỵ húy bên dưới thì <b>không</b> có ngưỡng nào cả, nên nó đo thẳng
 * được đúng kịch bản "ông bác còn sống ở chi khác".
 *
 * <h2>Ca đối chứng là bắt buộc</h2>
 * Cắt sạch tên đi thì người thêm nhân khẩu mất khả năng đối chiếu và sẽ bấm ghi đè bừa — tức là
 * bịt lỗ rò bằng cách phá chính cơ chế chống trùng. Nên mỗi ca "không thấy gì" đều đi kèm một ca
 * chứng minh rằng <b>khoá trong thân lỗi vẫn dẫn tới một phép đối chiếu thật</b> qua
 * {@code GET /persons/&#123;id&#125;}, và rằng endpoint ấy trả nhiều hơn cho người có quyền hơn.
 */
@DisplayName("409 của POST /persons không phát dữ liệu nhân khẩu đọc từ phả")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class PersonConflictPrivacyIT extends AbstractIntegrationTest {

    // --- Ứng viên nghi trùng: người CÒN SỐNG. Người gọi gõ bản KHÔNG DẤU, nên bản có dấu dưới
    //     đây chỉ tồn tại ở phía phả.
    private static final String TEN_CO_DAU = "Nguyễn Thị Bạch Liên";
    private static final String TEN_KHONG_DAU = "Nguyen Thi Bach Lien";
    private static final String TEN_HUY_UNG_VIEN = "Nguyễn Thị Kín Đáo";
    private static final int NAM_SINH_UNG_VIEN = 1971;

    // --- Bậc trên kỵ húy: người CÒN SỐNG Ở CHI KHÁC. Người gọi chỉ gõ phần tên chính "Ẩn Dật".
    private static final String TEN_BAC_TREN = "Nguyễn Đình Lựu";
    private static final String TEN_HUY_BAC_TREN = "Nguyễn Phúc Ẩn Dật";
    private static final String PHAN_TEN_CHINH_GO_VAO = "Ẩn Dật";

    /** Số điện thoại và nghề nghiệp mà {@link PersonFixtures} gắn cho mọi nhân khẩu — Tầng 3. */
    private static final String DIEN_THOAI_FIXTURE = "0900000001";
    private static final String NGHE_NGHIEP_FIXTURE = "Giao vien";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private UUID chiGiap;
    private UUID chiAt;
    private UUID chaDaKhuat;
    private UUID ungVienConSong;
    private UUID bacTrenConSongChiKhac;

    @BeforeEach
    void setUpDongHo() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");

        // Trưởng chi Giáp: được ghi trong chi Giáp, KHÔNG được xem khối kín của người còn sống.
        UUID truongChi = insertAppUser("sub-truong-chi-giap", null);
        assignBranchRole(truongChi, "BRANCH_HEAD", chiGiap);

        // Cha đã khuất — để người mới thêm có đời thứ suy ra được (đời 4) và thừa kế chi Giáp.
        chaDaKhuat = seed(PersonFixtures.deceased("Nguyễn Văn Tổ", 1998)
                .birthYear(1930).branch(chiGiap).generation(3));

        ungVienConSong = seed(PersonFixtures.living(TEN_CO_DAU)
                .gender(Gender.FEMALE)
                .birthYear(NAM_SINH_UNG_VIEN)
                .branch(chiGiap)
                .generation(4)
                .tabooName(TEN_HUY_UNG_VIEN));

        bacTrenConSongChiKhac = seed(PersonFixtures.living(TEN_BAC_TREN)
                .birthYear(1948)
                .branch(chiAt)
                .generation(2)
                .tabooName(TEN_HUY_BAC_TREN));
    }

    // =========================================================================================
    // Nghi trùng
    // =========================================================================================

    @Test
    @DisplayName("Nghi trùng: phản hồi chỉ có personId — không tên, không tên huý, không đời, không chi")
    void nghiTrung_khongMotGiaTriNaoDocTuPhaLotRaNgoai() throws Exception {
        MvcResult ketQua = mockMvc.perform(post("/api/v1/persons")
                        .with(truongChiGiap())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(themNguoiTrungTen(false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PERSON_SUSPECTED"))
                .andExpect(jsonPath("$.overridable").value(true))
                .andExpect(jsonPath("$.overrideField").value("confirmDuplicateOverride"))
                .andReturn();

        String than = than(ketQua);

        assertThat(than)
                .as("ten that co dau — nguoi goi chi go ban khong dau")
                .doesNotContain(TEN_CO_DAU)
                // matchedName la person_name.full_name cua HO SO BEN KIA. Khop o muc bo dau thi no
                // phat ra dung ban co dau ma nguoi goi chua tung biet, va phep khop chay cheo moi
                // lop ten nen no hoan toan co the la ten huy.
                .as("ten huy — khoi ungroupedFields, Truong chi khong duoc xem")
                .doesNotContain(TEN_HUY_UNG_VIEN)
                .as("chi/nganh cua ung vien")
                .doesNotContain(chiGiap.toString())
                .as("du lieu Tang 3 di kem ho so")
                .doesNotContain(DIEN_THOAI_FIXTURE, NGHE_NGHIEP_FIXTURE);

        // Cac khoa da bo han khoi hop dong — dung them lai, xem javadoc DuplicateCandidate.
        assertThat(than).doesNotContain("displayName", "generation", "branchId", "matchedName");

        // ...nhung KHOA thi phai co: giao dien cam no di goi GET /persons/{id}.
        assertThat(than).contains(ungVienConSong.toString());

        JsonNode ungVien = JSON.readTree(than).path("conflicts").path(0);
        assertThat(ungVien.path("personId").asText()).isEqualTo(ungVienConSong.toString());
        assertThat(ungVien.path("score").asInt())
                .as("diem noi MUC do dang ngo, khong noi gia tri nao cua ho so ben kia")
                .isGreaterThanOrEqualTo(70);
        assertThat(ungVien.path("signals").toString())
                .as("vi sao nghi — loai tin hieu, khong phai gia tri truong")
                .contains("TEN_TRUNG_KHONG_DAU", "NAM_SINH_KHOP", "CUNG_CHI");
    }

    /**
     * Đối chứng. Không có ca này thì bài kiểm trên có thể xanh vì khoá trả về là một ngõ cụt chứ
     * không phải vì thân lỗi đã sạch — và ta đã phá cơ chế chống trùng trong lúc vá lỗ rò.
     */
    @Test
    @DisplayName("Đối chứng: cầm personId trong 409 gọi GET /persons/{id} thì vẫn đối chiếu được")
    void doiChung_khoaTrong409VanDanToiMotPhepDoiChieuThat() throws Exception {
        MvcResult xungDot = mockMvc.perform(post("/api/v1/persons")
                        .with(truongChiGiap())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(themNguoiTrungTen(false)))
                .andExpect(status().isConflict())
                .andReturn();
        String khoa = JSON.readTree(than(xungDot)).path("conflicts").path(0).path("personId").asText();
        assertThat(khoa).isEqualTo(ungVienConSong.toString());

        // --- Trưởng chi: thấy tên chính (đủ để đối chiếu), KHÔNG thấy khối kín.
        String theoTruongChi = than(mockMvc.perform(get("/api/v1/persons/{id}", khoa)
                        .with(truongChiGiap()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(theoTruongChi)
                .as("van doi chieu duoc: ten chinh co day du, khong bi cat sach")
                .contains(TEN_CO_DAU)
                .as("...nhung bo loc that van giu kin khoi ungroupedFields")
                .doesNotContain(TEN_HUY_UNG_VIEN);

        // --- Hội đồng Tộc biểu: thấy đủ, kể cả tên huý. Đây là vai đối chiếu được trọn vẹn.
        String theoHoiDong = than(mockMvc.perform(get("/api/v1/persons/{id}", khoa)
                        .with(hoiDongTocBieu()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(theoHoiDong)
                .as("nguoi co quyen van doi chieu duoc day du — day moi la cho luat duoc ap dung")
                .contains(TEN_CO_DAU, TEN_HUY_UNG_VIEN);
    }

    @Test
    @DisplayName("Luồng hai bước: gửi lại kèm confirmDuplicateOverride = true thì ghi bình thường")
    void luongHaiBuoc_ghiDeVanChay() throws Exception {
        mockMvc.perform(post("/api/v1/persons")
                        .with(truongChiGiap())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(themNguoiTrungTen(false)))
                .andExpect(status().isConflict());

        MvcResult daGhi = mockMvc.perform(post("/api/v1/persons")
                        .with(truongChiGiap())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(themNguoiTrungTen(true)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = JSON.readTree(than(daGhi)).path("id").asText();
        assertThat(id).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM person WHERE id = CAST(? AS uuid)",
                Integer.class, id))
                .as("may nghi ngo, nguoi quyet dinh: xac nhan roi thi phai ghi that")
                .isEqualTo(1);
    }

    // =========================================================================================
    // Kỵ húy — bậc trên CÒN SỐNG Ở CHI KHÁC
    // =========================================================================================

    /**
     * Ca này mới là kịch bản "ông bác còn sống ở chi khác" ở dạng thuần nhất: phép dò kỵ húy lọc
     * theo {@code generation <} đời của người mới và <b>không</b> có điều kiện sống-mất hay
     * chi/ngành nào, nên không có ngưỡng điểm nào chắn đường như bên nghi trùng.
     */
    @Test
    @DisplayName("Kỵ húy: bậc trên còn sống ở chi khác — phản hồi chỉ có ancestorPersonId")
    void kyHuy_khongNeuDanhTinhBacTrenConSongOChiKhac() throws Exception {
        MvcResult ketQua = mockMvc.perform(post("/api/v1/persons")
                        .with(truongChiGiap())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(themNguoiTrungHuy(false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KY_HUY_CONFLICT"))
                .andExpect(jsonPath("$.overridable").value(true))
                .andExpect(jsonPath("$.overrideField").value("confirmTabooOverride"))
                .andReturn();

        String than = than(ketQua);

        assertThat(than)
                .as("ten hien thi cua bac tren")
                .doesNotContain(TEN_BAC_TREN)
                // Bay: tabooName la person_name.full_name DOC TU PHA. Nguoi goi go "An Dat", con
                // pha giu "Nguyen Phuc An Dat" — hai chuoi khac han nhau.
                .as("ten huy day du luu trong pha")
                .doesNotContain(TEN_HUY_BAC_TREN)
                .as("chi/nganh cua bac tren")
                .doesNotContain(chiAt.toString())
                .as("du lieu Tang 3 di kem ho so")
                .doesNotContain(DIEN_THOAI_FIXTURE, NGHE_NGHIEP_FIXTURE);

        assertThat(than)
                .doesNotContain("ancestorDisplayName", "ancestorGeneration", "tabooName",
                        "relationHint");
        assertThat(than).contains(bacTrenConSongChiKhac.toString());

        JsonNode vaCham = JSON.readTree(than).path("conflicts").path(0);
        assertThat(vaCham.path("ancestorPersonId").asText())
                .isEqualTo(bacTrenConSongChiKhac.toString());
        assertThat(vaCham.path("matchedNameType").asText()).isEqualTo("HUY");
        assertThat(vaCham.path("matchKind").asText())
                .as("kieu khop noi O CUA CHINH NGUOI GOI da khop toi muc nao")
                .isEqualTo("GIVEN_NAME");
    }

    @Test
    @DisplayName("Đối chứng kỵ húy: Hội đồng cầm ancestorPersonId vẫn tra được tên húy đầy đủ")
    void doiChungKyHuy_nguoiCoQuyenVanTraDuocBacTren() throws Exception {
        MvcResult xungDot = mockMvc.perform(post("/api/v1/persons")
                        .with(truongChiGiap())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(themNguoiTrungHuy(false)))
                .andExpect(status().isConflict())
                .andReturn();
        String khoa = JSON.readTree(than(xungDot)).path("conflicts").path(0)
                .path("ancestorPersonId").asText();

        String theoHoiDong = than(mockMvc.perform(get("/api/v1/persons/{id}", khoa)
                        .with(hoiDongTocBieu()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(theoHoiDong).contains(TEN_BAC_TREN, TEN_HUY_BAC_TREN);

        String theoTruongChi = than(mockMvc.perform(get("/api/v1/persons/{id}", khoa)
                        .with(truongChiGiap()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(theoTruongChi)
                .as("Truong chi chi Giap voi mot nguoi con song o chi At: Tang 1, khong co ten huy")
                .contains(TEN_BAC_TREN)
                .doesNotContain(TEN_HUY_BAC_TREN);
    }

    @Test
    @DisplayName("Luồng hai bước kỵ húy: confirmTabooOverride = true thì vẫn ghi")
    void luongHaiBuocKyHuy_ghiDeVanChay() throws Exception {
        mockMvc.perform(post("/api/v1/persons")
                        .with(truongChiGiap())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(themNguoiTrungHuy(false)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/persons")
                        .with(truongChiGiap())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(themNguoiTrungHuy(true)))
                .andExpect(status().isCreated());
    }

    // =========================================================================================
    // Tiện ích
    // =========================================================================================

    private RequestPostProcessor truongChiGiap() {
        return jwt().jwt(b -> b.subject("sub-truong-chi-giap"))
                .authorities(new SimpleGrantedAuthority("ROLE_BRANCH_HEAD"));
    }

    private RequestPostProcessor hoiDongTocBieu() {
        return jwt().jwt(b -> b.subject("sub-hoi-dong"))
                .authorities(new SimpleGrantedAuthority("ROLE_COUNCIL"));
    }

    private static String than(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * Thêm một người trùng tên ứng viên khi bỏ dấu, trùng năm sinh, cùng chi, cùng đời.
     *
     * <p>Điểm: {@code TEN_TRUNG_KHONG_DAU(30) + NAM_SINH_KHOP(22) + CUNG_CHI(18) + CUNG_DOI(6) = 76}
     * — trên ngưỡng 70 với biên 6 điểm, để một lần hiệu chỉnh trọng số không âm thầm tắt bài kiểm
     * này. Đời thứ 4 đến từ liên kết cha (đời 3), không phải client tự khai.</p>
     */
    private String themNguoiTrungTen(boolean ghiDe) {
        return """
                {
                  "names": [{"nameType": "THUONG_GOI", "fullName": "%s", "isPrimary": true}],
                  "gender": "FEMALE",
                  "isAlive": true,
                  "birth": {"solar": "%d-05-02", "precision": "DAY"},
                  "nativePlace": "Bac Ninh",
                  "primaryBranchId": "%s",
                  "initialRelationships": [
                    {"relType": "PARENT_BIO", "otherPersonId": "%s", "otherPersonRole": "SOURCE"}
                  ],
                  "confirmDuplicateOverride": %s
                }
                """.formatted(TEN_KHONG_DAU, NAM_SINH_UNG_VIEN, chiGiap, chaDaKhuat, ghiDe);
    }

    /**
     * Thêm một người có tên huý trùng <b>phần tên chính</b> với tên huý của bậc trên còn sống ở chi
     * khác ({@code UNACCENTED LIKE '% Dat'} → {@code GIVEN_NAME}).
     *
     * <p>Không nối cha, nên đời thứ là {@code null} và phép dò kỵ húy quét toàn dòng họ — đúng
     * hành vi khi nhập một nhân khẩu chưa gắn vào cây.</p>
     */
    private String themNguoiTrungHuy(boolean ghiDe) {
        return """
                {
                  "names": [
                    {"nameType": "THUONG_GOI", "fullName": "Nguyễn Văn Tân Lập", "isPrimary": true},
                    {"nameType": "HUY", "fullName": "%s"}
                  ],
                  "gender": "MALE",
                  "isAlive": true,
                  "primaryBranchId": "%s",
                  "confirmTabooOverride": %s
                }
                """.formatted(PHAN_TEN_CHINH_GO_VAO, chiGiap, ghiDe);
    }
}
