package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * <b>Hợp đồng thân lỗi</b> của toàn bộ {@code /api/v1}, chạy qua chuỗi HTTP thật (Spring Security +
 * MVC) trên PostgreSQL + Apache AGE thật.
 *
 * <h2>Vì sao phải là test tích hợp, không thể là unit test</h2>
 * Hai khiếm khuyết mà lớp này canh đều <b>không quan sát được</b> nếu chỉ gọi thẳng
 * {@code GlobalExceptionHandler}:
 * <ul>
 *   <li><b>{@code 401} thân rỗng.</b> Nguyên nhân nằm ở chỗ {@code ExceptionTranslationFilter} trả
 *       lời <i>trước khi</i> tới {@code DispatcherServlet}, nên {@code @RestControllerAdvice} không
 *       chạy. Một unit test gọi thẳng advice sẽ luôn xanh và chứng minh đúng điều không xảy ra
 *       trong thực tế. Chỉ có cả chuỗi lọc mới lộ ra sự thật.</li>
 *   <li><b>Mã HTTP của tài khoản chưa ghép.</b> Việc dịch {@code ACCOUNT_NOT_PROVISIONED} sang
 *       {@code 409} là hành vi của tầng lỗi; muốn chắc thì phải có một endpoint thật ném ra nó.</li>
 * </ul>
 *
 * <h2>Khẳng định theo {@code code}, không theo chuỗi mô tả</h2>
 * {@code contracts/README §3}: client rẽ nhánh theo {@code code}. Nên mọi ca ở đây ghim
 * {@code code}; {@code title}/{@code detail} là chuỗi cho người đọc và được phép đổi.
 */
@DisplayName("Hợp đồng RFC 7807: mọi lỗi đều có thân, và mã HTTP nói đúng trạng thái")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class ApiErrorContractIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    // =====================================================================================
    // Khiếm khuyết 1 — 401 phải có thân RFC 7807
    // =====================================================================================

    /**
     * Ba endpoint được đo trên hệ thống đang chạy: trước khi vá, cả ba trả
     * {@code Content-Length: 0} và không có {@code Content-Type}.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "/api/v1/tree",
            "/api/v1/directory",
            "/api/v1/persons/11111111-1111-1111-1111-111111111111"})
    @DisplayName("khách chưa đăng nhập nhận 401 KÈM thân RFC 7807 mang code UNAUTHENTICATED")
    void khachChuaDangNhap_nhan401CoThan(String duongDan) throws Exception {
        MvcResult result = mockMvc.perform(get(duongDan)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentType())
                .as("thân rỗng thì client đọc code ra \"UNKNOWN\" và rơi xuống nhánh lỗi chung")
                .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        JsonNode than = than(result);
        assertThat(than.path("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(than.path("status").asInt()).isEqualTo(401);
        assertThat(than.path("type").asText()).isEqualTo("https://giapha.vn/problems/unauthorized");
        assertThat(than.path("instance").asText()).isEqualTo(duongDan);
        assertThat(than.hasNonNull("title")).isTrue();
        assertThat(than.hasNonNull("timestamp")).isTrue();
    }

    @Test
    @DisplayName("401 vẫn giữ thử thách WWW-Authenticate của RFC 6750 — thân lỗi là phần THÊM")
    void van401GiuHeaderWwwAuthenticate() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/tree")).andReturn();

        assertThat(result.getResponse().getHeader("WWW-Authenticate"))
                .as("bo header nay la lam hong moi client OAuth2 chuan")
                .startsWith("Bearer");
    }

    @Test
    @DisplayName("401 không tiết lộ vì sao token hỏng, và không mang dữ liệu nhân khẩu nào")
    void than401KhongTietLoGi() throws Exception {
        String than = chuoi(mockMvc.perform(get("/api/v1/directory")
                .header("Authorization", "Bearer khong-phai-mot-jwt")).andReturn());

        assertThat(than).doesNotContain("khong-phai-mot-jwt");
        assertThat(than.toLowerCase()).doesNotContain("expired").doesNotContain("signature");
    }

    @Test
    @DisplayName("403 sinh trong chuỗi lọc (thiếu vai) cũng có thân, không còn Content-Length: 0")
    void thieuVai_nhan403CoThan() throws Exception {
        // /api/v1/audit-logs doi ADMIN hoac COUNCIL ngay tai authorizeHttpRequests, nen loi sinh ra
        // TRONG chuoi loc y het ca 401 — va truoc khi va thi cung tra than rong y het.
        MvcResult result = mockMvc.perform(get("/api/v1/audit-logs").with(thanhVien("sub-member")))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentType())
                .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(than(result).path("code").asText()).isEqualTo("FORBIDDEN");
    }

    // =====================================================================================
    // Khiếm khuyết 2 — tài khoản chưa gắn nhân khẩu
    // =====================================================================================

    @Test
    @DisplayName("tài khoản chưa gắn nhân khẩu nhận 409 ACCOUNT_NOT_PROVISIONED, KHÔNG phải 404")
    void chuaGanNhanKhau_nhan409() throws Exception {
        // Ca that va binh thuong: nguoi vua duoc moi vao, Truong chi chua gan ho voi mot nhan khau.
        // /api/v1/me tu tao app_user o trang thai PENDING, nen sub nay co tai khoan ma khong co
        // person_id — dung trang thai cua tai khoan "chuaduyet" tren he thong dang chay.
        MvcResult result = mockMvc.perform(get("/api/v1/me/person").with(thanhVien("sub-chua-ghep")))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("404 ve giao dien la man 'khong tim thay trang' cho mot nguoi VUA DANG NHAP"
                        + " THANH CONG; 403 noi sai rang ho bi tu choi vinh vien")
                .isEqualTo(409);

        JsonNode than = than(result);
        assertThat(than.path("code").asText()).isEqualTo("ACCOUNT_NOT_PROVISIONED");
        assertThat(than.path("type").asText())
                .isEqualTo("https://giapha.vn/problems/account-not-provisioned");
        assertThat(than.path("nextStep").asText())
                .as("phai noi duoc PHAI LAM GI TIEP bang mot ma may doc, khong phai bang chuoi mo ta")
                .isEqualTo("CONTACT_BRANCH_HEAD");
    }

    @Test
    @DisplayName("cùng tài khoản ấy vẫn gọi được /api/v1/me — chưa ghép không phải là lỗi phiên")
    void chuaGanNhanKhau_vanDangNhapBinhThuong() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/me").with(thanhVien("sub-chua-ghep-2")))
                .andReturn();

        // Neu /me cung hong thi giao dien khong con cach nao biet nguoi dung la ai de ve man
        // "cho ghep vao pha" — no se day ho ve trang dang nhap va ho quay vong mai o do.
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(than(result).path("linkedToTree").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("tài khoản ĐÃ ghép nhận 200 — 409 nói về trạng thái, không phải về endpoint")
    void daGanNhanKhau_nhan200() throws Exception {
        UUID chi = insertBranch("Chi Giáp", "goc.chi_giap", null, "CHI");
        authenticateAs("sub-admin", "ADMIN");
        UUID nhanKhau = seed(PersonFixtures.living("Nguyễn Văn Đã Ghép").branch(chi).generation(3));
        insertAppUser("sub-da-ghep", nhanKhau);

        MvcResult result = mockMvc.perform(get("/api/v1/me/person").with(thanhVien("sub-da-ghep")))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(than(result).path("personId").asText()).isEqualTo(nhanKhau.toString());
    }

    // =====================================================================================
    // Tiện ích
    // =====================================================================================

    private RequestPostProcessor thanhVien(String sub) {
        return jwt().jwt(builder -> builder.subject(sub))
                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }

    private static JsonNode than(MvcResult result) throws Exception {
        return JSON.readTree(chuoi(result));
    }

    private static String chuoi(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
