package vn.giapha.events.api.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.giapha.events.application.EventConflictException;
import vn.giapha.shared.api.ProblemTypes;

/**
 * Bổ sung cho {@code shared.api.GlobalExceptionHandler} hai tình huống mà nó cố ý không biết tới:
 * <b>409</b> xung đột trạng thái khi ghi sự kiện, và <b>412</b> thiếu {@code If-Match}.
 *
 * <p>Chỉ khai báo handler cho đúng hai kiểu ngoại lệ riêng của context này — <b>không</b> bắt
 * {@code DomainException} chung. Bắt kiểu cha ở đây sẽ nuốt luôn {@code NotFoundException} và
 * {@code ForbiddenException} của shared kernel và làm mọi lỗi 404/403 của toàn hệ thống đi sai
 * đường. Cùng kỷ luật với {@code GenealogyExceptionHandler}.</p>
 *
 * <p>Khoá lạc quan <i>thật sự va chạm</i> ({@code ObjectOptimisticLockingFailureException} do
 * Hibernate ném lúc flush) đã có handler 409 ở {@code GlobalExceptionHandler} — không nhân bản nó
 * ở đây.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EventsExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EventsExceptionHandler.class);

    /** Sự kiện đã xoá mềm, hoặc ngày giỗ phải sửa trên hồ sơ nhân khẩu. */
    @ExceptionHandler(EventConflictException.class)
    public ProblemDetail handleConflict(EventConflictException ex, HttpServletRequest request) {
        log.info("409 {} - [{}] {}", request.getRequestURI(), ex.getCode(), ex.getMessage());
        return problem(HttpStatus.CONFLICT, ProblemTypes.CONFLICT, "Xung đột dữ liệu",
                ex.getMessage(), ex.getCode(), request);
    }

    /** Thiếu hoặc sai {@code If-Match} — không cho ghi mù lên bản của người khác. */
    @ExceptionHandler(EventPreconditionRequiredException.class)
    public ProblemDetail handlePrecondition(EventPreconditionRequiredException ex,
                                            HttpServletRequest request) {
        log.info("412 {} - {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.PRECONDITION_FAILED, ProblemTypes.VALIDATION,
                "Thiếu điều kiện tiên quyết", ex.getMessage(), ex.getCode(), request);
    }

    private static ProblemDetail problem(HttpStatus status, URI type, String title, String detail,
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
