package vn.giapha.kinship.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * W3 · bang danh xung thuong dung cua <b>mien Bac</b>, chay tren mot dong ho dung san.
 *
 * <p>Dac trung mien Bac phai giu: <b>"bac" dung cho CA hai ben noi lan ngoai</b> khi la vai tren —
 * anh trai cua me la "bac" chu khong phai "cau"; chi gai cua bo la "bac gai" chu khong phai "co".
 * Sai o day la loi mat mat voi dong ho, khong phai bug ky thuat.</p>
 */
class CoreTitlesTest {

    /**
     * Ho mau: Cu -> (OngNoi, OngNoiEm) ; OngNoi + BaNoi -> (BacTrai, BacGai, Bo, Chu, Co)
     * OngNgoai + BaNgoai -> (BacNgoai, BacGaiNgoai, Me, Cau, Di) ; Bo + Me -> (AnhTrai, Toi, EmGai)
     * Toi -> (ConTrai, ConGai) ; ConTrai -> Chau.
     */
    private static final class HoMau {
        final InMemoryClan clan = new InMemoryClan();
        final PersonId cu = clan.nam("Cu");
        final PersonId ongNoi = clan.person("OngNoi", Gender.MALE, 1, null, false);
        final PersonId ongNoiEm = clan.person("OngNoiEm", Gender.MALE, 2, null, false);
        final PersonId baNoi = clan.nu("BaNoi");
        final PersonId bacTrai = clan.person("BacTrai", Gender.MALE, 1, null, false);
        final PersonId bacGai = clan.person("BacGai", Gender.FEMALE, 2, null, false);
        final PersonId bo = clan.person("Bo", Gender.MALE, 3, null, false);
        final PersonId chu = clan.person("Chu", Gender.MALE, 4, null, false);
        final PersonId co = clan.person("Co", Gender.FEMALE, 5, null, false);
        final PersonId ongNgoai = clan.nam("OngNgoai");
        final PersonId baNgoai = clan.nu("BaNgoai");
        final PersonId bacNgoai = clan.person("BacNgoai", Gender.MALE, 1, null, false);
        final PersonId bacGaiNgoai = clan.person("BacGaiNgoai", Gender.FEMALE, 2, null, false);
        final PersonId me = clan.person("Me", Gender.FEMALE, 3, null, false);
        final PersonId cau = clan.person("Cau", Gender.MALE, 4, null, false);
        final PersonId di = clan.person("Di", Gender.FEMALE, 5, null, false);
        final PersonId anhTrai = clan.person("AnhTrai", Gender.MALE, 1, null, false);
        final PersonId toi = clan.person("Toi", Gender.MALE, 2, null, false);
        final PersonId emGai = clan.person("EmGai", Gender.FEMALE, 3, null, false);
        final PersonId conTrai = clan.person("ConTrai", Gender.MALE, 1, null, false);
        final PersonId conGai = clan.person("ConGai", Gender.FEMALE, 2, null, false);
        final PersonId chau = clan.nam("Chau");
        final PersonId conBacTrai = clan.person("ConBacTrai", Gender.MALE, 1, null, false);
        final PersonId conChu = clan.person("ConChu", Gender.MALE, 1, null, false);
        final PersonId conCuaOngNoiEm = clan.person("ConCuaOngNoiEm", Gender.MALE, 1, null, false);

        HoMau() {
            clan.conRuot(cu, ongNoi);
            clan.conRuot(cu, ongNoiEm);
            clan.voChong(ongNoi, baNoi, 1);
            for (PersonId child : List.of(bacTrai, bacGai, bo, chu, co)) {
                clan.conRuot(ongNoi, child);
                clan.conRuot(baNoi, child);
            }
            clan.voChong(ongNgoai, baNgoai, 1);
            for (PersonId child : List.of(bacNgoai, bacGaiNgoai, me, cau, di)) {
                clan.conRuot(ongNgoai, child);
                clan.conRuot(baNgoai, child);
            }
            clan.voChong(bo, me, 1);
            for (PersonId child : List.of(anhTrai, toi, emGai)) {
                clan.conRuot(bo, child);
                clan.conRuot(me, child);
            }
            clan.conRuot(toi, conTrai);
            clan.conRuot(toi, conGai);
            clan.conRuot(conTrai, chau);
            clan.conRuot(bacTrai, conBacTrai);
            clan.conRuot(chu, conChu);
            clan.conRuot(ongNoiEm, conCuaOngNoiEm);
        }
    }

    private static final HoMau HO = new HoMau();

    static Stream<Arguments> danhXungMienBac() {
        return Stream.of(
                // --- truc he tren ---
                Arguments.of("bo ruot", HO.toi, HO.bo, "bố"),
                Arguments.of("me ruot", HO.toi, HO.me, "mẹ"),
                Arguments.of("bo cua bo", HO.toi, HO.ongNoi, "ông nội"),
                Arguments.of("me cua bo", HO.toi, HO.baNoi, "bà nội"),
                Arguments.of("bo cua me", HO.toi, HO.ongNgoai, "ông ngoại"),
                Arguments.of("me cua me", HO.toi, HO.baNgoai, "bà ngoại"),
                Arguments.of("ong noi cua bo", HO.toi, HO.cu, "cụ ông nội"),
                // --- truc he duoi ---
                Arguments.of("con trai", HO.toi, HO.conTrai, "con trai"),
                Arguments.of("con gai", HO.toi, HO.conGai, "con gái"),
                Arguments.of("con cua con trai", HO.toi, HO.chau, "cháu nội"),
                Arguments.of("con cua con trai, nhin tu ong noi", HO.bo, HO.chau, "chắt"),
                // --- cung doi ---
                Arguments.of("anh ruot", HO.toi, HO.anhTrai, "anh trai"),
                Arguments.of("em gai ruot", HO.toi, HO.emGai, "em gái"),
                Arguments.of("con cua bac trai (hon tuoi)", HO.toi, HO.conBacTrai, "anh họ"),
                Arguments.of("con cua chu", HO.toi, HO.conChu, "em trai họ"),
                // --- doi tren 1 bac, bang he ruot, BEN NOI ---
                Arguments.of("anh trai cua bo", HO.toi, HO.bacTrai, "bác"),
                Arguments.of("chi gai cua bo", HO.toi, HO.bacGai, "bác gái"),
                Arguments.of("em trai cua bo", HO.toi, HO.chu, "chú"),
                Arguments.of("em gai cua bo", HO.toi, HO.co, "cô ruột"),
                // --- doi tren 1 bac, bang he ruot, BEN NGOAI (dac trung mien Bac) ---
                Arguments.of("anh trai cua me - mien Bac goi BAC khong goi cau", HO.toi, HO.bacNgoai, "bác"),
                Arguments.of("chi gai cua me - mien Bac goi BAC GAI", HO.toi, HO.bacGaiNgoai, "bác gái"),
                Arguments.of("em trai cua me", HO.toi, HO.cau, "cậu"),
                Arguments.of("em gai cua me", HO.toi, HO.di, "dì"),
                // --- doi tren 2 bac, bang he ---
                Arguments.of("em trai cua ong noi", HO.toi, HO.ongNoiEm, "ông chú"),
                Arguments.of("em trai cua ong noi, nhin tu bo", HO.bo, HO.ongNoiEm, "chú"),
                // --- doi duoi, bang he ---
                Arguments.of("con cua em gai ruot", HO.bacTrai, HO.toi, "cháu trai"),
                Arguments.of("con cua anh chi em ruot, nu", HO.bacTrai, HO.emGai, "cháu gái"),
                Arguments.of("chau cua anh chi em ruot", HO.bacTrai, HO.conTrai, "chắt"),
                Arguments.of("con cua em trai ong noi = em ho cua bo", HO.toi, HO.conCuaOngNoiEm, "chú họ"),
                Arguments.of("chieu nguoc lai cua chu ho", HO.conCuaOngNoiEm, HO.toi, "cháu họ"),
                // --- chieu nguoc cua ong ba ---
                Arguments.of("chau trai cua ong noi", HO.ongNoi, HO.toi, "cháu nội"),
                Arguments.of("chau trai cua ong ngoai", HO.ongNgoai, HO.toi, "cháu ngoại"),
                Arguments.of("chat cua cu", HO.cu, HO.toi, "chắt"));
    }

    @ParameterizedTest(name = "[{index}] {0}: {3}")
    @MethodSource("danhXungMienBac")
    @DisplayName("Bang danh xung mien Bac tra dung theo bo luat DEFAULT doc tu file seed")
    void bangDanhXungMienBac(String moTa, PersonId ego, PersonId alter, String expected) {
        assertThat(HO.clan.danhXung(ego, alter)).as(moTa).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("danhXungMienBac")
    @DisplayName("Moi cap deu tra ve mot luat co ma quan he - khong bao gio ket qua rong")
    void moiCapDeuTraVeMotLuat(String moTa, PersonId ego, PersonId alter, String expected) {
        KinshipResolution resolution = HO.clan.resolve(ego, alter);

        assertThat(resolution.relationCode()).as(moTa).isNotNull();
        assertThat(resolution.title()).as(moTa).isNotBlank();
        assertThat(resolution.facts()).as("luon tra du kien de Hoi dong biet can viet luat nao").isNotNull();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("danhXungMienBac")
    @DisplayName("Danh xung chieu nguoc tinh tu cung bo bang chung khop voi khi tra truc tiep")
    void danhXungChieuNguocKhopVoiTraTrucTiep(String moTa, PersonId ego, PersonId alter, String expected) {
        String reciprocal = HO.clan.resolve(ego, alter).reciprocalTitle();
        String traTrucTiep = HO.clan.danhXung(alter, ego);

        assertThat(reciprocal)
                .as("%s: chieu nguoc tinh bang dao vai phai giong het khi hoi thang", moTa)
                .isEqualTo(traTrucTiep);
    }
}
