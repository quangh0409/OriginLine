package vn.giapha.calendar.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.giapha.shared.vo.LunarDate;

/**
 * W4 · property test — bat loi bien ma fixture viet tay khong phu het.
 *
 * <p>Chay bang vong lap thuong, khong them thu vien. Toan bo lop nay quet ~73.400 ngay theo ca hai
 * chieu va van xong trong vai giay vi {@link LunarConverter} la so hoc thuan.</p>
 *
 * <p>Cac bat bien o day <b>doc lap voi bang tra</b>: chung khong noi ngay am nao la dung, chung noi
 * hai chieu quy doi phai nhat quan voi nhau. Mot loi lam lech co thang nhuan giua {@code toLunar}
 * va {@code toSolar} se lam do bo nay ke ca khi tung fixture rieng le van xanh.</p>
 */
class LunarRoundTripPropertyTest {

    private static final LocalDate FROM = LocalDate.of(1900, 1, 1);
    private static final LocalDate TO = LocalDate.of(2100, 12, 31);

    @Test
    @DisplayName("Moi ngay duong 1900-2100 doi sang am roi nguoc lai phai ve dung chinh no (GMT+7)")
    void quyDoiKhuHoiDuongSangAmRoiNguocLai() {
        List<String> failures = new ArrayList<>();
        int checked = 0;

        for (LocalDate solar = FROM; !solar.isAfter(TO); solar = solar.plusDays(1)) {
            LunarDate lunar = LunarConverter.toLunar(solar);
            LocalDate back = LunarConverter.toSolar(lunar);
            checked++;
            if (!back.equals(solar)) {
                if (failures.size() < 20) {
                    failures.add(solar + " -> " + lunar + " -> " + back);
                }
            }
        }

        assertThat(checked).as("so ngay duong tu 1900-01-01 den 2100-12-31").isEqualTo(73414);
        assertThat(failures).as("cac ngay khong khu hoi duoc (20 dong dau)").isEmpty();
    }

    @ParameterizedTest
    @ValueSource(doubles = {LunarConverter.VIETNAM_ZONE_HOURS, LunarConverter.CHINA_ZONE_HOURS})
    @DisplayName("Khu hoi van dung o ca hai mui gio - GMT+7 va GMT+8")
    void quyDoiKhuHoiDungOCaHaiMuiGio(double zoneOffsetHours) {
        List<String> failures = new ArrayList<>();

        for (LocalDate solar = FROM; !solar.isAfter(TO); solar = solar.plusDays(1)) {
            LunarDate lunar = LunarConverter.toLunar(solar, zoneOffsetHours);
            LocalDate back = LunarConverter.toSolar(lunar, zoneOffsetHours);
            if (!back.equals(solar) && failures.size() < 20) {
                failures.add(solar + " -> " + lunar + " -> " + back);
            }
        }

        assertThat(failures).isEmpty();
    }

    @Test
    @DisplayName("Chieu nguoc lai: moi ngay am hop le cua 1900-2100 doi sang duong roi ve phai giu nguyen co nhuan")
    void quyDoiKhuHoiAmSangDuongRoiNguocLai() {
        List<String> failures = new ArrayList<>();
        int checked = 0;

        for (int year = 1900; year <= 2100; year++) {
            int leapMonth = LunarConverter.leapMonthOf(year);
            for (int month = 1; month <= 12; month++) {
                checked += checkMonth(year, month, false, failures);
                if (leapMonth == month) {
                    checked += checkMonth(year, month, true, failures);
                }
            }
        }

        assertThat(checked).isGreaterThan(73000);
        assertThat(failures).as("cac ngay am khong khu hoi duoc (20 dong dau)").isEmpty();
    }

    private int checkMonth(int year, int month, boolean leap, List<String> failures) {
        LunarDate first = new LunarDate(year, month, 1, leap);
        int length = LunarConverter.lengthOfMonth(first);
        if (length != 29 && length != 30) {
            failures.add("thang " + first + " dai " + length + " ngay - phai la 29 hoac 30");
            return 0;
        }
        for (int day = 1; day <= length; day++) {
            LunarDate lunar = new LunarDate(year, month, day, leap);
            LunarDate back = LunarConverter.toLunar(LunarConverter.toSolar(lunar));
            if (!back.equals(lunar) && failures.size() < 20) {
                failures.add(lunar + " -> " + LunarConverter.toSolar(lunar) + " -> " + back);
            }
        }
        return length;
    }

    @Test
    @DisplayName("Ngay duong lien tiep cho ngay am lien tiep - khong nhay coc, khong lap ngay")
    void ngayDuongLienTiepChoNgayAmLienTiep() {
        List<String> failures = new ArrayList<>();
        LunarDate previous = null;

        for (LocalDate solar = FROM; !solar.isAfter(TO); solar = solar.plusDays(1)) {
            LunarDate current = LunarConverter.toLunar(solar);
            if (previous != null) {
                boolean sameMonth = current.day() == previous.day() + 1;
                boolean newMonth = current.day() == 1 && (previous.day() == 29 || previous.day() == 30);
                if (!sameMonth && !newMonth && failures.size() < 20) {
                    failures.add(solar + ": " + previous + " -> " + current);
                }
            }
            previous = current;
        }

        assertThat(failures).isEmpty();
    }

    @Test
    @DisplayName("Moi thang am lich 1900-2100 dai dung 29 hoac 30 ngay")
    void moiThangAmLichDai29Hoac30Ngay() {
        List<String> failures = new ArrayList<>();

        for (int year = 1900; year <= 2100; year++) {
            int leapMonth = LunarConverter.leapMonthOf(year);
            for (int month = 1; month <= 12; month++) {
                int length = LunarConverter.lengthOfMonth(LunarDate.of(year, month, 1));
                if (length != 29 && length != 30) {
                    failures.add(month + "/" + year + " = " + length + " ngay");
                }
                if (leapMonth == month) {
                    int leapLength = LunarConverter.lengthOfMonth(LunarDate.ofLeap(year, month, 1));
                    if (leapLength != 29 && leapLength != 30) {
                        failures.add(month + " nhuan/" + year + " = " + leapLength + " ngay");
                    }
                }
            }
        }

        assertThat(failures).isEmpty();
    }

    @Test
    @DisplayName("Nam am lich thuong co 353-355 ngay, nam nhuan co 383-385 ngay")
    void doDaiNamAmLichNamTrongKhoangThienVan() {
        List<String> failures = new ArrayList<>();

        for (int year = 1900; year <= 2099; year++) {
            long start = LunarConverter.toSolar(LunarDate.of(year, 1, 1)).toEpochDay();
            long next = LunarConverter.toSolar(LunarDate.of(year + 1, 1, 1)).toEpochDay();
            long length = next - start;
            boolean leap = LunarConverter.isLeapYear(year);
            long low = leap ? 383 : 353;
            long high = leap ? 385 : 355;
            if (length < low || length > high) {
                failures.add("nam " + year + (leap ? " (nhuan)" : "") + " dai " + length + " ngay");
            }
        }

        assertThat(failures).isEmpty();
    }

    @Test
    @DisplayName("Nam ngoai khoang 1800-2199 bi tu choi tuong minh o ca hai chieu")
    void namNgoaiKhoangHoTroBiTuChoi() {
        assertThat(LunarConverter.MIN_SUPPORTED_YEAR).isEqualTo(1800);
        assertThat(LunarConverter.MAX_SUPPORTED_YEAR).isEqualTo(2199);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> LunarConverter.toLunar(LocalDate.of(1799, 12, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1800-2199");
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> LunarConverter.toLunar(LocalDate.of(2200, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> LunarConverter.toSolar(LunarDate.of(1799, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("So ngay Julius doi xuoi roi doi nguoc lai giu nguyen ngay duong")
    void soNgayJuliusDoiXuoiNguocGiuNguyen() {
        for (LocalDate solar = FROM; !solar.isAfter(TO); solar = solar.plusDays(1)) {
            long jd = LunarConverter.toJulianDayNumber(solar);
            assertThat(LunarConverter.fromJulianDayNumber(jd)).isEqualTo(solar);
        }
    }
}
