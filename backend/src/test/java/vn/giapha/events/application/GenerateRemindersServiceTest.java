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
import vn.giapha.events.domain.ReminderJob;
import vn.giapha.shared.vo.LunarDate;

/**
 * Vòng quét sinh lịch nhắc: phân lô, tầm nhìn hai năm âm lịch, và tính idempotent của cả lượt chạy.
 */
class GenerateRemindersServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LunarCalendarService calendar = new LunarCalendarService();
    private final LunarProbe probe = new LunarProbe(calendar);
    private final OccurrenceResolver resolver = new OccurrenceResolver(calendar);

    private final InMemoryReminderJobRepository jobs = new InMemoryReminderJobRepository();
    private final InMemoryEventSubjectPort subjects = new InMemoryEventSubjectPort();
    private final InMemoryEventRepository events = new InMemoryEventRepository();
    private final ReminderProperties properties = new ReminderProperties();

    private GenerateRemindersService service() {
        return new GenerateRemindersService(events, jobs,
                new ReminderBatchGenerator(subjects, jobs, resolver), resolver, properties);
    }

    @Test
    @DisplayName("Sinh truoc HAI nam am lich: gio thang Chap khong bi bo lo khi sang nam moi")
    void sinhTruocHaiNamAmLich() {
        // Giỗ 25 tháng Chạp: ngày dương của nó rơi vào tháng 1-2 của năm DƯƠNG kế tiếp. Chỉ sinh cho
        // năm âm hiện tại thì cái giỗ ấy chỉ có lịch nhắc sau khi đã sang năm âm mới - tức là sau
        // khi nó đã qua.
        events.add(EventFixtures.gioTo(UUID.randomUUID(), LunarDate.of(1700, 12, 25)));
        LocalDate homNay = LocalDate.of(2026, 3, 1);

        ReminderGenerationResult result = service().generateFor(homNay, homNay.atStartOfDay(VN).toInstant());

        assertThat(result.lunarYears()).containsExactly(2026, 2027);
        // Hai lần giỗ, mỗi lần ba mốc.
        assertThat(result.createdJobs()).isEqualTo(6);
        assertThat(jobs.all()).extracting(ReminderJob::occurrenceYear)
                .containsOnly(2027, 2028);
    }

    @Test
    @DisplayName("Quet theo lo: bang event lon khong bi keo het vao bo nho")
    void quetTheoLo() {
        for (int i = 0; i < 5; i++) {
            events.add(EventFixtures.gioTo(UUID.randomUUID(), LunarDate.of(1700, 3, 10 + i)));
        }
        properties.setBatchSize(2);
        LocalDate homNay = LocalDate.of(2026, 3, 1);

        ReminderGenerationResult result = service().generateFor(homNay, homNay.atStartOfDay(VN).toInstant());

        assertThat(result.scannedEvents()).isEqualTo(5);
        // 3 lô: 2 + 2 + 1. Lô cuối nhỏ hơn kích thước lô nên vòng quét dừng, không gọi lô rỗng thứ tư.
        assertThat(events.pageCalls).isEqualTo(3);
    }

    @Test
    @DisplayName("Chay lai lan hai: quet y nguyen nhung KHONG tao them job nao")
    void chayLaiKhongTaoThem() {
        events.add(EventFixtures.gioTo(UUID.randomUUID(), LunarDate.of(1700, 3, 10)));
        LocalDate homNay = LocalDate.of(2026, 3, 1);
        Instant bayGio = homNay.atStartOfDay(VN).toInstant();

        ReminderGenerationResult lanDau = service().generateFor(homNay, bayGio);
        ReminderGenerationResult lanHai = service().generateFor(homNay, bayGio);

        assertThat(lanDau.createdJobs()).isPositive();
        assertThat(lanHai.scannedEvents()).isEqualTo(lanDau.scannedEvents());
        assertThat(lanHai.createdJobs()).isZero();
        assertThat(lanHai.createdNothing()).isTrue();
        assertThat(jobs.size()).isEqualTo(lanDau.createdJobs());
    }

    @Test
    @DisplayName("Tat scheduler thi job dem khong cham vao co so du lieu")
    void tatSchedulerThiKhongChayGi() {
        events.add(EventFixtures.gioTo(UUID.randomUUID(), LunarDate.of(1700, 3, 10)));
        properties.setSchedulerEnabled(false);

        service().generateNightly();

        assertThat(events.pageCalls).isZero();
        assertThat(jobs.all()).isEmpty();
    }

    @Test
    @DisplayName("Su kien theo duong lich dung nam DUONG, khong bi quy doi am lich nham")
    void suKienDuongLichDungNamDuong() {
        events.add(EventFixtures.solar(UUID.randomUUID(), LocalDate.of(2020, 6, 15), true));
        LocalDate homNay = LocalDate.of(2026, 3, 1);

        ReminderGenerationResult result = service().generateFor(homNay, homNay.atStartOfDay(VN).toInstant());

        assertThat(result.solarYears()).containsExactly(2026, 2027);
        assertThat(jobs.all()).extracting(ReminderJob::dueSolarDate)
                .contains(LocalDate.of(2026, 6, 15), LocalDate.of(2027, 6, 15));
    }

    @Test
    @DisplayName("Gio ca nhan lay ngay tu death_lunar, khong tu ban ghi su kien")
    void gioCaNhanLayTheoHoSo() {
        UUID personId = UUID.randomUUID();
        events.add(EventFixtures.gio(UUID.randomUUID(), personId, LunarDate.of(1985, 1, 1), null));
        subjects.add(EventFixtures.deceased(personId, LunarDate.of(1985, 3, 10), null));
        LocalDate homNay = LocalDate.of(2026, 3, 1);

        service().generateFor(homNay, homNay.atStartOfDay(VN).toInstant());

        assertThat(jobs.all()).extracting(ReminderJob::dueSolarDate)
                .contains(probe.solarOf(2026, 3, 10, false))
                .doesNotContain(probe.solarOf(2026, 1, 1, false));
    }

    @Test
    @DisplayName("Bang event rong: khong no, khong tao gi, van tra ve ket qua doc duoc")
    void bangEventRong() {
        LocalDate homNay = LocalDate.of(2026, 3, 1);

        ReminderGenerationResult result = service().generateFor(homNay, homNay.atStartOfDay(VN).toInstant());

        assertThat(result.scannedEvents()).isZero();
        assertThat(result.createdJobs()).isZero();
        assertThat(result.referenceDate()).isEqualTo(homNay);
        assertThat(result.duration()).isNotNull();
    }

    @Test
    @DisplayName("Su kien da xoa mem khong lot vao vong quet")
    void suKienXoaMemKhongLotVaoVongQuet() {
        Event daXoa = Event.builder(UUID.randomUUID())
                .type(vn.giapha.events.domain.EventType.GIO_TO)
                .title("Gio To")
                .lunarDate(LunarDate.of(1700, 3, 10))
                .lunarBased(true)
                .recurring(true)
                .clanLevel(true)
                .deleted(true)
                .build();
        events.add(daXoa);
        LocalDate homNay = LocalDate.of(2026, 3, 1);

        ReminderGenerationResult result = service().generateFor(homNay, homNay.atStartOfDay(VN).toInstant());

        assertThat(result.scannedEvents()).isZero();
        assertThat(result.createdJobs()).isZero();
    }
}
