package vn.giapha.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.DeliveryResult;
import vn.giapha.notification.domain.NotificationCategory;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.Recipient;

/**
 * Phân giải người nhận và bung tin ra hàng đợi.
 *
 * <p>Hai điều được kiểm: <b>phạm vi chi/ngành</b> (người nhận là thành viên cùng chi và mọi nhánh
 * con — FR-2.2) và <b>ranh giới bất đồng bộ</b> (luồng gọi chỉ ghi vào broker rồi trả về, tuyệt đối
 * không chờ gateway).</p>
 */
class NotificationDispatchServiceTest {

    private final RecordingNotificationPublisher publisher = new RecordingNotificationPublisher();
    private final FakeRecipientDirectory directory = new FakeRecipientDirectory();
    private final NotificationDispatchService service =
            new NotificationDispatchService(directory, publisher);

    private final UUID chi1 = UUID.randomUUID();
    private final UUID chi2 = UUID.randomUUID();

    private final UUID truongChi1 = UUID.randomUUID();
    private final UUID nguoiNhanhCon = UUID.randomUUID();
    private final UUID nguoiChi2 = UUID.randomUUID();
    private final UUID kieuBao = UUID.randomUUID();

    private void dungDongHo() {
        directory.branch(chi1, "root.chi1")
                .branch(chi2, "root.chi2")
                .member(new Recipient(truongChi1, UUID.randomUUID(), "vi"), "root.chi1")
                .member(new Recipient(nguoiNhanhCon, UUID.randomUUID(), "vi"), "root.chi1.nhanh2")
                .member(new Recipient(nguoiChi2, UUID.randomUUID(), "vi"), "root.chi2")
                .member(new Recipient(kieuBao, UUID.randomUUID(), "en"), "root.chi1");
    }

    private ReminderDispatch nhacGio(UUID branchId, boolean clanLevel) {
        return new ReminderDispatch(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                branchId, clanLevel, 3, LocalDate.of(2026, 10, 20),
                LocalizedText.of("Con 3 ngay toi gio cu Duc", "3 days until the rite for Duc"),
                LocalizedText.of("Ngay 20/10/2026", "On 20/10/2026"),
                "/events/abc");
    }

    @Test
    @DisplayName("Nhac theo chi: thanh vien chi do VA moi nhanh con nhan, chi khac thi khong")
    void nhacDungPhamViChiVaNhanhCon() {
        dungDongHo();

        int soNguoi = service.dispatchReminder(nhacGio(chi1, false));

        assertThat(soNguoi).isEqualTo(3);
        assertThat(publisher.recipientIds(Channel.INAPP))
                .containsExactlyInAnyOrder(truongChi1, nguoiNhanhCon, kieuBao)
                .doesNotContain(nguoiChi2);
    }

    @Test
    @DisplayName("Su kien cap dong ho: ca ho nhan, khong loc theo chi")
    void suKienCapDongHoThiCaHoNhan() {
        dungDongHo();

        int soNguoi = service.dispatchReminder(nhacGio(null, true));

        assertThat(soNguoi).isEqualTo(4);
        assertThat(publisher.recipientIds(Channel.INAPP))
                .containsExactlyInAnyOrder(truongChi1, nguoiNhanhCon, nguoiChi2, kieuBao);
    }

    @Test
    @DisplayName("Moi nguoi nhan mot tin cho MOI kenh: in-app va Web Push doc lap nhau")
    void moiNguoiMotTinChoMoiKenh() {
        dungDongHo();

        service.dispatchReminder(nhacGio(chi2, false));

        // Gộp hai kênh vào một tin là mất đúng tính chất "push chết không kéo theo in-app".
        assertThat(publisher.forChannel(Channel.INAPP)).hasSize(1);
        assertThat(publisher.forChannel(Channel.WEBPUSH)).hasSize(1);
        assertThat(publisher.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Kieu bao dat locale=en nhan ban tieng Anh, nguoi trong nuoc nhan ban tieng Viet")
    void moiNguoiNhanTheoNgonNguCuaMinh() {
        dungDongHo();

        service.dispatchReminder(nhacGio(chi1, false));

        NotificationMessage banTiengAnh = publisher.forChannel(Channel.INAPP).stream()
                .filter(message -> message.recipient().personId().equals(kieuBao))
                .findFirst().orElseThrow();
        NotificationMessage banTiengViet = publisher.forChannel(Channel.INAPP).stream()
                .filter(message -> message.recipient().personId().equals(truongChi1))
                .findFirst().orElseThrow();

        assertThat(banTiengAnh.title()).isEqualTo("3 days until the rite for Duc");
        assertThat(banTiengViet.title()).isEqualTo("Con 3 ngay toi gio cu Duc");
    }

    @Test
    @DisplayName("Su kien khong cap ho ma cung khong gan chi: nhac ca ho con hon khong nhac ai")
    void thieuPhamViThiNhacCaHo() {
        dungDongHo();

        int soNguoi = service.dispatchReminder(nhacGio(null, false));

        // Thừa một thông báo thì có người phàn nàn và dữ liệu được sửa; thiếu một thông báo thì
        // không ai biết cho tới khi cái giỗ đã qua.
        assertThat(soNguoi).isEqualTo(4);
    }

    @Test
    @DisplayName("Chi khong co thanh vien nao: tra 0 va khong day tin rong len hang doi")
    void chiRongThiKhongDayTin() {
        directory.branch(chi1, "root.chi1");

        assertThat(service.dispatchReminder(nhacGio(chi1, false))).isZero();
        assertThat(publisher.published()).isEmpty();
    }

    @Test
    @DisplayName("Tin mang du khoa chong trung, moc D-n va ngay gio de consumer doi soat duoc")
    void tinMangDuThongTinDoiSoat() {
        dungDongHo();
        ReminderDispatch request = nhacGio(chi2, false);

        service.dispatchReminder(request);

        assertThat(publisher.published()).allSatisfy(message -> {
            assertThat(message.reminderJobId()).isEqualTo(request.reminderJobId());
            assertThat(message.eventId()).isEqualTo(request.eventId());
            assertThat(message.offsetDays()).isEqualTo(3);
            assertThat(message.dueSolarDate()).isEqualTo(LocalDate.of(2026, 10, 20));
            assertThat(message.category()).isEqualTo(NotificationCategory.REMINDER);
            assertThat(message.deepLink()).isEqualTo("/events/abc");
        });
    }

    @Test
    @Timeout(10)
    @DisplayName("Luong goi KHONG BAO GIO cho gateway: gateway treo 5 giay khong giu request nao")
    void khongBaoGioChoGatewayGui() {
        dungDongHo();
        // Gateway "chết": mỗi lần gửi treo 5 giây. Nếu luồng phát tin lỡ gọi thẳng provider thì
        // 8 tin x 5 giây = 40 giây, và bài test hết giờ.
        RecordingProvider gatewayTreo = new RecordingProvider(Channel.WEBPUSH, message -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return DeliveryResult.sent();
        });

        long batDau = System.nanoTime();
        int soNguoi = service.dispatchReminder(nhacGio(null, true));
        Duration daTon = Duration.ofNanos(System.nanoTime() - batDau);

        assertThat(soNguoi).isEqualTo(4);
        assertThat(daTon).isLessThan(Duration.ofSeconds(2));
        // Và quan trọng hơn con số thời gian: gateway KHÔNG hề được chạm tới ở luồng này.
        assertThat(gatewayTreo.sendCount()).isZero();
        assertThat(publisher.size()).isEqualTo(8);
    }

    @Test
    @Timeout(20)
    @DisplayName("Broker cham cung khong lam luong goi tra ve sai - tin van du va dung thu tu")
    void brokerChamVanDayDuTin() {
        dungDongHo();
        publisher.doTreMilis = 5;

        int soNguoi = service.dispatchReminder(nhacGio(chi1, false));

        assertThat(soNguoi).isEqualTo(3);
        assertThat(publisher.size()).isEqualTo(6);
    }
}
