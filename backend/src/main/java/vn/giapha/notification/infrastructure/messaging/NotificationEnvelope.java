package vn.giapha.notification.infrastructure.messaging;

import java.time.LocalDate;
import java.util.UUID;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.NotificationCategory;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.Recipient;

/**
 * Hình dạng JSON của một tin trên RabbitMQ.
 *
 * <h2>Vì sao không đẩy thẳng {@link NotificationMessage}</h2>
 * Tin nằm trong queue lâu hơn một lần triển khai: khi hàng nghìn tin đang chờ ở {@code notify.dlq},
 * đổi tên một trường trong domain sẽ làm tất cả không đọc lại được. Envelope là <b>hợp đồng nối
 * dây</b>, phẳng và chỉ gồm kiểu nguyên thuỷ, tách khỏi mô hình domain để domain được tự do thay
 * đổi. Đây cũng là lý do RabbitConfig dùng JSON thay vì serialization của Java: người vận hành phải
 * đọc được payload trong giao diện quản trị khi soi DLQ.
 *
 * <p>Enum được truyền dưới dạng <b>chuỗi</b> vì cùng lý do: thêm một hằng số vào giữa enum không
 * được phép làm hỏng những tin đang nằm chờ.</p>
 */
public record NotificationEnvelope(UUID reminderJobId,
                                   UUID eventId,
                                   UUID subjectPersonId,
                                   UUID recipientPersonId,
                                   UUID recipientAppUserId,
                                   String recipientLocale,
                                   String channel,
                                   String category,
                                   String title,
                                   String body,
                                   String deepLink,
                                   Integer offsetDays,
                                   LocalDate dueSolarDate) {

    public static NotificationEnvelope from(NotificationMessage message) {
        Recipient recipient = message.recipient();
        return new NotificationEnvelope(
                message.reminderJobId(),
                message.eventId(),
                message.subjectPersonId(),
                recipient.personId(),
                recipient.appUserId(),
                recipient.locale(),
                message.channel().name(),
                message.category().name(),
                message.title(),
                message.body(),
                message.deepLink(),
                message.offsetDays(),
                message.dueSolarDate());
    }

    public NotificationMessage toMessage() {
        return new NotificationMessage(
                reminderJobId,
                eventId,
                subjectPersonId,
                new Recipient(recipientPersonId, recipientAppUserId, recipientLocale),
                Channel.fromDbValue(channel),
                NotificationCategory.fromDbValue(category),
                title,
                body,
                deepLink,
                offsetDays,
                dueSolarDate);
    }
}
