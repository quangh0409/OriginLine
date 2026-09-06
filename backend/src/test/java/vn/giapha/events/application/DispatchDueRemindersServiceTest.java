package vn.giapha.events.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.calendar.application.LunarCalendarService;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventOccurrence;
import vn.giapha.events.domain.EventType;
import vn.giapha.events.domain.OccurrenceAdjustment;
import vn.giapha.events.domain.ReminderJob;
import vn.giapha.events.domain.ReminderStatus;
import vn.giapha.notification.application.FakeRecipientDirectory;
import vn.giapha.notification.application.NotificationDispatchService;
import vn.giapha.notification.application.RecordingNotificationPublisher;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.Recipient;
import vn.giapha.notification.domain.port.NotificationPublisher;
import vn.giapha.shared.vo.LunarDate;

/**
 * Lượt quét đẩy lịch nhắc đã tới giờ lên hàng đợi.
 *
 * <p>Trọng tâm là các ca <b>không được gửi</b>: giỗ đã qua, sự kiện bị xoá mềm, sự kiện biến mất.
 * Gửi nhầm những ca này không gây lỗi kỹ thuật nào cả — nó chỉ khiến cả họ nhận tin nhắc một cái
 * giỗ đã cúng xong, và đó là kiểu sai làm mất niềm tin nhanh nhất.</p>
 */
class DispatchDueRemindersServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate HOM_NAY = LocalDate.of(2026, 10, 19);
    private static final Instant BAY_GIO = HOM_NAY.atTime(7, 30).atZone(VN).toInstant();
    private static final LocalDate NGAY_GIO = LocalDate.of(2026, 10, 20);

    private final InMemoryReminderJobRepository jobs = new InMemoryReminderJobRepository();
    private final InMemoryEventRepository events = new InMemoryEventRepository();
    private final InMemoryEventSubjectPort subjects = new InMemoryEventSubjectPort();
    private final ReminderProperties properties = new ReminderProperties();
    private final RecordingNotificationPublisher publisher = new RecordingNotificationPublisher();
    private final FakeRecipientDirectory directory = new FakeRecipientDirectory();

    private final UUID branchId = UUID.randomUUID();
    private final UUID nguoiTrongChi = UUID.randomUUID();

    private DispatchDueRemindersService service() {
        return service(publisher);
    }

    private DispatchDueRemindersService service(NotificationPublisher publisherPort) {
        return new DispatchDueRemindersService(jobs, events, subjects,
                new ReminderMessageFactory(new OccurrenceResolver(new LunarCalendarService())),
                new NotificationDispatchService(directory, publisherPort), properties);
    }

    private UUID lenLichNhac(Event event, LocalDate ngayGio, int offsetDays) {
        events.add(event);
        directory.branch(branchId, "root.chi1")
                .member(new Recipient(nguoiTrongChi, UUID.randomUUID(), "vi"), "root.chi1");
        EventOccurrence occurrence = new EventOccurrence(ngayGio, ngayGio.getYear(),
                LunarDate.of(2026, 9, 10), OccurrenceAdjustment.EXACT);
        ReminderJob job = ReminderJob.pending(UUID.randomUUID(), event.id(), occurrence, offsetDays,
                ngayGio.minusDays(offsetDays).atTime(7, 0).atZone(VN).toInstant());
        jobs.insertIfAbsent(job);
        return job.id();
    }

    /** Giỗ của một cụ thuộc chi {@code root.chi1}, kèm ảnh chụp nhân khẩu. */
    private Event gioSapToi() {
        UUID personId = UUID.randomUUID();
        subjects.add(EventFixtures.deceased(personId, LunarDate.of(1985, 9, 10),
                EventFixtures.branch(branchId, "root.chi1")));
        return EventFixtures.gio(UUID.randomUUID(), personId, LunarDate.of(1985, 9, 10), branchId);
    }

    @Test
    @DisplayName("Job den gio: day len hang doi va danh dau SENT")
    void jobDenGioThiDayDi() {
        UUID jobId = lenLichNhac(gioSapToi(), NGAY_GIO, 1);

        int dispatched = service().dispatchDueAt(BAY_GIO, HOM_NAY);

        assertThat(dispatched).isEqualTo(1);
        assertThat(jobs.statusOf(jobId)).isEqualTo(ReminderStatus.SENT);
        assertThat(publisher.published()).isNotEmpty();
        assertThat(publisher.published()).allSatisfy(message -> {
            assertThat(message.reminderJobId()).isEqualTo(jobId);
            assertThat(message.dueSolarDate()).isEqualTo(NGAY_GIO);
            assertThat(message.offsetDays()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("Job chua toi gio thi khong bi giat len som")
    void jobChuaToiGioThiChua() {
        // Mốc D-1 bắn 07:00 ngày 19/10; lượt quét lúc 08:00 ngày 18/10 chưa được đụng tới nó.
        lenLichNhac(gioSapToi(), NGAY_GIO, 1);

        int dispatched = service().dispatchDueAt(
                HOM_NAY.minusDays(1).atTime(8, 0).atZone(VN).toInstant(), HOM_NAY.minusDays(1));

        assertThat(dispatched).isZero();
        assertThat(publisher.published()).isEmpty();
    }

    @Test
    @DisplayName("Ngay gio da qua: HUY chu khong ban - va la CANCELLED, khong phai FAILED")
    void gioDaQuaThiHuy() {
        UUID jobId = lenLichNhac(gioSapToi(), NGAY_GIO, 1);

        // Hệ thống ngừng chạy vài ngày rồi bật lại: job "đã tới giờ" nhưng cái giỗ thì đã xong.
        int dispatched = service().dispatchDueAt(BAY_GIO.plusSeconds(5 * 86_400), NGAY_GIO.plusDays(3));

        assertThat(dispatched).isZero();
        assertThat(publisher.published()).isEmpty();
        assertThat(jobs.statusOf(jobId)).isEqualTo(ReminderStatus.CANCELLED);
        assertThat(jobs.statusChanges)
                .anySatisfy(change -> assertThat(change.status()).isEqualTo(ReminderStatus.CANCELLED));
    }

    @Test
    @DisplayName("Su kien da xoa mem: huy lich nhac, khong lam phien ca ho nua")
    void suKienXoaMemThiHuy() {
        Event daXoa = Event.builder(UUID.randomUUID())
                .type(EventType.GIO_TO)
                .title("Gio To")
                .lunarDate(LunarDate.of(1700, 9, 10))
                .lunarBased(true)
                .recurring(true)
                .clanLevel(true)
                .deleted(true)
                .build();
        UUID jobId = lenLichNhac(daXoa, NGAY_GIO, 1);

        assertThat(service().dispatchDueAt(BAY_GIO, HOM_NAY)).isZero();
        assertThat(jobs.statusOf(jobId)).isEqualTo(ReminderStatus.CANCELLED);
        assertThat(publisher.published()).isEmpty();
    }

    @Test
    @DisplayName("Su kien khong con ton tai: huy job thay vi no NullPointerException")
    void suKienBienMatThiHuy() {
        EventOccurrence occurrence = new EventOccurrence(NGAY_GIO, 2026, null, OccurrenceAdjustment.EXACT);
        ReminderJob mocCoi = ReminderJob.pending(UUID.randomUUID(), UUID.randomUUID(), occurrence, 1,
                NGAY_GIO.minusDays(1).atTime(7, 0).atZone(VN).toInstant());
        jobs.insertIfAbsent(mocCoi);

        assertThat(service().dispatchDueAt(BAY_GIO, HOM_NAY)).isZero();
        assertThat(jobs.statusOf(mocCoi.id())).isEqualTo(ReminderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Broker chet: job tra ve PENDING de luot sau thu lai, KHONG danh dau da gui")
    void brokerChetThiTraVePending() {
        UUID jobId = lenLichNhac(gioSapToi(), NGAY_GIO, 1);
        NotificationPublisher brokerChet = message -> {
            throw new IllegalStateException("RabbitMQ khong phan hoi");
        };

        int dispatched = service(brokerChet).dispatchDueAt(BAY_GIO, HOM_NAY);

        assertThat(dispatched).isZero();
        // PENDING chứ không SENT: ghi "đã gửi" khi broker chết là cách chắc chắn nhất để cả họ mất
        // thông báo mà nhật ký vẫn nói mọi thứ ổn.
        assertThat(jobs.statusOf(jobId)).isEqualTo(ReminderStatus.PENDING);
    }

    @Test
    @DisplayName("claimDue giu job lai: luot quet thu hai khong lay lai chinh job do")
    void claimDueKhongTraLaiJobDaGiu() {
        lenLichNhac(gioSapToi(), NGAY_GIO, 1);

        int lanDau = service().dispatchDueAt(BAY_GIO, HOM_NAY);
        int lanHai = service().dispatchDueAt(BAY_GIO, HOM_NAY);

        assertThat(lanDau).isEqualTo(1);
        assertThat(lanHai).isZero();
    }

    @Test
    @DisplayName("Chi thanh vien CUNG CHI (va nhanh con) nhan nhac, khong phai ca ho")
    void chiThanhVienCungChiNhanNhac() {
        UUID nguoiChiKhac = UUID.randomUUID();
        UUID nguoiNhanhCon = UUID.randomUUID();
        lenLichNhac(gioSapToi(), NGAY_GIO, 1);
        directory.member(new Recipient(nguoiNhanhCon, UUID.randomUUID(), "vi"), "root.chi1.nhanh2")
                .member(new Recipient(nguoiChiKhac, UUID.randomUUID(), "vi"), "root.chi2");

        service().dispatchDueAt(BAY_GIO, HOM_NAY);

        assertThat(publisher.recipientIds(Channel.INAPP))
                .containsExactlyInAnyOrder(nguoiTrongChi, nguoiNhanhCon)
                .doesNotContain(nguoiChiKhac);
    }

    @Test
    @DisplayName("Noi dung thong bao mang du ngay duong, ngay am va dia diem - khong co du lieu Tang 3")
    void noiDungThongBaoDuThongTin() {
        lenLichNhac(gioSapToi(), NGAY_GIO, 1);

        service().dispatchDueAt(BAY_GIO, HOM_NAY);

        NotificationMessage message = publisher.forChannel(Channel.INAPP).get(0);
        assertThat(message.title()).contains("Ngay mai la");
        assertThat(message.body()).contains("20/10/2026").contains("am lich");
        assertThat(message.deepLink()).startsWith("/events/");
    }
}
