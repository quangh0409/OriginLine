package vn.giapha.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.giapha.audit.application.AuditRequestContext;
import vn.giapha.audit.domain.RequestFingerprint;
import vn.giapha.audit.infrastructure.web.AuditInterceptor;

/**
 * Interceptor nạp dấu vết HTTP cho <b>đường ghi</b>: ba cột {@code ip_address},
 * {@code user_agent}, {@code request_id} mà W2 đành để trống.
 *
 * <p>Hai điều được kiểm kỹ nhất: lấy đúng IP client sau reverse proxy, và <b>dọn sạch</b> trong mọi
 * trường hợp kể cả khi handler ném ngoại lệ.</p>
 */
class AuditInterceptorTest {

    private final AuditInterceptor interceptor = new AuditInterceptor();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void don() {
        AuditRequestContext.clear();
        MDC.clear();
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("10.0.0.5");
        return request;
    }

    @Nested
    @DisplayName("Địa chỉ IP thật sau reverse proxy")
    class DiaChiIp {

        @Test
        @DisplayName("Ưu tiên X-Forwarded-For, rồi X-Real-IP, rồi remoteAddr")
        void thuTuUuTien() {
            // getRemoteAddr() sau Nginx/Traefik luon la IP cua chinh proxy.
            MockHttpServletRequest coXff = request("/api/v1/persons");
            coXff.addHeader("X-Forwarded-For", "203.0.113.7, 70.41.3.18");
            coXff.addHeader("X-Real-IP", "198.51.100.9");
            interceptor.preHandle(coXff, response, null);
            assertThat(AuditRequestContext.current().ipAddress()).startsWith("203.0.113.7");
            AuditRequestContext.clear();

            MockHttpServletRequest chiCoRealIp = request("/api/v1/persons");
            chiCoRealIp.addHeader("X-Real-IP", "198.51.100.9");
            interceptor.preHandle(chiCoRealIp, response, null);
            assertThat(AuditRequestContext.current().ipAddress()).isEqualTo("198.51.100.9");
            AuditRequestContext.clear();

            interceptor.preHandle(request("/api/v1/persons"), response, null);
            assertThat(AuditRequestContext.current().ipAddress()).isEqualTo("10.0.0.5");
        }

        @Test
        @DisplayName("User-Agent được ghi lại nguyên văn")
        void ghiUserAgent() {
            MockHttpServletRequest request = request("/api/v1/persons");
            request.addHeader("User-Agent", "GiaPhaPWA/1.0 (Android 14)");

            interceptor.preHandle(request, response, null);

            assertThat(AuditRequestContext.current().userAgent())
                    .isEqualTo("GiaPhaPWA/1.0 (Android 14)");
        }
    }

    @Nested
    @DisplayName("Mã tương quan nối audit với log")
    class MaTuongQuan {

        @Test
        @DisplayName("Tôn trọng X-Request-Id do client gửi lên")
        void tonTrongMaCuaClient() {
            MockHttpServletRequest request = request("/api/v1/persons");
            request.addHeader(AuditInterceptor.HEADER_REQUEST_ID, "req-tu-client");

            interceptor.preHandle(request, response, null);

            assertThat(AuditRequestContext.current().requestId()).isEqualTo("req-tu-client");
            assertThat(MDC.get(AuditInterceptor.MDC_REQUEST_ID)).isEqualTo("req-tu-client");
            // Tra lai trong header phan hoi de nguoi dung bao loi kem duoc ma.
            assertThat(response.getHeader(AuditInterceptor.HEADER_REQUEST_ID))
                    .isEqualTo("req-tu-client");
        }

        @Test
        @DisplayName("Không có thì sinh một mã mới")
        void sinhMaMoi() {
            interceptor.preHandle(request("/api/v1/persons"), response, null);

            assertThat(AuditRequestContext.current().requestId()).isNotBlank();
            assertThat(MDC.get(AuditInterceptor.MDC_REQUEST_ID)).isNotBlank();
        }

        @Test
        @DisplayName("Mã quá dài bị cắt cho vừa VARCHAR(64)")
        void catMaQuaDai() {
            MockHttpServletRequest request = request("/api/v1/persons");
            request.addHeader(AuditInterceptor.HEADER_REQUEST_ID, "R".repeat(200));

            interceptor.preHandle(request, response, null);

            assertThat(AuditRequestContext.current().requestId())
                    .hasSize(RequestFingerprint.MAX_REQUEST_ID);
        }
    }

    @Nested
    @DisplayName("Dọn ngữ cảnh")
    class DonNguCanh {

        @Test
        @DisplayName("afterCompletion dọn sạch, kể cả khi handler ném ngoại lệ")
        void donKeCaKhiNemNgoaiLe() {
            // Servlet container tai su dung thread. Quen don thi request ke tiep tren cung thread
            // se duoc ghi audit voi IP va User-Agent cua nguoi dung truoc — vua sai du lieu vua sai
            // ve phap ly.
            MockHttpServletRequest request = request("/api/v1/persons");
            request.addHeader("X-Forwarded-For", "203.0.113.7");
            interceptor.preHandle(request, response, null);
            assertThat(AuditRequestContext.current().isEmpty()).isFalse();

            interceptor.afterCompletion(request, response, null,
                    new IllegalStateException("handler no"));

            assertThat(AuditRequestContext.current().isEmpty()).isTrue();
            assertThat(MDC.get(AuditInterceptor.MDC_REQUEST_ID)).isNull();
        }

        @Test
        @DisplayName("Hai request nối tiếp trên cùng thread không lẫn dấu vết của nhau")
        void haiRequestNoiTiepKhongLan() {
            MockHttpServletRequest thu1 = request("/api/v1/persons");
            thu1.addHeader("X-Forwarded-For", "203.0.113.7");
            interceptor.preHandle(thu1, response, null);
            interceptor.afterCompletion(thu1, response, null, null);

            MockHttpServletRequest thu2 = new MockHttpServletRequest("GET", "/api/v1/persons");
            thu2.setRemoteAddr("198.51.100.2");
            interceptor.preHandle(thu2, new MockHttpServletResponse(), null);

            assertThat(AuditRequestContext.current().ipAddress()).isEqualTo("198.51.100.2");
        }
    }

    @Test
    @DisplayName("Bỏ qua endpoint thăm dò sức khoẻ — ghi vết ở đó chỉ tạo nhiễu")
    void boQuaActuator() {
        assertThat(interceptor.preHandle(request("/actuator/health"), response, null)).isTrue();
        assertThat(AuditRequestContext.current().isEmpty()).isTrue();

        assertThat(interceptor.preHandle(request("/actuator/prometheus"), response, null)).isTrue();
        assertThat(AuditRequestContext.current().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("KHÔNG đọc thân request, tham số truy vấn hay header Authorization")
    void khongDocDuLieuTang3() {
        // Interceptor chi lay ba mau sieu du lieu. So dien thoai, email va anh khong bao gio di
        // qua day.
        MockHttpServletRequest request = request("/api/v1/persons?phone=0912345678");
        request.setParameter("phone", "0912345678");
        request.addHeader("Authorization", "Bearer eyJhbGciOi...");
        request.setContent("{\"phone\":\"0912345678\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        interceptor.preHandle(request, response, null);

        RequestFingerprint dau = AuditRequestContext.current();
        assertThat(String.valueOf(dau)).doesNotContain("0912345678");
        assertThat(String.valueOf(dau)).doesNotContain("Bearer");
    }
}
