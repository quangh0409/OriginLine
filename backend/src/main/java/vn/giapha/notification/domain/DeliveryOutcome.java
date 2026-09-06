package vn.giapha.notification.domain;

/**
 * Kết cục một lần gửi, nhìn từ phía {@code NotificationProvider}.
 *
 * <p>Phân biệt lỗi <b>tạm thời</b> với lỗi <b>vĩnh viễn</b> là điểm sống còn của luồng bất đồng bộ:
 * retry một lỗi vĩnh viễn (subscription đã bị thu hồi, số điện thoại không tồn tại) chỉ đốt hạn
 * ngạch của gateway rồi cuối cùng vẫn rơi vào DLQ, còn <i>không</i> retry một lỗi tạm thời thì mất
 * thông báo.</p>
 */
public enum DeliveryOutcome {

    /** Gateway đã nhận. */
    SENT,

    /** Lỗi tạm thời (mạng, 5xx, 429) — ném lại để RabbitMQ retry rồi mới sang DLQ. */
    RETRYABLE,

    /** Lỗi vĩnh viễn (payload sai, subscription bị thu hồi) — dừng, không retry. */
    PERMANENT,

    /** Không có gì để gửi (người nhận chưa đăng ký thiết bị nào). Không phải lỗi. */
    SKIPPED
}
