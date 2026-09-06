package vn.giapha.notification.domain.port;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.notification.domain.InboxEntry;
import vn.giapha.notification.domain.InboxItem;
import vn.giapha.notification.domain.NotificationCategory;

/**
 * Cổng đọc/ghi hộp thư trong ứng dụng ({@code notification_inbox}).
 */
public interface InboxPort {

    /**
     * Ghi một tin vào hộp thư, <b>bỏ qua nếu đã có</b>.
     *
     * <p>Chống trùng do {@code ux_notification_inbox_idempotency (reminder_job_id,
     * recipient_person_id)} bảo đảm. Đây là lớp chặn thứ hai, độc lập với {@code notification_log}:
     * nhật ký gửi có thể bị dọn theo chính sách lưu trữ, hộp thư thì không được nhân đôi.</p>
     *
     * @return {@code true} nếu thực sự ghi mới
     */
    boolean insertIfAbsent(InboxItem item);

    /** Một trang hộp thư của một người, mới nhất trước. */
    List<InboxEntry> findPage(UUID recipientPersonId, ReadFilter filter, NotificationCategory category,
                              int page, int size);

    long countPage(UUID recipientPersonId, ReadFilter filter, NotificationCategory category);

    /** Tổng số chưa đọc của <b>toàn hộp thư</b>, không phụ thuộc bộ lọc — dùng cho badge chuông. */
    long countUnread(UUID recipientPersonId);

    Optional<InboxItem> findOwned(UUID id, UUID recipientPersonId);

    /**
     * Đánh dấu đã đọc, <b>idempotent</b>: gọi lại giữ nguyên {@code read_at} của lần đầu.
     *
     * @return bản ghi sau khi cập nhật; rỗng nếu tin không tồn tại hoặc thuộc người khác
     */
    Optional<InboxItem> markRead(UUID id, UUID recipientPersonId);

    /**
     * Đánh dấu <b>toàn bộ</b> tin chưa đọc của một người là đã đọc.
     *
     * <p>Một câu {@code UPDATE} duy nhất, không phải vòng lặp "đọc từng tin rồi cập nhật": hộp thư
     * của một người sau mùa giỗ có thể vài trăm tin, và vòng lặp ấy vừa chậm vừa mở ra khe hở cho
     * tin mới rơi vào giữa chừng rồi bị đánh dấu đã đọc oan.</p>
     *
     * <p><b>Idempotent</b>: gọi lại khi không còn tin chưa đọc trả về 0, không phải lỗi.</p>
     *
     * @return số tin thực sự chuyển từ chưa đọc sang đã đọc
     */
    int markAllRead(UUID recipientPersonId);

    /** Bộ lọc trạng thái đọc của {@code GET /api/v1/notifications}. */
    enum ReadFilter {
        ALL, UNREAD, READ;

        public static ReadFilter parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return ALL;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Tham so status khong hop le: " + raw
                        + " (cho phep ALL, UNREAD, READ)");
            }
        }
    }
}
