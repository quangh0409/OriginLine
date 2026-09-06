package vn.giapha.shared.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Gốc của một aggregate: có định danh, bảo vệ bất biến nghiệp vụ của cụm, và tích luỹ
 * {@link DomainEvent} để tầng application publish sau khi lưu thành công.
 *
 * <p>POJO thuần — không {@code @Entity}, không {@code @Component}. Bản chiếu JPA nằm ở
 * {@code <context>.infrastructure}.</p>
 *
 * @param <ID> kiểu định danh (thường là một value object, ví dụ {@code PersonId})
 */
public abstract class AggregateRoot<ID> {

    private final transient List<DomainEvent> domainEvents = new ArrayList<>();

    /** Định danh của aggregate. Không bao giờ null sau khi khởi tạo xong. */
    public abstract ID id();

    /** Ghi nhận một sự kiện để publish sau khi transaction thành công. */
    protected void registerEvent(DomainEvent event) {
        domainEvents.add(Objects.requireNonNull(event, "domain event khong duoc null"));
    }

    /** Danh sách sự kiện chưa publish (bản sao chỉ đọc). */
    public List<DomainEvent> domainEvents() {
        return Collections.unmodifiableList(new ArrayList<>(domainEvents));
    }

    /** Xoá hàng đợi sự kiện — gọi sau khi đã publish xong. */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || !getClass().equals(other.getClass())) {
            return false;
        }
        Object otherId = ((AggregateRoot<?>) other).id();
        return id() != null && id().equals(otherId);
    }

    @Override
    public int hashCode() {
        return id() == null ? 0 : id().hashCode();
    }
}
