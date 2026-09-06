package vn.giapha.audit.support;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link ObjectProvider} tối giản cho unit test.
 *
 * <p>{@code AuditTrailService} nhận {@code AuditActorPort} qua {@code ObjectProvider} vì adapter
 * ấy do {@code membership} cung cấp và có thể <b>vắng mặt</b>. Bản này cho phép dựng cả hai tình
 * huống — có adapter và không có adapter — mà không cần khởi động Spring context.</p>
 *
 * @param <T> kiểu bean được cung cấp; {@code null} nghĩa là "không có adapter nào"
 */
public final class SimpleObjectProvider<T> implements ObjectProvider<T> {

    private final T value;

    private SimpleObjectProvider(T value) {
        this.value = value;
    }

    public static <T> SimpleObjectProvider<T> of(T value) {
        return new SimpleObjectProvider<>(value);
    }

    /** Không có bean nào — mô phỏng hệ thống chạy mà chưa nạp context {@code membership}. */
    public static <T> SimpleObjectProvider<T> empty() {
        return new SimpleObjectProvider<>(null);
    }

    @Override
    public T getObject() {
        if (value == null) {
            throw new IllegalStateException("Khong co bean nao duoc cung cap");
        }
        return value;
    }

    @Override
    public T getObject(Object... args) {
        return getObject();
    }

    @Override
    public T getIfAvailable() {
        return value;
    }

    @Override
    public T getIfUnique() {
        return value;
    }

    @Override
    public Stream<T> stream() {
        return value == null ? Stream.empty() : Stream.of(value);
    }

    @Override
    public Iterator<T> iterator() {
        return (value == null ? List.<T>of() : List.of(value)).iterator();
    }
}
