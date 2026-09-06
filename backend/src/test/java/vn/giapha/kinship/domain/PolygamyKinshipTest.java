package vn.giapha.kinship.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.PersonId;

/**
 * W3 ca bien 1/6 · <b>DA THE / DA PHU</b> ({@code spouse_order}).
 *
 * <p>Quy tac van hoa phai giu: vo ca hay vo le <b>khong lam doi danh xung</b> cua con. Con cung cha
 * khac me van la anh/chi/em RUOT (bac bang he 1, LCA la nguoi cha) chu khong phai anh chi em ho.
 * Rule engine chi tra "vo"/"chong"; viec ghep thanh "vo ca"/"vo hai" la cua tang hien thi (V3 nhom I).</p>
 */
class PolygamyKinshipTest {

    private InMemoryClan clan;
    private PersonId cha;
    private PersonId voCa;
    private PersonId voHai;
    private PersonId voBa;
    private PersonId conVoCa;
    private PersonId conVoHai;
    private PersonId conGaiVoBa;

    @BeforeEach
    void dungHoDaThe() {
        clan = new InMemoryClan();
        cha = clan.nam("Cha");
        voCa = clan.nu("VoCa");
        voHai = clan.nu("VoHai");
        voBa = clan.nu("VoBa");
        conVoCa = clan.person("ConVoCa", vn.giapha.shared.vo.Gender.MALE, 1, LocalDate.of(1980, 1, 1), false);
        conVoHai = clan.person("ConVoHai", vn.giapha.shared.vo.Gender.MALE, 2, LocalDate.of(1984, 1, 1), false);
        conGaiVoBa = clan.person("ConGaiVoBa", vn.giapha.shared.vo.Gender.FEMALE, 3, LocalDate.of(1988, 1, 1), false);

        clan.voChong(cha, voCa, 1);
        clan.voChong(cha, voHai, 2);
        clan.voChong(cha, voBa, 3);
        clan.conRuot(cha, conVoCa);
        clan.conRuot(voCa, conVoCa);
        clan.conRuot(cha, conVoHai);
        clan.conRuot(voHai, conVoHai);
        clan.conRuot(cha, conGaiVoBa);
        clan.conRuot(voBa, conGaiVoBa);
    }

    @Test
    @DisplayName("Con cung cha khac me van la anh em RUOT, khong phai anh em ho")
    void conCungChaKhacMeVanLaAnhEmRuot() {
        RelationFacts facts = clan.factsOf(conVoHai, conVoCa);

        assertThat(facts.collateralDegree())
                .as("LCA la nguoi cha nen bac bang he = 1 (ruot), khong phai 2 (ho)")
                .isEqualTo(1);
        assertThat(clan.danhXung(conVoHai, conVoCa)).isEqualTo("anh trai");
        assertThat(clan.danhXung(conVoCa, conVoHai)).isEqualTo("em trai");
        assertThat(clan.danhXung(conVoCa, conGaiVoBa)).isEqualTo("em gái");
        assertThat(clan.danhXung(conGaiVoBa, conVoCa)).isEqualTo("anh trai");
    }

    @Test
    @DisplayName("LCA cua con cung cha khac me chinh la nguoi cha")
    void lcaCuaConCungChaKhacMeLaNguoiCha() {
        LcaResult lca = clan.contextOf(conVoHai, conVoCa).lca();

        assertThat(lca.lca()).isEqualTo(cha);
        assertThat(lca.distEgo()).isEqualTo(1);
        assertThat(lca.distAlter()).isEqualTo(1);
        assertThat(lca.genDelta()).isZero();
    }

    @Test
    @DisplayName("spouse_order khong lam doi danh xung goc: vo ca, vo hai, vo ba deu la 'vo'")
    void spouseOrderKhongLamDoiDanhXungGoc() {
        assertThat(clan.danhXung(cha, voCa)).isEqualTo("vợ");
        assertThat(clan.danhXung(cha, voHai)).isEqualTo("vợ");
        assertThat(clan.danhXung(cha, voBa)).isEqualTo("vợ");
        assertThat(clan.resolve(cha, voCa).relationCode()).isEqualTo(RelationCode.VO);
        assertThat(clan.resolve(cha, voBa).relationCode()).isEqualTo(RelationCode.VO);
    }

    @Test
    @DisplayName("spouse_order van doc duoc tu do thi de tang hien thi ghep 'vo ca' / 'vo hai'")
    void spouseOrderVanDocDuocTuDoThi() {
        assertThat(clan.spousesOf(cha))
                .extracting(SpouseLink::spouseOrder)
                .containsExactlyInAnyOrder(1, 2, 3);
        assertThat(clan.spousesOf(voHai))
                .singleElement()
                .satisfies(link -> {
                    assertThat(link.spouse()).isEqualTo(cha);
                    assertThat(link.spouseOrder()).isEqualTo(2);
                });
    }

    @Test
    @DisplayName("Moi nguoi vo deu goi chung la 'chong' - da phu doi chieu cung cho ket qua doi xung")
    void moiNguoiVoDeuGoiChungLaChong() {
        assertThat(clan.danhXung(voCa, cha)).isEqualTo("chồng");
        assertThat(clan.danhXung(voHai, cha)).isEqualTo("chồng");
        assertThat(clan.danhXung(voBa, cha)).isEqualTo("chồng");
    }

    @Test
    @DisplayName("Con cua vo le van goi cha la 'bo' - khong phan biet dong chinh dong the")
    void conCuaVoLeVanGoiChaLaBo() {
        assertThat(clan.danhXung(conVoHai, cha)).isEqualTo("bố");
        assertThat(clan.danhXung(conGaiVoBa, cha)).isEqualTo("bố");
        assertThat(clan.danhXung(cha, conGaiVoBa))
                .as("con gai duoc ghi nhan ngang bang con trai - BA v2 §12")
                .isEqualTo("con gái");
    }

    @Test
    @DisplayName("Chau cua ba vo deu la chau NOI cua ong - khong ai bi day sang ben ngoai")
    void chauCuaBaVoDeuLaChauNoi() {
        PersonId chauCuaVoHai = clan.nam("ChauCuaVoHai");
        clan.conRuot(conVoHai, chauCuaVoHai);

        assertThat(clan.danhXung(cha, chauCuaVoHai)).isEqualTo("cháu nội");
        assertThat(clan.danhXung(chauCuaVoHai, cha)).isEqualTo("ông nội");
    }

    @Test
    @DisplayName("Vo le cua bo khong phai me cua con vo ca - khong duoc suy ra danh xung me")
    void voLeCuaBoKhongPhaiMeCuaConVoCa() {
        KinshipResolution resolution = clan.resolve(conVoCa, voHai);

        assertThat(resolution.relationCode())
                .as("bo luat mac dinh khong khai danh xung cho me ke; phai roi vao luat vet, "
                        + "KHONG duoc tra ve 'me'")
                .isEqualTo(RelationCode.KHONG_XAC_DINH);
        assertThat(resolution.title()).isNotEqualTo("mẹ");
    }

    @Test
    @DisplayName("Con cua hai ba vo khac nhau khong duoc coi la con cua nhau qua canh hon nhan")
    void honNhanKhongTaoRaQuanHeChaConGia() {
        assertThat(clan.factsOf(conVoCa, voHai).genDelta()).isEqualTo(1);
        assertThat(clan.factsOf(conVoCa, voHai).side()).isEqualTo(RelationSide.IN_LAW);
        assertThat(clan.factsOf(conVoCa, voHai).inLawDirection()).isEqualTo(InLawDirection.ALTER_IS_SPOUSE);
    }
}
