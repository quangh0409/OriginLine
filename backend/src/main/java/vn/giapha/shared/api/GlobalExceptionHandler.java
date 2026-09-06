package vn.giapha.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
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
        log.debug("404 {} - {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND, "Không tìm thấy dữ liệu",
                ex.getMessage(), ex.getCode(), request);
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

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.debug("401 {} - {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHORIZED, "Chưa đăng nhập",
                "Yêu cầu cần một JWT hợp lệ do Keycloak cấp", "UNAUTHENTICATED", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("403 {} - {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, ProblemTypes.FORBIDDEN, "Không đủ thẩm quyền",
                "Tài khoản không có quyền trên tài nguyên này", "FORBIDDEN", request);
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

    private ProblemDetail problem(HttpStatus status, URI type, String title, String detail,
                                  String code, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        problem.setType(type);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
