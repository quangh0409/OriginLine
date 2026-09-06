package vn.giapha.calendar.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import vn.giapha.shared.vo.LunarDate;

/**
 * W4 · {@code historical-zone.csv} — nhung nam am lich phap dinh cua Viet Nam KHONG tinh theo GMT+7.
 *
 * <p>Y nghia nghiep vu: cu mat nam 1965 co ngay gio ghi theo lich luu hanh nam 1965, ma lich mien
 * Bac nam 1965 tinh theo mui gio thu 8. Neu he thong cu lay GMT+7 quy doi thi ca ho gio lech mot
 * ngay — loai sai khong bao gio tu lo ra.</p>
 */
class VietnamLunarZoneTest {

    static List<LunarFixtures.Row> historicalZone() {
        return LunarFixtures.verifiedRows("historical-zone.csv");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("historicalZone")
    @DisplayName("Mui gio phap dinh cua nam lich su duoc chon dung theo moc 1968")
    void muiGioPhapDinhDuocChonDungTheoMoc1968(LunarFixtures.Row row) {
        LocalDate solar = row.date("solar_date");

        assertThat(VietnamLunarZone.offsetHoursFor(solar))
                .as("mui gio tinh am lich cho ngay %s", solar)
                .isEqualTo(row.decimal("zone_offset_hours"));
        assertThat(VietnamLunarZone.offsetHoursForYear(solar.getYear()))
                .isEqualTo(row.decimal("zone_offset_hours"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("historicalZone")
    @DisplayName("Dung mui gio lich su thi ra dung ngay am ghi trong gia pha")
    void dungMuiGioLichSuRaDungNgayAmTrongGiaPha(LunarFixtures.Row row) {
        LocalDate solar = row.date("solar_date");

        LunarDate actual = LunarConverter.toLunar(solar, VietnamLunarZone.offsetHoursFor(solar));

        assertThat(actual).isEqualTo(new LunarDate(row.integer("expected_lunar_year"),
                row.integer("expected_lunar_month"), row.integer("expected_lunar_day"), false));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("historicalZone")
    @DisplayName("Neu lay GMT+7 cho nam lich su thi ra ngay am KHAC - chung minh mui gio la yeu to quyet dinh")
    void layGmt7ChoNamLichSuThiRaNgayKhac(LunarFixtures.Row row) {
        LocalDate solar = row.date("solar_date");
        LunarDate wrong = row.lunarMonthDayYear("wrong_if_gmt7");

        LunarDate withGmt7 = LunarConverter.toLunar(solar, LunarConverter.VIETNAM_ZONE_HOURS);
        LunarDate withHistoricalZone = LunarConverter.toLunar(solar, VietnamLunarZone.offsetHoursFor(solar));

        assertThat(withGmt7.year()).isEqualTo(wrong.year());
        assertThat(withGmt7.month()).isEqualTo(wrong.month());
        assertThat(withGmt7.day()).isEqualTo(wrong.day());
        assertThat(withGmt7)
                .as("GMT+7 phai cho ket qua KHAC voi mui gio lich su, neu khong fixture vo nghia")
                .isNotEqualTo(withHistoricalZone);
    }

    @Test
    @DisplayName("Truoc 1968 dung mui gio thu 8, tu 1968 tro di dung mui gio thu 7")
    void mocChuyenMuiGioLaNam1968() {
        assertThat(VietnamLunarZone.offsetHoursForYear(1967)).isEqualTo(LunarConverter.CHINA_ZONE_HOURS);
        assertThat(VietnamLunarZone.offsetHoursForYear(1968)).isEqualTo(LunarConverter.VIETNAM_ZONE_HOURS);
        assertThat(VietnamLunarZone.offsetHoursFor(LocalDate.of(1967, 12, 31)))
                .isEqualTo(LunarConverter.CHINA_ZONE_HOURS);
        assertThat(VietnamLunarZone.offsetHoursFor(LocalDate.of(1968, 1, 1)))
                .isEqualTo(LunarConverter.VIETNAM_ZONE_HOURS);
    }

    @Test
    @DisplayName("Giai doan 1968-1975 hai mien dung hai lich chinh thuc khac nhau - phai canh bao nguoi nhap lieu")
    void giaiDoan1968Den1975CoHaiLichChinhThuc() {
        assertThat(VietnamLunarZone.hasTwoOfficialCalendars(LocalDate.of(1967, 12, 31))).isFalse();
        assertThat(VietnamLunarZone.hasTwoOfficialCalendars(LocalDate.of(1968, 1, 1))).isTrue();
        assertThat(VietnamLunarZone.hasTwoOfficialCalendars(LocalDate.of(1972, 6, 15))).isTrue();
        assertThat(VietnamLunarZone.hasTwoOfficialCalendars(LocalDate.of(1975, 12, 31))).isTrue();
        assertThat(VietnamLunarZone.hasTwoOfficialCalendars(LocalDate.of(1976, 1, 1)))
                .as("tu 1976 ca nuoc dung mot lich")
                .isFalse();
    }

    @Test
    @DisplayName("Truoc 1813 am lich Viet Nam khong tai lap duoc bang cong thuc thien van")
    void truoc1813KhongTaiLapDuocBangCongThucThienVan() {
        assertThat(VietnamLunarZone.isReconstructible(LocalDate.of(1812, 12, 31))).isFalse();
        assertThat(VietnamLunarZone.isReconstructible(LocalDate.of(1808, 1, 28))).isFalse();
        assertThat(VietnamLunarZone.isReconstructible(LocalDate.of(1813, 1, 1))).isTrue();
        assertThat(VietnamLunarZone.isReconstructible(LocalDate.of(1965, 2, 2))).isTrue();
    }

    @Test
    @DisplayName("Tet 1965 mien Bac: mui gio thu 8 cho mung 1, GMT+7 cho mung 2 - lech dung mot ngay")
    void tet1965LechMotNgayGiuaHaiMuiGio() {
        LocalDate tet1965 = LocalDate.of(1965, 2, 2);

        assertThat(LunarConverter.toLunar(tet1965, LunarConverter.CHINA_ZONE_HOURS))
                .isEqualTo(LunarDate.of(1965, 1, 1));
        assertThat(LunarConverter.toLunar(tet1965, LunarConverter.VIETNAM_ZONE_HOURS))
                .isEqualTo(LunarDate.of(1965, 1, 2));
    }
}
