package vn.giapha.shared.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Viết thân <b>RFC 7807</b> cho hai lỗi sinh ra <i>bên trong chuỗi lọc bảo mật</i>: {@code 401} khi
 * thiếu/hỏng JWT và {@code 403} khi thiếu vai ở tầng {@code authorizeHttpRequests}.
 *
 * <h2>Khiếm khuyết mà lớp này vá</h2>
 * {@link GlobalExceptionHandler} đã có nhánh cho {@code AuthenticationException} với mã
 * {@code UNAUTHENTICATED} từ lâu, nhưng nhánh ấy <b>không bao giờ chạy</b>. Lý do không nằm ở
 * {@code @RestControllerAdvice}: một yêu cầu không mang token bị {@code BearerTokenAuthenticationFilter}
 * chặn lại, {@code ExceptionTranslationFilter} gọi thẳng {@code AuthenticationEntryPoint} và
 * <b>kết thúc phản hồi ngay tại đó</b>. {@code DispatcherServlet} không hề được gọi, nên không một
 * {@code @ExceptionHandler} nào có cơ hội chạy. Mặc định
 * ({@code BearerTokenAuthenticationEntryPoint}) chỉ đặt header {@code WWW-Authenticate} rồi trả
 * {@code Content-Length: 0} — đúng RFC 6750, nhưng trái hợp đồng của dự án.
 *
 * <p>Hệ quả đo được trên hệ thống đang chạy: {@code /api/v1/tree}, {@code /api/v1/directory},
 * {@code /api/v1/persons/{id}} và {@code /api/v1/audit-logs} trả thân rỗng, không
 * {@code Content-Type}. Client làm đúng hợp đồng (rẽ nhánh theo {@code code}) đọc ra
 * {@code "UNKNOWN"} và rơi xuống nhánh lỗi chung; frontend hiện tại thoát nạn chỉ vì nó rẽ theo mã
 * HTTP — một bất biến không ai bảo đảm.
 *
 * <h2>Vì sao tự tuần tự hoá bằng {@link ObjectMapper} chứ không trả về một đối tượng</h2>
 * Ở tầng lọc chưa có {@code HttpMessageConverter} nào tham gia; chỉ còn
 * {@link HttpServletResponse} trần. Dùng chính {@code ObjectMapper} do Spring Boot cấu hình để bản
 * JSON ở đây <b>giống từng ký tự</b> bản do tầng advice sinh ra (Boot đã đăng ký
 * {@code ProblemDetailJacksonMixin}, nên {@code type}/{@code title}/{@code status}/{@code detail}/
 * {@code instance} cùng các thuộc tính mở rộng được trải phẳng y hệt).
 *
 * <h2>Header {@code WWW-Authenticate} vẫn do Spring Security dựng</h2>
 * Lớp này <b>thêm</b> thân lỗi chứ không thay thế thử thách RFC 6750: nó uỷ quyền cho
 * {@code BearerTokenAuthenticationEntryPoint} đặt header trước, rồi mới ghi JSON. Bỏ header đi là
 * làm hỏng mọi client OAuth2 chuẩn (kể cả bộ làm mới token của frontend).
 *
 * <p>Thân lỗi thì cố ý <b>không</b> nói vì sao token hỏng — "hết hạn", "sai chữ ký", "sai issuer"
 * là ba câu khác nhau và client của dự án không rẽ nhánh theo chúng. Chi tiết nằm ở log máy chủ
 * mức {@code debug}.</p>
 */
@Component
public class SecurityProblemSupport implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityProblemSupport.class);

    /** Giữ nguyên thử thách {@code WWW-Authenticate: Bearer ...} của RFC 6750. */
    private final AuthenticationEntryPoint bearerChallenge = new BearerTokenAuthenticationEntryPoint();

    private final ObjectMapper objectMapper;

    public SecurityProblemSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Thiếu token, token hỏng, hoặc token hết hạn — mọi trường hợp cùng một câu trả lời. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.debug("401 {} - {}", request.getRequestURI(), authException.getMessage());
        try {
            bearerChallenge.commence(request, response, authException);
        } catch (jakarta.servlet.ServletException ex) {
            // BearerTokenAuthenticationEntryPoint chi dat header, thuc te khong nem. Bat de chu ky
            // cua phuong thuc nay khong phai keo theo ServletException.
            log.warn("Khong dat duoc header WWW-Authenticate", ex);
        }
        write(request, response, HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHORIZED,
                "Chưa đăng nhập", "Yêu cầu cần một JWT hợp lệ do Keycloak cấp",
                ApiProblems.CODE_UNAUTHENTICATED);
    }

    /**
     * Đã xác thực nhưng không qua được luật ở {@code authorizeHttpRequests}.
     *
     * <p>Không đụng tới {@code AccessDeniedException} ném từ {@code @PreAuthorize} bên trong
     * controller: cái đó xảy ra sau khi đã vào {@code DispatcherServlet} nên
     * {@link GlobalExceptionHandler} bắt trước, và vẫn giữ nguyên hành vi cũ.</p>
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("403 {} - {}", request.getRequestURI(), accessDeniedException.getMessage());
        write(request, response, HttpStatus.FORBIDDEN, ProblemTypes.FORBIDDEN,
                "Không đủ thẩm quyền", "Tài khoản không có quyền trên tài nguyên này",
                ApiProblems.CODE_FORBIDDEN);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                       java.net.URI type, String title, String detail, String code)
            throws IOException {
        if (response.isCommitted()) {
            // Đã có ai đó viết xong phản hồi (ví dụ một filter phía trước). Ghi đè lúc này chỉ tạo
            // ra JSON rác nối đuôi nội dung cũ.
            log.warn("Phan hoi da commit truoc khi kip viet than loi {} cho {}", status,
                    request.getRequestURI());
            return;
        }
        ProblemDetail problem = ApiProblems.of(status, type, title, detail, code,
                request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
