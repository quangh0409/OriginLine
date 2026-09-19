package vn.giapha.dataimport.api.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.util.List;
import vn.giapha.dataimport.domain.ImportCommitBlockedException;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.ImportLimits;
import vn.giapha.dataimport.domain.ImportRejectedException;
import vn.giapha.dataimport.domain.RollbackRefusedException;
import vn.giapha.shared.api.ProblemTypes;

/**
 * Dịch <b>từ chối ở cửa</b> sang đúng mã HTTP.
 *
 * <h2>Vì sao không để {@code GlobalExceptionHandler} lo</h2>
 * {@link ImportRejectedException} kế thừa {@code DomainException}, mà advice chung dịch mọi
 * {@code DomainException} thành {@code 422}. Đúng cho vi phạm quy tắc nghiệp vụ, <b>sai</b> ở đây:
 * "tệp này không phải .xlsx" là một yêu cầu dị dạng ({@code 400}), còn "tệp này đã được ghi vào phả
 * rồi" là một xung đột trạng thái ({@code 409}). Ba mã ấy dẫn tới ba việc khác nhau của người dùng —
 * chọn tệp khác · xoá ảnh nhúng rồi tải lại · xác nhận nhập lại — nên gộp về một là bắt họ đoán.
 *
 * <h2>Ranh giới: lỗi của TỆP, không phải lỗi của DÒNG</h2>
 * Mọi thứ lớp này xử lý đều xảy ra <b>trước khi có dòng nào vào khu vực chờ</b>, nên phản hồi
 * không có số dòng nào để chỉ và giao diện phải hiện một câu xin lỗi chứ không phải bảng lỗi. Lỗi
 * của từng dòng đi lối khác hẳn: chúng là dữ liệu trong {@code import_issue}, không phải ngoại lệ.
 *
 * <p>Chỉ khai báo handler cho <b>đúng hai kiểu</b> riêng của context này — không bắt
 * {@code DomainException} chung, vì bắt kiểu cha ở đây sẽ nuốt luôn {@code NotFoundException} và
 * {@code ForbiddenException} của shared kernel và làm mọi lỗi 404/403 của hệ thống đi sai đường.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ImportApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ImportApiExceptionHandler.class);

    /**
     * Mã lỗi → mã HTTP.
     *
     * <p>{@code IMP_TOO_MANY_ROWS} là {@code 422} chứ không phải {@code 400}: tệp hoàn toàn hợp lệ
     * về cú pháp, chỉ là <b>quá lớn để ai đó đối soát nổi</b> với cuốn sổ giấy đặt cạnh. Hành động
     * cần làm là tách tệp, không phải sửa định dạng.</p>
     */
    private static final Map<String, HttpStatus> MA_SANG_HTTP = Map.ofEntries(
            Map.entry(ImportRejectedException.BAD_FORMAT, HttpStatus.BAD_REQUEST),
            Map.entry(ImportRejectedException.CORRUPT_FILE, HttpStatus.BAD_REQUEST),
            Map.entry(ImportRejectedException.UNSAFE_FILE, HttpStatus.BAD_REQUEST),
            Map.entry(ImportRejectedException.MISSING_SHEET, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(ImportRejectedException.MISSING_COLUMN, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(ImportRejectedException.TOO_MANY_ROWS, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(ImportRejectedException.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE),
            Map.entry("IMP_ALREADY_COMMITTED", HttpStatus.CONFLICT));

    @ExceptionHandler(ImportRejectedException.class)
    public ProblemDetail handleRejected(ImportRejectedException ex, HttpServletRequest request) {
        HttpStatus status = MA_SANG_HTTP.getOrDefault(ex.code(), HttpStatus.BAD_REQUEST);
        log.info("{} {} - [{}] {}", status.value(), request.getRequestURI(), ex.code(),
                ex.getMessage());
        ProblemDetail problem = problem(status, "Không nhận được tệp này", ex.getMessage(),
                ex.code(), request);
        if (status == HttpStatus.CONFLICT) {
            // Chot bam-hai-lan CO the vuot qua, va giao dien can biet bang cach nao.
            problem.setProperty("overridable", true);
            problem.setProperty("overrideField", "force");
        }
        return problem;
    }

    /**
     * Tệp vượt trần multipart của Spring.
     *
     * <p>Không có handler này thì một tệp quá lớn trả {@code 500} kèm một câu chung chung, và
     * Trưởng chi không có cách nào biết mình phải làm gì. Cùng mã lỗi với
     * {@code ImportLimits.MAX_FILE_BYTES} vì với người dùng đó là <b>một</b> tình huống, bất kể
     * chặn ở tầng nào.</p>
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex,
                                        HttpServletRequest request) {
        log.info("413 {} - tep vuot tran multipart", request.getRequestURI());
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp quá lớn",
                "Tệp vượt trần " + (ImportLimits.MAX_FILE_BYTES / 1024 / 1024) + " MB. Một chi 400"
                        + " người chỉ xuất ra vài trăm KB, nên tệp lớn thế này thường là có ảnh"
                        + " nhúng — xoá ảnh đi rồi tải lại.",
                ImportRejectedException.FILE_TOO_LARGE, request);
    }

    /**
     * Bước ghi dừng lại trước khi ghi vì lô chạm một ca mà quy ước dòng họ chưa trả lời.
     *
     * <p>{@code 422}, và <b>chưa một dòng nào vào phả</b>. Phản hồi mang theo số vấn đề chứ không
     * mang nội dung từng vấn đề: bộ kiểm đã ghi lại toàn bộ vào {@code import_issue}, nên giao diện
     * nạp lại {@code GET /issues} là thấy đủ — nhân đôi danh sách ở đây chỉ tạo ra một bản có thể
     * lệch.</p>
     *
     * <p>Lối vào thường gặp của ngoại lệ này là <b>luồng nền</b> của {@code POST /commit}, và ở đó
     * nó không thành HTTP mà thành {@code status = FAILED} kèm {@code failureReason}. Handler này
     * phục vụ các lối gọi đồng bộ.</p>
     */
    @ExceptionHandler(ImportCommitBlockedException.class)
    public ProblemDetail handleCommitBlocked(ImportCommitBlockedException ex,
                                             HttpServletRequest request) {
        List<ImportIssue> issues = ex.issues();
        log.warn("422 {} - buoc ghi dung lai voi {} van de; khong mot dong nao vao pha",
                request.getRequestURI(), issues.size());
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Lô chạm quy ước chưa được chốt", ex.getMessage(), ex.getCode(), request);
        problem.setProperty("issueCount", issues.size());
        return problem;
    }

    /**
     * Từ chối gỡ lô.
     *
     * <p>Mang theo danh sách vướng mắc bằng tiếng Việt, vì đây là chỗ người dùng cần biết
     * <b>chính xác</b> cái gì đang chặn: "12 người đã được sửa sau khi ghi" dẫn tới một hành động
     * hoàn toàn khác với "đã quá hạn gỡ".</p>
     */
    @ExceptionHandler(RollbackRefusedException.class)
    public ProblemDetail handleRollbackRefused(RollbackRefusedException ex,
                                               HttpServletRequest request) {
        log.warn("422 {} - tu choi go lo: {} vuong mac", request.getRequestURI(), ex.lyDo().size());
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Không gỡ được lô này",
                ex.getMessage(), ex.getCode(), request);
        problem.setProperty("blockers", ex.lyDo());
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String code,
                                  HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                detail == null ? title : detail);
        problem.setType(switch (status) {
            case CONFLICT -> ProblemTypes.CONFLICT;
            case UNPROCESSABLE_ENTITY -> ProblemTypes.BUSINESS_RULE;
            default -> ProblemTypes.VALIDATION;
        });
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
