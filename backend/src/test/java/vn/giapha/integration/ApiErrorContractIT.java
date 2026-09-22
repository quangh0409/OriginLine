package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
 *       {@code 403} là hành vi của tầng lỗi; muốn chắc thì phải có một endpoint thật ném ra nó.</li>
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

    @Autowired
    private org.springframework.context.ApplicationContext context;

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
    @DisplayName("tài khoản chưa gắn nhân khẩu nhận 403 ACCOUNT_NOT_PROVISIONED, KHÔNG phải 404")
    void chuaGanNhanKhau_nhan403() throws Exception {
        // Ca that va binh thuong: nguoi vua duoc moi vao, Truong chi chua gan ho voi mot nhan khau.
        // /api/v1/me tu tao app_user o trang thai PENDING, nen sub nay co tai khoan ma khong co
        // person_id — dung trang thai cua tai khoan "chuaduyet" tren he thong dang chay.
        MvcResult result = mockMvc.perform(get("/api/v1/me/person").with(thanhVien("sub-chua-ghep")))
                .andReturn();

        // BAN DAU ca nay ghim 409, voi ly le "403 noi sai rang ho bi tu choi vinh vien". Ly le ay
        // giu lai o javadoc cua GlobalExceptionHandler vi no dang doc, nhung no da bi lat boi hai
        // dieu: RFC 9110 dinh nghia 409 cho tinh huong NGUOI GOI tu go duoc roi gui lai — o day
        // chi Truong chi go duoc — va cung mot ma loi dang tra HAI status (hai guard va hop dong
        // deu noi 403). Mot ma, hai status la khiem khuyet nang hon ca hai lua chon.
        assertThat(result.getResponse().getStatus())
                .as("404 ve giao dien la man 'khong tim thay trang' cho mot nguoi VUA DANG NHAP"
                        + " THANH CONG; 403 la thu hai guard va contracts/openapi.yaml deu noi")
                .isEqualTo(403);

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
    // Khiếm khuyết 3 — lỗi kiểm THÂN yêu cầu phải đi qua advice của dự án
    // =====================================================================================

    /**
     * <b>Đã đo trên hệ đang chạy:</b> {@code POST /api/v1/person-claims/existing} với thân thiếu
     * trường bắt buộc trả về
     * <pre>{"type":"about:blank","title":"Dữ liệu không hợp lệ","status":400,
     * "detail":"Dữ liệu gửi lên không hợp lệ. Xem chi tiết ở trường errors.","instance":…}</pre>
     * — tức là câu chữ bảo client đọc {@code errors} trong khi thân <b>không hề có</b>
     * {@code errors}, và cũng không có {@code code} để client rẽ nhánh.
     *
     * <p>Nguyên nhân: {@code spring.mvc.problemdetails.enabled: true} đăng ký
     * {@code ProblemDetailsExceptionHandler} của Spring ở {@code LOWEST_PRECEDENCE}, ngang với
     * advice của dự án, và advice của Spring thắng — nên
     * {@code GlobalExceptionHandler#handleBeanValidation} là mã chết.</p>
     *
     * <p>Ca này phải là test tích hợp vì thứ nó đo là <b>thứ tự giữa hai advice</b>, một tính chất
     * chỉ tồn tại khi cả context Spring MVC được dựng.</p>
     */
    @Test
    @DisplayName("lỗi kiểm thân yêu cầu trả về code VALIDATION_FAILED KÈM mảng errors từng trường")
    void loiKiemThanYeuCau_coCodeVaErrors() throws Exception {
        // Thieu ca personId lan phone — hai truong @NotNull/@NotBlank cua SubmitPersonClaimRequest.
        MvcResult result = mockMvc.perform(post("/api/v1/person-claims/existing")
                        .with(thanhVien("sub-kiem-than"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);

        JsonNode than = than(result);
        assertThat(than.path("code").asText())
                .as("khong co code thi client chi con cach doc chuoi mo ta de re nhanh")
                .isEqualTo("VALIDATION_FAILED");
        assertThat(than.path("type").asText())
                .as("about:blank la dau hieu advice cua Spring da thang advice cua du an")
                .isEqualTo("https://giapha.vn/problems/validation-error");
        assertThat(than.hasNonNull("timestamp")).isTrue();

        JsonNode errors = than.path("errors");
        assertThat(errors.isArray())
                .as("chuoi mo ta bao nguoi doc xem truong errors; truong ay phai co that")
                .isTrue();
        assertThat(errors).isNotEmpty();
        assertThat(errors).allSatisfy(loi -> {
            assertThat(loi.hasNonNull("field")).isTrue();
            assertThat(loi.hasNonNull("message")).isTrue();
        });
    }

    /**
     * Chốt giữ ranh giới của bản vá: nâng thứ tự {@code GlobalExceptionHandler} lên trên advice của
     * Spring <b>không được</b> kéo nó lên trên bốn advice theo ngữ cảnh — chúng khai
     * {@code HIGHEST_PRECEDENCE} và vẫn phải thắng.
     */
    @Test
    @DisplayName("advice theo ngữ cảnh vẫn thắng advice chung — thứ tự mới không nuốt mã của chúng")
    void adviceTheoNguCanh_vanThang() throws Exception {
        // PreconditionRequiredException cua genealogy: PATCH /persons/{id} thieu If-Match.
        // GenealogyExceptionHandler (HIGHEST_PRECEDENCE) phai la noi tra loi, khong phai advice chung.
        UUID chi = insertBranch("Chi Ranh Gioi", "goc.chi_ranh_gioi", null, "CHI");
        authenticateAs("sub-admin-ranh-gioi", "ADMIN");
        UUID nhanKhau = seed(PersonFixtures.living("Nguyễn Văn Ranh Giới").branch(chi).generation(3));

        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .patch("/api/v1/persons/" + nhanKhau)
                                .with(jwt().jwt(b -> b.subject("sub-admin-ranh-gioi"))
                                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(412);
        assertThat(than(result).path("code").asText()).isEqualTo("PRECONDITION_REQUIRED");
    }

    // =====================================================================================
    // Khiếm khuyết 4 — SÁU lớp lỗi client từng bị một handler bắt-tất nuốt thành 500
    //
    // Hồi quy đã đo trên hệ đang chạy: GlobalExceptionHandler được nâng lên
    // HIGHEST_PRECEDENCE + 100 (đúng, để giành lại MethodArgumentNotValidException) NHƯNG vẫn giữ
    // @ExceptionHandler(Exception.class). ExceptionHandlerExceptionResolver duyệt advice THEO THỨ
    // TỰ và dừng ở advice ĐẦU TIÊN có bất kỳ phương thức nào khớp — một handler bắt-tất khớp với
    // mọi thứ — nên advice ấy nuốt trọn danh sách mà ResponseEntityExceptionHandler từng dịch.
    //
    // 2.771 ca vẫn xanh suốt trong khi sáu lớp lỗi này thành 500. Nghĩa là KHÔNG ca nào ghim
    // chiều ấy. Sáu ca dưới đây ghim từng cái một; chúng cố ý KHÔNG gộp thành một
    // @ParameterizedTest vì mỗi ca có một mã HTTP riêng và một cách hỏng riêng, và một ca đỏ phải
    // gọi đúng tên thứ đã hỏng.
    // =====================================================================================

    @Test
    @DisplayName("thân JSON hỏng trả 400, không phải 500 — và có code để client rẽ nhánh")
    void thanJsonHong_tra400() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/person-claims/existing")
                        .with(thanhVien("sub-json-hong"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": "))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("500 noi 'may chu hong' cho mot loi hoan toan cua client")
                .isEqualTo(400);
        assertThat(than(result).path("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    /**
     * <b>Đây là ca có hậu quả nhìn thấy được ngoài đời.</b> {@code ImportBatchController} khai
     * {@code consumes = multipart/form-data} + {@code @RequestPart("file")}, nên sai
     * {@code Content-Type} ở màn tải Excel — một trong hai lỗi client hay gặp nhất tại đó — trả
     * {@code 500}, và người vận hành đọc nó là "máy chủ hỏng" rồi gọi người trực.
     */
    @Test
    @DisplayName("thiếu Content-Type trả 415, không phải 500")
    void thieuContentType_tra415() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/person-claims/existing")
                        .with(thanhVien("sub-thieu-content-type"))
                        .content("{}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(415);
    }

    @Test
    @DisplayName("tham số sai kiểu trả 400 — và thân lỗi KHÔNG nhắc lại giá trị đã gửi")
    void thamSoSaiKieu_tra400() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/public/persons/khong-phai-uuid")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);

        JsonNode than = than(result);
        assertThat(than.path("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(than.path("detail").asText())
                .as("mac dinh cua Spring la \"Failed to convert 'id' with value: 'x'\" — chuoi ay"
                        + " cho nguyen van thu client vua go vao than loi VA vao log ERROR")
                .doesNotContain("khong-phai-uuid")
                .contains("id");
        // `instance` VAN la duong dan yeu cau, va do la dung RFC 7807 — no khong phai duong ro:
        // duong dan nam san trong access log, con `detail` thi khong.
        assertThat(than.path("instance").asText())
                .isEqualTo("/api/v1/public/persons/khong-phai-uuid");
    }

    @Test
    @DisplayName("thiếu tham số bắt buộc trả 400, không phải 500")
    void thieuThamSoBatBuoc_tra400() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/public/persons/search")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(than(result).path("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("Accept không phục vụ được trả 406, không phải 500")
    void acceptKhongPhucVuDuoc_tra406() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/public/persons/search")
                        .param("q", "nguyen")
                        .header("Accept", MediaType.APPLICATION_XML_VALUE))
                .andReturn();

        // Chi ghim STATUS. Khi Accept khong phuc vu duoc thi than loi cung khong phuc vu duoc —
        // Spring co y bo qua thay vi de quy vo tan; day la hanh vi dung cua RFC 9110 §15.5.7.
        assertThat(result.getResponse().getStatus()).isEqualTo(406);
    }

    @Test
    @DisplayName("sai method HTTP trả 405, không phải 500")
    void saiMethod_tra405() throws Exception {
        MvcResult result = mockMvc.perform(post("/actuator/health")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(405);
    }

    /**
     * Chốt còn lại của bản vá: handler bắt-tất <b>vẫn phải tồn tại</b>, chỉ là ở
     * {@code LOWEST_PRECEDENCE} ({@code UnexpectedErrorHandler}). Không có nó thì một ngoại lệ
     * thật rơi xuống {@code BasicErrorController} và mất thân RFC 7807.
     *
     * <p>Không có cách nào ném một ngoại lệ tuỳ ý qua HTTP mà không thêm endpoint giả, nên ca này
     * ghim bằng thứ quan sát được: lớp ấy có mặt trong context và khai đúng thứ tự.</p>
     */
    @Test
    @DisplayName("handler bắt-tất vẫn còn, nhưng ở LOWEST_PRECEDENCE và trong một lớp RIÊNG")
    void handlerBatTat_oCuoiHangVaTachLop() {
        assertThat(context.getBean(vn.giapha.shared.api.UnexpectedErrorHandler.class)).isNotNull();

        assertThat(org.springframework.core.annotation.AnnotationUtils.findAnnotation(
                        vn.giapha.shared.api.UnexpectedErrorHandler.class,
                        org.springframework.core.annotation.Order.class))
                .isNotNull()
                .extracting(org.springframework.core.annotation.Order::value)
                .isEqualTo(org.springframework.core.Ordered.LOWEST_PRECEDENCE);

        assertThat(vn.giapha.shared.api.GlobalExceptionHandler.class.getDeclaredMethods())
                .as("mot @ExceptionHandler(Exception.class) trong advice uu tien cao khop voi MOI"
                        + " ngoai le va nuot ca 20 ngoai le khung — dung dieu vua phai va")
                .noneSatisfy(method -> {
                    var handler = method.getAnnotation(
                            org.springframework.web.bind.annotation.ExceptionHandler.class);
                    assertThat(handler).isNotNull();
                    assertThat(handler.value()).contains(Exception.class);
                });
    }

    // =====================================================================================
    // Khiếm khuyết 5 — thân lỗi kiểm trường KHÔNG được chở giá trị bị từ chối
    // =====================================================================================

    /**
     * <b>Bất biến chưa ai canh.</b> Hôm nay mỗi phần tử {@code errors} có đúng hai khoá
     * {@code field} + {@code message} — đã xác minh: không {@code rejectedValue}, không
     * {@code ${validatedValue}} ở bất kỳ {@code messages*.properties} nào.
     *
     * <p>Nhưng không bài kiểm nào ghim điều đó, nên người kế tiếp "cải tiến trải nghiệm" bằng
     * {@code fieldError.getRejectedValue()} sẽ đưa <b>số điện thoại của một người đang sống</b>
     * vào thân một {@code 400} mà ai cũng lấy được, và {@code SensitiveFieldRedactorTest} sẽ
     * <b>không</b> đỏ — nó chỉ canh nhật ký kiểm toán, không canh thân phản hồi.</p>
     *
     * <p>Ca này chọn đúng trường {@code phone} của {@code SubmitPersonClaimRequest}
     * ({@code @Size(max = 32)}) để thứ bị từ chối <i>là</i> một số điện thoại thật, chứ không phải
     * một chuỗi vô hại.</p>
     */
    @Test
    @DisplayName("errors[] chỉ có field + message — không bao giờ chở giá trị bị từ chối")
    void thanLoiKhongChoGiaTriBiTuChoi() throws Exception {
        String soDienThoai = "0912345678901234567890123456789012345";

        MvcResult result = mockMvc.perform(post("/api/v1/person-claims/existing")
                        .with(thanhVien("sub-khong-ro-ri"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personId\":\"11111111-1111-1111-1111-111111111111\","
                                + "\"phone\":\"" + soDienThoai + "\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);

        JsonNode errors = than(result).path("errors");
        assertThat(errors.isArray()).isTrue();
        assertThat(errors).isNotEmpty();
        assertThat(errors).allSatisfy(loi -> {
            List<String> khoa = new ArrayList<>();
            loi.fieldNames().forEachRemaining(khoa::add);
            assertThat(khoa)
                    .as("them bat ky khoa nao nua — nhat la rejectedValue — la mot duong ro Tang 3")
                    .containsExactlyInAnyOrder("field", "message");
        });

        assertThat(chuoi(result))
                .as("so dien thoai cua nguoi con song khong duoc nam trong mot than loi 400")
                .doesNotContain(soDienThoai);
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
