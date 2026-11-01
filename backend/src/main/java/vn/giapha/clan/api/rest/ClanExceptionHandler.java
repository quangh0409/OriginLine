package vn.giapha.clan.api.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.giapha.clan.application.ClanConflictException;
import vn.giapha.clan.application.ClanPreconditionException;
import vn.giapha.shared.api.ApiProblems;
import vn.giapha.shared.api.ProblemTypes;

/**
 * Hai ánh xạ riêng của context {@code clan}: {@code 409} xung đột và {@code 412} thiếu
 * {@code If-Match}. Mọi thứ khác ({@code 404 CLAN_NOT_INITIALIZED} là {@code NotFoundException},
 * {@code 422} là {@code DomainException}) đã có đường ở {@code GlobalExceptionHandler}.
 *
 * <p>Phải đứng <b>trước</b> {@code GlobalExceptionHandler}: cả hai ngoại lệ ở đây đều là
 * {@code DomainException}, và handler chung sẽ nuốt chúng thành {@code 422}.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ClanExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ClanExceptionHandler.class);

    @ExceptionHandler(ClanConflictException.class)
    public ProblemDetail handleConflict(ClanConflictException ex, HttpServletRequest request) {
        log.info("409 {} - [{}] {}", request.getRequestURI(), ex.getCode(), ex.getMessage());
        return ApiProblems.of(HttpStatus.CONFLICT, ProblemTypes.CONFLICT, "Xung đột dữ liệu",
                ex.getMessage(), ex.getCode(), request);
    }

    @ExceptionHandler(ClanPreconditionException.class)
    public ProblemDetail handlePrecondition(ClanPreconditionException ex,
                                            HttpServletRequest request) {
        log.info("412 {} - {}", request.getRequestURI(), ex.getMessage());
        return ApiProblems.of(HttpStatus.PRECONDITION_FAILED, ProblemTypes.VALIDATION,
                "Thiếu điều kiện tiên quyết", ex.getMessage(), ex.getCode(), request);
    }
}
