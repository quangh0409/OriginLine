package vn.giapha.genealogy.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import vn.giapha.shared.domain.AggregateRoot;
import vn.giapha.shared.domain.DomainEvent;

/**
 * Đẩy các domain event mà aggregate đã tích luỹ ra ngoài, rồi <b>dọn hàng đợi</b>.
 *
 * <p>Aggregate là POJO thuần nên không tự publish được - đó là chủ ý, và cũng là lý do phải có
 * một chỗ duy nhất làm việc này thay vì mỗi service tự gọi
 * {@code ApplicationEventPublisher} rồi quên {@code clearDomainEvents()}. Quên dọn thì lần lưu
 * sau sẽ phát lại đúng sự kiện cũ, và consumer nhắc giỗ sẽ gửi trùng.</p>
 *
 * <p>Sự kiện được phát <b>trong</b> transaction; bên nhận tự chọn
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} nếu việc của họ không được phép làm
 * hỏng giao dịch phả hệ gốc (gửi thông báo là ví dụ điển hình).</p>
 */
@Component
public class DomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    public DomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishAndClear(AggregateRoot<?> aggregate) {
        if (aggregate == null) {
            return;
        }
        for (DomainEvent event : aggregate.domainEvents()) {
            publisher.publishEvent(event);
        }
        aggregate.clearDomainEvents();
    }

    /** Phát một sự kiện lẻ - dùng cho những thứ không do aggregate sinh ra, ví dụ cạnh quan hệ. */
    public void publish(DomainEvent event) {
        if (event != null) {
            publisher.publishEvent(event);
        }
    }
}
