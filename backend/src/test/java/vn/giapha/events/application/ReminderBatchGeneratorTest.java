package vn.giapha.events.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.calendar.application.LunarCalendarService;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.ReminderJob;
import vn.giapha.events.domain.ReminderPlan;
import vn.giapha.shared.vo.LunarDate;

/**
 * Từ {@code ReminderPlan} tới các dòng {@code reminder_job}.
 *
 * <p>Ba tính chất được kiểm ở đây, và cả ba đều là thứ chỉ lộ ra trong sản xuất nếu sai:
 * <b>idempotent</b> (chạy lại không nhân đôi lịch nhắc), <b>đúng nguồn ngày giỗ</b>
 * ({@code person.death_lunar}, kể cả tháng nhuận), và <b>một sự kiện hỏng không làm câm lịch nhắc
 * của cả dòng họ</b>.</p>
 */
class ReminderBatchGeneratorTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LunarCalendarService calendar = new LunarCalendarService();
    private final LunarProbe probe = new LunarProbe(calendar);
    private final OccurrenceResolver resolver = new OccurrenceResolver(calendar);

    private final InMemoryReminderJobRepository jobs = new InMemoryReminderJobRepository();
    private final InMemoryEventSubjectPort subjects = new InMemoryEventSubjectPort();
    private final ReminderBatchGenerator generator =
            new ReminderBatchGenerator(subjects, jobs, resolver);

    private static final ReminderPlan PLAN = ReminderPlan.defaultPlan(VN);

    /** Đầu năm 2026 dương lịch, đủ xa mọi ngày giỗ dùng trong bài test. */
    private static final LocalDate HOM_NAY = LocalDate.of(2026, 3, 1);
    private static final Instant BAY_GIO = HOM_NAY.atStartOfDay(VN).toInstant();

    @Test
    @DisplayName("Mot lan gio sinh dung ba job: D-7, D-3, D-1")
    void baMocChoMotLanGio() {
        UUID eventId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        Event gio = EventFixtures.gio(eventId, personId, LunarDate.of(1985, 3, 10), UUID.randomUUID());
        subjects.add(EventFixtures.deceased(personId, LunarDate.of(1985, 3, 10), null));

        int created = generator.generate(List.of(gio), PLAN, List.of(2026), List.of(2026),
                HOM_NAY, BAY_GIO);

        LocalDate ngayGio = probe.solarOf(2026, 3, 10, false);
        assertThat(created).isEqualTo(3);
        assertThat(jobs.all()).extracting(ReminderJob::offsetDays).containsExactlyInAnyOrder(1, 3, 7);
        assertThat(jobs.all()).allSatisfy(job -> {
            assertThat(job.dueSolarDate()).isEqualTo(ngayGio);
            assertThat(job.eventId()).isEqualTo(eventId);
            assertThat(job.occurrenceYear()).isEqualTo(ngayGio.getYear());
        });
        // Mỗi job bắn đúng D-n ngày trước ngày giỗ, lúc 07:00 giờ Việt Nam.
        assertThat(jobs.all()).allSatisfy(job -> assertThat(job.fireAt())
                .isEqualTo(ngayGio.minusDays(job.offsetDays()).atTime(7, 0).atZone(VN).toInstant()));
    }

    @Test
    @DisplayName("Chay lai lan hai KHONG sinh trung - chan tai chi muc duy nhat cua CSDL")
    void chayLaiKhongSinhTrung() {
        UUID personId = UUID.randomUUID();
        Event gio = EventFixtures.gio(UUID.randomUUID(), personId, LunarDate.of(1985, 3, 10), null);
        subjects.add(EventFixtures.deceased(personId, LunarDate.of(1985, 3, 10), null));

        int lanDau = generator.generate(List.of(gio), PLAN, List.of(2026), List.of(2026), HOM_NAY, BAY_GIO);
        int lanHai = generator.generate(List.of(gio), PLAN, List.of(2026), List.of(2026), HOM_NAY, BAY_GIO);

        assertThat(lanDau).isEqualTo(3);
        assertThat(lanHai).isZero();
        assertThat(jobs.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("Gio lay theo death_lunar cua ho so, ke ca khi ngay ay la THANG NHUAN")
    void gioLayTheoDeathLunarThangNhuan() {
        int namNhuan = probe.yearWithLeapMonth(6);
        UUID personId = UUID.randomUUID();
        // Bản ghi sự kiện chép nhầm sang tháng 3; hồ sơ nhân khẩu mới là nguồn chân lý.
        Event gio = EventFixtures.gio(UUID.randomUUID(), personId, LunarDate.of(1985, 3, 10), null);
        subjects.add(EventFixtures.deceased(personId, LunarDate.ofLeap(1985, 6, 10), null));

        LocalDate truocNgayGio = probe.solarOf(namNhuan, 6, 10, true).minusMonths(2);
        generator.generate(List.of(gio), PLAN, List.of(namNhuan), List.of(namNhuan),
                truocNgayGio, truocNgayGio.atStartOfDay(VN).toInstant());

        assertThat(jobs.all()).isNotEmpty();
        assertThat(jobs.all()).allSatisfy(job -> assertThat(job.dueSolarDate())
                .isEqualTo(probe.solarOf(namNhuan, 6, 10, true)));
    }

    @Test
    @DisplayName("Su kien da xoa mem khong con lam phien ca ho")
    void suKienXoaMemKhongSinhLichNhac() {
        Event daXoa = Event.builder(UUID.randomUUID())
                .type(vn.giapha.events.domain.EventType.GIO_TO)
                .title("Gio To ho Nguyen")
                .lunarDate(LunarDate.of(1700, 3, 10))
                .lunarBased(true)
                .recurring(true)
                .clanLevel(true)
                .deleted(true)
                .build();

        int created = generator.generate(List.of(daXoa), PLAN, List.of(2026), List.of(2026),
                HOM_NAY, BAY_GIO);

        assertThat(created).isZero();
        assertThat(jobs.all()).isEmpty();
    }

    @Test
    @DisplayName("Su kien mot lan (khong lap) khong sinh lich nhac hang nam")
    void suKienKhongLapKhongSinh() {
        Event motLan = EventFixtures.solar(UUID.randomUUID(), LocalDate.of(2026, 6, 1), false);

        assertThat(generator.generate(List.of(motLan), PLAN, List.of(2026), List.of(2026),
                HOM_NAY, BAY_GIO)).isZero();
    }

    @Test
    @DisplayName("Lan gio da qua trong nam nay bi bo qua, khong nhac nguoc ve qua khu")
    void lanGioDaQuaBiBoQua() {
        UUID personId = UUID.randomUUID();
        Event gio = EventFixtures.gio(UUID.randomUUID(), personId, LunarDate.of(1985, 3, 10), null);
        subjects.add(EventFixtures.deceased(personId, LunarDate.of(1985, 3, 10), null));

        LocalDate cuoiNam = LocalDate.of(2026, 12, 31);
        int created = generator.generate(List.of(gio), PLAN, List.of(2026), List.of(2026),
                cuoiNam, cuoiNam.atStartOfDay(VN).toInstant());

        assertThat(created).isZero();
    }

    @Test
    @DisplayName("Them su kien sat ngay gio: chi con moc D-1, khong ban ba tin mau thuan")
    void satNgayGioChiConMocMotNgay() {
        UUID personId = UUID.randomUUID();
        Event gio = EventFixtures.gio(UUID.randomUUID(), personId, LunarDate.of(1985, 3, 10), null);
        subjects.add(EventFixtures.deceased(personId, LunarDate.of(1985, 3, 10), null));

        LocalDate ngayGio = probe.solarOf(2026, 3, 10, false);
        LocalDate haiNgayTruoc = ngayGio.minusDays(2);

        int created = generator.generate(List.of(gio), PLAN, List.of(2026), List.of(2026),
                haiNgayTruoc, haiNgayTruoc.atStartOfDay(VN).toInstant());

        assertThat(created).isEqualTo(1);
        assertThat(jobs.all()).extracting(ReminderJob::offsetDays).containsExactly(1);
    }

    @Test
    @DisplayName("Mot su kien hong khong duoc lam cam lich nhac cua ca dong ho")
    void motSuKienHongKhongChanCaLo() {
        UUID hong = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        Event suKienHong = EventFixtures.gio(hong, personId, LunarDate.of(1985, 3, 10), null);
        Event suKienLanh = EventFixtures.gioTo(UUID.randomUUID(), LunarDate.of(1700, 3, 12));
        subjects.add(EventFixtures.deceased(personId, LunarDate.of(1985, 3, 10), null));

        // Ghi job của sự kiện hỏng ném lỗi hạ tầng; sự kiện còn lại vẫn phải có đủ lịch nhắc.
        ReminderBatchGenerator fragile = new ReminderBatchGenerator(subjects,
                new ThrowingReminderJobRepository(jobs, hong), resolver);

        int created = fragile.generate(List.of(suKienHong, suKienLanh), PLAN, List.of(2026),
                List.of(2026), HOM_NAY, BAY_GIO);

        assertThat(created).isEqualTo(3);
        assertThat(jobs.all()).allSatisfy(job -> assertThat(job.eventId()).isNotEqualTo(hong));
    }

    @Test
    @DisplayName("Su kien cap dong ho khong gan nhan khau: khong duoc ne NullPointerException")
    void suKienCapDongHoKhongCoNhanKhau() {
        Event gioTo = EventFixtures.gioTo(UUID.randomUUID(), LunarDate.of(1700, 3, 10));

        int created = generator.generate(List.of(gioTo), PLAN, List.of(2026), List.of(2026),
                HOM_NAY, BAY_GIO);

        assertThat(created).isEqualTo(3);
    }

    /** Bọc kho job thật, ném lỗi cho đúng một sự kiện — mô phỏng một dòng dữ liệu hỏng. */
    private record ThrowingReminderJobRepository(InMemoryReminderJobRepository delegate, UUID eventBiHong)
            implements vn.giapha.events.domain.port.ReminderJobRepository {

        @Override
        public boolean insertIfAbsent(ReminderJob job) {
            if (job.eventId().equals(eventBiHong)) {
                throw new IllegalStateException("Loi ha tang gia lap khi ghi reminder_job");
            }
            return delegate.insertIfAbsent(job);
        }

        @Override
        public List<ReminderJob> claimDue(Instant now, int limit) {
            return delegate.claimDue(now, limit);
        }

        @Override
        public void markStatus(UUID jobId, vn.giapha.events.domain.ReminderStatus status, String error) {
            delegate.markStatus(jobId, status, error);
        }
    }
}
