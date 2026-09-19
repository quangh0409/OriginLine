package vn.giapha.membership.api.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.giapha.membership.application.InvitationNotUsableException;
import vn.giapha.membership.application.MembershipProblemCodes;
import vn.giapha.membership.application.TooManyAttemptsException;
import vn.giapha.membership.domain.port.IdentityProviderException;
import vn.giapha.membership.domain.port.PasswordRejectedException;
import vn.giapha.membership.domain.port.SetPasswordNotAllowedException;
import vn.giapha.shared.api.ApiProblems;
import vn.giapha.shared.api.ProblemTypes;

/**
 * Bổ sung cho {@code shared.api.GlobalExceptionHandler} hai tình huống của luồng mời mà nó cố ý
 * không biết tới: <b>410/409</b> mã mời không dùng được, và <b>429</b> vượt giới hạn tần suất.
 *
 * <p>Chỉ khai báo handler cho đúng hai kiểu ngoại lệ riêng — <b>không</b> bắt
 * {@code DomainException} chung. Bắt kiểu cha ở đây sẽ nuốt luôn {@code NotFoundException} và
 * {@code ForbiddenException} của shared kernel và làm mọi lỗi 404/403 của toàn hệ thống đi sai
 * đường.</p>
 *
 * <p>Mã mời <b>không khớp</b> lời mời nào không đi qua đây: nó là {@code NotFoundException} thường
 * và {@code GlobalExceptionHandler} trả {@code 404 NOT_FOUND}. Cố ý dùng lại mã sẵn có thay vì đẻ
 * mã thứ tư — {@code ProblemCode} là danh sách đóng, và ca ấy không mang thêm nghĩa nào.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MembershipExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MembershipExceptionHandler.class);

    /**
     * Mã khớp một lời mời có thật nhưng đã hết hạn / đã dùng / bị thu hồi.
     *
     * <h2>Vì sao 410 cho "hết hạn" và "thu hồi", 409 cho "đã dùng"</h2>
     * {@code 410 Gone} nói tài nguyên từng tồn tại và nay mất vĩnh viễn — đúng nghĩa một mã đã quá
     * hạn hoặc đã bị huỷ; thử lại không bao giờ đổi kết quả. {@code 409 Conflict} nói yêu cầu xung
     * đột với <i>trạng thái hiện tại</i> — đúng nghĩa "đã có người nhận rồi".
     *
     * <p>Nhưng giao diện <b>không</b> được phân nhánh theo mã HTTP: nó phân nhánh theo {@code code}
     * (`INVITATION_EXPIRED` · `INVITATION_ALREADY_USED` · `INVITATION_REVOKED`). Mã trạng thái ở
     * đây là để proxy, cache và công cụ giám sát hiểu đúng, không phải để client rẽ nhánh.</p>
     */
    @ExceptionHandler(InvitationNotUsableException.class)
    public ResponseEntity<ProblemDetail> handleInvitationNotUsable(InvitationNotUsableException ex,
                                                                   HttpServletRequest request) {
        HttpStatus status = switch (ex.usability()) {
            case ALREADY_USED -> HttpStatus.CONFLICT;
            case EXPIRED, REVOKED -> HttpStatus.GONE;
            case USABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        log.info("{} {} - loi moi khong dung duoc: {}", status.value(), request.getRequestURI(),
                ex.usability());
        // KHONG gan ten hay so dien thoai nguoi moi vao than loi: mot ma hong theo dinh nghia la
        // ma co the dang nam trong tay nguoi la.
        ProblemDetail problem = ApiProblems.of(status,
                status == HttpStatus.CONFLICT ? ProblemTypes.CONFLICT : ProblemTypes.BUSINESS_RULE,
                "Mã mời không dùng được", ex.getMessage(), ex.getCode(), request);
        return ResponseEntity.status(status).body(problem);
    }

    /**
     * Vượt giới hạn tần suất thử mã — HTTP <b>429</b>, kèm {@code Retry-After}.
     *
     * <p>Không gộp vào 422: yêu cầu hoàn toàn hợp lệ, chỉ là đến quá dày. Client phản ứng với hai
     * thứ ấy hoàn toàn khác nhau — 422 thì sửa dữ liệu rồi gửi lại ngay, 429 thì <b>đợi</b>.</p>
     */
    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyAttempts(TooManyAttemptsException ex,
                                                               HttpServletRequest request) {
        log.warn("429 {} - vuot gioi han thu ma moi", request.getRequestURI());
        ProblemDetail problem = ApiProblems.of(HttpStatus.TOO_MANY_REQUESTS,
                ProblemTypes.BUSINESS_RULE, "Thử quá nhiều lần", ex.getMessage(),
                MembershipProblemCodes.RATE_LIMITED, request);
        problem.setProperty("retryAfterSeconds", ex.retryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(problem);
    }

    /**
     * Realm từ chối mật khẩu vì chính sách mật khẩu — HTTP <b>422</b>, không phải 503.
     *
     * <p>Khai báo <i>trước</i> {@link #handleIdentityProviderDown} dù là lớp con của cùng một cây
     * ngoại lệ: Spring chọn handler theo kiểu <b>cụ thể nhất</b>, nên thứ tự khai báo không quyết
     * định — nhưng đặt cạnh nhau để người đọc thấy ngay hai ca này cố ý ra hai mã khác nhau. Đây là
     * lỗi người dùng sửa được ("mật khẩu quá ngắn"), còn 503 thì người dùng không sửa được gì.</p>
     */
    @ExceptionHandler(PasswordRejectedException.class)
    public ResponseEntity<ProblemDetail> handlePasswordRejected(
            PasswordRejectedException ex, HttpServletRequest request) {
        log.info("422 {} - realm tu choi mat khau", request.getRequestURI());
        ProblemDetail problem = ApiProblems.of(HttpStatus.UNPROCESSABLE_ENTITY,
                ProblemTypes.VALIDATION, "Mật khẩu không hợp lệ", ex.getMessage(),
                MembershipProblemCodes.VALIDATION_FAILED, request);
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    /**
     * Liên kết đặt mật khẩu sai chữ ký, quá hạn, hoặc <b>đã dùng rồi</b> — HTTP <b>410</b>.
     *
     * <p>Cùng mã trạng thái và cùng tinh thần với mã mời hết hạn: tài nguyên từng tồn tại và nay
     * mất vĩnh viễn; thử lại không bao giờ đổi kết quả, lối đi tiếp là xin phát lại lời mời.</p>
     */
    @ExceptionHandler(SetPasswordNotAllowedException.class)
    public ResponseEntity<ProblemDetail> handleSetPasswordNotAllowed(
            SetPasswordNotAllowedException ex, HttpServletRequest request) {
        log.info("410 {} - lien ket dat mat khau khong dung duoc", request.getRequestURI());
        ProblemDetail problem = ApiProblems.of(HttpStatus.GONE, ProblemTypes.BUSINESS_RULE,
                "Liên kết đặt mật khẩu không dùng được", ex.getMessage(),
                MembershipProblemCodes.SET_PASSWORD_LINK_INVALID, request);
        return ResponseEntity.status(HttpStatus.GONE).body(problem);
    }

    /**
     * Không với tới được Keycloak, hoặc chưa cấu hình tài khoản dịch vụ — HTTP <b>503</b>.
     *
     * <h2>Vì sao 503 chứ không 500</h2>
     * Người dùng không làm gì sai và không sửa được gì; câu trả lời đúng là "thử lại sau". Quan
     * trọng hơn, khi ngoại lệ này bay ra thì <b>mã mời chưa bị đánh dấu đã dùng</b> — thao tác
     * Keycloak nằm <i>trước</i> lần ghi CSDL, đúng để ca này an toàn. Nói "thử lại" mà mã đã chết
     * mới là điều không tha thứ được.
     */
    @ExceptionHandler(IdentityProviderException.class)
    public ResponseEntity<ProblemDetail> handleIdentityProviderDown(IdentityProviderException ex,
                                                                    HttpServletRequest request) {
        log.error("503 {} - cong danh tinh khong dung duoc: {}", request.getRequestURI(),
                ex.getMessage());
        ProblemDetail problem = ApiProblems.of(HttpStatus.SERVICE_UNAVAILABLE,
                ProblemTypes.BUSINESS_RULE, "Chưa lập được tài khoản",
                "Chua lap duoc tai khoan dang nhap luc nay. Ma moi CHUA bi dung, hay thu lai sau.",
                MembershipProblemCodes.IDENTITY_PROVIDER_UNAVAILABLE, request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }
}
