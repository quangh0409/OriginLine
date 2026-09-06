package vn.giapha.kinship.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * W3 · <b>GHI DE RULE THEO CHI</b>: {@code DEFAULT → REGION → CLAN → BRANCH}.
 *
 * <p>FR-1.3a. Mot chi muon goi "thay" thay cho "bo" chi can khai <b>mot dong</b> {@code CHA}; phan
 * con lai van thua huong bo mac dinh mien Bac. Bo test khang dinh ca ba dieu: luat con thay the
 * luat cha theo {@code relation_code}, luat mang ma moi duoc cong them, va ket qua truy vet duoc
 * ve dung cap da cap ra luat.</p>
 */
class RuleSetInheritanceTest {

    private KinshipRuleSet mienBac;
    private KinshipRuleSet vungTrung;
    private KinshipRuleSet dongHo;
    private KinshipRuleSet chiHai;

    private InMemoryClan clan;
    private PersonId toi;
    private PersonId bo;
    private PersonId me;
    private PersonId co;
    private PersonId duong;

    @BeforeEach
    void dungChuoiKeThua() {
        mienBac = SeedKinshipRules.mienBac();
        vungTrung = mienBac.child(RuleScope.REGION, "REGION_TRUNG", List.of(
                KinshipRule.builder("CHU_RE_CUA_CO", "dượng").priority(15)
                        .genDelta(1).collateralDegree(1).side(RelationSide.IN_LAW).gender(Gender.MALE)
                        .isElder(false).linkSide(RelationSide.PATERNAL).linkGender(Gender.FEMALE)
                        .inLawDirection(InLawDirection.ALTER_IS_SPOUSE).egoSelfTerm("cháu").build()));
        dongHo = vungTrung.child(RuleScope.CLAN, "CLAN_VU_DINH", List.of(
                KinshipRule.builder("CHA", "thầy").priority(10)
                        .genDelta(1).collateralDegree(0).side(RelationSide.BLOOD).gender(Gender.MALE)
                        .egoSelfTerm("con").build()));
        chiHai = dongHo.child(RuleScope.BRANCH, "BRANCH_CHI_HAI", List.of(
                KinshipRule.builder("ME", "u").priority(10)
                        .genDelta(1).collateralDegree(0).side(RelationSide.BLOOD).gender(Gender.FEMALE)
                        .egoSelfTerm("con").build(),
                KinshipRule.builder("BO_DUONG", "bố dượng").priority(14)
                        .genDelta(1).collateralDegree(0).side(RelationSide.IN_LAW).gender(Gender.MALE)
                        .linkGender(Gender.FEMALE).inLawDirection(InLawDirection.ALTER_IS_SPOUSE)
                        .egoSelfTerm("con").build()));

        clan = new InMemoryClan();
        PersonId ong = clan.nam("Ong");
        bo = clan.person("Bo", Gender.MALE, 1, null, false);
        co = clan.person("Co", Gender.FEMALE, 2, null, false);
        me = clan.nu("Me");
        duong = clan.nam("Duong");
        toi = clan.nam("Toi");
        clan.conRuot(ong, bo);
        clan.conRuot(ong, co);
        clan.conRuot(bo, toi);
        clan.conRuot(me, toi);
        clan.voChong(bo, me, 1);
        clan.voChong(co, duong, 1);
    }

    @Test
    @DisplayName("Chuoi ke thua duoc ap dung tu goc DEFAULT xuong den bo la")
    void chuoiKeThuaTuGocXuongLa() {
        assertThat(chiHai.chain())
                .extracting(KinshipRuleSet::scope)
                .containsExactly(RuleScope.DEFAULT, RuleScope.REGION, RuleScope.CLAN, RuleScope.BRANCH);
        assertThat(chiHai.chain()).extracting(KinshipRuleSet::code)
                .containsExactly("DEFAULT", "REGION_TRUNG", "CLAN_VU_DINH", "BRANCH_CHI_HAI");
    }

    @Test
    @DisplayName("Luat con THAY THE luat cha co cung relation_code, khong cong don")
    void luatConThayTheLuatChaCungMa() {
        assertThat(mienBac.effectiveRules()).hasSize(157);
        assertThat(chiHai.effectiveRules())
                .as("157 luat goc + 1 ma moi BO_DUONG; ba luat cung ma la GHI DE chu khong cong them")
                .hasSize(158);
        assertThat(soLuatTheoMa(chiHai, "CHA")).isEqualTo(1);
        assertThat(soLuatTheoMa(chiHai, "ME")).isEqualTo(1);
    }

    @Test
    @DisplayName("Chi khai mot dong CHA thi chi doi danh xung do, phan con lai van thua huong")
    void chiKhaiMotDongThiChiDoiDanhXungDo() {
        assertThat(clan.danhXung(toi, bo, mienBac)).isEqualTo("bố");
        assertThat(clan.danhXung(toi, bo, chiHai)).isEqualTo("thầy");
        assertThat(clan.danhXung(toi, me, chiHai)).isEqualTo("u");
        assertThat(clan.danhXung(toi, co, chiHai))
                .as("luat khong bi ghi de van giu nguyen tu bo mac dinh")
                .isEqualTo("cô ruột");
    }

    @Test
    @DisplayName("Ket qua truy vet duoc ve dung cap da cap ra luat - biet phai sua o dau khi goi sai")
    void ketQuaTruyVetVeDungCapDaCapRaLuat() {
        assertThat(clan.resolve(toi, bo, chiHai).ruleSetScope())
                .as("CHA duoc ghi de o cap CLAN")
                .isEqualTo(RuleScope.CLAN);
        assertThat(clan.resolve(toi, me, chiHai).ruleSetScope())
                .as("ME duoc ghi de o cap BRANCH")
                .isEqualTo(RuleScope.BRANCH);
        assertThat(clan.resolve(toi, co, chiHai).ruleSetScope())
                .as("CO_RUOT van den tu bo DEFAULT")
                .isEqualTo(RuleScope.DEFAULT);
        assertThat(clan.resolve(toi, duong, chiHai).ruleSetScope())
                .as("CHU_RE_CUA_CO duoc ghi de o cap REGION")
                .isEqualTo(RuleScope.REGION);
    }

    @Test
    @DisplayName("Cap REGION doi 'chu' thanh 'duong' - khac biet vung mien la du lieu, khong phai nhanh if")
    void capRegionDoiChuThanhDuong() {
        assertThat(clan.danhXung(toi, duong, mienBac)).isEqualTo("chú");
        assertThat(clan.danhXung(toi, duong, vungTrung)).isEqualTo("dượng");
        assertThat(clan.danhXung(toi, duong, chiHai)).isEqualTo("dượng");
        assertThat(clan.resolve(toi, duong, vungTrung).relationCode())
                .as("ma quan he giu nguyen; chi title doi")
                .isEqualTo(RelationCode.of("CHU_RE_CUA_CO"));
    }

    @Test
    @DisplayName("Cap gan hon luon thang cap xa hon du priority bang nhau")
    void capGanHonLuonThangCapXaHon() {
        KinshipRuleSet chiGhiDeLaiCha = chiHai.child(RuleScope.BRANCH, "BRANCH_CHI_HAI_CON", List.of(
                KinshipRule.builder("CHA", "cha").priority(10)
                        .genDelta(1).collateralDegree(0).side(RelationSide.BLOOD).gender(Gender.MALE)
                        .egoSelfTerm("con").build()));

        assertThat(clan.danhXung(toi, bo, chiGhiDeLaiCha)).isEqualTo("cha");
        assertThat(clan.resolve(toi, bo, chiGhiDeLaiCha).ruleSetScope()).isEqualTo(RuleScope.BRANCH);
    }

    @Test
    @DisplayName("overriddenRules liet ke dung nhung luat cha da bi ghi de - de to mau man hinh quan tri")
    void overriddenRulesLietKeLuatDaBiGhiDe() {
        assertThat(chiHai.overriddenRules())
                .extracting(rule -> rule.relationCode().value() + "=" + rule.title())
                .containsExactlyInAnyOrder("CHU_RE_CUA_CO=chú", "CHA=bố", "ME=mẹ");
    }

    @Test
    @DisplayName("Bo luat bi tat khong dong gop luat nao vao ket qua hop nhat")
    void boLuatBiTatKhongDongGopLuatNao() {
        KinshipRuleSet chiTat = new KinshipRuleSet(java.util.UUID.randomUUID(), "BRANCH_TAT", "BRANCH_TAT",
                RuleScope.BRANCH, null, null, mienBac,
                List.of(KinshipRule.builder("CHA", "thầy").priority(10)
                        .genDelta(1).collateralDegree(0).side(RelationSide.BLOOD).gender(Gender.MALE).build()),
                false, 0L);

        assertThat(clan.danhXung(toi, bo, chiTat))
                .as("bo luat is_active=false bi bo qua khi hop nhat")
                .isEqualTo("bố");
    }

    @Test
    @DisplayName("Luat rieng le bi tat khong duoc khop, quan he roi xuong luat co uu tien sau")
    void luatRiengLeBiTatKhongDuocKhop() {
        KinshipRuleSet chiTatCha = mienBac.child(RuleScope.BRANCH, "BRANCH_TAT_CHA", List.of(
                KinshipRule.builder("CHA", "bố").priority(10).active(false)
                        .genDelta(1).collateralDegree(0).side(RelationSide.BLOOD).gender(Gender.MALE).build()));

        assertThat(clan.resolve(toi, bo, chiTatCha).relationCode())
                .as("khong con luat CHA nao con hieu luc nen roi vao luat vet")
                .isEqualTo(RelationCode.KHONG_XAC_DINH);
    }

    @Test
    @DisplayName("Chuoi ke thua qua sau hoac co chu trinh bi chan thay vi lap vo han")
    void chuoiKeThuaQuaSauBiChan() {
        KinshipRuleSet current = mienBac;
        for (int i = 0; i < 40; i++) {
            current = current.child(RuleScope.BRANCH, "CHI_" + i, List.of());
        }
        KinshipRuleSet quaSau = current;

        assertThatThrownBy(quaSau::chain)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chuoi ke thua");
    }

    @Test
    @DisplayName("Hop nhat luat la tat dinh - goi nhieu lan cho cung mot ket qua")
    void hopNhatLuatLaTatDinh() {
        List<KinshipRule> lan1 = chiHai.effectiveRules();
        List<KinshipRule> lan2 = chiHai.effectiveRules();

        assertThat(lan2).isSameAs(lan1);
        assertThat(lan1).isSortedAccordingTo(java.util.Comparator.comparingInt(KinshipRule::priority));
    }

    private static long soLuatTheoMa(KinshipRuleSet set, String code) {
        RelationCode target = RelationCode.of(code);
        return set.effectiveRules().stream().filter(rule -> rule.relationCode().equals(target)).count();
    }
}
