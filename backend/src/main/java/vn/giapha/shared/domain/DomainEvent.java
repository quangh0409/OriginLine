package vn.giapha.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện nghiệp vụ do một aggregate phát ra. Là phương tiện giao tiếp <b>duy nhất</b> giữa các
 * bounded context ngoài việc gọi application service public.
 *
 * <p>POJO thuần: không annotation Spring. Việc publish do tầng application đảm nhiệm
 * (qua {@code ApplicationEventPublisher}), sau khi transaction commit nếu người nhận không được
 * phép làm hỏng giao dịch gốc.</p>
 */
public interface DomainEvent {

    /** Định danh duy nhất của sự kiện — dùng làm khoá chống trùng cho consumer idempotent. */
    UUID eventId();

    /** Thời điểm sự kiện xảy ra (UTC). */
    Instant occurredAt();

    /** Tên loại sự kiện dùng khi ghi log/audit; mặc định là tên lớp. */
    default String eventType() {
        return getClass().getSimpleName();
    }
}
