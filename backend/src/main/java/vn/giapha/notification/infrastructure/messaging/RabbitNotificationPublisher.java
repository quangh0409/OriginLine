package vn.giapha.notification.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.stereotype.Component;
import vn.giapha.config.RabbitConfig;
import vn.giapha.notification.application.NotificationRetryException;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.port.NotificationPublisher;

/**
 * Đẩy thông báo lên exchange {@code notify} với routing key theo kênh.
 *
 * <pre>
 *   notify (topic)
 *      ├── "notify.inapp"   → notify.inapp
 *      └── "notify.webpush" → notify.webpush
 * </pre>
 *
 * <p>Tin được đánh dấu <b>bền</b> (mặc định của {@code Jackson2JsonMessageConverter} + queue
 * durable): broker khởi động lại giữa mùa giỗ không được làm bay mất lịch nhắc.</p>
 *
 * <p>Không nuốt lỗi đẩy tin. Broker chết mà scheduler vẫn ghi "đã gửi" thì cả họ mất thông báo và
 * nhật ký nói ngược lại — bên gọi ({@code DispatchDueRemindersService}) bắt ngoại lệ này, trả job về
 * {@code PENDING} và lượt quét sau thử lại.</p>
 */
@Component
public class RabbitNotificationPublisher implements NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitNotificationPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitNotificationPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(NotificationMessage message) {
        String routingKey = message.channel().routingKey();
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.NOTIFY_EXCHANGE, routingKey,
                    NotificationEnvelope.from(message));
            log.trace("Da day tin len {} rk={} job={} nguoi nhan={}",
                    RabbitConfig.NOTIFY_EXCHANGE, routingKey, message.reminderJobId(),
                    message.recipient().personId());
        } catch (MessageConversionException ex) {
            // Payload không serialize được là lỗi lập trình, không phải lỗi hạ tầng — thử lại vô ích.
            log.error("Khong serialize duoc thong bao job={} kenh={}: {}",
                    message.reminderJobId(), message.channel(), ex.getMessage(), ex);
            throw ex;
        } catch (RuntimeException ex) {
            throw new NotificationRetryException(
                    "Khong day duoc thong bao len RabbitMQ (rk=" + routingKey + ")", ex);
        }
    }
}
