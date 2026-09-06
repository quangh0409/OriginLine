package vn.giapha.notification.application;

/**
 * Lỗi <b>tạm thời</b> khi gửi thông báo — tín hiệu để Spring AMQP thử lại theo backoff
 * ({@code spring.rabbitmq.listener.simple.retry.*}), và khi hết lượt thì đẩy tin sang
 * {@code notify.dlq}.
 *
 * <p>Là {@link RuntimeException} vì đó là điều kiện để listener container bắt được và kích hoạt
 * cơ chế retry; ngoại lệ kiểm tra sẽ bị bọc lại và mất ý nghĩa.</p>
 *
 * <p><b>Chỉ ném cho lỗi có cơ hội tự khỏi</b>: mạng, 5xx, 429. Lỗi vĩnh viễn phải được ghi nhận rồi
 * nuốt — retry chúng chỉ đốt hạn ngạch gateway và làm nhiễu đúng cái hàng đợi mà người vận hành
 * cần nhìn.</p>
 */
public class NotificationRetryException extends RuntimeException {

    public NotificationRetryException(String message) {
        super(message);
    }

    public NotificationRetryException(String message, Throwable cause) {
        super(message, cause);
    }
}
