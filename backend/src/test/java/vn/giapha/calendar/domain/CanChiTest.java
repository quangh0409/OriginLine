package vn.giapha.calendar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * W4 · {@code can-chi.csv} — can chi cua NAM am lich, 1990–2040.
 *
 * <p>Cong thuc Ho Ngoc Duc: {@code can = (nam + 6) % 10}, {@code chi = (nam + 8) % 12}. Can chi cua
 * nam khong phu thuoc mui gio nen lich Viet Nam va Trung Quoc luon trung o cot nay.</p>
 */
class CanChiTest {

    static List<LunarFixtures.Row> canChi() {
        return LunarFixtures.verifiedRows("can-chi.csv");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("canChi")
    @DisplayName("Can chi cua nam am lich khop bang tra Dai Thien van Hong Kong")
    void canChiCuaNamKhopBangTra(LunarFixtures.Row row) {
        int year = row.integer("lunar_year");

        Can can = Can.of(year + 6L);
        Chi chi = Chi.of(year + 8L);

        assertThat(can.vietnameseName()).as("Can cua nam %d", year).isEqualTo(row.str("can"));
        assertThat(chi.vietnameseName()).as("Chi cua nam %d", year).isEqualTo(row.str("chi"));
        assertThat(can.vietnameseName() + " " + chi.vietnameseName()).isEqualTo(row.str("can_chi"));
    }

    @Test
    @DisplayName("Chu ky can chi lap lai dung sau 60 nam va khong lap lai som hon")
    void chuKyCanChiLapLaiSau60Nam() {
        for (int year = 1900; year <= 2100 - 60; year++) {
            assertThat(Can.of(year + 6L)).isEqualTo(Can.of(year + 66L));
            assertThat(Chi.of(year + 8L)).isEqualTo(Chi.of(year + 68L));
        }
        for (int offset = 1; offset < 60; offset++) {
            boolean same = Can.of(2000 + 6L) == Can.of(2000 + 6L + offset)
                    && Chi.of(2000 + 8L) == Chi.of(2000 + 8L + offset);
            assertThat(same).as("khoang cach %d nam khong duoc lap lai can chi", offset).isFalse();
        }
    }

    @Test
    @DisplayName("Chi Mao o Viet Nam la con Meo chu khong phai con Tho nhu cach goi Trung Quoc")
    void chiMaoLaConMeoTheoCachGoiVietNam() {
        LunarFixtures.Row nam2023 = canChi().stream()
                .filter(row -> row.integer("lunar_year") == 2023)
                .findFirst()
                .orElseThrow();

        assertThat(nam2023.str("chi")).isEqualTo("Mão");
        assertThat(nam2023.str("zodiac_en")).as("HKO goi con Tho").isEqualTo("Rabbit");
        assertThat(Chi.MAO.zodiacVietnamese()).as("nguoi Viet goi con Meo").isEqualTo("Mèo");
    }

    @Test
    @DisplayName("Can chi quy vong voi chi so am, khong nem ngoai le")
    void canChiQuyVongVoiChiSoAm() {
        assertThat(Can.of(-1)).isEqualTo(Can.QUY);
        assertThat(Chi.of(-1)).isEqualTo(Chi.HOI);
        assertThat(Can.of(10)).isEqualTo(Can.GIAP);
        assertThat(Chi.of(12)).isEqualTo(Chi.TY);
    }

    @Test
    @DisplayName("Canh gio Ty bat dau tu 23h hom truoc va om ca gio 0h")
    void canhGioTyOmCaGio23HVaGio0H() {
        assertThat(Chi.ofHour(23)).isEqualTo(Chi.TY);
        assertThat(Chi.ofHour(0)).isEqualTo(Chi.TY);
        assertThat(Chi.ofHour(1)).isEqualTo(Chi.SUU);
        assertThat(Chi.ofHour(2)).isEqualTo(Chi.SUU);
        assertThat(Chi.ofHour(3)).isEqualTo(Chi.DAN);
        assertThat(Chi.ofHour(12)).isEqualTo(Chi.NGO);
        assertThat(Chi.TY.startHour()).isEqualTo(23);
    }

    @Test
    @DisplayName("Gio ngoai khoang 0-23 bi tu choi")
    void gioNgoaiKhoangBiTuChoi() {
        assertThatThrownBy(() -> Chi.ofHour(24)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Chi.ofHour(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Thu tu khai bao Can va Chi la thu tu chu ky - khong duoc dao")
    void thuTuKhaiBaoLaThuTuChuKy() {
        assertThat(Can.values()).hasSize(10);
        assertThat(Chi.values()).hasSize(12);
        assertThat(Can.GIAP.ordinal()).isZero();
        assertThat(Chi.TY.ordinal()).isZero();
        assertThat(Can.of(2024 + 6L).vietnameseName() + " " + Chi.of(2024 + 8L).vietnameseName())
                .isEqualTo("Giáp Thìn");
    }
}
