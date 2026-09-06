package vn.giapha.kinship.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W3 · tinh toan ven cua bo luat danh xung MAC DINH mien Bac
 * ({@code R__seed_kinship_rules_default.sql}).
 *
 * <p>FR-1.3a: danh xung la <b>du lieu</b>. Bo test nay canh chinh du lieu do — mot luat bi mat, mot
 * uu tien bi dao, mot ma bi trung deu lam ca ho goi sai ma khong sinh exception nao.</p>
 */
class SeedKinshipRuleSetTest {

    @Test
    @DisplayName("Bo luat mac dinh co du 157 luat dung nhu kiem tra sau seed cua migration")
    void boLuatMacDinhCoDu157Luat() {
        List<KinshipRule> rules = SeedKinshipRules.load();

        assertThat(rules)
                .as("migration tu kiem 'mong doi 157 luat' va dung neu duoi 150")
                .hasSize(157);
    }

    @Test
    @DisplayName("Bo luat DEFAULT khong lan sang bo REGION nam cung file seed")
    void boLuatDefaultKhongLanSangBoRegion() {
        assertThat(SeedKinshipRules.load())
                .as("bo quet phai loc theo rule_set_id, khong duoc nuot ca luat cua bo REGION")
                .noneMatch(rule -> rule.title().contains("kĩnh"));

        assertThat(SeedKinshipRules.loadRuleSet("00000000-0000-0000-0000-0000000000b2"))
                .as("bien the vung mien 'kinh' cho Hoi dong chot, khai o cap REGION")
                .isNotEmpty()
                .allSatisfy(rule -> assertThat(rule.title()).contains("kĩnh"));
    }

    @Test
    @DisplayName("Chieu EGO_IS_SPOUSE phu du bac tren, cung doi va bac duoi cho nguoi lam dau/re")
    void chieuEgoIsSpousePhuDuMoiBac() {
        List<KinshipRule> egoIsSpouse = SeedKinshipRules.load().stream()
                .filter(rule -> rule.inLawDirection() == InLawDirection.EGO_IS_SPOUSE)
                .toList();

        assertThat(egoIsSpouse).hasSizeGreaterThanOrEqualTo(30);
        assertThat(egoIsSpouse).anyMatch(rule -> Integer.valueOf(2).equals(rule.genDelta()));
        assertThat(egoIsSpouse).anyMatch(rule -> Integer.valueOf(0).equals(rule.genDelta()));
        assertThat(egoIsSpouse).anyMatch(rule -> Integer.valueOf(-1).equals(rule.genDelta()));
    }

    @Test
    @DisplayName("Bien the side=BLOOD ton tai o moi bac truc he - phu ca ca thieu gioi tinh nguoi noi")
    void bienTheBloodTonTaiOMoiBacTrucHe() {
        List<KinshipRule> trucHe = SeedKinshipRules.load().stream()
                .filter(rule -> rule.side() == RelationSide.BLOOD)
                .filter(rule -> Integer.valueOf(0).equals(rule.collateralDegree()))
                .toList();

        for (int genDelta : new int[] {1, 2, 3, 4, -1, -2, -3}) {
            int wanted = genDelta;
            assertThat(trucHe)
                    .as("thieu bien the BLOOD o gen_delta " + wanted)
                    .anyMatch(rule -> Integer.valueOf(wanted).equals(rule.genDelta()));
        }
    }

    @Test
    @DisplayName("Chieu nguoc cua ke tu duoc khai; chieu nguoc cua dich ton thi khong")
    void chieuNguocChiKhaiChoKeTu() {
        List<KinshipRule> reversedHeir = SeedKinshipRules.load().stream()
                .filter(rule -> rule.directLink() == DirectLinkType.HEIR && rule.directLinkReversed())
                .toList();

        assertThat(reversedHeir)
                .as("dich ton van la chau noi ruot nen chieu nguoc phai de cho luat huyet thong")
                .allMatch(rule -> "KE_TU".equals(rule.directLinkSubtype()));
        assertThat(reversedHeir).extracting(rule -> rule.relationCode().value())
                .contains("CHA_KE_TU", "ME_KE_TU");
    }

    @Test
    @DisplayName("Moi relation_code chi xuat hien mot lan - trung ma la ghi de am tham")
    void moiRelationCodeChiXuatHienMotLan() {
        Set<RelationCode> seen = new HashSet<>();
        List<RelationCode> duplicates = new java.util.ArrayList<>();

        for (KinshipRule rule : SeedKinshipRules.load()) {
            if (!seen.add(rule.relationCode())) {
                duplicates.add(rule.relationCode());
            }
        }

        assertThat(duplicates)
                .as("CSDL co unique index tren (rule_set_id, relation_code); seed phai ton trong")
                .isEmpty();
    }

    @Test
    @DisplayName("Luat vet KHONG_XAC_DINH co uu tien lon nhat nen luon duoc xet sau cung")
    void luatVetCoUuTienLonNhat() {
        List<KinshipRule> effective = SeedKinshipRules.mienBac().effectiveRules();
        KinshipRule last = effective.get(effective.size() - 1);

        assertThat(last.relationCode()).isEqualTo(RelationCode.KHONG_XAC_DINH);
        assertThat(last.priority()).isEqualTo(999);
        assertThat(effective).isSortedAccordingTo(java.util.Comparator.comparingInt(KinshipRule::priority));
    }

    @Test
    @DisplayName("Luat vet cuoi cung khop moi du kien - nguoi dung khong bao gio nhan ket qua rong")
    void luatVetKhopMoiDuKien() {
        KinshipRule catchAll = SeedKinshipRules.mienBac().effectiveRules().stream()
                .filter(rule -> rule.relationCode().equals(RelationCode.KHONG_XAC_DINH))
                .findFirst()
                .orElseThrow();

        assertThat(catchAll.matches(RelationFacts.unrelated(vn.giapha.shared.vo.Gender.UNKNOWN))).isTrue();
        assertThat(catchAll.matches(RelationFacts.builder()
                .genDelta(7).collateralDegree(9).side(RelationSide.MATERNAL).build())).isTrue();
    }

    @Test
    @DisplayName("Luat khop theo canh truc tiep duoc xet TRUOC luat suy tu LCA")
    void luatTheoCanhTrucTiepDuocXetTruoc() {
        List<KinshipRule> effective = SeedKinshipRules.mienBac().effectiveRules();
        int boNuoi = indexOf(effective, "BO_NUOI");
        int cha = indexOf(effective, "CHA");
        int dichTon = indexOf(effective, "DICH_TON");
        int chauNoi = indexOf(effective, "CHAU_NOI");
        int vo = indexOf(effective, "VO");

        assertThat(boNuoi).as("BO_NUOI phai thang CHA").isLessThan(cha);
        assertThat(dichTon).as("DICH_TON phai thang CHAU_NOI").isLessThan(chauNoi);
        assertThat(vo).as("VO/CHONG khop theo canh SPOUSE, xet som nhat").isLessThan(cha);
    }

    @Test
    @DisplayName("Luat 'chua ro vai' co uu tien lon hon luat co is_elder - chi do khi thieu birth_order")
    void luatChuaRoVaiCoUuTienLonHon() {
        List<KinshipRule> effective = SeedKinshipRules.mienBac().effectiveRules();

        assertThat(indexOf(effective, "BAC_TRAI_NOI")).isLessThan(indexOf(effective, "BAC_CHU_NOI_CHUA_RO"));
        assertThat(indexOf(effective, "ANH_RUOT")).isLessThan(indexOf(effective, "ANH_EM_CHUA_RO_VAI"));
        assertThat(rule(effective, "BAC_CHU_NOI_CHUA_RO").isElder())
                .as("luat 'chua ro vai' khong duoc rang buoc is_elder")
                .isNull();
    }

    @Test
    @DisplayName("Luat bang he ruot duoc xet truoc luat bang he ho - 'bac ruot' khong duoc bien thanh 'bac ho'")
    void luatBangHeRuotDuocXetTruocBangHeHo() {
        List<KinshipRule> effective = SeedKinshipRules.mienBac().effectiveRules();

        assertThat(indexOf(effective, "BAC_TRAI_NOI")).isLessThan(indexOf(effective, "BAC_HO_NOI"));
        assertThat(indexOf(effective, "ANH_RUOT")).isLessThan(indexOf(effective, "ANH_HO"));
        assertThat(rule(effective, "BAC_TRAI_NOI").collateralDegree()).isEqualTo(1);
        assertThat(rule(effective, "BAC_HO_NOI").collateralDegreeMin()).isEqualTo(2);
    }

    @Test
    @DisplayName("Con gai va ben ngoai duoc khai bao ngang bang con trai va ben noi")
    void conGaiVaBenNgoaiNgangBangConTraiVaBenNoi() {
        List<KinshipRule> effective = SeedKinshipRules.mienBac().effectiveRules();

        KinshipRule conTrai = rule(effective, "CON_TRAI");
        KinshipRule conGai = rule(effective, "CON_GAI");
        KinshipRule chauNoi = rule(effective, "CHAU_NOI");
        KinshipRule chauNgoai = rule(effective, "CHAU_NGOAI");

        assertThat(conGai.priority()).isEqualTo(conTrai.priority());
        assertThat(conGai.genDelta()).isEqualTo(conTrai.genDelta());
        assertThat(chauNgoai.priority()).isEqualTo(chauNoi.priority());
        assertThat(chauNgoai.genDelta()).isEqualTo(chauNoi.genDelta());
    }

    @Test
    @DisplayName("Khong luat nao hard-code danh xung o cap ma - ma va danh xung tach nhau")
    void maQuanHeVaDanhXungTachNhau() {
        List<KinshipRule> effective = SeedKinshipRules.mienBac().effectiveRules();

        assertThat(rule(effective, "BAC_TRAI_NOI").title()).isEqualTo("bác");
        assertThat(rule(effective, "BAC_TRAI_NGOAI").title())
                .as("mien Bac goi anh trai cua me la 'bac', khong goi 'cau'")
                .isEqualTo("bác");
        assertThat(rule(effective, "CHU_RE_CUA_CO").title())
                .as("mien Bac goi chong cua co la 'chu'; mien Trung/Nam ghi de thanh 'duong' o cap REGION")
                .isEqualTo("chú");
    }

    @Test
    @DisplayName("Moi luat deu co title khong rong va priority khong am")
    void moiLuatCoTitleVaPriorityHopLe() {
        assertThat(SeedKinshipRules.load()).allSatisfy(rule -> {
            assertThat(rule.title()).isNotBlank();
            assertThat(rule.priority()).isNotNegative();
            assertThat(rule.active()).isTrue();
        });
    }

    private static int indexOf(List<KinshipRule> rules, String code) {
        RelationCode target = RelationCode.of(code);
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).relationCode().equals(target)) {
                return i;
            }
        }
        throw new AssertionError("Seed thieu luat " + code);
    }

    private static KinshipRule rule(List<KinshipRule> rules, String code) {
        return rules.get(indexOf(rules, code));
    }
}
