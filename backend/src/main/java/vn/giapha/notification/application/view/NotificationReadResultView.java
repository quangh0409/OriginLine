package vn.giapha.notification.application.view;

import java.time.Instant;
import java.util.UUID;

/**
 * Kết quả đánh dấu đã đọc.
 *
 * <p>Trả kèm {@code unreadCount} mới để giao diện cập nhật badge ngay, khỏi phải gọi lại
 * {@code GET /notifications}. {@code readAt} là thời điểm đọc <b>lần đầu</b> — gọi lại không cập
 * nhật giá trị này (idempotent).</p>
 */
public record NotificationReadResultView(UUID id, boolean read, Instant readAt, long unreadCount) {
}
