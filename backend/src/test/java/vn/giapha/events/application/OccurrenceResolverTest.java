package vn.giapha.events.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.calendar.application.LunarCalendarService;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventOccurrence;
import vn.giapha.events.domain.OccurrenceAdjustment;
import vn.giapha.shared.vo.LunarDate;

/**
 * Chính sách quy đổi "ngày âm chép trong gia phả" thành "ngày dương của năm nay".
 *
 * <p>Nguyên tắc xuyên suốt được kiểm ở đây: <b>chỉ được giỗ sớm, tuyệt đối không giỗ muộn</b>. Cúng
 * trước một ngày là tập quán có thật (cáo giỗ); cúng sau ngày giỗ thì không.</p>
 */
class OccurrenceResolverTest {

    private final LunarCalendarService calendar = new LunarCalendarService();
    private final OccurrenceResolver resolver = new OccurrenceResolver(calendar);
    private final LunarProbe probe = new LunarProbe(calendar);

    private static Event lunarEvent(LunarDate lunar) {
        return EventFixtures.gio(UUID.randomUUID(), UUID.randomUUID(), lunar, UUID.randomUUID());
    }

    @Test
    @DisplayName("Mung 1 thang Gieng nam am 2025 quy doi ra dung Tet duong lich 29/01/2025")
    void mungMotThangGiengRaDungTet() {
        Event event = lunarEvent(LunarDate.of(1990, 1, 1));

        Optional<EventOccurrence> occurrence =
                resolver.resolveLunar(event, LunarDate.of(1990, 1, 1), 2025);

        assertThat(occurrence).isPresent();
        assertThat(occurrence.get().dueSolarDate()).isEqualTo(LocalDate.of(2025, 1, 29));
        assertThat(occurrence.get().adjustment()).isEqualTo(OccurrenceAdjustment.EXACT);
        assertThat(occurrence.get().occurrenceYear()).isEqualTo(2025);
    }

    @Test
    @DisplayName("occurrenceYear la nam DUONG cua ngay gio, khong phai nam am truyen vao")
    void occurrenceYearLaNamDuong() {
        // Giỗ 25 tháng Chạp: ngày dương rơi sang tháng 1-2 của năm sau. Nếu occurrenceYear lấy theo
        // năm âm thì khoá chống trùng (event, occurrenceYear, offset) sẽ hiểu sai hai lần giỗ liền kề.
        Event event = lunarEvent(LunarDate.of(1990, 12, 25));

        EventOccurrence occurrence =
                resolver.resolveLunar(event, LunarDate.of(1990, 12, 25), 2025).orElseThrow();

        assertThat(occurrence.dueSolarDate().getYear()).isEqualTo(2026);
        assertThat(occurrence.occurrenceYear()).isEqualTo(2026);
    }

    @Nested
    @DisplayName("Thang nhuan")
    class ThangNhuan {

        @Test
        @DisplayName("Nam co thang nhuan: giu nguyen thang nhuan, khong lech")
        void namCoThangNhuanThiGiuNguyen() {
            int leapYear = probe.yearWithLeapMonth(6);
            Event event = lunarEvent(LunarDate.ofLeap(2000, 6, 10));

            EventOccurrence occurrence =
                    resolver.resolveLunar(event, LunarDate.ofLeap(2000, 6, 10), leapYear).orElseThrow();

            assertThat(occurrence.adjustment()).isEqualTo(OccurrenceAdjustment.EXACT);
            assertThat(occurrence.resolvedLunar().leapMonth()).isTrue();
            assertThat(occurrence.dueSolarDate()).isEqualTo(probe.solarOf(leapYear, 6, 10, true));
        }

        @Test
        @DisplayName("Nam khong nhuan thang ay: dung thang thuong cung so va noi ro ly do")
        void namKhongNhuanThiDungThangThuong() {
            int plainYear = probe.yearWithoutLeapMonth(6);
            Event event = lunarEvent(LunarDate.ofLeap(2000, 6, 10));

            EventOccurrence occurrence =
                    resolver.resolveLunar(event, LunarDate.ofLeap(2000, 6, 10), plainYear).orElseThrow();

            assertThat(occurrence.adjustment()).isEqualTo(OccurrenceAdjustment.LEAP_MONTH_ABSENT);
            assertThat(occurrence.resolvedLunar().leapMonth()).isFalse();
            assertThat(occurrence.resolvedLunar().month()).isEqualTo(6);
            assertThat(occurrence.resolvedLunar().day()).isEqualTo(10);
            assertThat(occurrence.adjustment().note()).isNotBlank();
        }

        @Test
        @DisplayName("Thang thuong luon dung TRUOC thang nhuan nen phuong an thay the khong muon hon")
        void thangThuongDungTruocThangNhuan() {
            int leapYear = probe.yearWithLeapMonth(6);

            LocalDate plain = probe.solarOf(leapYear, 6, 10, false);
            LocalDate leap = probe.solarOf(leapYear, 6, 10, true);

            assertThat(plain).isBefore(leap);
        }
    }

    @Nested
    @DisplayName("Thang thieu (khong co ngay 30)")
    class ThangThieu {

        @Test
        @DisplayName("Nam thang du: giu nguyen ngay 30")
        void thangDuThiGiuNguyen() {
            int fullYear = probe.yearWithFullMonth(12);
            Event event = lunarEvent(LunarDate.of(2000, 12, 30));

            EventOccurrence occurrence =
                    resolver.resolveLunar(event, LunarDate.of(2000, 12, 30), fullYear).orElseThrow();

            assertThat(occurrence.adjustment()).isEqualTo(OccurrenceAdjustment.EXACT);
            assertThat(occurrence.resolvedLunar().day()).isEqualTo(30);
        }

        @Test
        @DisplayName("Nam thang thieu: gio lui ve ngay 29, dung tap quan")
        void thangThieuThiLuiVe29() {
            int shortYear = probe.yearWithShortMonth(12);
            Event event = lunarEvent(LunarDate.of(2000, 12, 30));

            EventOccurrence occurrence =
                    resolver.resolveLunar(event, LunarDate.of(2000, 12, 30), shortYear).orElseThrow();

            assertThat(occurrence.adjustment()).isEqualTo(OccurrenceAdjustment.SHORT_MONTH);
            assertThat(occurrence.resolvedLunar().day()).isEqualTo(29);
            assertThat(occurrence.dueSolarDate()).isEqualTo(probe.solarOf(shortYear, 12, 29, false));
        }

        @Test
        @DisplayName("Ngay thay the luon SOM hon ngay goc, khong bao gio muon hon")
        void ngayThayTheLuonSomHon() {
            int shortYear = probe.yearWithShortMonth(12);
            Event event = lunarEvent(LunarDate.of(2000, 12, 30));

            EventOccurrence occurrence =
                    resolver.resolveLunar(event, LunarDate.of(2000, 12, 30), shortYear).orElseThrow();

            // Mùng 1 tháng Giêng năm sau là mốc "muộn hơn" gần nhất có thể bị chọn nhầm.
            LocalDate tetSauDo = probe.solarOf(shortYear + 1, 1, 1, false);
            assertThat(occurrence.dueSolarDate()).isBefore(tetSauDo);
            assertThat(occurrence.resolvedLunar().month()).isEqualTo(12);
        }
    }

    @Nested
    @DisplayName("Su kien theo duong lich")
    class DuongLich {

        @Test
        @DisplayName("29/02 cua nam khong nhuan lui ve 28/02 - cung nguyen tac chi lui som")
        void ngay29Thang2LuiVe28() {
            Event event = EventFixtures.solar(UUID.randomUUID(), LocalDate.of(2024, 2, 29), true);

            EventOccurrence occurrence = resolver.resolveSolar(event, 2025).orElseThrow();

            assertThat(occurrence.dueSolarDate()).isEqualTo(LocalDate.of(2025, 2, 28));
            assertThat(occurrence.adjustment()).isEqualTo(OccurrenceAdjustment.SHIFTED_EARLIER);
        }

        @Test
        @DisplayName("Su kien khong lap lai giu nguyen ngay goc, khong bi keo sang nam khac")
        void suKienKhongLapLaiGiuNguyenNgay() {
            Event event = EventFixtures.solar(UUID.randomUUID(), LocalDate.of(2024, 5, 20), false);

            EventOccurrence occurrence = resolver.resolveSolar(event, 2030).orElseThrow();

            assertThat(occurrence.dueSolarDate()).isEqualTo(LocalDate.of(2024, 5, 20));
            assertThat(occurrence.occurrenceYear()).isEqualTo(2024);
        }
    }

    @Test
    @DisplayName("Khong co ngay am hieu luc thi khong sinh lich nhac, khong doan bua")
    void khongCoNgayAmThiKhongSinh() {
        Event event = lunarEvent(LunarDate.of(1990, 3, 3));

        assertThat(resolver.resolveLunar(event, null, 2026)).isEmpty();
    }

    /**
     * {@code resolveAll} là nơi <b>duy nhất</b> biết "lặp hằng năm hay xảy ra một lần".
     *
     * <p>Trước khi có nó, phép chọn năm nằm rải ở {@code EventQueryService} và
     * {@code ReminderBatchGenerator}, và cả hai quét mọi năm trong tầm nhìn cho <i>mọi</i> sự kiện
     * âm lịch — nên một lễ khánh thành tạo bằng lối ghi mới sẽ lặp lại tới vô tận.</p>
     */
    @Nested
    @DisplayName("resolveAll: lap hang nam vs xay ra mot lan")
    class LapHayMotLan {

        private final List<Integer> tamNhinAm = List.of(2025, 2026, 2027);
        private final List<Integer> tamNhinDuong = List.of(2025, 2026, 2027);

        @Test
        @DisplayName("Su kien lap hang nam sinh mot lan xay ra cho MOI nam am trong tam nhin")
        void lapHangNamThiMoiNamMotLan() {
            Event gio = lunarEvent(LunarDate.of(1990, 3, 10));

            List<EventOccurrence> found =
                    resolver.resolveAll(gio, LunarDate.of(1990, 3, 10), tamNhinAm, tamNhinDuong);

            assertThat(found).hasSize(3);
            assertThat(found).extracting(EventOccurrence::dueSolarDate)
                    .doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("Su kien MOT LAN theo am lich chi co DUNG MOT lan xay ra, lay nam chep trong so")
        void motLanThiChiMotLan() {
            // Khánh thành từ đường: xảy ra đúng một lần, ngày âm mang sẵn năm.
            LunarDate ngayAm = LunarDate.of(2026, 2, 12);
            Event khanhThanh = Event.builder(UUID.randomUUID())
                    .type(vn.giapha.events.domain.EventType.KHANH_THANH)
                    .title("Khanh thanh tu duong")
                    .lunarDate(ngayAm)
                    .lunarBased(true)
                    .recurring(false)
                    .clanLevel(true)
                    .build();

            List<EventOccurrence> found =
                    resolver.resolveAll(khanhThanh, ngayAm, tamNhinAm, tamNhinDuong);

            assertThat(found).hasSize(1);
            assertThat(found.get(0).dueSolarDate()).isEqualTo(probe.solarOf(2026, 2, 12, false));
        }

        @Test
        @DisplayName("Thang nhuan trong resolveAll cho cung mot cau tra loi voi resolveLunar")
        void thangNhuanTraLoiGiongResolveLunar() {
            // Đây là bất biến quan trọng nhất của đợt này: màn lịch và bộ nhắc giỗ phải trả lời
            // GIỐNG HỆT nhau về cùng một ngày. Cả hai đi qua resolveAll -> resolveLunar, nên phép
            // giải tháng nhuận chỉ có một bản. Bài test này ghim điều đó lại.
            int namKhongNhuan = probe.yearWithoutLeapMonth(6);
            LunarDate ngayAmNhuan = LunarDate.ofLeap(2017, 6, 3);
            Event event = lunarEvent(ngayAmNhuan);

            EventOccurrence quaResolveAll = resolver
                    .resolveAll(event, ngayAmNhuan, List.of(namKhongNhuan), List.of())
                    .get(0);
            EventOccurrence quaResolveLunar =
                    resolver.resolveLunar(event, ngayAmNhuan, namKhongNhuan).orElseThrow();

            assertThat(quaResolveAll).isEqualTo(quaResolveLunar);
            assertThat(quaResolveAll.adjustment()).isEqualTo(OccurrenceAdjustment.LEAP_MONTH_ABSENT);
        }

        @Test
        @DisplayName("Su kien mot lan theo am lich ma thieu nam thi bo qua, khong doan bua")
        void motLanThieuNamThiBoQua() {
            // ck_event_oneoff_lunar_year (V18) chặn ca này ở CSDL, nhưng dòng ghi trước V18 vẫn có
            // thể như vậy. Đoán một năm nào đó nghĩa là nhắc cả họ một cái lễ không có thật.
            Event event = lunarEvent(LunarDate.of(1990, 3, 10));
            Event motLan = event.toBuilder().recurring(false).lunarDate(LunarDate.of(2030, 3, 10))
                    .build();

            assertThat(resolver.resolveAll(motLan, LunarDate.of(0, 3, 10), tamNhinAm, tamNhinDuong))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("lunarYearOf tra dung nam am cua mot ngay duong o ranh gioi Tet")
    void lunarYearOfTraDungNamAm() {
        // 28/01/2025 vẫn thuộc năm âm 2024 (Tết 2025 rơi vào 29/01) - ranh giới hay bị nhầm nhất.
        assertThat(resolver.lunarYearOf(LocalDate.of(2025, 1, 28))).isEqualTo(2024);
        assertThat(resolver.lunarYearOf(LocalDate.of(2025, 1, 29))).isEqualTo(2025);
    }
}
