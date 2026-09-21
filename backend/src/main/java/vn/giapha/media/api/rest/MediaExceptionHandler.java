package vn.giapha.media.api.rest;

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
import vn.giapha.media.domain.MediaConflictException;
import vn.giapha.media.domain.MediaProblemCodes;
import vn.giapha.media.domain.MediaRejectedException;
import vn.giapha.media.infrastructure.minio.MinioObjectStorageAdapter.ObjectStorageException;
import vn.giapha.shared.api.ApiProblems;
import vn.giapha.shared.api.ProblemTypes;

/**
 * Bổ sung cho {@code shared.api.GlobalExceptionHandler} ba tình huống riêng của kho tệp.
 *
 * <h2>Chỉ khai handler cho ĐÚNG hai kiểu ngoại lệ riêng của context</h2>
 * Tuyệt đối <b>không</b> bắt {@code DomainException} chung ở đây: {@code @RestControllerAdvice}
 * mặc định áp cho <i>toàn ứng dụng</i>, nên bắt kiểu cha sẽ nuốt luôn
 * {@code NotFoundException}/{@code ForbiddenException} của shared kernel và làm mọi lỗi 404/403
 * của <i>mọi</i> context đi sai đường. Cảnh báo này đã có ở {@code ContentExceptionHandler} và
 * {@code MembershipExceptionHandler}; nó đáng nhắc lại vì triệu chứng — "tự dưng 404 thành 422" —
 * không chỉ về file này.
 *
 * <h2>Vì sao 413 và 422 là hai mã khác nhau, không gộp</h2>
 * Vì chúng dẫn người dùng đi hai đường khác nhau:
 * <ul>
 *   <li><b>413</b> — tệp quá lớn. Việc phải làm: <i>nén hoặc xuất lại nhỏ hơn</i>. Mã này cũng là
 *       mã mà mọi proxy hiểu, nên một tầng hạ tầng phía trước cũng nói đúng câu.</li>
 *   <li><b>422</b> — tệp đúng kích thước nhưng <i>sai kiểu</i>, hoặc <i>quá dài</i>, hoặc
 *       <i>không đọc được thời lượng</i>. Việc phải làm hoàn toàn khác: đổi định dạng, cắt ngắn,
 *       hay xuất lại bằng công cụ khác. Trả 400 ở đây là nói dối — thân yêu cầu hợp lệ hoàn
 *       toàn.</li>
 * </ul>
 * Như mọi nơi khác trong sản phẩm, giao diện phân nhánh theo {@code code}, không theo mã HTTP.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public class MediaExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MediaExceptionHandler.class);

    /**
     * Trạng thái phía máy chủ không cho phép — HTTP <b>409</b>.
     *
     * <p>Mặc định của {@code GlobalExceptionHandler} cho một {@code DomainException} là
     * <b>422</b>, và 422 là <i>sai</i> ở bốn tình huống này: nó nói "bạn gửi sai, hãy sửa body",
     * trong khi thân yêu cầu hoàn toàn hợp lệ và thứ đã đổi là trạng thái phía máy chủ. Client
     * phải <b>tải lại rồi xem</b>, không phải sửa JSON.</p>
     */
    @ExceptionHandler(MediaConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(MediaConflictException ex,
                                                        HttpServletRequest request) {
        log.info("409 {} - xung dot trang thai kho tep: {}", request.getRequestURI(), ex.getCode());
        ProblemDetail problem = ApiProblems.of(HttpStatus.CONFLICT, ProblemTypes.CONFLICT,
                "Trạng thái đã thay đổi", ex.getMessage(), ex.getCode(), request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /** Tệp bị từ chối vì chính nó. {@code 413} khi quá khổ, {@code 422} cho mọi lý do còn lại. */
    @ExceptionHandler(MediaRejectedException.class)
    public ResponseEntity<ProblemDetail> handleRejected(MediaRejectedException ex,
                                                        HttpServletRequest request) {
        HttpStatus status = MediaProblemCodes.MEDIA_TOO_LARGE.equals(ex.getCode())
                ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.UNPROCESSABLE_ENTITY;
        log.info("{} {} - tu choi tep: {}", status.value(), request.getRequestURI(), ex.getCode());
        ProblemDetail problem = ApiProblems.of(status, ProblemTypes.VALIDATION,
                "Tệp không hợp lệ", ex.getMessage(), ex.getCode(), request);
        return ResponseEntity.status(status).body(problem);
    }

    /**
     * Kho đối tượng hỏng — {@code 503}, <b>không</b> {@code 500}.
     *
     * <p>Phân biệt này không phải để đẹp mã: 503 nói "thử lại sau" và mọi client, proxy, bảng điều
     * khiển đều hiểu thế, trong khi 500 nói "mã có lỗi" và sẽ dẫn người trực đi đọc stack trace
     * của backend thay vì đi xem MinIO còn sống không. Kho tệp là phụ thuộc <i>ngoài</i>, và phần
     * còn lại của hệ thống gia phả vẫn chạy khi nó hỏng.</p>
     */
    @ExceptionHandler(ObjectStorageException.class)
    public ResponseEntity<ProblemDetail> handleStorage(ObjectStorageException ex,
                                                       HttpServletRequest request) {
        log.error("503 {} - kho doi tuong khong phan hoi", request.getRequestURI(), ex);
        ProblemDetail problem = ApiProblems.of(HttpStatus.SERVICE_UNAVAILABLE,
                ProblemTypes.INTERNAL, "Kho tệp tạm thời không phản hồi",
                "Không kết nối được tới kho ảnh/video. Phần còn lại của hệ thống vẫn dùng bình"
                        + " thường; hãy thử lại sau ít phút.",
                "MEDIA_STORAGE_UNAVAILABLE", request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }
}
