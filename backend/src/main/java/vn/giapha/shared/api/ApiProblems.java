package vn.giapha.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Xưởng dựng thân lỗi <b>RFC 7807</b> dùng chung cho cả hai nơi sinh ra lỗi trong ứng dụng.
 *
 * <h2>Vì sao phải tách ra khỏi {@link GlobalExceptionHandler}</h2>
 * Một yêu cầu có thể chết ở <b>hai chỗ khác nhau</b>, và trước đây chỉ một trong hai biết viết
 * thân lỗi:
 * <ol>
 *   <li><b>Sau khi đã vào {@code DispatcherServlet}</b> — ngoại lệ ném từ controller/service, do
 *       {@code @RestControllerAdvice} bắt. Đây là đường đi duy nhất mà
 *       {@link GlobalExceptionHandler} nhìn thấy.</li>
 *   <li><b>Trong chuỗi lọc của Spring Security, trước khi tới servlet</b> — thiếu/hỏng JWT hoặc
 *       thiếu vai. {@code ExceptionTranslationFilter} tự trả lời tại chỗ và
 *       <b>{@code DispatcherServlet} không bao giờ được gọi</b>, nên không một
 *       {@code @ExceptionHandler} nào chạy. Mặc định của {@code BearerTokenAuthenticationEntryPoint}
 *       chỉ đặt {@code WWW-Authenticate} rồi kết thúc với {@code Content-Length: 0}.</li>
 * </ol>
 * Xem {@link SecurityProblemSupport} cho nhánh (2). Cả hai nhánh gọi về đây nên thân lỗi có
 * <b>một</b> hình dạng duy nhất — client phân nhánh theo {@code code} (contracts §3) không cần biết
 * lỗi phát sinh ở tầng nào.
 *
 * <p><b>Nguyên tắc riêng tư (Nghị định 13/2023):</b> {@code detail} không bao giờ được mang dữ liệu
 * nhân khẩu. Ở nhánh bảo mật, nó còn không được nói vì sao token hỏng — "hết hạn" và "chữ ký sai"
 * là hai câu trả lời khác nhau và cái thứ hai là thông tin cho kẻ dò.</p>
 */
public final class ApiProblems {

    /** Chưa đăng nhập, hoặc token không dùng được. Trùng nguyên văn mã cũ của tầng advice. */
    public static final String CODE_UNAUTHENTICATED = "UNAUTHENTICATED";

    /** Đã đăng nhập nhưng không đủ thẩm quyền trên tài nguyên. */
    public static final String CODE_FORBIDDEN = "FORBIDDEN";

    /**
     * Token hợp lệ nhưng tài khoản <b>chưa sẵn sàng dùng</b> — chưa có dòng {@code app_user},
     * hoặc có rồi mà chưa được Trưởng chi/Hội đồng ghép với một nhân khẩu trong phả.
     *
     * <p>Hằng số được nhân bản ở đây thay vì {@code import} từ {@code membership}: {@code shared}
     * là hạt nhân chung, nó không được phụ thuộc ngược vào một bounded context (ModularityTests
     * ghim điều này). Giá trị chuỗi là hợp đồng, và hợp đồng thì có quyền có hai bản sao — cả hai
     * bản đều bị {@code GlobalExceptionHandlerTest} ghim để không lệch nhau.</p>
     */
    public static final String CODE_ACCOUNT_NOT_PROVISIONED = "ACCOUNT_NOT_PROVISIONED";

    private ApiProblems() {
    }

    /**
     * Thân lỗi chuẩn.
     *
     * @param code mã lỗi ổn định, máy đọc — <b>thứ duy nhất client được phép phân nhánh theo</b>;
     *             {@code title}/{@code detail} là chuỗi cho người đọc và có thể đổi bất cứ lúc nào
     */
    public static ProblemDetail of(HttpStatusCode status, URI type, String title, String detail,
                                   String code, String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        problem.setType(type);
        problem.setTitle(title);
        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    /** Biến thể tiện dụng cho tầng MVC, lấy {@code instance} từ URI của yêu cầu. */
    public static ProblemDetail of(HttpStatus status, URI type, String title, String detail,
                                   String code, HttpServletRequest request) {
        return of(status, type, title, detail, code,
                request == null ? null : request.getRequestURI());
    }
}
