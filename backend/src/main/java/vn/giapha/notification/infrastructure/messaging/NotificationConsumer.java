package vn.giapha.notification.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import vn.giapha.notification.application.NotificationDeliveryService;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.port.NotificationLogPort;

/**
 * Consumer của hai queue thông báo, cộng một consumer cho hàng đợi tin chết.
 *
 * <h2>Hai queue, hai listener, cố ý không gộp</h2>
 * {@code notify.inapp} và {@code notify.webpush} có hai phương thức riêng, hai luồng riêng, hai
 * đường retry riêng. Hộp thư in-app là <b>nguồn chân lý</b> của thông báo MVP; Web Push chỉ là lớp
 * đẩy thêm ra ngoài và phụ thuộc vào hạ tầng của Google/Mozilla/Apple. Gộp chung một listener thì
 * gateway push hỏng — hoặc chỉ chậm — sẽ chiếm hết prefetch và làm nghẽn cả in-app, tức là hỏng
 * đúng cái kênh đáng tin nhất vì cái kênh kém tin nhất.
 *
 * <h2>Idempotent</h2>
 * Toàn bộ nằm ở {@link NotificationDeliveryService}: khoá {@code reminder_job_id + recipient}
 * (+ kênh) do chỉ mục duy nhất trong cơ sở dữ liệu bảo đảm. RabbitMQ giao <i>ít nhất một lần</i>,
 * nên bản sao là chuyện thường ngày chứ không phải sự cố.
 *
 * <h2>Retry và DLQ</h2>
 * Cấu hình ở {@code application.yml}: 5 lượt, backoff nhân đôi từ 2s tới 30s, và
 * {@code default-requeue-rejected: false} — thiếu dòng cuối thì tin lỗi quay vòng vô tận giữa queue
 * và consumer, không bao giờ tới được {@code notify.dlq}. Hết lượt, tin rơi sang {@code notify.dlx}
 * rồi {@code notify.dlq}, nơi {@link #onDeadLetter} ghi lại để người vận hành nhìn thấy.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationDeliveryService delivery;
    private final NotificationLogPort logs;

    public NotificationConsumer(NotificationDeliveryService delivery, NotificationLogPort logs) {
        this.delivery = delivery;
        this.logs = logs;
    }

    /** Hộp thư trong ứng dụng — kênh phải luôn tới nơi. */
    @RabbitListener(queues = "notify.inapp", concurrency = "${giapha.notify.inapp-concurrency:2-8}")
    public void onInApp(NotificationEnvelope envelope) {
        handle(envelope, Channel.INAPP);
    }

    /**
     * Web Push — cố ý ít luồng hơn in-app: mỗi tin là một lượt gọi HTTP ra ngoài, và mở quá nhiều
     * kết nối song song tới push service chỉ dẫn tới bị giới hạn tốc độ (429).
     */
    @RabbitListener(queues = "notify.webpush", concurrency = "${giapha.notify.webpush-concurrency:1-4}")
    public void onWebPush(NotificationEnvelope envelope) {
        handle(envelope, Channel.WEBPUSH);
    }

    /**
     * Hàng đợi tin chết. Không thử gửi lại — chỉ ghi nhận để tin không <b>biến mất lặng lẽ</b>.
     *
     * <p>Một cái giỗ không được nhắc mà không ai biết là kịch bản tệ nhất của cả tính năng này. Dòng
     * {@code DEAD_LETTER} trong {@code notification_log} chính là thứ cho phép người vận hành trả
     * lời được câu "cụ X có được nhắc không" sau khi sự việc đã qua.</p>
     */
    @RabbitListener(queues = "notify.dlq")
    public void onDeadLetter(NotificationEnvelope envelope) {
        Channel channel = Channel.fromDbValue(envelope.channel());
        log.error("TIN CHET: job={} nguoi nhan={} kenh={} tieu de={} - da het luot retry, can nguoi xu ly",
                envelope.reminderJobId(), envelope.recipientPersonId(), channel, envelope.title());
        if (envelope.reminderJobId() == null) {
            return;
        }
        logs.markDeadLetter(envelope.reminderJobId(), envelope.recipientPersonId(), channel,
                "Het luot retry, tin roi vao notify.dlq");
    }

    /**
     * Kênh trong tin phải khớp queue nhận được. Lệch nghĩa là routing key sai hoặc ai đó đẩy tay tin
     * vào nhầm queue; xử lý theo kênh <b>của queue</b> để một tin lạc không kéo theo hành vi lạ.
     */
    private void handle(NotificationEnvelope envelope, Channel expected) {
        NotificationMessage message = envelope.toMessage();
        if (message.channel() != expected) {
            log.warn("Tin o queue {} nhung khai bao kenh {} (job={}) - xu ly theo kenh cua queue",
                    expected, message.channel(), envelope.reminderJobId());
            message = new NotificationMessage(message.reminderJobId(), message.eventId(),
                    message.subjectPersonId(), message.recipient(), expected, message.category(),
                    message.title(), message.body(), message.deepLink(), message.offsetDays(),
                    message.dueSolarDate());
        }
        delivery.deliver(message);
    }
}
