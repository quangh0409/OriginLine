package vn.giapha.notification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng trong hộp thư ứng dụng ({@code notification_inbox}).
 *
 * <p>{@code readAt == null} nghĩa là chưa đọc — đó cũng là điều kiện của chỉ mục riêng phần
 * {@code ix_notification_inbox_unread}, thứ đỡ cho badge chuông trên toàn ứng dụng.</p>
 */
public record InboxItem(UUID id,
                        UUID recipientPersonId,
                        UUID eventId,
                        UUID reminderJobId,
                        String title,
                        String body,
                        String linkUrl,
                        NotificationCategory category,
                        Instant createdAt,
                        Instant readAt) {

    public boolean isRead() {
        return readAt != null;
    }
}
