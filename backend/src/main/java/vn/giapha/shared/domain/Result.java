package vn.giapha.shared.domain;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import vn.giapha.shared.exception.DomainException;

/**
 * Kết quả của một thao tác nghiệp vụ: hoặc thành công kèm giá trị, hoặc thất bại kèm mã lỗi.
 *
 * <p>Dùng cho các lỗi <b>lường trước được</b> (validate, vi phạm quy tắc dòng họ) để domain không
 * phải ném exception cho luồng thường. Lỗi thật sự bất thường vẫn ném {@link DomainException}.</p>
 *
 * <p>Ví dụ điển hình trong hệ thống này: cảnh báo kỵ húy khi tên mới trùng tên húy của bậc trên —
 * đó là một {@code Failure} có mã lỗi rõ ràng để tầng API hỏi lại người dùng, chứ không phải một
 * sự cố kỹ thuật.</p>
 *
 * @param <T> kiểu giá trị khi thành công
 */
public sealed interface Result<T> {

    /** Thành công, mang theo giá trị. */
    record Success<T>(T value) implements Result<T> {
    }

    /** Thất bại, mang theo mã lỗi ổn định và thông điệp cho người dùng. */
    record Failure<T>(String code, String message) implements Result<T> {
    }

    static <T> Result<T> ok(T value) {
        return new Success<>(value);
    }

    static <T> Result<T> fail(String code, String message) {
        return new Failure<>(code, message);
    }

    default boolean isSuccess() {
        return this instanceof Success<T>;
    }

    default boolean isFailure() {
        return this instanceof Failure<T>;
    }

    /** Giá trị nếu thành công, rỗng nếu thất bại. */
    default Optional<T> toOptional() {
        return this instanceof Success<T> success ? Optional.ofNullable(success.value()) : Optional.empty();
    }

    /** Mã lỗi nếu thất bại. */
    default Optional<String> errorCode() {
        return this instanceof Failure<T> failure ? Optional.of(failure.code()) : Optional.empty();
    }

    /** Thông điệp lỗi nếu thất bại. */
    default Optional<String> errorMessage() {
        return this instanceof Failure<T> failure ? Optional.ofNullable(failure.message()) : Optional.empty();
    }

    default <R> Result<R> map(Function<? super T, ? extends R> mapper) {
        if (this instanceof Success<T> success) {
            return Result.ok(mapper.apply(success.value()));
        }
        Failure<T> failure = (Failure<T>) this;
        return Result.fail(failure.code(), failure.message());
    }

    default Result<T> ifSuccess(Consumer<? super T> action) {
        if (this instanceof Success<T> success) {
            action.accept(success.value());
        }
        return this;
    }

    default T orElse(T fallback) {
        return this instanceof Success<T> success ? success.value() : fallback;
    }

    /** Lấy giá trị, hoặc ném {@link DomainException} mang đúng mã lỗi nếu thất bại. */
    default T orElseThrow() {
        if (this instanceof Success<T> success) {
            return success.value();
        }
        Failure<T> failure = (Failure<T>) this;
        throw new DomainException(failure.code(), failure.message());
    }
}
