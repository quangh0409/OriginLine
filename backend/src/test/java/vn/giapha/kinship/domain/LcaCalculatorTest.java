package vn.giapha.kinship.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * W3 · {@link LcaCalculator} — phan de sai nhat cua truy van to chung: chon LCA khi hoa, dung lai
 * duong di, co con nuoi, va bo qua nguoi da xoa mem.
 */
class LcaCalculatorTest {

    @Test
    @DisplayName("Anh chi em ruot co ca cha lan me la to chung 1-1: pha hoa TAT DINH, uu tien to chung nam")
    void phaHoaTatDinhUuTienToChungNam() {
        InMemoryClan clan = new InMemoryClan();
        PersonId cha = clan.nam("Cha");
        PersonId me = clan.nu("Me");
        PersonId anh = clan.nam("Anh", 1);
        PersonId em = clan.nu("Em", 2);
        clan.conRuot(cha, anh);
        clan.conRuot(me, anh);
        clan.conRuot(cha, em);
        clan.conRuot(me, em);

        for (int lan = 0; lan < 20; lan++) {
            LcaResult lca = clan.contextOf(em, anh).lca();
            assertThat(lca.lca())
                    .as("cung mot cau hoi phai cho cung mot duong quan he o moi lan goi")
                    .isEqualTo(cha);
        }
        assertThat(clan.factsOf(em, anh).side()).isEqualTo(RelationSide.PATERNAL);
    }

    @Test
    @DisplayName("Chon to chung co tong so bac nho nhat, khong phai to chung xa nhat")
    void chonToChungCoTongSoBacNhoNhat() {
        InMemoryClan clan = new InMemoryClan();
        PersonId cu = clan.nam("Cu");
        PersonId ong = clan.nam("Ong");
        PersonId bo = clan.nam("Bo", 1);
        PersonId chu = clan.nam("Chu", 2);
        PersonId toi = clan.nam("Toi");
        PersonId conChu = clan.nam("ConChu");
        clan.conRuot(cu, ong);
        clan.conRuot(ong, bo);
        clan.conRuot(ong, chu);
        clan.conRuot(bo, toi);
        clan.conRuot(chu, conChu);

        LcaResult lca = clan.contextOf(toi, conChu).lca();

        assertThat(lca.lca()).as("Ong o 2+2 bac, Cu o 3+3 bac").isEqualTo(ong);
        assertThat(lca.distEgo()).isEqualTo(2);
        assertThat(lca.distAlter()).isEqualTo(2);
        assertThat(lca.collateralDegree()).isEqualTo(2);
    }

    @Test
    @DisplayName("Nguoi da xoa mem van di XUYEN QUA duoc nhung KHONG duoc chon lam to chung")
    void nguoiDaXoaMemKhongDuocLamToChung() {
        InMemoryClan clan = new InMemoryClan();
        PersonId cu = clan.nam("Cu");
        PersonId ong = clan.nam("Ong");
        PersonId bo1 = clan.nam("Bo1", 1);
        PersonId bo2 = clan.nam("Bo2", 2);
        PersonId a = clan.nam("A");
        PersonId b = clan.nam("B");
        clan.conRuot(cu, ong);
        clan.conRuot(ong, bo1);
        clan.conRuot(ong, bo2);
        clan.conRuot(bo1, a);
        clan.conRuot(bo2, b);

        LcaResult truoc = clan.contextOf(a, b).lca();
        assertThat(truoc.lca()).isEqualTo(ong);
        assertThat(truoc.collateralDegree()).isEqualTo(2);

        clan.xoaMem(ong);
        LcaResult sau = clan.contextOf(a, b).lca();

        assertThat(sau.lca()).as("phai leo len to chung tiep theo chua bi xoa").isEqualTo(cu);
        assertThat(sau.egoPathUp())
                .as("node da xoa mem VAN nam tren duong di de khong dut lien ket cac doi")
                .containsExactly(a, bo1, ong, cu);
        assertThat(sau.collateralDegree()).isEqualTo(3);
    }

    @Test
    @DisplayName("Hai nguoi khong cung dong ho thi khong co to chung nao")
    void haiNguoiKhongCungDongHoThiKhongCoToChung() {
        InMemoryClan clan = new InMemoryClan();
        PersonId a = clan.nam("HoA");
        PersonId b = clan.nam("HoB");
        clan.conRuot(clan.nam("ChaA"), a);
        clan.conRuot(clan.nam("ChaB"), b);

        assertThat(clan.contextOf(a, b).lca()).isNull();
        assertThat(clan.contextOf(a, b).hasNoConnection()).isTrue();
        assertThat(clan.resolve(a, b).status()).isEqualTo(KinshipStatus.NO_COMMON_ANCESTOR);
    }

    @Test
    @DisplayName("Duong di len duoc dung lai day du: phan tu dau la chinh nguoi do, phan tu cuoi la LCA")
    void duongDiLenDuocDungLaiDayDu() {
        InMemoryClan clan = new InMemoryClan();
        PersonId cu = clan.nam("Cu");
        PersonId ongA = clan.nam("OngA", 1);
        PersonId ongB = clan.nam("OngB", 2);
        PersonId boA = clan.nam("BoA");
        PersonId boB = clan.nam("BoB");
        PersonId toi = clan.nam("Toi");
        PersonId hoHang = clan.nam("HoHang");
        clan.conRuot(cu, ongA);
        clan.conRuot(cu, ongB);
        clan.conRuot(ongA, boA);
        clan.conRuot(ongB, boB);
        clan.conRuot(boA, toi);
        clan.conRuot(boB, hoHang);

        LcaResult lca = clan.contextOf(toi, hoHang).lca();

        assertThat(lca.egoPathUp()).containsExactly(toi, boA, ongA, cu);
        assertThat(lca.alterPathUp()).containsExactly(hoHang, boB, ongB, cu);
        assertThat(lca.egoFirstParent()).isEqualTo(boA);
        assertThat(lca.alterFirstParent()).isEqualTo(boB);
        assertThat(lca.egoLineNode()).as("con cua LCA tren duong cua ego").isEqualTo(ongA);
        assertThat(lca.alterLineNode()).as("con cua LCA tren duong cua alter").isEqualTo(ongB);
        assertThat(lca.genDelta()).isZero();
        assertThat(lca.collateralDegree()).isEqualTo(3);
    }

    @Test
    @DisplayName("Dao chieu ego/alter chi doi cho hai duong di, khong hoi lai do thi")
    void daoChieuChiDoiChoHaiDuongDi() {
        InMemoryClan clan = new InMemoryClan();
        PersonId ong = clan.nam("Ong");
        PersonId bo = clan.nam("Bo", 1);
        PersonId bac = clan.nam("Bac", 0);
        PersonId toi = clan.nam("Toi");
        clan.conRuot(ong, bo);
        clan.conRuot(ong, bac);
        clan.conRuot(bo, toi);

        LcaResult xuoi = clan.contextOf(toi, bac).lca();
        LcaResult nguoc = xuoi.reversed();

        assertThat(nguoc.egoPathUp()).isEqualTo(xuoi.alterPathUp());
        assertThat(nguoc.alterPathUp()).isEqualTo(xuoi.egoPathUp());
        assertThat(nguoc.genDelta()).isEqualTo(-xuoi.genDelta());
        assertThat(nguoc.collateralDegree()).isEqualTo(xuoi.collateralDegree());
        assertThat(nguoc.lca()).isEqualTo(xuoi.lca());
    }

    @Test
    @DisplayName("Co qua-con-nuoi danh dau theo CAP CANH: duong ben nha cha de khong bi bao nham")
    void coQuaConNuoiDanhDauTheoCapCanh() {
        InMemoryClan clan = new InMemoryClan();
        PersonId ongDe = clan.nam("OngDe");
        PersonId chaDe = clan.nam("ChaDe");
        PersonId chaNuoi = clan.nam("ChaNuoi");
        PersonId ongNuoi = clan.nam("OngNuoi");
        PersonId tre = clan.nam("Tre");
        clan.conRuot(ongDe, chaDe);
        clan.conRuot(chaDe, tre);
        clan.conRuot(ongNuoi, chaNuoi);
        clan.conNuoi(chaNuoi, tre);

        assertThat(clan.contextOf(tre, ongDe).lca().viaAdoption())
                .as("duong ben nha cha de khong co canh nuoi nao")
                .isFalse();
        assertThat(clan.contextOf(tre, ongNuoi).lca().viaAdoption())
                .as("duong ben nha cha nuoi co canh nuoi")
                .isTrue();
    }

    @Test
    @DisplayName("Tra ve chinh minh khi hai id trung nhau - trang thai SELF, khong co danh xung")
    void traVeChinhMinh() {
        InMemoryClan clan = new InMemoryClan();
        PersonId toi = clan.nam("Toi");

        KinshipResolution resolution = clan.resolve(toi, toi);

        assertThat(resolution.status()).isEqualTo(KinshipStatus.SELF);
        assertThat(resolution.title()).isNull();
        assertThat(resolution.reciprocalTitle()).isNull();
        assertThat(clan.contextOf(toi, toi).isSelf()).isTrue();
    }

    @Test
    @DisplayName("Cay sau 7 doi van tinh dung LCA va bac bang he")
    void caySau7DoiVanTinhDungLca() {
        InMemoryClan clan = new InMemoryClan();
        PersonId thuyTo = clan.nam("ThuyTo");
        PersonId truoc = thuyTo;
        for (int doi = 2; doi <= 7; doi++) {
            PersonId con = clan.nam("Doi" + doi, 1);
            clan.conRuot(truoc, con);
            truoc = con;
        }
        PersonId doi7NhanhChinh = truoc;

        PersonId nhanhPhu = clan.nam("NhanhPhuDoi2", 2);
        clan.conRuot(thuyTo, nhanhPhu);
        PersonId truocPhu = nhanhPhu;
        for (int doi = 3; doi <= 7; doi++) {
            PersonId con = clan.nam("NhanhPhuDoi" + doi, 1);
            clan.conRuot(truocPhu, con);
            truocPhu = con;
        }
        PersonId doi7NhanhPhu = truocPhu;

        LcaResult lca = clan.contextOf(doi7NhanhChinh, doi7NhanhPhu).lca();

        assertThat(lca.lca()).isEqualTo(thuyTo);
        assertThat(lca.distEgo()).isEqualTo(6);
        assertThat(lca.distAlter()).isEqualTo(6);
        assertThat(lca.collateralDegree()).isEqualTo(6);
        assertThat(clan.danhXung(doi7NhanhChinh, doi7NhanhPhu))
                .as("nhanh phu la nhanh thu (birth_order 2 ngay duoi thuy to) nen la vai duoi")
                .isEqualTo("em trai họ");
        assertThat(clan.danhXung(doi7NhanhPhu, doi7NhanhChinh))
                .as("chieu nguoc lai: nhanh truong la vai tren")
                .isEqualTo("anh họ");
    }

    @Test
    @DisplayName("LcaResult tu choi hai duong di ket thuc o hai to chung khac nhau")
    void lcaResultTuChoiHaiDuongDiKhacToChung() {
        PersonId a = PersonId.newId();
        PersonId b = PersonId.newId();
        PersonId toA = PersonId.newId();
        PersonId toB = PersonId.newId();

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> new LcaResult(List.of(a, toA), List.of(b, toB), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cung mot LCA");
    }

    @Test
    @DisplayName("PersonView khong ro gioi tinh duoc quy ve UNKNOWN chu khong de null")
    void personViewKhongRoGioiTinhQuyVeUnknown() {
        PersonView view = PersonView.minimal(PersonId.newId(), null);

        assertThat(view.gender()).isEqualTo(Gender.UNKNOWN);
        assertThat(RelationSide.fromParentGender(Gender.UNKNOWN)).isEqualTo(RelationSide.BLOOD);
        assertThat(RelationSide.fromParentGender(null)).isEqualTo(RelationSide.BLOOD);
    }
}
