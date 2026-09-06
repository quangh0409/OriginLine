package vn.giapha.events.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import vn.giapha.calendar.application.LunarCalendarService;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventType;
import vn.giapha.events.domain.ReminderJob;
import vn.giapha.shared.vo.LunarDate;

/**
 * Endpoint chạy tay {@code POST /api/v1/admin/reminders/generate} — phần nghiệp vụ của nó.
 *
 * <p>Đây là thứ mở khoá bản demo đầu-cuối: không có nó thì bảng {@code reminder_job} chỉ có dữ liệu
 * sau 01:30 sáng, và một môi trường dựng lên rồi tắt trong ngày sẽ không bao giờ nhắc ai.</p>
 */
class ReminderGenerationTriggerTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LunarCalendarService calendar = new LunarCalendarService();
    private final OccurrenceResolver resolver = new OccurrenceResolver(calendar);

    private final InMemoryReminderJobRepository jobs = new InMemoryReminderJobRepository();
    private final InMemoryEventSubjectPort subjects = new InMemoryEventSubjectPort();
    private final InMemoryEventRepository events = new InMemoryEventRepository();
    private final ReminderProperties properties = new ReminderProperties();

    private ReminderGenerationTrigger trigger() {
        return new ReminderGenerationTrigger(new GenerateRemindersService(events,
                new ReminderBatchGenerator(subjects, jobs, resolver), resolver, properties), properties);
    }

    /**
     * Dựng một sự kiện có ngày giỗ rơi đúng vào {@code ngayDuong}.
     *
     * <p>Ngày âm được suy ngược từ ngày dương thay vì gõ tay: bài test phải chạy đúng ở mọi thời
     * điểm trong năm, kể cả khi ai đó chạy lại nó sau ba năm nữa.</p>
     */
    private Event gioVaoNgay(LocalDate ngayDuong) {
        LunarDate lunar = calendar.toLunar(ngayDuong);
        assertThat(lunar).as("khong quy doi duoc %s sang am lich", ngayDuong).isNotNull();
        return EventFixtures.gioTo(UUID.randomUUID(), lunar);
    }

    @Test
    @DisplayName("Khong truyen ngay: lay hom nay theo gio Viet Nam")
    void macDinhLaHomNay() {
        LocalDate homNay = LocalDate.now(VN);
        events.add(gioVaoNgay(homNay.plusDays(30)));

        ReminderGenerationResult result = trigger().runNow(null);

        assertThat(result.referenceDate()).isEqualTo(homNay);
        assertThat(result.createdJobs()).isPositive();
    }

    @Test
    @DisplayName("Ngay tham chieu trong qua khu dung lai duoc ca ba moc 7/3/1 da troi qua")
    void ngayThamChieuQuaKhuDungLaiDuocCacMoc() {
        LocalDate homNay = LocalDate.now(VN);
        LocalDate ngayGioDaQua = homNay.minusDays(5);
        events.add(gioVaoNgay(ngayGioDaQua));

        // Chạy với "hôm nay" thật: cái giỗ đã qua nên không còn mốc nào để sinh.
        assertThat(jobsCoNgayGio(ngayGioDaQua)).isEmpty();
        trigger().runNow(null);
        assertThat(jobsCoNgayGio(ngayGioDaQua)).isEmpty();

        // Lùi ngày tham chiếu về trước cái giỗ: cả ba mốc sống lại, và fire_at của chúng nằm trong
        // quá khứ - đúng thứ cần để lượt đẩy kế tiếp gửi thật trong bản demo.
        ReminderGenerationResult result = trigger().runNow(homNay.minusDays(20));

        assertThat(result.referenceDate()).isEqualTo(homNay.minusDays(20));
        assertThat(result.createdJobs()).isPositive();
        assertThat(jobsCoNgayGio(ngayGioDaQua)).hasSize(3);
        assertThat(jobsCoNgayGio(ngayGioDaQua)).extracting(ReminderJob::offsetDays)
                .containsExactlyInAnyOrder(1, 3, 7);
        assertThat(jobsCoNgayGio(ngayGioDaQua))
                .allSatisfy(job -> assertThat(job.fireAt()).isBefore(Instant.now()));
    }

    @Test
    @DisplayName("Bam hai lan cung mot ngay tham chieu: lan hai tao 0 job (idempotent)")
    void bamHaiLanKhongSinhTrung() {
        LocalDate homNay = LocalDate.now(VN);
        events.add(gioVaoNgay(homNay.plusDays(30)));

        ReminderGenerationResult lanDau = trigger().runNow(homNay);
        int truocKhiBamLai = jobs.size();
        ReminderGenerationResult lanHai = trigger().runNow(homNay);

        assertThat(lanDau.createdJobs()).isPositive();
        assertThat(lanHai.createdJobs()).isZero();
        assertThat(jobs.size()).isEqualTo(truocKhiBamLai);
    }

    @Test
    @DisplayName("Go nham nam: ngay tham chieu lech qua mot nam bi tu choi (400 Problem Details)")
    void ngayThamChieuLechQuaXaBiTuChoi() {
        LocalDate homNay = LocalDate.now(VN);

        assertThatThrownBy(() -> trigger().runNow(homNay.plusDays(400)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referenceDate");
        assertThatThrownBy(() -> trigger().runNow(homNay.minusDays(400)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jobs.all()).isEmpty();
    }

    @Test
    @DisplayName("Bien +/- mot nam van duoc chap nhan")
    void bienMotNamVanChayDuoc() {
        LocalDate homNay = LocalDate.now(VN);

        assertThat(trigger().runNow(homNay.plusDays(ReminderGenerationTrigger.MAX_DRIFT_DAYS))).isNotNull();
        assertThat(trigger().runNow(homNay.minusDays(ReminderGenerationTrigger.MAX_DRIFT_DAYS))).isNotNull();
    }

    @Test
    @Timeout(10)
    @DisplayName("Bam lai khi luot truoc chua xong: tra 409 ngay, khong treo luong servlet")
    void bamLaiKhiDangChayThiTra409() throws Exception {
        CountDownLatch dangQuet = new CountDownLatch(1);
        CountDownLatch choThaRa = new CountDownLatch(1);
        ReminderGenerationTrigger trigger = new ReminderGenerationTrigger(
                new GenerateRemindersService(new BlockingEventRepository(dangQuet, choThaRa),
                        new ReminderBatchGenerator(subjects, jobs, resolver), resolver, properties),
                properties);

        AtomicReference<Throwable> loiCuaLuongNen = new AtomicReference<>();
        Thread luotDauTien = new Thread(() -> {
            try {
                trigger.runNow(null);
            } catch (Throwable ex) {
                loiCuaLuongNen.set(ex);
            }
        }, "luot-sinh-lich-nhac-dau-tien");
        luotDauTien.start();

        assertThat(dangQuet.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> trigger.runNow(null))
                .isInstanceOf(ReminderGenerationBusyException.class);

        choThaRa.countDown();
        luotDauTien.join(5_000);
        assertThat(loiCuaLuongNen.get()).isNull();

        // Lượt trước xong thì chốt được nhả ra ngay, không kẹt vĩnh viễn.
        assertThat(trigger.runNow(null)).isNotNull();
    }

    private List<ReminderJob> jobsCoNgayGio(LocalDate ngayGio) {
        return jobs.all().stream().filter(job -> job.dueSolarDate().equals(ngayGio)).toList();
    }

    /** Kho sự kiện chặn ở lô đầu tiên để mô phỏng một lượt quét đang chạy dở. */
    private record BlockingEventRepository(CountDownLatch dangQuet, CountDownLatch choThaRa)
            implements vn.giapha.events.domain.port.EventRepository {

        @Override
        public java.util.Optional<Event> findById(UUID id) {
            return java.util.Optional.empty();
        }

        @Override
        public List<Event> findRecurringPage(int page, int size) {
            dangQuet.countDown();
            try {
                choThaRa.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }

        @Override
        public List<Event> search(List<EventType> types, UUID personId, UUID branchId, boolean includeDeleted) {
            return List.of();
        }
    }
}
