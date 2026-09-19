package vn.giapha.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Dịch mọi ngoại lệ của tầng API sang <b>RFC 7807 Problem Details</b> (TDD: bắt buộc cho toàn bộ
 * {@code /api/v1}).
 *
 * <p>Mọi phản hồi lỗi đều có thêm hai thuộc tính mở rộng: {@code code} (mã lỗi ổn định để FE hiển
 * thị thông điệp song ngữ VI/EN) và {@code timestamp}.</p>
 *
 * <p><b>Nguyên tắc riêng tư (Nghị định 13/2023):</b> thông điệp lỗi không được rò rỉ dữ liệu người
 * còn sống. Lỗi 500 chỉ trả câu chung chung, chi tiết nằm ở log phía máy chủ.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest request) {
        if (ApiProblems.CODE_ACCOUNT_NOT_PROVISIONED.equals(ex.getCode())) {
            return accountNotProvisioned(ex, request);
        }
        log.debug("404 {} - {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND, "Không tìm thấy dữ liệu",
                ex.getMessage(), ex.getCode(), request);
    }

    /**
     * <b>{@code 409}, không phải {@code 404}</b> — tài khoản đăng nhập được nhưng chưa được ghép
     * với một nhân khẩu trong phả.
     *
     * <h2>Vì sao ba mã kia đều sai</h2>
     * <ul>
     *   <li>{@code 404} nói "không có gì ở đây". Sai hẳn: người gọi <b>đã đăng nhập thành công</b>,
     *       tài nguyên có thật, và giao diện vẽ ra màn "không tìm thấy trang" cho một người vừa
     *       nhập đúng mật khẩu. Đây chính là khiếm khuyết đang vá.</li>
     *   <li>{@code 403} nói "bạn không được phép" — hàm ý một quyết định đã dứt điểm. Nhưng người
     *       này <b>sẽ</b> được phép ngay khi Trưởng chi bấm nút ghép; không có gì bị từ chối cả.</li>
     *   <li>{@code 401} sai vì token hoàn toàn hợp lệ; trả 401 sẽ khiến client đá người dùng về
     *       trang đăng nhập và họ đăng nhập lại mãi mà không bao giờ thoát khỏi vòng lặp.</li>
     * </ul>
     *
     * <p>{@code 409 Conflict} nói đúng điều đang xảy ra: yêu cầu hợp lệ nhưng <b>xung đột với
     * trạng thái hiện tại của tài nguyên</b> (RFC 9110 §15.5.10) — và trạng thái ấy thay đổi được
     * bằng một hành động ngoài luồng yêu cầu này. Nó cũng là mã duy nhất trong bốn mã mà một
     * client cache/proxy không diễn giải nhầm.</p>
     *
     * <p>Thân lỗi mang thêm {@code nextStep} — <b>mã máy đọc</b>, không phải chuỗi mô tả — để giao
     * diện rẽ nhánh sang màn "chờ ghép vào phả" mà không phải đọc tiếng Việt trong {@code detail}.</p>
     *
     * <p><b>Đã biết và cố ý chưa đụng tới:</b> {@code BranchScopeGuard.requireProvisionedAccount}
     * và {@code ImportScopeGuard} ném cùng mã này dưới dạng {@code ForbiddenException} ⇒ vẫn
     * {@code 403}. Hai chỗ đó nằm trong vùng của agent khác và có test ghim {@code 403}; việc gộp
     * về một mã HTTP duy nhất đã được báo cáo cho chủ hợp đồng.</p>
     */
    private ProblemDetail accountNotProvisioned(DomainException ex, HttpServletRequest request) {
        log.info("409 {} - tai khoan chua san sang: {}", request.getRequestURI(), ex.getMessage());
        ProblemDetail problem = problem(HttpStatus.CONFLICT, ProblemTypes.ACCOUNT_NOT_PROVISIONED,
                "Tài khoản chưa được ghép vào gia phả",
                "Tài khoản đã đăng nhập nhưng chưa được ghép với một nhân khẩu trong phả."
                        + " Hãy liên hệ Trưởng chi hoặc Hội đồng Tộc biểu để được ghép.",
                ApiProblems.CODE_ACCOUNT_NOT_PROVISIONED, request);
        problem.setProperty("nextStep", "CONTACT_BRANCH_HEAD");
        return problem;
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        log.warn("403 {} - {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, ProblemTypes.FORBIDDEN, "Không đủ thẩm quyền",
                ex.getMessage(), ex.getCode(), request);
    }

    /** Vi phạm quy tắc nghiệp vụ (kỵ húy, vòng lặp quan hệ, ...) - tra ve 422. */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex, HttpServletRequest request) {
        log.info("422 {} - [{}] {}", request.getRequestURI(), ex.getCode(), ex.getMessage());
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ProblemTypes.BUSINESS_RULE,
                "Vi phạm quy tắc nghiệp vụ", ex.getMessage(), ex.getCode(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION,
                "Dữ liệu gửi lên không hợp lệ", "Một hoặc nhiều trường không hợp lệ",
                "VALIDATION_FAILED", request);
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(Map.of(
                    "field", fieldError.getField(),
                    "message", fieldError.getDefaultMessage() == null ? "" : fieldError.getDefaultMessage()));
        }
        for (var globalError : ex.getBindingResult().getGlobalErrors()) {
            errors.add(Map.of(
                    "field", globalError.getObjectName(),
                    "message", globalError.getDefaultMessage() == null ? "" : globalError.getDefaultMessage()));
        }
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION,
                "Dữ liệu gửi lên không hợp lệ", "Một hoặc nhiều tham số không hợp lệ",
                "VALIDATION_FAILED", request);
        List<Map<String, String>> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.add(Map.of(
                    "field", String.valueOf(violation.getPropertyPath()),
                    "message", violation.getMessage()));
        }
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION, "Dữ liệu gửi lên không hợp lệ",
                ex.getMessage(), "VALIDATION_FAILED", request);
    }

    /**
     * <b>Nhánh này gần như không bao giờ chạy, và đó không phải lỗi.</b>
     *
     * <p>Tuyệt đại đa số {@code 401} của hệ thống sinh ra <i>trong chuỗi lọc của Spring Security</i>,
     * trước khi {@code DispatcherServlet} được gọi — nên không một {@code @ExceptionHandler} nào
     * có cơ hội chạy. Đó chính là lý do mọi {@code 401} từng trả {@code Content-Length: 0}. Chỗ vá
     * thật nằm ở {@link SecurityProblemSupport}, được cắm vào
     * {@code exceptionHandling}/{@code oauth2ResourceServer} trong {@code SecurityConfig}.</p>
     *
     * <p>Giữ lại nhánh này cho phần dư: một {@code AuthenticationException} ném ra từ bên trong
     * controller hay service (ví dụ khi đọc token thủ công). Hai nơi trả cùng một thân lỗi vì cùng
     * đi qua {@link ApiProblems}.</p>
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.debug("401 {} - {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHORIZED, "Chưa đăng nhập",
                "Yêu cầu cần một JWT hợp lệ do Keycloak cấp", ApiProblems.CODE_UNAUTHENTICATED,
                request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("403 {} - {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, ProblemTypes.FORBIDDEN, "Không đủ thẩm quyền",
                "Tài khoản không có quyền trên tài nguyên này", ApiProblems.CODE_FORBIDDEN, request);
    }

    /** Xung đột khoá lạc quan {@code @Version} - hai nguoi cung sua mot ho so nhan khau. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                              HttpServletRequest request) {
        log.info("409 {} - {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.CONFLICT, ProblemTypes.CONFLICT, "Dữ liệu đã bị người khác thay đổi",
                "Bản ghi đã được cập nhật bởi người dùng khác, hãy tải lại và thử lại",
                "OPTIMISTIC_LOCK_CONFLICT", request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ProblemDetail handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND, "Không tìm thấy endpoint",
                ex.getRequestURL() + " không tồn tại", "NOT_FOUND", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("500 {} - loi khong luong truoc", request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ProblemTypes.INTERNAL, "Lỗi hệ thống",
                "Đã xảy ra lỗi không mong muốn. Vui lòng thử lại sau.", "INTERNAL_ERROR", request);
    }

    /** Uỷ quyền cho {@link ApiProblems} để thân lỗi ở đây và ở tầng lọc bảo mật không lệch nhau. */
    private ProblemDetail problem(HttpStatus status, URI type, String title, String detail,
                                  String code, HttpServletRequest request) {
        return ApiProblems.of(status, type, title, detail, code, request);
    }
}
