package vn.giapha.notification.domain.port;

import vn.giapha.notification.domain.NotificationMessage;

/**
 * Cổng đẩy thông báo lên message broker.
 *
 * <p>Tồn tại để tầng application không biết gì về RabbitMQ, và để kiểm thử luồng gửi không cần
 * broker thật. Hiện thực: {@code infrastructure.messaging.RabbitNotificationPublisher}.</p>
 *
 * <p><b>Luồng web không bao giờ chờ ở đây.</b> Điểm gọi duy nhất trong Giai đoạn 1 là scheduler;
 * nếu về sau có API gọi tới thì nó cũng chỉ đẩy tin rồi trả về ngay, không chờ gateway.</p>
 */
public interface NotificationPublisher {

    void publish(NotificationMessage message);
}
