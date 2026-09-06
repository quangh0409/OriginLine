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
 * W4 · {@code divergence.csv} — <b>bo test gia tri nhat cua W4</b>.
 *
 * <p>Neu hien thuc nham mui gio (+8 thay vi +7) thi tet.csv, leap-months.csv, solar-terms.csv van
 * co the xanh gan het; chi rieng bo nay do. Moi dong la mot thang am lich ma lich Viet Nam (GMT+7)
 * bat dau som hon lich Trung Quoc (GMT+8) — tru dot 23/11/1984–19/4/1985 khi hai lich dat thang
 * nhuan khac cho nen so hieu thang lech <b>ca thang</b>.</p>
 *
 * <p>Nguon hai cot doc lap nhau: {@code vn_*} tu bang tra Ho Ngoc Duc, {@code cn_*} tu Dai Thien van
 * Hong Kong.</p>
 */
class TimeZoneDivergenceTest {

    static List<LunarFixtures.Row> divergence() {
        return LunarFixtures.verifiedRows("divergence.csv");
    }

    static List<LunarFixtures.Row> lechMotNgay() {
        return divergence().stream().filter(row -> "day_shift".equals(row.str("kind"))).toList();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("divergence")
    @DisplayName("Tai ngay mo dau doan lech, quy doi GMT+7 ra dung ngay am lich Viet Nam")
    void quyDoiGmt7RaDungNgayAmLichVietNam(LunarFixtures.Row row) {
        LocalDate from = row.date("from_solar");

        LunarDate actual = LunarConverter.toLunar(from, LunarConverter.VIETNAM_ZONE_HOURS);

        assertThat(actual)
                .as("lich Viet Nam (GMT+7) tai %s", from)
                .isEqualTo(new LunarDate(actual.year(), row.integer("vn_month"), row.integer("vn_day"),
                        row.flag("vn_leap")));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("divergence")
    @DisplayName("Cung ngay do, quy doi GMT+8 ra dung ngay am lich Trung Quoc - khac han ket qua GMT+7")
    void quyDoiGmt8RaDungNgayAmLichTrungQuoc(LunarFixtures.Row row) {
        LocalDate from = row.date("from_solar");

        LunarDate china = LunarConverter.toLunar(from, LunarConverter.CHINA_ZONE_HOURS);
        LunarDate vietnam = LunarConverter.toLunar(from, LunarConverter.VIETNAM_ZONE_HOURS);

        assertThat(china)
                .as("lich Trung Quoc (GMT+8) tai %s", from)
                .isEqualTo(new LunarDate(china.year(), row.integer("cn_month"), row.integer("cn_day"),
                        row.flag("cn_leap")));
        assertThat(china)
                .as("day la doan LECH: hai mui gio khong duoc cho cung ket qua tai %s", from)
                .isNotEqualTo(vietnam);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("lechMotNgay")
    @DisplayName("Doan lech mot ngay: thang am lich Viet Nam bat dau dung tai from_solar va keo dai het to_solar")
    void thangAmLichVietNamTrongDoanLechDungDoDai(LunarFixtures.Row row) {
        LocalDate from = row.date("from_solar");
        LocalDate to = row.date("to_solar");
        int days = row.integer("days");

        LunarDate monthStart = LunarConverter.toLunar(from, LunarConverter.VIETNAM_ZONE_HOURS);

        assertThat(monthStart.day()).as("from_solar phai la mung 1 am lich").isEqualTo(1);
        assertThat(LunarConverter.lengthOfMonth(monthStart, LunarConverter.VIETNAM_ZONE_HOURS))
                .as("do dai thang am lich bat dau %s", from)
                .isEqualTo(days);
        assertThat(to.toEpochDay() - from.toEpochDay() + 1)
                .as("cot days cua fixture phai khop khoang from_solar..to_solar")
                .isEqualTo(days);
        assertThat(LunarConverter.toLunar(to, LunarConverter.VIETNAM_ZONE_HOURS).month())
                .as("ngay cuoi doan van thuoc cung thang am lich")
                .isEqualTo(monthStart.month());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("lechMotNgay")
    @DisplayName("Doan lech mot ngay: thang am lich Trung Quoc bat dau muon hon Viet Nam dung mot ngay")
    void thangTrungQuocBatDauMuonHonVietNamMotNgay(LunarFixtures.Row row) {
        LocalDate from = row.date("from_solar");

        LunarDate chinaOnVietnameseFirstDay = LunarConverter.toLunar(from, LunarConverter.CHINA_ZONE_HOURS);
        LunarDate chinaNextDay = LunarConverter.toLunar(from.plusDays(1), LunarConverter.CHINA_ZONE_HOURS);

        assertThat(chinaOnVietnameseFirstDay.day())
                .as("o Trung Quoc %s van la ngay cuoi thang truoc", from)
                .isEqualTo(row.integer("cn_day"));
        assertThat(chinaNextDay.day()).as("o Trung Quoc thang moi bat dau hom sau").isEqualTo(1);
    }

    @Test
    @DisplayName("Dot 1984-1985: hai lich dat thang nhuan khac cho nen so hieu thang lech ca mot thang")
    void dot1984Den1985LechCaMotThang() {
        LocalDate start = LocalDate.of(1984, 11, 23);

        LunarDate vietnam = LunarConverter.toLunar(start, LunarConverter.VIETNAM_ZONE_HOURS);
        LunarDate china = LunarConverter.toLunar(start, LunarConverter.CHINA_ZONE_HOURS);

        assertThat(vietnam.month()).isEqualTo(11);
        assertThat(vietnam.leapMonth()).isFalse();
        assertThat(china.month()).isEqualTo(10);
        assertThat(china.leapMonth()).as("lich Trung Quoc nam do nhuan thang 10").isTrue();
        assertThat(LunarConverter.leapMonthOf(1984, LunarConverter.CHINA_ZONE_HOURS)).isEqualTo(10);
        assertThat(LunarConverter.leapMonthOf(1984, LunarConverter.VIETNAM_ZONE_HOURS))
                .as("lich Viet Nam nam 1984 khong nhuan thang 10")
                .isNotEqualTo(10);
    }

    @Test
    @DisplayName("Fixture chi lay tu 1968 tro di - truoc do mien Bac dung mui gio thu 8 nen phep so sanh vo nghia")
    void fixtureChiLayTu1968TroDi() {
        assertThat(divergence())
                .allSatisfy(row -> assertThat(row.date("from_solar").getYear())
                        .isGreaterThanOrEqualTo(VietnamLunarZone.FIRST_YEAR_GMT7));
    }
}
