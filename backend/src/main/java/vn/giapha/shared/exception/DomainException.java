package vn.giapha.shared.exception;

/**
 * Vi phạm một quy tắc nghiệp vụ của dòng họ. Mang theo <b>mã lỗi ổn định</b> để frontend hiển thị
 * đúng thông điệp song ngữ VI/EN, thay vì bám vào chuỗi tiếng Anh của exception.
 *
 * <p>Mặc định được ánh xạ sang HTTP 422 trong {@code GlobalExceptionHandler}.</p>
 */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Mã lỗi dạng {@code CONTEXT.RULE}, ví dụ {@code genealogy.taboo-name-conflict}. */
    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public DomainException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
