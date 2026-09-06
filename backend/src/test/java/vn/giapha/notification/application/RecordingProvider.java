package vn.giapha.notification.application;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.DeliveryResult;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.port.NotificationProvider;

/** Gateway giả cho một kênh: đếm số lần gửi và trả kết quả do bài test quyết định. */
public final class RecordingProvider implements NotificationProvider {

    private final Channel channel;
    private final Function<NotificationMessage, DeliveryResult> behaviour;
    private final List<NotificationMessage> sent = new ArrayList<>();

    public RecordingProvider(Channel channel, Function<NotificationMessage, DeliveryResult> behaviour) {
        this.channel = channel;
        this.behaviour = behaviour;
    }

    public static RecordingProvider alwaysSent(Channel channel) {
        return new RecordingProvider(channel, message -> DeliveryResult.sent("msg-" + message.channel()));
    }

    public static RecordingProvider always(Channel channel, DeliveryResult result) {
        return new RecordingProvider(channel, message -> result);
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        sent.add(message);
        return behaviour.apply(message);
    }

    public List<NotificationMessage> sent() {
        return List.copyOf(sent);
    }

    public int sendCount() {
        return sent.size();
    }
}
