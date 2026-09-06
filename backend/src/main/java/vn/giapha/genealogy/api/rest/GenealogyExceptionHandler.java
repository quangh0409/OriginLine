package vn.giapha.genealogy.api.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.giapha.genealogy.application.GenealogyConflictException;
import vn.giapha.genealogy.application.TabooNameConflictException;
import vn.giapha.genealogy.domain.TabooConflict;
import vn.giapha.shared.api.ProblemTypes;

/**
 * Bổ sung cho {@code shared.api.GlobalExceptionHandler} ba tình huống mà nó cố ý không biết tới:
 * <b>409</b> xung đột trạng thái, <b>409 kỵ húy</b> (có danh sách va chạm và ghi đè được), và
 * <b>412</b> thiếu {@code If-Match}.
 *
 * <p>Chỉ khai báo handler cho đúng ba kiểu ngoại lệ riêng của context này - <b>không</b> bắt
 * {@code DomainException} chung. Bắt kiểu cha ở đây sẽ nuốt luôn {@code NotFoundException} và
 * {@code ForbiddenException} của shared kernel và làm mọi lỗi 404/403 của toàn hệ thống đi sai
 * đường.</p>
 *
 * <p>Ưu tiên cao nhất để chọn trước advice mặc định khi cả hai cùng khớp.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GenealogyExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GenealogyExceptionHandler.class);

    /**
     * Va chạm <b>kỵ húy</b> (FR-1.6).
     *
     * <p>Phản hồi mang đủ dữ liệu để giao diện dựng hộp thoại xác nhận có ý thức: cụ nào, đời thứ
     * mấy, trùng tên húy nào. {@code overridable} và {@code overrideField} nói cho client biết
     * đây là cảnh báo ghi đè được, không phải lệnh cấm - quyền quyết định thuộc về dòng họ.</p>
     */
    @ExceptionHandler(TabooNameConflictException.class)
    public ProblemDetail handleTabooConflict(TabooNameConflictException ex, HttpServletRequest request) {
        log.info("409 {} - ky huy: {} va cham", request.getRequestURI(), ex.conflicts().size());
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Trùng tên húy bậc trên",
                ex.getMessage(), ex.getCode(), request);
        problem.setProperty("overridable", true);
        problem.setProperty("overrideField", "confirmTabooOverride");
        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (TabooConflict conflict : ex.conflicts()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ancestorPersonId", conflict.ancestorPersonId().toString());
            entry.put("ancestorDisplayName", conflict.ancestorDisplayName());
            entry.put("ancestorGeneration", conflict.ancestorGeneration());
            entry.put("tabooName", conflict.tabooName());
            entry.put("matchedNameType", conflict.matchedNameType() == null
                    ? null : conflict.matchedNameType().name());
            entry.put("matchKind", conflict.matchKind() == null ? null : conflict.matchKind().name());
            entry.put("relationHint", conflict.relationHint());
            conflicts.add(entry);
        }
        problem.setProperty("conflicts", conflicts);
        return problem;
    }

    /** Xung đột trạng thái: khoá lạc quan, đã xoá mềm, hoặc quan hệ tạo chu trình. */
    @ExceptionHandler(GenealogyConflictException.class)
    public ProblemDetail handleConflict(GenealogyConflictException ex, HttpServletRequest request) {
        log.info("409 {} - [{}] {}", request.getRequestURI(), ex.getCode(), ex.getMessage());
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Xung đột dữ liệu", ex.getMessage(),
                ex.getCode(), request);
        problem.setProperty("overridable", false);
        return problem;
    }

    /** Thiếu hoặc sai {@code If-Match} khi sửa hồ sơ - không cho ghi mù lên bản của người khác. */
    @ExceptionHandler(PreconditionRequiredException.class)
    public ProblemDetail handlePrecondition(PreconditionRequiredException ex,
                                            HttpServletRequest request) {
        return problem(HttpStatus.PRECONDITION_FAILED, "Thiếu điều kiện tiên quyết", ex.getMessage(),
                ex.getCode(), request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String code,
                                  HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        problem.setType(status == HttpStatus.CONFLICT ? ProblemTypes.CONFLICT : ProblemTypes.VALIDATION);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
