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
 * W4 · {@code tet.csv} — moc de kiem chung doc lap nhat cua lich am: mung 1 Tet 1990–2040.
 *
 * <p>Fixture ghi ca ngay Tet Trung Quoc, nen bo test nay dong thoi khoa lai viec quy doi o mui gio
 * GMT+8 — hai nam 2007 va 2030 lech that mot ngay, khong phai sai so.</p>
 */
class TetConversionTest {

    static List<LunarFixtures.Row> tet() {
        return LunarFixtures.verifiedRows("tet.csv");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("tet")
    @DisplayName("Mung 1 Tet doi ra dung ngay duong lich cua bang tra Ho Ngoc Duc (GMT+7)")
    void mung1TetRaDungNgayDuong(LunarFixtures.Row row) {
        int lunarYear = row.integer("lunar_year");

        LocalDate actual = LunarConverter.toSolar(LunarDate.of(lunarYear, 1, 1));

        assertThat(actual)
                .as("mung 1 Tet nam am lich %d", lunarYear)
                .isEqualTo(row.date("tet_solar_date"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("tet")
    @DisplayName("Ngay duong cua Tet doi nguoc lai ra dung mung 1 thang 1 khong nhuan")
    void ngayDuongCuaTetDoiNguocRaMung1(LunarFixtures.Row row) {
        int lunarYear = row.integer("lunar_year");

        LunarDate actual = LunarConverter.toLunar(row.date("tet_solar_date"));

        assertThat(actual).isEqualTo(LunarDate.of(lunarYear, 1, 1));
        assertThat(actual.leapMonth()).as("mung 1 Tet khong bao gio la thang nhuan").isFalse();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("tet")
    @DisplayName("Cung mot ngay am o GMT+8 cho ra dung ngay Tet Trung Quoc cua Dai Thien van Hong Kong")
    void tetTrungQuocTinhTheoGmt8(LunarFixtures.Row row) {
        int lunarYear = row.integer("lunar_year");

        LocalDate actual =
                LunarConverter.toSolar(LunarDate.of(lunarYear, 1, 1), LunarConverter.CHINA_ZONE_HOURS);

        assertThat(actual)
                .as("Tet Trung Quoc nam am lich %d", lunarYear)
                .isEqualTo(row.date("chinese_new_year"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("tet")
    @DisplayName("Cot same_as_china cua fixture khop voi ket qua hai mui gio cua bo quy doi")
    void cotSameAsChinaKhopVoiKetQuaHaiMuiGio(LunarFixtures.Row row) {
        int lunarYear = row.integer("lunar_year");
        boolean fixtureSaysSame = "yes".equalsIgnoreCase(row.str("same_as_china"));

        LocalDate vietnam = LunarConverter.toSolar(LunarDate.of(lunarYear, 1, 1));
        LocalDate china = LunarConverter.toSolar(LunarDate.of(lunarYear, 1, 1), LunarConverter.CHINA_ZONE_HOURS);

        assertThat(vietnam.equals(china))
                .as("nam %d: fixture bao same_as_china=%s", lunarYear, row.str("same_as_china"))
                .isEqualTo(fixtureSaysSame);
    }

    @Test
    @DisplayName("Fixture Tet phu du 51 nam 1990-2040, khong sot nam nao")
    void fixtureTetPhuDuKhoang1990Den2040() {
        List<LunarFixtures.Row> rows = tet();

        assertThat(rows).hasSize(51);
        assertThat(rows.stream().map(row -> row.integer("lunar_year")).toList())
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1990, 2040).boxed().toList());
    }

    @Test
    @DisplayName("Tet 2007 va Tet 2030 lech Trung Quoc dung mot ngay - ca bien mui gio kinh dien")
    void tet2007Va2030LechTrungQuocMotNgay() {
        assertThat(LunarConverter.toSolar(LunarDate.of(2007, 1, 1))).isEqualTo(LocalDate.of(2007, 2, 17));
        assertThat(LunarConverter.toSolar(LunarDate.of(2007, 1, 1), LunarConverter.CHINA_ZONE_HOURS))
                .isEqualTo(LocalDate.of(2007, 2, 18));

        assertThat(LunarConverter.toSolar(LunarDate.of(2030, 1, 1))).isEqualTo(LocalDate.of(2030, 2, 2));
        assertThat(LunarConverter.toSolar(LunarDate.of(2030, 1, 1), LunarConverter.CHINA_ZONE_HOURS))
                .isEqualTo(LocalDate.of(2030, 2, 3));
    }
}
