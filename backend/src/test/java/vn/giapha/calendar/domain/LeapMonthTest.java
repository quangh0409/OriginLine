package vn.giapha.calendar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.giapha.shared.vo.LunarDate;

/**
 * W4 · {@code leap-months.csv} — thang nhuan la nguon bug so mot cua lich am.
 *
 * <p>Bo test khang dinh <b>ca hai chieu</b>: nam co trong fixture phai nhuan dung thang do, va moi
 * nam 1990–2040 khong co trong fixture phai la nam thuong. Chi kiem mot chieu thi mot hien thuc
 * bao "nam nao cung nhuan" van xanh.</p>
 */
class LeapMonthTest {

    static List<LunarFixtures.Row> leapMonths() {
        return LunarFixtures.verifiedRows("leap-months.csv");
    }

    static IntStream namThuong() {
        Set<Integer> leapYears = leapMonths().stream()
                .map(row -> row.integer("lunar_year"))
                .collect(Collectors.toSet());
        return IntStream.rangeClosed(1990, 2040).filter(year -> !leapYears.contains(year));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("leapMonths")
    @DisplayName("Nam nhuan bao dung so hieu thang nhuan cua bang tra Ho Ngoc Duc")
    void namNhuanBaoDungSoHieuThangNhuan(LunarFixtures.Row row) {
        int year = row.integer("lunar_year");
        int expected = row.integer("leap_month");

        assertThat(LunarConverter.isLeapYear(year)).as("nam %d phai la nam nhuan", year).isTrue();
        assertThat(LunarConverter.leapMonthOf(year)).as("thang nhuan cua nam %d", year).isEqualTo(expected);
        assertThat(LunarConverter.hasLeapMonth(year, expected)).isTrue();
        assertThat(LunarConverter.hasLeapMonth(year, expected == 12 ? 1 : expected + 1))
                .as("nam %d chi co dung mot thang nhuan", year)
                .isFalse();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("leapMonths")
    @DisplayName("Thang nhuan bat dau dung ngay duong lich va co dung so ngay 29 hoac 30")
    void thangNhuanBatDauDungNgayVaDuSoNgay(LunarFixtures.Row row) {
        int year = row.integer("lunar_year");
        int month = row.integer("leap_month");
        LunarDate firstDay = LunarDate.ofLeap(year, month, 1);

        assertThat(LunarConverter.toSolar(firstDay))
                .as("ngay duong cua mung 1 thang %d nhuan nam %d", month, year)
                .isEqualTo(row.date("leap_month_start_solar"));
        assertThat(LunarConverter.lengthOfMonth(firstDay))
                .as("so ngay cua thang %d nhuan nam %d", month, year)
                .isEqualTo(row.integer("leap_month_days"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("leapMonths")
    @DisplayName("Ngay duong dau thang nhuan doi nguoc lai van mang co nhuan")
    void ngayDuongDauThangNhuanDoiNguocVanMangCoNhuan(LunarFixtures.Row row) {
        LunarDate actual = LunarConverter.toLunar(row.date("leap_month_start_solar"));

        assertThat(actual.leapMonth()).as("phai la thang NHUAN, khong phai thang thuong").isTrue();
        assertThat(actual.month()).isEqualTo(row.integer("leap_month"));
        assertThat(actual.day()).isEqualTo(1);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("leapMonths")
    @DisplayName("Thang nhuan cua lich Trung Quoc GMT+8 khop Dai Thien van Hong Kong")
    void thangNhuanTrungQuocTheoGmt8(LunarFixtures.Row row) {
        int year = row.integer("lunar_year");
        int chinaLeapMonth = row.integer("china_leap_month");

        assertThat(LunarConverter.leapMonthOf(year, LunarConverter.CHINA_ZONE_HOURS))
                .as("thang nhuan lich Trung Quoc nam %d", year)
                .isEqualTo(chinaLeapMonth);
        assertThat(LunarConverter.toSolar(LunarDate.ofLeap(year, chinaLeapMonth, 1),
                LunarConverter.CHINA_ZONE_HOURS))
                .as("ngay bat dau thang nhuan lich Trung Quoc nam %d", year)
                .isEqualTo(row.date("china_leap_start_solar"));
    }

    @ParameterizedTest
    @MethodSource("namThuong")
    @DisplayName("Nam khong co trong fixture la nam thuong - khong duoc bao nhuan")
    void namKhongCoTrongFixtureLaNamThuong(int year) {
        assertThat(LunarConverter.isLeapYear(year)).as("nam %d phai la nam thuong", year).isFalse();
        assertThat(LunarConverter.leapMonthOf(year)).isZero();
    }

    @Test
    @DisplayName("Nam 2033 nhuan thang 11 - bay kinh dien: thang nhuan khong phai luc nao cung giua nam")
    void nam2033NhuanThang11() {
        assertThat(LunarConverter.leapMonthOf(2033)).isEqualTo(11);
        assertThat(LunarConverter.toSolar(LunarDate.ofLeap(2033, 11, 1))).isEqualTo(LocalDate.of(2033, 12, 22));
    }

    @Test
    @DisplayName("Nam 1995 va 2031: cung so hieu thang nhuan nhung Viet Nam bat dau som hon Trung Quoc 1 ngay")
    void nam1995Va2031ThangNhuanLechMotNgaySoVoiTrungQuoc() {
        assertThat(LunarConverter.toSolar(LunarDate.ofLeap(1995, 8, 1))).isEqualTo(LocalDate.of(1995, 9, 24));
        assertThat(LunarConverter.toSolar(LunarDate.ofLeap(1995, 8, 1), LunarConverter.CHINA_ZONE_HOURS))
                .isEqualTo(LocalDate.of(1995, 9, 25));

        assertThat(LunarConverter.toSolar(LunarDate.ofLeap(2031, 3, 1))).isEqualTo(LocalDate.of(2031, 4, 21));
        assertThat(LunarConverter.toSolar(LunarDate.ofLeap(2031, 3, 1), LunarConverter.CHINA_ZONE_HOURS))
                .isEqualTo(LocalDate.of(2031, 4, 22));
    }

    @Test
    @DisplayName("Xin thang nhuan cua mot nam thuong bi tu choi, khong im lang tra ve thang ke tiep")
    void xinThangNhuanCuaNamThuongBiTuChoi() {
        assertThatThrownBy(() -> LunarConverter.toSolar(LunarDate.ofLeap(2024, 3, 1)))
                .isInstanceOf(NoSuchLunarDateException.class)
                .hasMessageContaining("2024")
                .hasMessageContaining("nam thuong");
    }

    @Test
    @DisplayName("Xin sai so hieu thang nhuan bi tu choi kem theo so hieu dung")
    void xinSaiSoHieuThangNhuanBiTuChoi() {
        assertThatThrownBy(() -> LunarConverter.toSolar(LunarDate.ofLeap(2025, 5, 1)))
                .isInstanceOf(NoSuchLunarDateException.class)
                .hasMessageContaining("thang nhuan cua nam nay la thang 6");
    }

    @ParameterizedTest
    @ValueSource(ints = {2025, 2028, 2031})
    @DisplayName("Xin ngay 30 cua mot thang thieu bi tu choi - ngay gio khong duoc lang le troi sang thang sau")
    void xinNgay30CuaThangThieuBiTuChoi(int year) {
        int shortMonth = IntStream.rangeClosed(1, 12)
                .filter(month -> LunarConverter.lengthOfMonth(LunarDate.of(year, month, 1)) == 29)
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() -> LunarConverter.toSolar(LunarDate.of(year, shortMonth, 30)))
                .isInstanceOf(NoSuchLunarDateException.class)
                .hasMessageContaining("chi co 29 ngay");
    }
}
