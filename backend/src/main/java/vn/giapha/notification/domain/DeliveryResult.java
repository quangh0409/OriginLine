package vn.giapha.notification.domain;

/**
 * Kết quả một lần gửi.
 *
 * @param providerMessageId mã tin do gateway trả về, để đối soát khi có khiếu nại; thường
 *                          {@code null} với Web Push
 * @param detail            mô tả lỗi <b>không chứa dữ liệu cá nhân</b> — dòng này đi vào cột
 *                          {@code notification_log.error} và bị đọc bởi mọi quản trị viên kỹ thuật
 */
public record DeliveryResult(DeliveryOutcome outcome, String providerMessageId, String detail) {

    public static DeliveryResult sent() {
        return new DeliveryResult(DeliveryOutcome.SENT, null, null);
    }

    public static DeliveryResult sent(String providerMessageId) {
        return new DeliveryResult(DeliveryOutcome.SENT, providerMessageId, null);
    }

    public static DeliveryResult retryable(String detail) {
        return new DeliveryResult(DeliveryOutcome.RETRYABLE, null, detail);
    }

    public static DeliveryResult permanent(String detail) {
        return new DeliveryResult(DeliveryOutcome.PERMANENT, null, detail);
    }

    public static DeliveryResult skipped(String detail) {
        return new DeliveryResult(DeliveryOutcome.SKIPPED, null, detail);
    }

    public NotificationStatus toStatus() {
        return switch (outcome) {
            case SENT -> NotificationStatus.SENT;
            case SKIPPED -> NotificationStatus.SKIPPED;
            case RETRYABLE, PERMANENT -> NotificationStatus.FAILED;
        };
    }
}
