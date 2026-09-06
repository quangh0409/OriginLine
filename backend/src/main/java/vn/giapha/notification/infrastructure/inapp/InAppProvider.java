package vn.giapha.notification.infrastructure.inapp;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.DeliveryResult;
import vn.giapha.notification.domain.InboxItem;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.port.InboxPort;
import vn.giapha.notification.domain.port.NotificationProvider;

/**
 * Kênh <b>in-app</b>: ghi thông báo vào {@code notification_inbox}.
 *
 * <p>Đây là hiện thực {@code NotificationProvider} của MVP và là <b>nguồn chân lý</b> của thông báo:
 * người dùng từ chối quyền Web Push, dùng iPhone chưa cài PWA vào màn hình chính, hay đơn giản là
 * tắt thông báo hệ điều hành — tất cả vẫn nhận đủ ở đây. Web Push chỉ là lớp đẩy ra ngoài.</p>
 *
 * <h2>Không có lỗi tạm thời</h2>
 * Kênh này chỉ ghi một dòng vào cơ sở dữ liệu, không gọi ra ngoài. Lỗi duy nhất có thể gặp là vi
 * phạm ràng buộc (người nhận không tồn tại), và đó là lỗi <b>vĩnh viễn</b> — retry không giúp gì.
 * Ghi nhận rồi dừng, thay vì đẩy một tin hỏng qua năm lượt retry vào DLQ.
 */
@Component
public class InAppProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(InAppProvider.class);

    private final InboxPort inbox;

    public InAppProvider(InboxPort inbox) {
        this.inbox = inbox;
    }

    @Override
    public Channel channel() {
        return Channel.INAPP;
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        InboxItem item = new InboxItem(
                UUID.randomUUID(),
                message.recipient().personId(),
                message.eventId(),
                message.reminderJobId(),
                message.title(),
                message.body(),
                message.deepLink(),
                message.category(),
                null,
                null);
        try {
            boolean inserted = inbox.insertIfAbsent(item);
            if (!inserted) {
                // Chỉ mục ux_notification_inbox_idempotency đã chặn: tin này từng vào hộp thư rồi.
                // Đây là kết quả ĐÚNG của một lần giao lại, không phải lỗi.
                log.debug("Tin da co trong hop thu cua {} (job {}) - khong ghi lai",
                        message.recipient().personId(), message.reminderJobId());
            }
            return DeliveryResult.sent();
        } catch (DataIntegrityViolationException ex) {
            log.error("Khong ghi duoc vao hop thu cua {} (job {}): {}",
                    message.recipient().personId(), message.reminderJobId(), ex.getMessage());
            return DeliveryResult.permanent("Vi pham rang buoc khi ghi notification_inbox");
        }
    }
}
