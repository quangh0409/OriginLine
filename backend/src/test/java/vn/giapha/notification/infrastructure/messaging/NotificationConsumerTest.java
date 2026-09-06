package vn.giapha.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.notification.application.InMemoryNotificationLog;
import vn.giapha.notification.application.NotificationDeliveryService;
import vn.giapha.notification.application.RecordingProvider;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.NotificationStatus;
import vn.giapha.notification.domain.port.NotificationProvider;

/**
 * Consumer của {@code notify.inapp} / {@code notify.webpush} / {@code notify.dlq}.
 *
 * <p>Ba hành vi được kiểm: <b>idempotent xuyên lượt giao lại</b>, <b>kênh của queue thắng kênh khai
 * báo trong tin</b>, và <b>tin chết được ghi nhận thay vì biến mất lặng lẽ</b> — cái cuối là thứ cho
 * phép người vận hành trả lời được câu "cụ X có được nhắc không" sau khi sự việc đã qua.</p>
 */
class NotificationConsumerTest {

    private final InMemoryNotificationLog logs = new InMemoryNotificationLog();
    private final RecordingProvider inapp = RecordingProvider.alwaysSent(Channel.INAPP);
    private final RecordingProvider webpush = RecordingProvider.alwaysSent(Channel.WEBPUSH);

    private final NotificationConsumer consumer = new NotificationConsumer(
            new NotificationDeliveryService(List.<NotificationProvider>of(inapp, webpush), logs), logs);

    private final UUID jobId = UUID.randomUUID();
    private final UUID personId = UUID.randomUUID();

    private NotificationEnvelope envelope(Channel channel) {
        return new NotificationEnvelope(jobId, UUID.randomUUID(), UUID.randomUUID(), personId,
                UUID.randomUUID(), "vi", channel.name(), "REMINDER", "Ngay mai la gio cu Duc",
                "Ngay 20/10/2026 duong lich", "/events/abc", 1, LocalDate.of(2026, 10, 20));
    }

    @Test
    @DisplayName("Giao lai cung mot tin (RabbitMQ giao it nhat mot lan): chi gui MOT lan")
    void giaoLaiChiGuiMotLan() {
        NotificationEnvelope tin = envelope(Channel.INAPP);

        consumer.onInApp(tin);
        consumer.onInApp(tin);

        assertThat(inapp.sendCount()).isEqualTo(1);
        assertThat(logs.statusOf(jobId, personId, Channel.INAPP)).contains(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("Hai queue chay doc lap: in-app va Web Push khong nuot cua nhau")
    void haiQueueDocLap() {
        consumer.onInApp(envelope(Channel.INAPP));
        consumer.onWebPush(envelope(Channel.WEBPUSH));

        assertThat(inapp.sendCount()).isEqualTo(1);
        assertThat(webpush.sendCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Tin lac queue: xu ly theo kenh CUA QUEUE, khong theo kenh khai bao trong tin")
    void tinLacQueueThiTheoQueue() {
        // Routing key sai, hoặc ai đó đẩy tay tin vào nhầm queue trong giao diện quản trị.
        consumer.onInApp(envelope(Channel.WEBPUSH));

        assertThat(inapp.sendCount()).isEqualTo(1);
        assertThat(webpush.sendCount()).isZero();
        assertThat(logs.statusOf(jobId, personId, Channel.INAPP)).contains(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("Tin roi vao notify.dlq duoc ghi DEAD_LETTER, khong bien mat lang le")
    void tinChetDuocGhiNhan() {
        consumer.onDeadLetter(envelope(Channel.WEBPUSH));

        assertThat(logs.statusOf(jobId, personId, Channel.WEBPUSH))
                .contains(NotificationStatus.DEAD_LETTER);
        assertThat(logs.errorOf(jobId, personId, Channel.WEBPUSH))
                .hasValueSatisfying(error -> assertThat(error).contains("notify.dlq"));
        // Hàng đợi tin chết KHÔNG thử gửi lại - đó là việc của người vận hành.
        assertThat(webpush.sendCount()).isZero();
    }

    @Test
    @DisplayName("Tin da gui thanh cong roi moi roi vao DLQ: van ghi de thanh DEAD_LETTER de co dau vet")
    void tinDaGuiRoiVanGhiDauVetDlq() {
        consumer.onInApp(envelope(Channel.INAPP));
        assertThat(logs.statusOf(jobId, personId, Channel.INAPP)).contains(NotificationStatus.SENT);

        consumer.onDeadLetter(envelope(Channel.INAPP));

        assertThat(logs.statusOf(jobId, personId, Channel.INAPP))
                .contains(NotificationStatus.DEAD_LETTER);
    }

    @Test
    @DisplayName("Tin chet khong co reminder_job_id: khong ghi bua, khong no")
    void tinChetKhongCoKhoaThiBoQua() {
        NotificationEnvelope khongKhoa = new NotificationEnvelope(null, UUID.randomUUID(), null,
                personId, UUID.randomUUID(), "vi", "INAPP", "SYSTEM", "Thong bao he thong",
                null, null, null, null);

        consumer.onDeadLetter(khongKhoa);

        assertThat(logs.size()).isZero();
    }
}
