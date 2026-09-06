package vn.giapha.notification.domain;

import java.util.Locale;

/**
 * Trạng thái một dòng {@code notification_log} — khớp {@code ck_notification_log_status}.
 *
 * <p>{@link #DEAD_LETTER} là trạng thái <b>chỉ có ý nghĩa vận hành</b>: tin đã hết lượt retry và
 * nằm ở {@code notify.dlq}. Tách khỏi {@link #FAILED} để bảng theo dõi phân biệt được "lỗi tạm thời
 * đang được thử lại" với "đã bỏ cuộc, cần người xử lý".</p>
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    DEAD_LETTER,
    /** Người nhận đã tắt kênh này, hoặc không có thiết bị nào đăng ký — không phải lỗi. */
    SKIPPED;

    public boolean isDelivered() {
        return this == SENT;
    }

    /** Đã kết thúc, không thử lại nữa. */
    public boolean isFinal() {
        return this == SENT || this == SKIPPED || this == DEAD_LETTER;
    }

    public static NotificationStatus fromDbValue(String raw) {
        return raw == null ? PENDING : valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
