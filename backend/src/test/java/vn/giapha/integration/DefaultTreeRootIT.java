package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.giapha.genealogy.application.LinkRelationshipService;
import vn.giapha.genealogy.application.command.LinkRelationshipCommand;
import vn.giapha.genealogy.domain.RelType;

/**
 * <b>Gốc mặc định của phả đồ</b> — {@code GET /api/v1/tree} và {@code GET /api/v1/public/tree}
 * <i>không</i> kèm {@code rootId}, chạy qua chuỗi HTTP thật trên PostgreSQL + Apache AGE thật.
 *
 * <h2>Lỗi mà lớp này sinh ra để chặn quay lại</h2>
 * {@code rootId} từng là {@code @RequestParam} bắt buộc không có mặc định, nên trang phả đồ — màn
 * hình chính của cả sản phẩm — trả {@code 400} cho <b>mọi vai, kể cả Quản trị</b>, chỉ vì người
 * dùng mở nó ra mà chưa chọn ai. Đó không phải lỗi dữ liệu và không có thông báo nào nói được điều
 * gì hữu ích, nên nó rất dễ quay lại dưới dạng "dọn dẹp tham số".
 *
 * <h2>Bốn vai phải ra bốn gốc khác nhau</h2>
 * Kiểm đúng điều đó chứ không chỉ kiểm "có trả về 200": một hiện thực gán cứng Thuỷ tổ cho tất cả
 * cũng xanh ở phép kiểm mã trạng thái, trong khi nó vứt bỏ toàn bộ lý do của tính năng.
 *
 * <p>Ca <b>then chốt</b> là {@link #truongChi_moRaChiDuocGiaoChuKhongPhaiChiNha()}: Trưởng chi
 * trong dữ liệu nền sinh ra ở Chi Giáp nhưng được giao Chi Ất. Hiện thực nào lấy "chi nhà" cho mọi
 * vai không-toàn-họ sẽ đỏ đúng ở đây và chỉ ở đây.</p>
 */
@DisplayName("Phả đồ không có rootId: mỗi vai một gốc, và lối gọi cũ không đổi")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class DefaultTreeRootIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Mỗi lượt gọi công khai đi từ một IP riêng — {@code PublicRateLimitFilter} tính theo IP. */
    private static final AtomicInteger CLIENT_SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LinkRelationshipService linkRelationship;

    private UUID chiGiap;
    private UUID chiAt;

    /** Thuỷ tổ, đời 1, đã khuất — gốc của cả dòng họ và là gốc duy nhất Khách nhìn thấy được. */
    private UUID thuyTo;
    /** Ông tổ Chi Giáp, đời 2. */
    private UUID toChiGiap;
    /** Ông tổ Chi Ất, đời 2. */
    private UUID toChiAt;
    /** Thành viên còn sống, đời 3, thuộc Chi Giáp — <b>chưa có con</b>, y như đa số người dùng thật. */
    private UUID thanhVien;
    /** Trưởng chi: sinh ra ở Chi Giáp nhưng được giao quản trị Chi Ất. */
    private UUID truongChi;

    @BeforeEach
    void setUpDongHo() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");
        insertBranch("Ngành Hai", "goc.chi_giap.nganh_hai", chiGiap, "NGANH");

        authenticateAs("sub-admin", "ADMIN");

        thuyTo = seed(PersonFixtures.deceased("Nguyễn Phúc Thuỷ Tổ", 1900)
                .birthYear(1830).generation(1).branch(goc));
        toChiGiap = seed(PersonFixtures.deceased("Nguyễn Văn Tổ Giáp", 1950)
                .birthYear(1870).generation(2).branch(chiGiap));
        toChiAt = seed(PersonFixtures.deceased("Nguyễn Văn Tổ Ất", 1955)
                .birthYear(1872).generation(2).branch(chiAt));
        thanhVien = seed(PersonFixtures.living("Nguyễn Văn Cháu")
                .birthYear(1990).generation(3).branch(chiGiap));
        truongChi = seed(PersonFixtures.living("Nguyễn Văn Trưởng Chi")
                .birthYear(1965).generation(3).branch(chiGiap));

        lamCha(thuyTo, toChiGiap);
        lamCha(thuyTo, toChiAt);
        lamCha(toChiGiap, thanhVien);
        lamCha(toChiGiap, truongChi);

        insertAppUser("sub-thanh-vien", thanhVien);
        UUID taiKhoanTruongChi = insertAppUser("sub-truong-chi", truongChi);
        assignBranchRole(taiKhoanTruongChi, "BRANCH_HEAD", chiAt);
        insertAppUser("sub-hoi-dong", null);
        insertAppUser("sub-admin", null);

        authenticateAsGuest();
    }

    private void lamCha(UUID cha, UUID con) {
        linkRelationship.link(new LinkRelationshipCommand(cha, con, RelType.PARENT_BIO, null, null,
                null, null, null));
    }

    // =========================================================================================
    // Không có rootId — bốn vai, bốn gốc
    // =========================================================================================

    @Test
    @DisplayName("Thành viên mở ra gốc CHI MÌNH, không phải chính mình và không phải cả dòng họ")
    void thanhVien_moRaGocChiMinh() throws Exception {
        JsonNode than = than(goi(thanhVienChiGiap()));

        assertThat(than.get("rootId").asText())
                .as("lấy chính hồ sơ người dùng làm gốc thì với chiều mặc định DESCENDANTS họ sẽ"
                        + " thấy đúng một node: chính mình")
                .isEqualTo(toChiGiap.toString());
        assertThat(ids(than))
                .as("cây phải chứa chính người đang xem, nếu không thì gốc chọn sai chi")
                .contains(thanhVien.toString());
    }

    @Test
    @DisplayName("Trưởng chi mở ra CHI ĐƯỢC GIAO, không phải chi nhà mình")
    void truongChi_moRaChiDuocGiaoChuKhongPhaiChiNha() throws Exception {
        JsonNode than = than(goi(truongChi()));

        assertThat(than.get("rootId").asText())
                .as("Trưởng chi này sinh ra ở Chi Giáp nhưng chịu trách nhiệm duyệt Chi Ất —"
                        + " mở ra Chi Giáp là mở nhầm cây")
                .isEqualTo(toChiAt.toString());
    }

    @Test
    @DisplayName("Hội đồng Tộc biểu và Quản trị mở ra Thuỷ tổ — phạm vi của họ là cả dòng họ")
    void vaiToanDongHo_moRaThuyTo() throws Exception {
        assertThat(than(goi(hoiDong())).get("rootId").asText()).isEqualTo(thuyTo.toString());
        assertThat(than(goi(quanTri())).get("rootId").asText()).isEqualTo(thuyTo.toString());
    }

    @Test
    @DisplayName("Khách mở phả đồ công khai không có rootId: ra Thuỷ tổ, và chỉ thấy người đã khuất")
    void khach_moRaThuyTo() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/public/tree").with(tuMotKhachLa()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode than = than(result);
        assertThat(than.get("rootId").asText()).isEqualTo(thuyTo.toString());
        assertThat(chuoiThan(result))
                .as("gốc mặc định không được mở thêm một khe nào cho dữ liệu người còn sống")
                .doesNotContain(thanhVien.toString())
                .doesNotContain("Nguyễn Văn Cháu");
    }

    @Test
    @DisplayName("Thành viên mang token tới cổng công khai vẫn nhận gốc của Khách")
    void khach_gocCongKhaiKhongDoiTheoToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/public/tree").with(tuMotKhachLa())
                .with(nhuLa("sub-thanh-vien", "ROLE_MEMBER"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(than(result).get("rootId").asText())
                .as("một URL công khai trả hai cây khác nhau tuỳ token thì Cache-Control: public"
                        + " trên chính nó trở thành lỗ rò rỉ")
                .isEqualTo(thuyTo.toString());
    }

    @Test
    @DisplayName("Khách gọi /api/v1/tree (bản thành viên) vẫn nhận 401 — gốc mặc định không nới quyền")
    void khach_khongLotVaoBanThanhVien() throws Exception {
        assertThat(mockMvc.perform(get("/api/v1/tree")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("Tài khoản chưa ghép với nhân khẩu nào rơi về Thuỷ tổ, không phải 400 hay 404")
    void taiKhoanChuaGhep_roiVeThuyTo() throws Exception {
        MockHttpServletRequestBuilder chuaGhep = get("/api/v1/tree")
                .with(nhuLa("sub-chua-ghep", "ROLE_MEMBER"));

        assertThat(than(goi(chuaGhep)).get("rootId").asText()).isEqualTo(thuyTo.toString());
    }

    // =========================================================================================
    // Có rootId — hành vi cũ không đổi
    // =========================================================================================

    @Test
    @DisplayName("Gửi rootId tường minh: phản hồi y hệt lối gọi cũ, gồm cả ETag")
    void coRootId_hanhViKhongDoi() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/tree")
                .param("rootId", toChiAt.toString())
                .with(nhuLa("sub-thanh-vien", "ROLE_MEMBER"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(than(result).get("rootId").asText())
                .as("rootId tường minh phải thắng, kể cả khi nó nằm ngoài chi nhà của người gọi")
                .isEqualTo(toChiAt.toString());
        assertThat(result.getResponse().getHeader("ETag")).isNotBlank();
    }

    @Test
    @DisplayName("rootId tường minh trỏ vào nhân khẩu không tồn tại vẫn là 404, không rơi về gốc mặc định")
    void coRootIdSai_van404() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/tree")
                .param("rootId", UUID.randomUUID().toString())
                .with(nhuLa("sub-thanh-vien", "ROLE_MEMBER"))).andReturn();

        assertThat(result.getResponse().getStatus())
                .as("im lặng rơi về gốc mặc định sẽ giấu một lỗi của client dưới một cây trông"
                        + " hoàn toàn hợp lệ")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("Mọi tham số còn lại vẫn có tác dụng khi không có rootId")
    void khongCoRootId_thamSoKhacVanChay() throws Exception {
        JsonNode than = than(goi(quanTri().param("depth", "1")));

        assertThat(than.get("meta").get("depth").asInt()).isEqualTo(1);
        assertThat(ids(than))
                .as("depth=1 từ Thuỷ tổ: có hai ông tổ chi, chưa tới đời 3")
                .contains(toChiGiap.toString(), toChiAt.toString())
                .doesNotContain(thanhVien.toString());
    }

    // =========================================================================================
    // Tiện ích
    // =========================================================================================

    private MockHttpServletRequestBuilder thanhVienChiGiap() {
        return get("/api/v1/tree").with(nhuLa("sub-thanh-vien", "ROLE_MEMBER"));
    }

    private MockHttpServletRequestBuilder truongChi() {
        return get("/api/v1/tree").with(nhuLa("sub-truong-chi", "ROLE_BRANCH_HEAD"));
    }

    private MockHttpServletRequestBuilder hoiDong() {
        return get("/api/v1/tree").with(nhuLa("sub-hoi-dong", "ROLE_COUNCIL"));
    }

    private MockHttpServletRequestBuilder quanTri() {
        return get("/api/v1/tree").with(nhuLa("sub-admin", "ROLE_ADMIN"));
    }

    private static RequestPostProcessor nhuLa(String sub, String authority) {
        return jwt().jwt(builder -> builder.subject(sub))
                .authorities(new SimpleGrantedAuthority(authority));
    }

    private static RequestPostProcessor tuMotKhachLa() {
        String ip = "198.51.100." + (CLIENT_SEQ.incrementAndGet() % 250 + 1);
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private MvcResult goi(MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult result = mockMvc.perform(builder).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("than phan hoi: %s", chuoiThan(result))
                .isEqualTo(200);
        return result;
    }

    private static String chuoiThan(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static JsonNode than(MvcResult result) throws Exception {
        return JSON.readTree(chuoiThan(result));
    }

    private static List<String> ids(JsonNode than) {
        List<String> ids = new ArrayList<>();
        than.get("nodes").forEach(node -> ids.add(node.get("id").asText()));
        return ids;
    }
}
