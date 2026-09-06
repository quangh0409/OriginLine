package vn.giapha.notification.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.port.NotificationPublisher;

/**
 * Ghi lại các tin được đẩy lên hàng đợi, thay cho RabbitMQ.
 *
 * <p>Nó cũng là <b>bằng chứng của ranh giới bất đồng bộ</b>: mọi thứ mà
 * {@link NotificationDispatchService} làm phải dừng lại ở đây. Nếu một ngày nào đó luồng gửi lén
 * gọi thẳng gateway, con số ở đây vẫn đúng nhưng bài test đo thời gian sẽ đỏ.</p>
 */
public final class RecordingNotificationPublisher implements NotificationPublisher {

    private final List<NotificationMessage> published = new ArrayList<>();

    /** Đặt khác 0 để mô phỏng broker chậm. */
    public long doTreMilis;

    @Override
    public void publish(NotificationMessage message) {
        if (doTreMilis > 0) {
            try {
                Thread.sleep(doTreMilis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        published.add(message);
    }

    public List<NotificationMessage> published() {
        return List.copyOf(published);
    }

    public List<NotificationMessage> forChannel(Channel channel) {
        return published.stream().filter(message -> message.channel() == channel).toList();
    }

    public List<UUID> recipientIds(Channel channel) {
        return forChannel(channel).stream().map(message -> message.recipient().personId()).toList();
    }

    public int size() {
        return published.size();
    }
}
