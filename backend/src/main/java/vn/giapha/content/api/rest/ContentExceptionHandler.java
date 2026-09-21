package vn.giapha.content.api.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.giapha.content.application.ContentConflictException;
import vn.giapha.content.application.ContentPreconditionException;
import vn.giapha.shared.api.ApiProblems;
import vn.giapha.shared.api.ProblemTypes;

/**
 * Bổ sung cho {@code shared.api.GlobalExceptionHandler} hai tình huống mà nó cố ý không biết tới:
 * <b>409</b> xung đột trạng thái/phiên bản và <b>412</b> thiếu {@code If-Match}.
 *
 * <h2>Chỉ khai handler cho ĐÚNG hai kiểu ngoại lệ riêng của context</h2>
 * Tuyệt đối <b>không</b> bắt {@code DomainException} chung ở đây: {@code @RestControllerAdvice}
 * mặc định áp cho <i>toàn ứng dụng</i>, nên bắt kiểu cha sẽ nuốt luôn
 * {@code NotFoundException}/{@code ForbiddenException} của shared kernel và làm mọi lỗi 404/403
 * của <i>mọi</i> context đi sai đường. Đây là cùng cảnh báo đã ghi ở
 * {@code MembershipExceptionHandler}, và nó đáng nhắc lại vì triệu chứng — "tự dưng 404 thành
 * 409" — không chỉ về file này.
 *
 * <h2>Vì sao 409 chứ không 422</h2>
 * 422 nói "bạn gửi sai, hãy sửa body". Ở đây thân yêu cầu hoàn toàn hợp lệ; thứ đã đổi là
 * <i>trạng thái phía máy chủ</i>, gần như luôn vì hai người mở cùng một bài và người kia bấm
 * trước. Hành động đúng của client là <b>tải lại rồi xem</b>, và hai mã trạng thái ấy dẫn tới hai
 * màn hình khác nhau.
 *
 * <p>Như mọi nơi khác trong sản phẩm, giao diện phân nhánh theo {@code code}
 * ({@code CONTENT_CLOSED} · {@code OPTIMISTIC_LOCK_CONFLICT} · {@code PRECONDITION_REQUIRED}),
 * không theo mã HTTP — mã HTTP là để proxy, cache và công cụ giám sát hiểu đúng.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ContentExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ContentExceptionHandler.class);

    /** Trạng thái đã đóng, hoặc phiên bản lệch — HTTP <b>409</b>. */
    @ExceptionHandler(ContentConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ContentConflictException ex,
                                                        HttpServletRequest request) {
        log.info("409 {} - xung dot trang thai noi dung: {}", request.getRequestURI(), ex.getCode());
        ProblemDetail problem = ApiProblems.of(HttpStatus.CONFLICT, ProblemTypes.CONFLICT,
                "Nội dung đã thay đổi", ex.getMessage(), ex.getCode(), request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * Thiếu hoặc sai {@code If-Match} — HTTP <b>412</b>.
     *
     * <p>Không gộp vào 400: yêu cầu đúng cú pháp, chỉ thiếu <i>điều kiện tiên quyết</i> của giao
     * thức. Client sửa bằng cách đọc lại tài nguyên để lấy {@code ETag}, không phải bằng cách sửa
     * thân yêu cầu.</p>
     */
    @ExceptionHandler(ContentPreconditionException.class)
    public ResponseEntity<ProblemDetail> handlePrecondition(ContentPreconditionException ex,
                                                            HttpServletRequest request) {
        log.info("412 {} - thieu dieu kien tien quyet", request.getRequestURI());
        ProblemDetail problem = ApiProblems.of(HttpStatus.PRECONDITION_FAILED,
                ProblemTypes.BUSINESS_RULE, "Thiếu điều kiện tiên quyết", ex.getMessage(),
                ex.getCode(), request);
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(problem);
    }
}
