package vn.giapha.kinship.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.PersonId;

/**
 * W3 ca bien 2/6 · <b>CON NUOI vs CON RUOT</b>.
 *
 * <p>Nguyen tac: canh {@code PARENT type=ADOPT} <b>van nam tren duong di pha he</b> — con nuoi duoc
 * ghi nhan day du. Muon danh xung khac thi loc o tang LUAT (luat {@code BO_NUOI} priority 8 thang
 * luat {@code CHA} priority 10), KHONG bo canh khoi do thi.</p>
 */
class AdoptionKinshipTest {

    private InMemoryClan clan;
    private PersonId boNuoi;
    private PersonId meNuoi;
    private PersonId conNuoi;
    private PersonId conRuot;
    private PersonId chaDe;
    private PersonId ongNoiNuoi;

    @BeforeEach
    void dungHoCoConNuoi() {
        clan = new InMemoryClan();
        ongNoiNuoi = clan.nam("OngNoiNuoi");
        boNuoi = clan.nam("BoNuoi");
        meNuoi = clan.nu("MeNuoi");
        conNuoi = clan.nam("ConNuoi", 2);
        conRuot = clan.nam("ConRuot", 1);
        chaDe = clan.nam("ChaDe");

        clan.conRuot(ongNoiNuoi, boNuoi);
        clan.voChong(boNuoi, meNuoi, 1);
        clan.conRuot(boNuoi, conRuot);
        clan.conRuot(meNuoi, conRuot);
        clan.conNuoi(boNuoi, conNuoi);
        clan.conNuoi(meNuoi, conNuoi);
        clan.conRuot(chaDe, conNuoi);
    }

    @Test
    @DisplayName("Con nuoi goi nguoi nhan nuoi la 'bo nuoi' chu khong phai 'bo'")
    void conNuoiGoiLaBoNuoiChuKhongPhaiBo() {
        KinshipResolution resolution = clan.resolve(conNuoi, boNuoi);

        assertThat(resolution.title()).isEqualTo("bố nuôi");
        assertThat(resolution.relationCode()).isEqualTo(RelationCode.BO_NUOI);
        assertThat(clan.danhXung(conNuoi, meNuoi)).isEqualTo("mẹ nuôi");
    }

    @Test
    @DisplayName("Luat khop canh PARENT_ADOPT thang luat CHA suy tu LCA - uu tien 8 truoc 10")
    void luatCanhNuoiThangLuatChaSuyTuLca() {
        KinshipRuleSet rules = SeedKinshipRules.mienBac();

        java.util.List<KinshipRule> allMatches = rules.matchAll(clan.factsOf(conNuoi, boNuoi));

        assertThat(allMatches)
                .as("ca hai luat cung khop; priority quyet dinh ai thang")
                .extracting(rule -> rule.relationCode().value())
                .contains("BO_NUOI", "CHA");
        assertThat(allMatches.get(0).relationCode()).isEqualTo(RelationCode.BO_NUOI);
    }

    @Test
    @DisplayName("Chieu nguoc lai: nguoi nhan nuoi goi la 'con nuoi'")
    void nguoiNhanNuoiGoiLaConNuoi() {
        KinshipResolution resolution = clan.resolve(boNuoi, conNuoi);

        assertThat(resolution.title()).isEqualTo("con nuôi");
        assertThat(resolution.relationCode()).isEqualTo(RelationCode.CON_NUOI);
        assertThat(resolution.reciprocalTitle()).isEqualTo("bố nuôi");
    }

    @Test
    @DisplayName("Con ruot van goi la 'bo' du nha co them con nuoi")
    void conRuotVanGoiLaBo() {
        assertThat(clan.danhXung(conRuot, boNuoi)).isEqualTo("bố");
        assertThat(clan.resolve(conRuot, boNuoi).relationCode()).isEqualTo(RelationCode.CHA);
        assertThat(clan.factsOf(conRuot, boNuoi).throughAdoption())
                .as("duong di ben nha cha de khong duoc danh dau la qua con nuoi")
                .isFalse();
    }

    @Test
    @DisplayName("Canh nhan nuoi VAN nam tren duong di pha he - con nuoi khong bi cat khoi cay")
    void canhNhanNuoiVanNamTrenDuongDiPhaHe() {
        LcaResult lca = clan.contextOf(conNuoi, ongNoiNuoi).lca();

        assertThat(lca).as("con nuoi phai co to chung voi ong noi nuoi").isNotNull();
        assertThat(lca.lca()).isEqualTo(ongNoiNuoi);
        assertThat(lca.egoPathUp()).containsExactly(conNuoi, boNuoi, ongNoiNuoi);
        assertThat(clan.danhXung(conNuoi, ongNoiNuoi)).isEqualTo("ông nội");
    }

    @Test
    @DisplayName("Co viaAdoption danh dau dung duong di co canh nuoi, khong danh dau cho nguoi")
    void coViaAdoptionDanhDauTheoCanhChuKhongTheoNguoi() {
        assertThat(clan.contextOf(conNuoi, boNuoi).lca().viaAdoption()).isTrue();
        assertThat(clan.contextOf(conNuoi, chaDe).lca().viaAdoption())
                .as("duong di ConNuoi -> ChaDe chi co canh ruot")
                .isFalse();
        assertThat(clan.factsOf(conNuoi, chaDe).throughAdoption()).isFalse();
    }

    @Test
    @DisplayName("Con nuoi van goi cha de la 'bo' - nhan nuoi khong xoa quan he huyet thong")
    void conNuoiVanGoiChaDeLaBo() {
        assertThat(clan.danhXung(conNuoi, chaDe)).isEqualTo("bố");
        assertThat(clan.resolve(conNuoi, chaDe).relationCode()).isEqualTo(RelationCode.CHA);
    }

    @Test
    @DisplayName("Con nuoi va con ruot cua cung nha la anh em - bac bang he 1")
    void conNuoiVaConRuotLaAnhEm() {
        RelationFacts facts = clan.factsOf(conNuoi, conRuot);

        assertThat(facts.collateralDegree()).isEqualTo(1);
        assertThat(facts.genDelta()).isZero();
        assertThat(clan.danhXung(conNuoi, conRuot)).isEqualTo("anh trai");
        assertThat(clan.danhXung(conRuot, conNuoi)).isEqualTo("em trai");
    }

    @Test
    @DisplayName("Chi co canh nuoi tu ngoai ho van co side BLOOD de luat ghi side='BLOOD' khong truot")
    void chiCoCanhNuoiTuNgoaiHoVanCoSideBlood() {
        InMemoryClan rieng = new InMemoryClan();
        PersonId nguoiNuoi = rieng.nam("NguoiNuoi");
        PersonId treNuoi = rieng.nam("TreNuoi");
        rieng.conNuoi(nguoiNuoi, treNuoi);

        assertThat(rieng.danhXung(treNuoi, nguoiNuoi)).isEqualTo("bố nuôi");
        assertThat(rieng.danhXung(nguoiNuoi, treNuoi)).isEqualTo("con nuôi");
    }
}
