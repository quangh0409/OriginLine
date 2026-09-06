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
 * W4 · {@code known-limitations.csv} — GIOI HAN DA BIET cua ban hien thuc.
 *
 * <p><b>Bo test nay khong khang dinh dong nao o day la dung.</b> No khoa lai hanh vi HIEN TAI dung
 * nhu ghi o cot {@code impl_result}, de khong ai tuong nham la dung va de khi nang do chinh xac len
 * (VSOP87/ELP hoac nhung bang tra) thi test do va nguoi sua PHAI cap nhat fixture. Do la muc dich —
 * khong dung {@code @Disabled} o day, vi mot test bi tat khong bao ai biet khi hanh vi thay doi.</p>
 *
 * <p>Pham vi anh huong thuc te: khong co moc {@code lunar_month_start} nao truoc nam 2054 o mui gio
 * +7, nen ngay gio cua nguoi da mat (tinh cho nam hien tai va vai chuc nam toi) khong bi anh huong.</p>
 */
class KnownLimitationsTest {

    static List<LunarFixtures.Row> limitations() {
        return LunarFixtures.rows("known-limitations.csv");
    }

    static List<LunarFixtures.Row> lunarMonthStart() {
        return limitations().stream().filter(row -> "lunar_month_start".equals(row.str("kind"))).toList();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("lunarMonthStart")
    @DisplayName("Moc lech so voi bang tra: thuat toan rut gon cho ra dung ket qua da ghi trong fixture")
    void mocLechSoVoiBangTraChoRaKetQuaDaGhi(LunarFixtures.Row row) {
        LocalDate solar = row.date("solar_date");
        LunarDate implResult = row.lunarMonthDayYear("impl_result");
        LunarDate authorityResult = row.lunarMonthDayYear("authority_result");

        LunarDate actual = LunarConverter.toLunar(solar, LunarConverter.VIETNAM_ZONE_HOURS);

        assertThat(actual.year()).isEqualTo(implResult.year());
        assertThat(actual.month()).isEqualTo(implResult.month());
        assertThat(actual.day()).isEqualTo(implResult.day());
        assertThat(actual.day())
                .as("neu ket qua da trung bang tra %s thi PHAI cap nhat known-limitations.csv, "
                        + "khong duoc de dong nay o lai", row.str("authority_result"))
                .isNotEqualTo(authorityResult.day());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("lunarMonthStart")
    @DisplayName("Moc lech deu roi sau nam 2054 - khong cham vao ngay gio cua cac doi dang song")
    void mocLechDeuRoiSauNam2054(LunarFixtures.Row row) {
        assertThat(row.date("solar_date").getYear())
                .as("neu xuat hien moc lech truoc 2054 thi day la loi nghiem trong, khong con la gioi han")
                .isGreaterThanOrEqualTo(2054);
    }

    @Test
    @DisplayName("Dai tuyet 2020 lech mot ngay so voi Dai Thien van Hong Kong - gioi han da biet duy nhat cua tiet khi")
    void daiTuyet2020LechMotNgaySoVoiHongKong() {
        LunarFixtures.Row row = limitations().stream()
                .filter(item -> "solar_term".equals(item.str("kind")))
                .findFirst()
                .orElseThrow();

        LocalDate implResult = LocalDate.parse(row.str("impl_result"));
        LocalDate authorityResult = LocalDate.parse(row.str("authority_result"));

        assertThat(SolarTerms.dateOf(SolarTerm.DAI_TUYET, 2020, LunarConverter.CHINA_ZONE_HOURS))
                .as("hanh vi hien tai o UTC+8")
                .isEqualTo(implResult);
        assertThat(implResult)
                .as("moc that theo HKO la %s", authorityResult)
                .isEqualTo(authorityResult.minusDays(1));
    }

    @Test
    @DisplayName("Nam 1808 thuoc ky lich Dai Thong - he thong tu bao khong tai lap duoc thay vi doan bua")
    void nam1808KhongTaiLapDuoc() {
        LunarFixtures.Row row = limitations().stream()
                .filter(item -> "historical_calendar".equals(item.str("kind")))
                .findFirst()
                .orElseThrow();

        assertThat(VietnamLunarZone.isReconstructible(row.date("solar_date"))).isFalse();
        assertThat(row.str("impl_result")).isEqualTo("khong tai lap duoc");
    }

    @Test
    @DisplayName("Fixture gioi han khong duoc phinh ra am tham - dung 10 dong da biet")
    void fixtureGioiHanDungMuoiDong() {
        assertThat(limitations())
                .as("them mot dong vao known-limitations.csv nghia la mat mot moc dung; phai co ly do")
                .hasSize(10);
    }
}
