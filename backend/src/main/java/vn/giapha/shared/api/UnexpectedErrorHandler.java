package vn.giapha.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Lưới cuối cùng: mọi ngoại lệ mà <b>không</b> advice nào khác nhận thì thành {@code 500} có thân
 * RFC 7807.
 *
 * <h2>Vì sao đây phải là một lớp RIÊNG, ở LOWEST_PRECEDENCE</h2>
 * Handler này từng nằm trong {@link GlobalExceptionHandler}, và khi lớp ấy được nâng lên
 * {@code HIGHEST_PRECEDENCE + 100} thì nó trở thành một hồi quy P0.
 *
 * <p>{@code ExceptionHandlerExceptionResolver} duyệt advice <b>theo thứ tự</b> và dừng ở advice
 * <b>đầu tiên có bất kỳ phương thức nào khớp</b>. Một {@code @ExceptionHandler(Exception.class)}
 * khớp với <i>tất cả</i>. Nên đặt nó trong một advice ưu tiên cao là tuyên bố "advice này trả lời
 * mọi thứ" — và sáu lớp lỗi client (400 thân JSON hỏng · 415 thiếu {@code Content-Type} · 400 sai
 * kiểu tham số · 400 thiếu tham số bắt buộc · 406 {@code Accept} lạ · 405 sai method) cùng rơi
 * xuống {@code 500}, đo được trên hệ đang chạy. Màn tải Excel là nơi lộ ra sớm nhất: thiếu phần
 * {@code file} hoặc sai {@code Content-Type} trả {@code 500} và người vận hành đọc nó là "máy chủ
 * hỏng".</p>
 *
 * <p>Ở {@code LOWEST_PRECEDENCE} thì nó chỉ nhận phần dư thật sự — mọi advice theo ngữ cảnh, và
 * {@link GlobalExceptionHandler} (nay kế thừa {@code ResponseEntityExceptionHandler} nên tự biết
 * cả 20 ngoại lệ khung), đều được hỏi trước.</p>
 *
 * <p><b>Đừng gộp ngược vào {@code GlobalExceptionHandler}</b> dù hai lớp chỉ cách nhau một phương
 * thức. Một lớp = một thứ tự; hai thứ tự thì phải là hai lớp.</p>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class UnexpectedErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(UnexpectedErrorHandler.class);

    /**
     * {@code 500} với câu chung chung.
     *
     * <p>Chi tiết chỉ nằm ở log, <b>không</b> ở thân phản hồi: một ngoại lệ không lường trước
     * thường mang theo dữ liệu đang xử lý (tên, số điện thoại, ngày sinh của người còn sống), và
     * thân lỗi là nơi ít kiểm soát nhất.</p>
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("500 {} - loi khong luong truoc", request.getRequestURI(), ex);
        return ApiProblems.of(HttpStatus.INTERNAL_SERVER_ERROR, ProblemTypes.INTERNAL,
                "Lỗi hệ thống", "Đã xảy ra lỗi không mong muốn. Vui lòng thử lại sau.",
                "INTERNAL_ERROR", request);
    }
}
