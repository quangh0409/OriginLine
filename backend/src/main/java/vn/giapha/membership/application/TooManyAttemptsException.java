package vn.giapha.membership.application;

/**
 * Người gọi đã thử mã mời quá nhiều lần — HTTP 429.
 *
 * <h2>Vì sao không tái dùng {@code DomainException}</h2>
 * {@code GlobalExceptionHandler} của shared kernel dịch {@code DomainException} thành 422. Giới hạn
 * tần suất không phải "dữ liệu vi phạm quy tắc nghiệp vụ": yêu cầu hoàn toàn hợp lệ, chỉ là đến quá
 * dày. Client phân biệt hai thứ ấy bằng hành động hoàn toàn khác nhau — 422 thì sửa dữ liệu rồi gửi
 * lại ngay, 429 thì <b>đợi</b>. Một kiểu riêng để {@code MembershipExceptionHandler} gắn được cả mã
 * trạng thái đúng lẫn header {@code Retry-After}.
 */
public class TooManyAttemptsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Số giây client nên đợi trước khi thử lại — đi thẳng vào header {@code Retry-After}. */
    private final long retryAfterSeconds;

    public TooManyAttemptsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
