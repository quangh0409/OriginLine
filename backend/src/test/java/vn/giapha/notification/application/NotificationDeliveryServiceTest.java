package vn.giapha.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.DeliveryResult;
import vn.giapha.notification.domain.NotificationCategory;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.NotificationStatus;
import vn.giapha.notification.domain.Recipient;
import vn.giapha.notification.domain.port.NotificationProvider;

/**
 * Thân của consumer RabbitMQ: gửi <b>một</b> tin, tới <b>một</b> người, qua <b>một</b> kênh.
 *
 * <p>Hai tính chất sống còn được kiểm ở đây:</p>
 * <ol>
 *   <li><b>Idempotent.</b> RabbitMQ giao <i>ít nhất một lần</i> — bản sao là chuyện thường ngày.
 *       Người trong họ nhận hai tin nhắc cùng một cái giỗ sẽ mất tin vào hệ thống nhanh hơn nhiều
 *       so với nhận muộn một tin.</li>
 *   <li><b>Chỉ lỗi tạm thời mới ném ngoại lệ.</b> Ném là tín hiệu duy nhất để Spring AMQP retry rồi
 *       đẩy sang {@code notify.dlq}. Ném cho lỗi vĩnh viễn thì DLQ đầy rác đúng lúc người vận hành
 *       cần nhìn vào nó.</li>
 * </ol>
 */
class NotificationDeliveryServiceTest {

    private final InMemoryNotificationLog logs = new InMemoryNotificationLog();

    private final UUID jobId = UUID.randomUUID();
    private final UUID personId = UUID.randomUUID();

    private NotificationMessage tinNhac(Channel channel) {
        return new NotificationMessage(jobId, UUID.randomUUID(), UUID.randomUUID(),
                new Recipient(personId, UUID.randomUUID(), "vi"), channel,
                NotificationCategory.REMINDER, "Ngay mai la gio cu Duc",
                "Ngay 20/10/2026 duong lich", "/events/x", 1, LocalDate.of(2026, 10, 20));
    }

    private NotificationDeliveryService service(NotificationProvider... providers) {
        return new NotificationDeliveryService(List.of(providers), logs);
    }

    @Test
    @DisplayName("Giao lai cung mot tin: gateway chi duoc goi DUNG MOT lan")
    void giaoLaiChiGuiMotLan() {
        RecordingProvider inapp = RecordingProvider.alwaysSent(Channel.INAPP);
        NotificationDeliveryService service = service(inapp);
        NotificationMessage message = tinNhac(Channel.INAPP);

        service.deliver(message);
        service.deliver(message);
        service.deliver(message);

        assertThat(inapp.sendCount()).isEqualTo(1);
        assertThat(logs.claimCalls).isEqualTo(3);
        assertThat(logs.statusOf(jobId, personId, Channel.INAPP)).contains(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("Hai kenh cua cung mot lich nhac la hai dong nhat ky doc lap")
    void haiKenhLaHaiDongDocLap() {
        RecordingProvider inapp = RecordingProvider.alwaysSent(Channel.INAPP);
        RecordingProvider webpush = RecordingProvider.alwaysSent(Channel.WEBPUSH);
        NotificationDeliveryService service = service(inapp, webpush);

        service.deliver(tinNhac(Channel.INAPP));
        service.deliver(tinNhac(Channel.WEBPUSH));

        // Kênh là một phần của khoá chống trùng: gửi in-app rồi không được nuốt mất Web Push.
        assertThat(inapp.sendCount()).isEqualTo(1);
        assertThat(webpush.sendCount()).isEqualTo(1);
        assertThat(logs.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Loi TAM THOI ném ngoai le -> Spring AMQP retry roi day sang notify.dlq")
    void loiTamThoiThiNem() {
        RecordingProvider gatewayChapChon = RecordingProvider.always(Channel.WEBPUSH,
                DeliveryResult.retryable("Push service tra HTTP 503"));
        NotificationDeliveryService service = service(gatewayChapChon);

        assertThatThrownBy(() -> service.deliver(tinNhac(Channel.WEBPUSH)))
                .isInstanceOf(NotificationRetryException.class)
                .hasMessageContaining("503");

        // Dòng nhật ký ở FAILED chứ không phải kết thúc: lượt giao lại vẫn được phép gửi tiếp.
        assertThat(logs.statusOf(jobId, personId, Channel.WEBPUSH)).contains(NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("Sau mot lan that bai, lan giao lai VAN duoc gui - tin khong bi mat")
    void thatBaiRoiGiaoLaiVanGui() {
        RecordingProvider chapChon = new RecordingProvider(Channel.WEBPUSH, message ->
                DeliveryResult.retryable("mang chap chon"));
        NotificationDeliveryService hong = service(chapChon);
        assertThatThrownBy(() -> hong.deliver(tinNhac(Channel.WEBPUSH)))
                .isInstanceOf(NotificationRetryException.class);

        RecordingProvider daHoi = RecordingProvider.alwaysSent(Channel.WEBPUSH);
        service(daHoi).deliver(tinNhac(Channel.WEBPUSH));

        assertThat(daHoi.sendCount()).isEqualTo(1);
        assertThat(logs.statusOf(jobId, personId, Channel.WEBPUSH)).contains(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("Loi VINH VIEN duoc ghi nhan roi nuot - khong dot han ngach, khong lam nhieu DLQ")
    void loiVinhVienThiKhongNem() {
        RecordingProvider tuChoi = RecordingProvider.always(Channel.WEBPUSH,
                DeliveryResult.permanent("Subscription bi thu hoi (HTTP 410)"));
        NotificationDeliveryService service = service(tuChoi);

        assertThatCode(() -> service.deliver(tinNhac(Channel.WEBPUSH))).doesNotThrowAnyException();
        assertThat(logs.statusOf(jobId, personId, Channel.WEBPUSH)).contains(NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("Kenh chua co adapter: ghi SKIPPED, khong retry, khong day sang DLQ")
    void kenhChuaCoAdapterThiBoQua() {
        NotificationDeliveryService service = service(RecordingProvider.alwaysSent(Channel.INAPP));

        assertThatCode(() -> service.deliver(tinNhac(Channel.ZALO))).doesNotThrowAnyException();
        assertThat(logs.statusOf(jobId, personId, Channel.ZALO)).contains(NotificationStatus.SKIPPED);
    }

    @Test
    @DisplayName("Provider ném ngoai le ngoai hop dong: coi la tam thoi, giu tin lai de thu lai")
    void providerNemNgoaiLeThiCoiLaTamThoi() {
        RecordingProvider noTung = new RecordingProvider(Channel.WEBPUSH, message -> {
            throw new IllegalStateException("NullPointer trong thu vien push");
        });
        NotificationDeliveryService service = service(noTung);

        assertThatThrownBy(() -> service.deliver(tinNhac(Channel.WEBPUSH)))
                .isInstanceOf(NotificationRetryException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(logs.statusOf(jobId, personId, Channel.WEBPUSH)).contains(NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("Hai provider cung dang ky mot kenh: chet ngay luc khoi dong, khong am tham gui hai lan")
    void haiProviderCungKenhThiChetSom() {
        assertThatThrownBy(() -> new NotificationDeliveryService(
                List.<NotificationProvider>of(RecordingProvider.alwaysSent(Channel.INAPP),
                        RecordingProvider.alwaysSent(Channel.INAPP)), logs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INAPP");
    }

    @Test
    @DisplayName("Chi tiet loi duoc cat ngan va bo xuong dong truoc khi vao cot error")
    void chiTietLoiDuocDonSach() {
        String loiDaiLoangNgoang = "Push service tra ve\n\n" + "x".repeat(1_000);
        NotificationDeliveryService service = service(RecordingProvider.always(Channel.WEBPUSH,
                DeliveryResult.permanent(loiDaiLoangNgoang)));

        service.deliver(tinNhac(Channel.WEBPUSH));

        String error = logs.errorOf(jobId, personId, Channel.WEBPUSH).orElseThrow();
        assertThat(error).hasSize(500).doesNotContain("\n");
    }

    @Test
    @DisplayName("Ket qua SKIPPED cua gateway (chua dang ky thiet bi nao) khong phai loi")
    void ketQuaSkippedKhongPhaiLoi() {
        NotificationDeliveryService service = service(RecordingProvider.always(Channel.WEBPUSH,
                DeliveryResult.skipped("Nguoi nhan chua dang ky thiet bi nao")));

        assertThatCode(() -> service.deliver(tinNhac(Channel.WEBPUSH))).doesNotThrowAnyException();
        assertThat(logs.statusOf(jobId, personId, Channel.WEBPUSH)).contains(NotificationStatus.SKIPPED);
    }
}
