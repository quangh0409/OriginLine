package vn.giapha.kinship.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * W3 ca bien 4/6 · <b>TAI HON + CON RIENG</b>.
 *
 * <p>Quyet dinh da chot trong seed (chu thich vi du #8): chong moi <b>khong</b> co canh PARENT toi
 * con rieng cua vo, nen mac dinh he thong KHONG tu suy ra "bo duong". Muon ghi nhan thi hoac tao
 * canh {@code PARENT_ADOPT}, hoac them luat o rule set con — <b>quyet dinh cua Hoi dong Toc bieu,
 * khong phai mac dinh he thong</b>. Bo test khoa lai ca hai duong.</p>
 */
class RemarriageKinshipTest {

    private InMemoryClan clan;
    private PersonId me;
    private PersonId chongCu;
    private PersonId chongMoi;
    private PersonId conRieng;
    private PersonId conChung;
    private PersonId conRiengCuaChongMoi;

    @BeforeEach
    void dungHoTaiHon() {
        clan = new InMemoryClan();
        me = clan.nu("Me");
        chongCu = clan.nam("ChongCu");
        chongMoi = clan.nam("ChongMoi");
        conRieng = clan.person("ConRieng", Gender.MALE, 1, LocalDate.of(1998, 3, 1), false);
        conChung = clan.person("ConChung", Gender.MALE, 2, LocalDate.of(2006, 9, 1), false);
        conRiengCuaChongMoi = clan.person("ConRiengCuaChongMoi", Gender.FEMALE, 1, LocalDate.of(1996, 2, 1), false);

        clan.voChongDaChamDut(me, chongCu, 1, LocalDate.of(2003, 1, 1));
        clan.voChong(me, chongMoi, 2);
        clan.conRuot(me, conRieng);
        clan.conRuot(chongCu, conRieng);
        clan.conRuot(me, conChung);
        clan.conRuot(chongMoi, conChung);
        clan.conRuot(chongMoi, conRiengCuaChongMoi);
    }

    @Test
    @DisplayName("Hon nhan da ly hon khong con sinh danh xung vo/chong")
    void honNhanDaLyHonKhongConSinhDanhXung() {
        KinshipResolution resolution = clan.resolve(me, chongCu);

        assertThat(resolution.status()).isEqualTo(KinshipStatus.NO_COMMON_ANCESTOR);
        assertThat(resolution.title()).isNotEqualTo("chồng");
        assertThat(clan.danhXung(me, chongMoi)).as("hon nhan hien tai van sinh danh xung").isEqualTo("chồng");
    }

    @Test
    @DisplayName("Con rieng van goi cha de la 'bo' du cha me da ly hon")
    void conRiengVanGoiChaDeLaBo() {
        assertThat(clan.danhXung(conRieng, chongCu)).isEqualTo("bố");
        assertThat(clan.danhXung(chongCu, conRieng)).isEqualTo("con trai");
    }

    @Test
    @DisplayName("MAC DINH: chong moi cua me KHONG duoc tu suy ra la 'bo' hay 'bo duong'")
    void chongMoiKhongDuocTuSuyRaLaBo() {
        KinshipResolution resolution = clan.resolve(conRieng, chongMoi);

        assertThat(resolution.title()).isNotEqualTo("bố");
        assertThat(resolution.title()).isNotEqualTo("bố nuôi");
        assertThat(resolution.relationCode())
                .as("quyet dinh da chot trong seed vi du #8: roi vao luat vet, cho Hoi dong quyet")
                .isEqualTo(RelationCode.KHONG_XAC_DINH);
        assertThat(clan.contextOf(conRieng, chongMoi).lca())
                .as("khong co canh PARENT nao giua hai nguoi nen khong co to chung")
                .isNull();
    }

    @Test
    @DisplayName("Duong 1 - dong ho lap canh PARENT_ADOPT: chong moi thanh 'bo nuoi'")
    void duong1LapCanhNhanNuoiThiThanhBoNuoi() {
        InMemoryClan nhan = new InMemoryClan();
        PersonId meN = nhan.nu("Me");
        PersonId chongMoiN = nhan.nam("ChongMoi");
        PersonId conRiengN = nhan.nam("ConRieng");
        nhan.voChong(meN, chongMoiN, 2);
        nhan.conRuot(meN, conRiengN);
        nhan.conNuoi(chongMoiN, conRiengN);

        assertThat(nhan.danhXung(conRiengN, chongMoiN)).isEqualTo("bố nuôi");
        assertThat(nhan.danhXung(chongMoiN, conRiengN)).isEqualTo("con nuôi");
    }

    @Test
    @DisplayName("Duong 2 - them luat BO_DUONG o cap chi: cung do thi cho danh xung khac")
    void duong2ThemLuatBoDuongOCapChi() {
        KinshipRuleSet chiCoBoDuong = SeedKinshipRules.mienBac().child(RuleScope.BRANCH, "CHI_CO_BO_DUONG",
                List.of(KinshipRule.builder("BO_DUONG", "bố dượng")
                        .genDelta(1).collateralDegree(0).side(RelationSide.IN_LAW).gender(Gender.MALE)
                        .linkGender(Gender.FEMALE).inLawDirection(InLawDirection.ALTER_IS_SPOUSE)
                        .egoSelfTerm("con").titleShort("dượng").priority(14).build()));

        KinshipResolution resolution = clan.resolve(conRieng, chongMoi, chiCoBoDuong);

        assertThat(resolution.title()).isEqualTo("bố dượng");
        assertThat(resolution.ruleSetScope())
                .as("truy vet duoc luat den tu cap nao khi dong ho bao goi sai")
                .isEqualTo(RuleScope.BRANCH);
        assertThat(clan.danhXung(conRieng, chongMoi, SeedKinshipRules.mienBac()))
                .as("bo DEFAULT khong bi anh huong")
                .isEqualTo("chưa xác định quan hệ");
    }

    @Test
    @DisplayName("Con rieng va con chung cung me khac cha van la anh em RUOT")
    void conRiengVaConChungCungMeKhacChaVanLaAnhEmRuot() {
        RelationFacts facts = clan.factsOf(conRieng, conChung);

        assertThat(facts.collateralDegree()).isEqualTo(1);
        assertThat(facts.side()).as("to chung la nguoi me nen ben ngoai").isEqualTo(RelationSide.MATERNAL);
        assertThat(clan.contextOf(conRieng, conChung).lca().lca()).isEqualTo(me);
        assertThat(clan.danhXung(conRieng, conChung)).isEqualTo("em trai");
        assertThat(clan.danhXung(conChung, conRieng)).isEqualTo("anh trai");
    }

    @Test
    @DisplayName("Con rieng cua vo va con rieng cua chong khong co quan he huyet thong nao")
    void haiConRiengCuaHaiBenKhongCoQuanHeHuyetThong() {
        KinshipResolution resolution = clan.resolve(conRieng, conRiengCuaChongMoi);

        assertThat(clan.contextOf(conRieng, conRiengCuaChongMoi).lca()).isNull();
        assertThat(resolution.title()).isNotEqualTo("chị gái");
        assertThat(resolution.relationCode()).isEqualTo(RelationCode.KHONG_XAC_DINH);
    }

    @Test
    @DisplayName("Con rieng cua chong moi va con chung la anh em ruot qua duong nguoi cha")
    void conRiengCuaChongMoiVaConChungLaAnhEmRuot() {
        assertThat(clan.contextOf(conChung, conRiengCuaChongMoi).lca().lca()).isEqualTo(chongMoi);
        assertThat(clan.factsOf(conChung, conRiengCuaChongMoi).collateralDegree()).isEqualTo(1);
        assertThat(clan.danhXung(conChung, conRiengCuaChongMoi)).isEqualTo("chị gái");
    }

    @Test
    @DisplayName("Canh hon nhan da cham dut van doc duoc de hien thi lich su, chi khong sinh danh xung")
    void canhHonNhanDaChamDutVanDocDuocDeHienThiLichSu() {
        List<SpouseLink> spouses = clan.spousesOf(me);

        assertThat(spouses).hasSize(2);
        assertThat(spouses).filteredOn(link -> !link.active())
                .singleElement()
                .satisfies(link -> {
                    assertThat(link.spouse()).isEqualTo(chongCu);
                    assertThat(link.validTo()).isEqualTo(LocalDate.of(2003, 1, 1));
                    assertThat(link.spouseOrder()).isEqualTo(1);
                });
        assertThat(clan.contextOf(me, chongCu).hasNoConnection()).isTrue();
    }
}
