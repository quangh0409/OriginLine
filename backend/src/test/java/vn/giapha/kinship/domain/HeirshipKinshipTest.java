package vn.giapha.kinship.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * W3 ca bien 5+6/6 · <b>TUYET TU / KE TU</b> va <b>DICH TON / THUA TU</b>.
 *
 * <p>Ba danh xung nay khop theo canh {@code HEIR} nen luon thang suy luan tu LCA (priority 6). Do
 * chinh la diem quan trong: chau dich ton van la chau noi ve mat huyet thong, nhung trong gia pha
 * va trong cung gio, vai tro giu huong hoa moi la thu duoc goi ten.</p>
 */
class HeirshipKinshipTest {

    private InMemoryClan clan;
    private PersonId ong;
    private PersonId conTruong;
    private PersonId conThu;
    private PersonId nguoiTuyetTu;
    private PersonId dichTon;
    private PersonId chauThu;
    private PersonId nguoiKeTu;
    private PersonId nguoiThuaTu;

    @BeforeEach
    void dungHoCoNguoiKeTu() {
        clan = new InMemoryClan();
        ong = clan.nam("Ong");
        conTruong = clan.person("ConTruong", Gender.MALE, 1, null, false);
        conThu = clan.person("ConThu", Gender.MALE, 2, null, false);
        nguoiTuyetTu = clan.person("NguoiTuyetTu", Gender.MALE, 3, null, false);
        dichTon = clan.person("DichTon", Gender.MALE, 1, null, false);
        chauThu = clan.person("ChauThu", Gender.MALE, 2, null, false);
        nguoiKeTu = clan.person("NguoiKeTu", Gender.MALE, 1, null, false);
        nguoiThuaTu = clan.person("NguoiThuaTu", Gender.MALE, 2, null, false);

        clan.conRuot(ong, conTruong);
        clan.conRuot(ong, conThu);
        clan.conRuot(ong, nguoiTuyetTu);
        clan.conRuot(conTruong, dichTon);
        clan.conRuot(conTruong, chauThu);
        clan.conRuot(conThu, nguoiKeTu);
        clan.conRuot(conThu, nguoiThuaTu);

        clan.heir(ong, dichTon, "DICH_TON");
        clan.heir(nguoiTuyetTu, nguoiKeTu, "KE_TU");
        clan.heir(conThu, nguoiThuaTu, "THUA_TU");
    }

    @Test
    @DisplayName("Chau trai truong cua con trai truong duoc goi la 'dich ton', khong phai 'chau noi'")
    void chauTraiTruongCuaConTraiTruongLaDichTon() {
        KinshipResolution resolution = clan.resolve(ong, dichTon);

        assertThat(resolution.title()).isEqualTo("đích tôn");
        assertThat(resolution.relationCode()).isEqualTo(RelationCode.DICH_TON);
        assertThat(clan.danhXung(ong, chauThu))
                .as("chau thu van la chau noi binh thuong")
                .isEqualTo("cháu nội");
    }

    @Test
    @DisplayName("Luat khop canh HEIR thang luat CHAU_NOI suy tu LCA")
    void luatCanhHeirThangLuatChauNoi() {
        List<KinshipRule> matches = SeedKinshipRules.mienBac().matchAll(clan.factsOf(ong, dichTon));

        assertThat(matches).extracting(rule -> rule.relationCode().value())
                .contains("DICH_TON", "CHAU_NOI");
        assertThat(matches.get(0).relationCode()).isEqualTo(RelationCode.DICH_TON);
        assertThat(matches.get(0).priority()).isLessThan(
                matches.stream().filter(r -> r.relationCode().equals(RelationCode.CHAU_NOI))
                        .findFirst().orElseThrow().priority());
    }

    @Test
    @DisplayName("Dich ton van goi ong la 'ong noi' - canh HEIR khong lam mat quan he huyet thong")
    void dichTonVanGoiOngLaOngNoi() {
        assertThat(clan.danhXung(dichTon, ong)).isEqualTo("ông nội");
        assertThat(clan.resolve(dichTon, ong).reciprocalTitle()).isEqualTo("đích tôn");
    }

    @Test
    @DisplayName("Nguoi duoc lap noi doi cho nguoi tuyet tu duoc goi la 'con ke tu'")
    void nguoiDuocLapNoiDoiLaConKeTu() {
        KinshipResolution resolution = clan.resolve(nguoiTuyetTu, nguoiKeTu);

        assertThat(resolution.title()).isEqualTo("con kế tự");
        assertThat(resolution.relationCode()).isEqualTo(RelationCode.CON_KE_TU);
    }

    @Test
    @DisplayName("Nguoi duoc lap de thua tu huong hoa duoc goi la 'con thua tu'")
    void nguoiDuocLapThuaTuHuongHoa() {
        KinshipResolution resolution = clan.resolve(conThu, nguoiThuaTu);

        assertThat(resolution.title()).isEqualTo("con thừa tự");
        assertThat(resolution.relationCode()).isEqualTo(RelationCode.CON_THUA_TU);
        assertThat(clan.danhXung(conThu, nguoiKeTu))
                .as("nguoi con con lai van la con trai binh thuong")
                .isEqualTo("con trai");
    }

    @Test
    @DisplayName("Ba loai canh HEIR phan biet duoc nhau bang subtype, khong lan sang nhau")
    void baLoaiCanhHeirPhanBietBangSubtype() {
        assertThat(clan.factsOf(ong, dichTon).hasDirectLink(DirectLinkType.HEIR, "DICH_TON", false)).isTrue();
        assertThat(clan.factsOf(ong, dichTon).hasDirectLink(DirectLinkType.HEIR, "KE_TU", false)).isFalse();
        assertThat(clan.factsOf(nguoiTuyetTu, nguoiKeTu).hasDirectLink(DirectLinkType.HEIR, "KE_TU", false)).isTrue();
        assertThat(clan.factsOf(conThu, nguoiThuaTu).hasDirectLink(DirectLinkType.HEIR, "THUA_TU", false)).isTrue();
    }

    @Test
    @DisplayName("Canh HEIR chi khop dung chieu: cha ke tu -> con ke tu, khong khop chieu nguoc")
    void canhHeirChiKhopDungChieu() {
        assertThat(clan.factsOf(nguoiKeTu, nguoiTuyetTu)
                .hasDirectLink(DirectLinkType.HEIR, "KE_TU", false))
                .as("nhin tu phia nguoi ke tu, canh la reversed=true")
                .isFalse();
        assertThat(clan.factsOf(nguoiKeTu, nguoiTuyetTu)
                .hasDirectLink(DirectLinkType.HEIR, "KE_TU", true)).isTrue();
    }

    @Test
    @DisplayName("Nguoi tuyet tu khong co con van giu nguyen vi tri trong cay - LCA cua ho van tinh duoc")
    void nguoiTuyetTuVanGiuNguyenViTriTrongCay() {
        assertThat(clan.contextOf(nguoiTuyetTu, dichTon).lca().lca()).isEqualTo(ong);
        assertThat(clan.danhXung(nguoiTuyetTu, dichTon))
                .as("dich ton la con cua anh trai nen la 'chau trai'")
                .isEqualTo("cháu trai");
    }

    @Test
    @DisplayName("Nguoi ke tu goi nguoi de lai huong hoa bang danh xung cua vai tro noi doi")
    void nguoiKeTuGoiNguoiDeLaiHuongHoaTheoVaiTroNoiDoi() {
        assertThat(clan.resolve(nguoiKeTu, nguoiTuyetTu).relationCode())
                .isEqualTo(RelationCode.of("CHA_KE_TU"));
    }

    @Test
    @DisplayName("Chieu nguoc cua 'con ke tu' giu vai tro noi doi, khong con roi ve 'chu' tron tron")
    void chieuNguocCuaConKeTuGiuVaiTroNoiDoi() {
        KinshipResolution nguoc = clan.resolve(nguoiKeTu, nguoiTuyetTu);

        // Ke tu lap ra mot dong cha-con TREN DANH NGHIA: nguoi ke tu cung gio nhu con, nen
        // chieu nguoc phai la "cha ke tu" chu khong phai suy luan huyet thong thuan tuy ("chu").
        assertThat(nguoc.title()).isEqualTo("cha kế tự");
        assertThat(nguoc.egoSelfTerm()).isEqualTo("con kế tự");
        assertThat(clan.resolve(nguoiTuyetTu, nguoiKeTu).reciprocalTitle()).isEqualTo("cha kế tự");

        // Doi chieu: quan he huyet thong that su van la "chu" - chi la vai tro noi doi thang.
        assertThat(clan.danhXung(nguoiKeTu, conTruong))
                .as("nguoi bac ruot khong dinh gi toi ke tu thi van la danh xung huyet thong")
                .isEqualTo("bác");
    }

    @Test
    @DisplayName("Nguoi thua tu la con ruot van goi 'bo' - canh HEIR khong xoa quan he huyet thong")
    void nguoiThuaTuVanGoiChaDeLaBo() {
        // Vi sao nhom K2 CO Y chi khai chieu nguoc cho KE_TU: thua tu thuong la chinh con ruot,
        // va dich ton von la chau noi ruot. Khai chieu nguoc cho hai loai do se xoa mat quan he
        // huyet thong dang co.
        assertThat(clan.danhXung(nguoiThuaTu, conThu)).isEqualTo("bố");
        assertThat(clan.danhXung(dichTon, ong)).isEqualTo("ông nội");
    }

    @Test
    @DisplayName("Dong ho co the khai danh xung rieng cho nguoi ke tu o cap chi ma khong sua code")
    void dongHoKhaiDanhXungRiengChoNguoiKeTuOCapChi() {
        KinshipRuleSet chi = SeedKinshipRules.mienBac().child(RuleScope.BRANCH, "CHI_CO_CHA_KE_TU",
                List.of(KinshipRule.builder("CHA_KE_TU", "thân phụ kế tự")
                        .side(RelationSide.BLOOD).directLink(DirectLinkType.HEIR)
                        .directLinkSubtype("KE_TU").directLinkReversed(true)
                        .egoSelfTerm("con kế tự").priority(6).build()));

        assertThat(clan.danhXung(nguoiKeTu, nguoiTuyetTu, chi))
                .as("chi ho ghi de theo relation_code, khong sua mot dong code nao")
                .isEqualTo("thân phụ kế tự");
        assertThat(clan.danhXung(nguoiKeTu, nguoiTuyetTu, SeedKinshipRules.mienBac()))
                .as("bo DEFAULT khong doi")
                .isEqualTo("cha kế tự");
    }

    @Test
    @DisplayName("Vai tro noi doi tach theo bac va theo gioi tinh dung nhu Hoi dong chot")
    void vaiTroNoiDoiTachTheoBacVaGioiTinh() {
        // Dich ton la nu thi goi "dich nu" - con gai ghi nhan ngang bang con trai (BA v2 §12).
        InMemoryClan hoNu = new InMemoryClan();
        PersonId cu = hoNu.nam("Cu");
        PersonId chaN = hoNu.nam("ChaN");
        PersonId dichNu = hoNu.nu("DichNu");
        hoNu.conRuot(cu, chaN);
        hoNu.conRuot(chaN, dichNu);
        hoNu.heir(cu, dichNu, "DICH_TON");

        assertThat(hoNu.resolve(cu, dichNu).relationCode()).isEqualTo(RelationCode.of("DICH_NU"));
        assertThat(hoNu.danhXung(cu, dichNu)).isEqualTo("đích nữ");

        // Ke tu kem hai doi thi goi "chau ke tu" chu khong phai "con ke tu".
        InMemoryClan hoChau = new InMemoryClan();
        PersonId to = hoChau.nam("To");
        PersonId tuyetTu = hoChau.nam("TuyetTu");
        PersonId nhanh = hoChau.nam("Nhanh");
        PersonId conNhanh = hoChau.nam("ConNhanh");
        PersonId chauKeTu = hoChau.nam("ChauKeTu");
        hoChau.conRuot(to, tuyetTu);
        hoChau.conRuot(to, nhanh);
        hoChau.conRuot(nhanh, conNhanh);
        hoChau.conRuot(conNhanh, chauKeTu);
        hoChau.heir(tuyetTu, chauKeTu, "KE_TU");

        assertThat(hoChau.resolve(tuyetTu, chauKeTu).relationCode())
                .isEqualTo(RelationCode.of("CHAU_KE_TU"));
        assertThat(hoChau.danhXung(tuyetTu, chauKeTu)).isEqualTo("cháu kế tự");
    }
}
