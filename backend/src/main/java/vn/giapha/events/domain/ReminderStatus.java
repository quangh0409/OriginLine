package vn.giapha.events.domain;

/**
 * Vòng đời một {@link ReminderJob} — khớp {@code ck_reminder_job_status} của {@code V4__events.sql}.
 *
 * <pre>
 *   PENDING ──(tới giờ, đã đẩy lên RabbitMQ)──▶ QUEUED ──(consumer báo xong)──▶ SENT
 *      │                                           │
 *      │                                           └──(hết retry)──▶ FAILED
 *      └──(ngày giỗ đã qua trước khi kịp đẩy)──▶ CANCELLED
 * </pre>
 *
 * <p>{@code CANCELLED} cố ý tách khỏi {@code FAILED}: một job quá hạn vì hệ thống ngừng chạy vài
 * ngày <b>không phải lỗi gửi</b>, và trộn chung hai loại thì bảng cảnh báo vận hành sẽ đầy nhiễu
 * đến mức không ai còn đọc.</p>
 */
public enum ReminderStatus {
    PENDING,
    QUEUED,
    SENT,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SENT || this == CANCELLED;
    }

    public static ReminderStatus fromDbValue(String raw) {
        return raw == null ? PENDING : valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
