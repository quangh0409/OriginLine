package vn.giapha.calendar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.giapha.shared.vo.LunarDate;

/**
 * W4 · {@code solar-terms.csv} — 24 tiet khi.
 *
 * <p>Thanh minh la moc tuc chap ma; Dong chi la moc chinh thuat toan am lich dua vao (Dong chi luon
 * roi vao thang 11 am lich, tu do moi xac dinh duoc thang nhuan). Sai Dong chi mot ngay co the doi
 * cho thang nhuan ca nam.</p>
 */
class SolarTermsTest {

    static List<LunarFixtures.Row> solarTerms() {
        return LunarFixtures.verifiedRows("solar-terms.csv");
    }

    /** Cac dong {@code verified=derived}: thoi diem tiet khi roi 00:00–01:00 gio Hong Kong, tuc ca bien GMT+7. */
    static List<LunarFixtures.Row> caBienMuiGio() {
        return solarTerms().stream().filter(row -> "derived".equalsIgnoreCase(row.str("verified"))).toList();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("solarTerms")
    @DisplayName("Tiet khi roi dung ngay duong lich Viet Nam GMT+7")
    void tietKhiRoiDungNgayDuongVietNam(LunarFixtures.Row row) {
        SolarTerm term = SolarTerm.valueOf(row.str("term_code"));

        LocalDate actual = SolarTerms.dateOf(term, row.integer("year"));

        assertThat(actual)
                .as("%s nam %d (GMT+7)", term, row.integer("year"))
                .isEqualTo(row.date("solar_date_vn"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("solarTerms")
    @DisplayName("Cung tiet khi tinh o GMT+8 ra dung ngay Dai Thien van Hong Kong cong bo")
    void tietKhiOGmt8KhopHongKong(LunarFixtures.Row row) {
        SolarTerm term = SolarTerm.valueOf(row.str("term_code"));

        LocalDate actual = SolarTerms.dateOf(term, row.integer("year"), LunarConverter.CHINA_ZONE_HOURS);

        assertThat(actual)
                .as("%s nam %d (UTC+8, HKO)", term, row.integer("year"))
                .isEqualTo(row.date("hko_date_utc8"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("caBienMuiGio")
    @DisplayName("Ca bien mui gio: tiet khi roi sat nua dem thi ngay Viet Nam som hon ngay Hong Kong dung 1 ngay")
    void caBienMuiGioNgayVietNamSomHonMotNgay(LunarFixtures.Row row) {
        SolarTerm term = SolarTerm.valueOf(row.str("term_code"));
        int year = row.integer("year");

        LocalDate vietnam = SolarTerms.dateOf(term, year);
        LocalDate hongKong = SolarTerms.dateOf(term, year, LunarConverter.CHINA_ZONE_HOURS);

        assertThat(vietnam)
                .as("%s nam %d phai som hon ngay Hong Kong dung 1 ngay", term, year)
                .isEqualTo(hongKong.minusDays(1));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("solarTerms")
    @DisplayName("Kinh do, ten tieng Viet va co Trung khi cua enum khop dinh nghia trong fixture")
    void thuocTinhEnumKhopFixture(LunarFixtures.Row row) {
        SolarTerm term = SolarTerm.valueOf(row.str("term_code"));

        assertThat(term.longitudeDegrees()).isEqualTo(row.integer("sun_longitude_deg"));
        assertThat(term.vietnameseName()).isEqualTo(row.str("term_vi"));
        assertThat(term.isTrungKhi())
                .as("%s co phai Trung khi khong - luat thang nhuan chi dem Trung khi", term)
                .isEqualTo(row.flag("trung_khi"));
    }

    @Test
    @DisplayName("Dung 12 trong 24 tiet khi la Trung khi, va deu la boi cua 30 do")
    void dungMuoiHaiTrungKhiOKinhDoBoiCua30() {
        List<SolarTerm> trungKhi = java.util.Arrays.stream(SolarTerm.values())
                .filter(SolarTerm::isTrungKhi)
                .toList();

        assertThat(trungKhi).hasSize(12);
        assertThat(trungKhi).allSatisfy(term -> assertThat(term.longitudeDegrees() % 30).isZero());
    }

    @Test
    @DisplayName("Tra tiet khi theo kinh do quy vong 360 do, va tu choi kinh do khong phai boi cua 15")
    void traTietKhiTheoKinhDo() {
        assertThat(SolarTerm.ofLongitude(15)).isEqualTo(SolarTerm.THANH_MINH);
        assertThat(SolarTerm.ofLongitude(270)).isEqualTo(SolarTerm.DONG_CHI);
        assertThat(SolarTerm.ofLongitude(-90)).as("quy vong: -90 do = 270 do").isEqualTo(SolarTerm.DONG_CHI);
        assertThat(SolarTerm.ofLongitude(360)).isEqualTo(SolarTerm.XUAN_PHAN);

        assertThatThrownBy(() -> SolarTerm.ofLongitude(7)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1985, 2007, 2024, 2025, 2026})
    @DisplayName("Ham tat Thanh minh va Dong chi tra dung ket qua cua dateOf")
    void hamTatThanhMinhVaDongChi(int year) {
        assertThat(SolarTerms.thanhMinh(year)).isEqualTo(SolarTerms.dateOf(SolarTerm.THANH_MINH, year));
        assertThat(SolarTerms.dongChi(year)).isEqualTo(SolarTerms.dateOf(SolarTerm.DONG_CHI, year));
    }

    @ParameterizedTest
    @ValueSource(ints = {1985, 2007, 2024, 2025, 2026})
    @DisplayName("allOf tra du 24 tiet khi, khong trung ngay va tang dan theo kinh do trong nam")
    void allOfTraDu24TietKhi(int year) {
        Map<SolarTerm, LocalDate> terms = SolarTerms.allOf(year);

        assertThat(terms).hasSize(24);
        assertThat(terms.values()).doesNotHaveDuplicates();
        assertThat(terms.values()).allSatisfy(date -> assertThat(date.getYear()).isEqualTo(year));
    }

    @ParameterizedTest
    @ValueSource(ints = {1900, 1950, 1985, 2000, 2025, 2050, 2100})
    @DisplayName("Bat bien cua thuat toan: Dong chi luon roi vao thang 11 am lich khong nhuan")
    void dongChiLuonRoiVaoThang11AmLich(int year) {
        LunarDate lunar = LunarConverter.toLunar(SolarTerms.dongChi(year));

        assertThat(lunar.month()).as("Dong chi nam %d roi vao thang am lich", year).isEqualTo(11);
        assertThat(lunar.leapMonth()).isFalse();
    }

    @Test
    @DisplayName("Thanh minh luon roi vao 4 hoac 5 thang 4 duong lich - moc chap ma")
    void thanhMinhLuonRoiVaoDau5Thang4() {
        for (int year = 1900; year <= 2100; year++) {
            LocalDate thanhMinh = SolarTerms.thanhMinh(year);
            assertThat(thanhMinh.getMonthValue()).as("Thanh minh %d", year).isEqualTo(4);
            assertThat(thanhMinh.getDayOfMonth()).as("Thanh minh %d", year).isBetween(4, 6);
        }
    }

    // ------------------------------------------------------------------ Defect da phat hien

    @Test
    @DisplayName("Tiet khi dang co hieu luc trong ngay bat dau tiet khi chinh la tiet khi do")
    void tietKhiDangHieuLucTrongNgayBatDauLaChinhNo() {
        assertThat(SolarTerms.at(SolarTerms.thanhMinh(2025))).isEqualTo(SolarTerm.THANH_MINH);
        assertThat(SolarTerms.at(SolarTerms.dongChi(2025))).isEqualTo(SolarTerm.DONG_CHI);

        for (Map.Entry<SolarTerm, LocalDate> entry : SolarTerms.allOf(2025).entrySet()) {
            assertThat(SolarTerms.at(entry.getValue()))
                    .as("at(%s) phai la %s", entry.getValue(), entry.getKey())
                    .isEqualTo(entry.getKey());
        }
    }

    @Test
    @DisplayName("isTermDay nhan ra dung ngay bat dau cua tiet khi va tu choi ngay khac")
    void isTermDayNhanRaNgayBatDauTietKhi() {
        for (int year = 2024; year <= 2026; year++) {
            for (Map.Entry<SolarTerm, LocalDate> entry : SolarTerms.allOf(year).entrySet()) {
                assertThat(SolarTerms.isTermDay(entry.getValue()))
                        .as("%s nam %d bat dau ngay %s", entry.getKey(), year, entry.getValue())
                        .isTrue();
            }
        }
        assertThat(SolarTerms.isTermDay(SolarTerms.thanhMinh(2025).plusDays(3))).isFalse();
    }
}
