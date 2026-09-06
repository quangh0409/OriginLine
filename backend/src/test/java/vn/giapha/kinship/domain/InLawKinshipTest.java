package vn.giapha.kinship.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * W3 ca bien 3/6 · <b>DAU / RE</b> — danh xung khong suy thang tu LCA ma di vong qua NGUOI NOI.
 *
 * <p>Hai chieu ({@link InLawDirection}):</p>
 * <ul>
 *   <li>{@code ALTER_IS_SPOUSE} — B lay vao ho nha A (thim, mo, chi dau, con dau...).</li>
 *   <li>{@code EGO_IS_SPOUSE} — A la dau/re, B la ruot thit ben vo/chong A (bo chong, me vo...).</li>
 * </ul>
 */
class InLawKinshipTest {

    private InMemoryClan clan;
    private PersonId ong;
    private PersonId bo;
    private PersonId chu;
    private PersonId co;
    private PersonId thim;
    private PersonId chongCuaCo;
    private PersonId anhTrai;
    private PersonId chiDau;
    private PersonId toi;
    private PersonId conTrai;
    private PersonId conDau;
    private PersonId conGai;
    private PersonId conRe;

    @BeforeEach
    void dungHoCoDauRe() {
        clan = new InMemoryClan();
        ong = clan.nam("Ong");
        bo = clan.person("Bo", Gender.MALE, 1, LocalDate.of(1950, 1, 1), false);
        chu = clan.person("Chu", Gender.MALE, 2, LocalDate.of(1955, 1, 1), false);
        co = clan.person("Co", Gender.FEMALE, 3, LocalDate.of(1958, 1, 1), false);
        thim = clan.nu("Thim");
        chongCuaCo = clan.nam("ChongCuaCo");
        anhTrai = clan.person("AnhTrai", Gender.MALE, 1, LocalDate.of(1975, 1, 1), false);
        toi = clan.person("Toi", Gender.MALE, 2, LocalDate.of(1980, 1, 1), false);
        chiDau = clan.nu("ChiDau");
        conTrai = clan.nam("ConTrai");
        conDau = clan.nu("ConDau");
        conGai = clan.nu("ConGai");
        conRe = clan.nam("ConRe");

        clan.conRuot(ong, bo);
        clan.conRuot(ong, chu);
        clan.conRuot(ong, co);
        clan.conRuot(bo, anhTrai);
        clan.conRuot(bo, toi);
        clan.voChong(chu, thim, 1);
        clan.voChong(co, chongCuaCo, 1);
        clan.voChong(anhTrai, chiDau, 1);
        clan.conRuot(toi, conTrai);
        clan.conRuot(toi, conGai);
        clan.voChong(conTrai, conDau, 1);
        clan.voChong(conGai, conRe, 1);
    }

    @Test
    @DisplayName("Vo cua chu la 'thim' - nguoi noi la em trai cua bo, ben noi")
    void voCuaChuLaThim() {
        RelationFacts facts = clan.factsOf(toi, thim);

        assertThat(facts.side()).isEqualTo(RelationSide.IN_LAW);
        assertThat(facts.inLawDirection()).isEqualTo(InLawDirection.ALTER_IS_SPOUSE);
        assertThat(facts.linkSide()).as("nguoi noi la Chu, ben noi").isEqualTo(RelationSide.PATERNAL);
        assertThat(facts.linkGender()).isEqualTo(Gender.MALE);
        assertThat(facts.isElder()).as("Chu la em cua Bo nen vai duoi").isFalse();
        assertThat(clan.danhXung(toi, thim)).isEqualTo("thím");
    }

    @Test
    @DisplayName("Chong cua co goi la 'chu' theo loi mien Bac")
    void chongCuaCoGoiLaChu() {
        RelationFacts facts = clan.factsOf(toi, chongCuaCo);

        assertThat(facts.linkGender()).as("nguoi noi la Co, nu").isEqualTo(Gender.FEMALE);
        assertThat(clan.resolve(toi, chongCuaCo).relationCode()).isEqualTo(RelationCode.of("CHU_RE_CUA_CO"));
        assertThat(clan.danhXung(toi, chongCuaCo)).isEqualTo("chú");
    }

    @Test
    @DisplayName("Vo cua anh trai la 'chi dau', chong cua chi gai la 'anh re'")
    void voCuaAnhTraiLaChiDau() {
        assertThat(clan.danhXung(toi, chiDau)).isEqualTo("chị dâu");
        assertThat(clan.factsOf(toi, chiDau).isElder()).isTrue();
        assertThat(clan.factsOf(toi, chiDau).genDelta()).isZero();
        assertThat(clan.factsOf(toi, chiDau).collateralDegree()).isEqualTo(1);
    }

    @Test
    @DisplayName("Vo cua con trai la 'con dau', chong cua con gai la 'con re'")
    void voCuaConTraiLaConDau() {
        assertThat(clan.danhXung(toi, conDau)).isEqualTo("con dâu");
        assertThat(clan.danhXung(toi, conRe)).isEqualTo("con rể");
        assertThat(clan.factsOf(toi, conDau).linkGender()).isEqualTo(Gender.MALE);
        assertThat(clan.factsOf(toi, conRe).linkGender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("Con dau goi bo chong la 'bo chong' - chieu EGO_IS_SPOUSE")
    void conDauGoiBoChong() {
        RelationFacts facts = clan.factsOf(conDau, toi);

        assertThat(facts.inLawDirection()).isEqualTo(InLawDirection.EGO_IS_SPOUSE);
        assertThat(facts.genDelta()).isEqualTo(1);
        assertThat(facts.collateralDegree()).isZero();
        assertThat(clan.danhXung(conDau, toi)).isEqualTo("bố chồng");
        assertThat(clan.resolve(conDau, toi).reciprocalTitle()).isEqualTo("con dâu");
    }

    @Test
    @DisplayName("Con re goi bo vo la 'bo vo' - phan biet duoc nho gioi tinh nguoi noi")
    void conReGoiBoVo() {
        assertThat(clan.danhXung(conRe, toi)).isEqualTo("bố vợ");
        assertThat(clan.factsOf(conRe, toi).linkGender())
                .as("nguoi noi la ConGai, nu => B la bo VO chu khong phai bo CHONG")
                .isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("Hon nhan da cham dut khong sinh danh xung dau/re")
    void honNhanDaChamDutKhongSinhDanhXungDauRe() {
        InMemoryClan ly = new InMemoryClan();
        PersonId ongLy = ly.nam("Ong");
        PersonId chuLy = ly.nam("Chu", 2);
        PersonId boLy = ly.nam("Bo", 1);
        PersonId toiLy = ly.nam("Toi");
        PersonId thimCu = ly.nu("ThimCu");
        ly.conRuot(ongLy, boLy);
        ly.conRuot(ongLy, chuLy);
        ly.conRuot(boLy, toiLy);
        ly.voChongDaChamDut(chuLy, thimCu, 1, LocalDate.of(2010, 6, 1));

        KinshipResolution resolution = ly.resolve(toiLy, thimCu);

        assertThat(resolution.status()).isEqualTo(KinshipStatus.NO_COMMON_ANCESTOR);
        assertThat(resolution.relationCode()).isEqualTo(RelationCode.KHONG_XAC_DINH);
        assertThat(resolution.title()).isNotEqualTo("thím");
    }

    @Test
    @DisplayName("Vua la ho xa vua la vo cua chu thi lay duong GAN hon - van goi 'thim'")
    void vuaLaHoXaVuaLaVoCuaChuThiLayDuongGanHon() {
        InMemoryClan lang = new InMemoryClan();
        PersonId thuyTo = lang.nam("ThuyTo");
        PersonId nhanhA = lang.nam("NhanhA", 1);
        PersonId nhanhB = lang.nam("NhanhB", 2);
        PersonId ongL = lang.nam("OngL");
        PersonId boL = lang.person("BoL", Gender.MALE, 1, null, false);
        PersonId chuL = lang.person("ChuL", Gender.MALE, 2, null, false);
        PersonId toiL = lang.nam("ToiL");
        PersonId thimL = lang.nu("ThimL");
        PersonId trungGian1 = lang.nam("TrungGian1");
        PersonId trungGian2 = lang.nam("TrungGian2");
        lang.conRuot(thuyTo, nhanhA);
        lang.conRuot(thuyTo, nhanhB);
        lang.conRuot(nhanhA, ongL);
        lang.conRuot(ongL, boL);
        lang.conRuot(ongL, chuL);
        lang.conRuot(boL, toiL);
        lang.conRuot(nhanhB, trungGian1);
        lang.conRuot(trungGian1, trungGian2);
        lang.conRuot(trungGian2, thimL);
        lang.voChong(chuL, thimL, 1);

        RelationFacts facts = lang.factsOf(toiL, thimL);

        assertThat(facts.side())
                .as("duong huyet thong dai 8 bac, duong dau/re chi 3 bac => lay duong dau/re")
                .isEqualTo(RelationSide.IN_LAW);
        assertThat(lang.danhXung(toiL, thimL)).isEqualTo("thím");
    }

    @Test
    @DisplayName("Quan he huyet thong gan hon quan he hon nhan thi lay huyet thong")
    void quanHeHuyetThongGanHonThiLayHuyetThong() {
        // Anh chi em ho lay nhau: A vua la anh ho cua B vua co the noi qua hon nhan.
        InMemoryClan h = new InMemoryClan();
        PersonId cu = h.nam("Cu");
        PersonId ongA = h.person("OngA", Gender.MALE, 1, null, false);
        PersonId ongB = h.person("OngB", Gender.MALE, 2, null, false);
        PersonId chong = h.nam("Chong");
        PersonId vo = h.nu("Vo");
        h.conRuot(cu, ongA);
        h.conRuot(cu, ongB);
        h.conRuot(ongA, chong);
        h.conRuot(ongB, vo);
        h.voChong(chong, vo, 1);

        assertThat(h.danhXung(chong, vo))
                .as("luat VO khop theo canh SPOUSE (priority 5) nen van thang luat 'em gai ho'")
                .isEqualTo("vợ");
    }

    // ------------------------------------------------------------------ Lo hong da phat hien

    @Test
    @DisplayName("Nguoi lam dau goi duoc ho hang ben chong o moi bac, khong chi rieng bo me chong")
    void nguoiLamDauGoiDuocHoHangBenChong() {
        assertThat(clan.danhXung(conDau, bo)).as("ong noi cua chong").isEqualTo("ông");
        assertThat(clan.danhXung(chiDau, toi)).as("em chong").isEqualTo("chú");
        assertThat(clan.danhXung(thim, toi)).as("chau cua chong").isEqualTo("cháu");
        assertThat(clan.danhXung(conDau, chu)).as("ong chu ben chong").isEqualTo("ông");
    }

    @Test
    @DisplayName("Dau xung ho y nhu chong: chong goi loi nao thi vo dung loi ay")
    void dauXungHoYNhuChong() {
        // Quyet dinh cua Hoi dong: nguoi lam dau xung ho y nhu chong minh. Ben huyet thong co
        // danh xung MO TA ("ong noi", "ong chu"); nguoi lam dau dung LOI GOI cua chinh danh xung do.
        assertThat(clan.danhXung(conTrai, bo)).isEqualTo("ông nội");
        assertThat(clan.danhXung(conDau, bo)).isEqualTo("ông");

        assertThat(clan.danhXung(conTrai, chu)).isEqualTo("ông chú");
        assertThat(clan.danhXung(conDau, chu)).isEqualTo("ông");

        assertThat(clan.resolve(conDau, bo).relationCode()).isEqualTo(RelationCode.of("ONG_BEN_BAN_DOI"));
        assertThat(clan.resolve(conDau, bo).egoSelfTerm()).isEqualTo("cháu");
    }

    @Test
    @DisplayName("Re xung ho y nhu vo; em cua ban doi goi theo loi con cai goi")
    void reXungHoYNhuVo() {
        assertThat(clan.danhXung(conRe, bo)).as("ong noi cua vo").isEqualTo("ông");

        // Cung mot the bac (gen_delta 0, collateral 1) nhung link_gender khac nhau cho hai
        // danh xung khac nhau: em cua CHONG goi "chu"/"co", em cua VO goi "cau"/"di".
        InMemoryClan nhaVo = new InMemoryClan();
        PersonId boVo = nhaVo.nam("BoVo");
        PersonId anhCuaVo = nhaVo.person("AnhCuaVo", Gender.MALE, 1, null, false);
        PersonId vo = nhaVo.person("Vo", Gender.FEMALE, 2, null, false);
        PersonId emTraiCuaVo = nhaVo.person("EmTraiCuaVo", Gender.MALE, 3, null, false);
        PersonId emGaiCuaVo = nhaVo.person("EmGaiCuaVo", Gender.FEMALE, 4, null, false);
        PersonId re = nhaVo.nam("Re");
        nhaVo.conRuot(boVo, anhCuaVo);
        nhaVo.conRuot(boVo, vo);
        nhaVo.conRuot(boVo, emTraiCuaVo);
        nhaVo.conRuot(boVo, emGaiCuaVo);
        nhaVo.voChong(vo, re, 1);

        assertThat(nhaVo.danhXung(re, anhCuaVo)).as("anh vo").isEqualTo("anh");
        assertThat(nhaVo.danhXung(re, emTraiCuaVo)).as("em trai vo").isEqualTo("cậu");
        assertThat(nhaVo.danhXung(re, emGaiCuaVo)).as("em gai vo").isEqualTo("dì");

        // NGOAI LE doi xung ben chong: chi dau goi em chong la "chu", khong goi "em".
        assertThat(clan.resolve(chiDau, toi).relationCode()).isEqualTo(RelationCode.of("CHU_EM_CHONG"));
    }

    @Test
    @DisplayName("Thieu thu tu sinh thi dau/re lui ve danh xung chua ro vai, khong doan bua")
    void thieuThuTuSinhThiDauReLuiVeChuaRoVai() {
        // ConGai va ConTrai deu khong co birth_order lan birth_solar nen khong ket luan duoc vai.
        assertThat(clan.factsOf(conRe, conTrai).isElder()).isNull();
        assertThat(clan.danhXung(conRe, conTrai))
                .as("luat ANH_CAU_BEN_VO_CHUA_RO priority 46 do, lay ca hai kha nang")
                .isEqualTo("anh/cậu");
    }

    @Test
    @DisplayName("Dau/re khong duoc tu suy ra danh xung voi con rieng cua ban doi")
    void dauKhongTuSuyRaDanhXungVoiConRiengCuaBanDoi() {
        // Nhom J6 CO Y bo trong gen_delta=-1 collateral=0: do la quan he con rieng / me ke,
        // quyet dinh #8 cua seed noi ro he thong KHONG tu suy ra ma cho Hoi dong khai.
        InMemoryClan taiHon = new InMemoryClan();
        PersonId chongT = taiHon.nam("ChongT");
        PersonId voMoi = taiHon.nu("VoMoi");
        PersonId conRiengCuaChong = taiHon.nam("ConRiengCuaChong");
        taiHon.voChong(chongT, voMoi, 2);
        taiHon.conRuot(chongT, conRiengCuaChong);

        assertThat(taiHon.resolve(voMoi, conRiengCuaChong).relationCode())
                .as("me ke van phai do Hoi dong khai, khong duoc lang le suy thanh 'chau'")
                .isEqualTo(RelationCode.KHONG_XAC_DINH);
    }

    @Test
    @DisplayName("Danh xung chieu nguoc cua quan he dau/re lui ve ego_self_term cua luat thuan")
    void danhXungChieuNguocLuiVeEgoSelfTerm() {
        KinshipResolution resolution = clan.resolve(toi, thim);

        assertThat(resolution.title()).isEqualTo("thím");
        assertThat(resolution.egoSelfTerm()).isEqualTo("cháu");
        assertThat(resolution.reciprocalTitle())
                .as("thim goi ta la 'chau', khong phai 'chua xac dinh quan he'")
                .isEqualTo("cháu");
    }

    @Test
    @DisplayName("Tra cuu doc lap chieu thim -> toi khop voi reciprocalTitle sau khi bit lo hong J6")
    void chieuNguocCuaThimTraCuuDocLapVanKhopVoiReciprocalTitle() {
        KinshipResolution xuoi = clan.resolve(toi, thim);
        KinshipResolution nguoc = clan.resolve(thim, toi);

        assertThat(xuoi.title()).isEqualTo("thím");

        // Nhom J6 da bit lo hong: chieu EGO_IS_SPOUSE gen_delta=-1 collateral=1 gio khop THAT
        // (CHAU_BEN_BAN_DOI) chu khong con roi vao luat vet.
        assertThat(nguoc.relationCode()).isEqualTo(RelationCode.of("CHAU_BEN_BAN_DOI"));
        assertThat(nguoc.title()).isEqualTo("cháu");

        // Hai duong tinh doc lap nhau phai gap nhau o cung mot cau tra loi.
        assertThat(xuoi.reciprocalTitle())
                .as("reciprocalTitle va tra cuu doc lap khong duoc lech nhau")
                .isEqualTo("cháu")
                .isEqualTo(nguoc.title());
    }

    @Test
    @DisplayName("Chi ho van ghi de duoc luat J6 - danh xung dau/re la du lieu, khong phai code")
    void chiHoGhiDeDuocLuatChieuEgoIsSpouse() {
        // Mot chi giu loi cu, muon con dau cung goi day du "ong noi". Ghi de theo relation_code
        // cua nhom J6, khong dong toi KinshipResolver - dung tinh than FR-1.3a va chuoi ke thua
        // DEFAULT -> REGION -> CLAN -> BRANCH.
        KinshipRuleSet chi = SeedKinshipRules.mienBac().child(RuleScope.BRANCH, "CHI_GIU_LOI_CU",
                java.util.List.of(KinshipRule.builder("ONG_BEN_BAN_DOI", "ông nội")
                        .genDelta(2).side(RelationSide.IN_LAW).gender(Gender.MALE)
                        .inLawDirection(InLawDirection.EGO_IS_SPOUSE)
                        .egoSelfTerm("cháu").priority(20).build()));

        assertThat(clan.danhXung(conDau, bo, SeedKinshipRules.mienBac()))
                .as("bo DEFAULT: dau xung ho y nhu chong")
                .isEqualTo("ông");
        assertThat(clan.danhXung(conDau, bo, chi))
                .as("them mot dong du lieu o cap chi, khong sua mot dong code")
                .isEqualTo("ông nội");
        assertThat(clan.resolve(conDau, bo, chi).ruleSetScope()).isEqualTo(RuleScope.BRANCH);
    }
}
