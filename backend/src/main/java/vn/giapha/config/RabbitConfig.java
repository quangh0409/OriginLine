package vn.giapha.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology RabbitMQ cho luồng nhắc giỗ bất đồng bộ (FR-2.5).
 *
 * <pre>
 *   notify (topic)
 *      ├── rk "notify.inapp"   → notify.inapp    ─┐
 *      └── rk "notify.webpush" → notify.webpush  ─┤  hết retry thì nack
 *                                                 ▼
 *                              notify.dlx (topic) → notify.dlq
 * </pre>
 *
 * <p><b>Vì sao hai queue tách rời:</b> hộp thư trong ứng dụng là nguồn chân lý của thông báo, Web
 * Push chỉ là kênh đẩy thêm. Gateway push chết không được phép kéo theo in-app — gộp chung một
 * queue là mất tính chất đó.</p>
 *
 * <p><b>Vì sao có DLQ:</b> mùa giỗ sinh hàng nghìn job cùng lúc; tin nhắn hỏng phải rơi vào
 * {@code notify.dlq} để người vận hành xử lý, tuyệt đối không được biến mất lặng lẽ. Retry và
 * backoff cấu hình ở {@code application.yml} ({@code spring.rabbitmq.listener.simple.retry.*}),
 * kèm {@code default-requeue-rejected: false} — nếu để {@code true}, tin lỗi sẽ quay vòng vô tận
 * và không bao giờ tới được DLQ.</p>
 *
 * <p>Consumer phải <b>idempotent</b>: khoá chống trùng là {@code reminder_job_id + recipient}.</p>
 */
@Configuration
public class RabbitConfig {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitConfig.class);

    public static final String NOTIFY_EXCHANGE = "notify";
    public static final String NOTIFY_DLX = "notify.dlx";

    public static final String QUEUE_INAPP = "notify.inapp";
    public static final String QUEUE_WEBPUSH = "notify.webpush";
    public static final String QUEUE_DLQ = "notify.dlq";

    public static final String RK_INAPP = "notify.inapp";
    public static final String RK_WEBPUSH = "notify.webpush";
    public static final String RK_ALL = "notify.#";

    @Bean
    public TopicExchange notifyExchange() {
        return new TopicExchange(NOTIFY_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange notifyDeadLetterExchange() {
        return new TopicExchange(NOTIFY_DLX, true, false);
    }

    @Bean
    public Queue inAppQueue() {
        return QueueBuilder.durable(QUEUE_INAPP)
                .deadLetterExchange(NOTIFY_DLX)
                .deadLetterRoutingKey(QUEUE_INAPP)
                .build();
    }

    @Bean
    public Queue webPushQueue() {
        return QueueBuilder.durable(QUEUE_WEBPUSH)
                .deadLetterExchange(NOTIFY_DLX)
                .deadLetterRoutingKey(QUEUE_WEBPUSH)
                .build();
    }

    /** Hàng đợi tin chết - khong gan DLX de tranh vong lap. */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(QUEUE_DLQ).build();
    }

    @Bean
    public Binding inAppBinding(Queue inAppQueue, TopicExchange notifyExchange) {
        return BindingBuilder.bind(inAppQueue).to(notifyExchange).with(RK_INAPP);
    }

    @Bean
    public Binding webPushBinding(Queue webPushQueue, TopicExchange notifyExchange) {
        return BindingBuilder.bind(webPushQueue).to(notifyExchange).with(RK_WEBPUSH);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange notifyDeadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(notifyDeadLetterExchange).with("#");
    }

    /** JSON thay vì Java serialization: payload đọc được khi soi DLQ, và không khoá vào kiểu Java. */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * <b>Bẫy đã sập một lần:</b> {@code spring.rabbitmq.template.mandatory} chỉ áp cho
     * {@code RabbitTemplate} do Spring Boot tự cấu hình. Lớp này khai báo bean thủ công nên thuộc
     * tính ấy bị bỏ qua — phải đặt tay {@link RabbitTemplate#setMandatory(boolean)}.
     *
     * <p>Không có {@code mandatory} + {@code ReturnsCallback}, một tin gửi với routing key sai
     * (hoặc tới một exchange chưa có queue nào bind) sẽ bị broker <b>vứt lặng lẽ</b>: publisher nhận
     * ack bình thường, nhật ký ghi "đã gửi", và cả họ không được nhắc giỗ mà không ai biết vì sao.
     * Đó đúng là kịch bản mà toàn bộ thiết kế retry + DLQ ở đây sinh ra để ngăn.</p>
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setExchange(NOTIFY_EXCHANGE);
        template.setMandatory(true);
        template.setReturnsCallback(returned -> LOG.error(
                "TIN BI TRA VE, KHONG VAO QUEUE NAO: exchange={} rk={} ma={} ly do={}."
                        + " Kiem tra binding cua exchange notify.",
                returned.getExchange(), returned.getRoutingKey(),
                returned.getReplyCode(), returned.getReplyText()));
        template.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) {
                LOG.error("Broker TU CHOI nhan tin (nack): correlation={} ly do={}", correlation, cause);
            }
        });
        return template;
    }
}
