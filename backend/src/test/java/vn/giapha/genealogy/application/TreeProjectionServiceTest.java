package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.command.TreeQuery;
import vn.giapha.genealogy.application.view.PersonBadge;
import vn.giapha.genealogy.application.view.TreeDirection;
import vn.giapha.genealogy.application.view.TreeNodeView;
import vn.giapha.genealogy.application.view.TreeProjectionView;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonFixtures;
import vn.giapha.genealogy.domain.Relationship;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.Gender;

/**
 * <b>W7 — dựng projection phẳng của một nhánh phả đồ.</b>
 *
 * <p>Ba nhịp tách bạch có chủ ý: duyệt đồ thị ra khung xương (đắt, <b>được cache</b>), nạp hồ sơ
 * theo lô, rồi lọc phân tầng riêng tư (<b>luôn chạy tươi</b>). Nhịp ba không được trộn vào nhịp
 * một: cache một cây đã lọc theo vai này rồi trả cho vai khác là rò rỉ dữ liệu người còn sống mà
 * không có bất kỳ dấu vết nào trong log.
 *
 * <p><b>Cây có lỗ là kết quả đúng.</b> Cạnh chỉ xuất hiện khi cả hai đầu đều hiển thị được, nên với
 * Khách phả đồ có thể đứt đoạn giữa các đời.
 */
class TreeProjectionServiceTest {

    private GenealogyServiceFixture fx;
    private Person cuTo;
    private Person conTraiDaKhuat;
    private Person chauConSong;

    @BeforeEach
    void dungHoBaDoi() {
        fx = new GenealogyServiceFixture();
        cuTo = fx.seed(PersonFixtures.daKhuat("Cụ Tổ", Gender.MALE), fx.chiGiap);
        cuTo.placeInGeneration(1);
        fx.persons.seed(cuTo);
        conTraiDaKhuat = fx.seed(PersonFixtures.daKhuat("Con Trai Cụ", Gender.MALE), fx.chiGiap);
        chauConSong = fx.seed(PersonFixtures.nu("Cháu Gái Còn Sống"), fx.chiGiap);
        fx.seedParent(cuTo, conTraiDaKhuat, false);
        fx.seedParent(conTraiDaKhuat, chauConSong, false);
    }

    @AfterEach
    void dongPhien() {
        fx.dangXuat();
    }

    private TreeQuery truyVan(UUID goc, TreeDirection chieu, boolean kemVoChong) {
        return new TreeQuery(goc, 5, chieu, kemVoChong, false, 100);
    }

    @Test
    @DisplayName("Dựng đủ node và cạnh của một nhánh ba đời")
    void dungDuNodeVaCanhBaDoi() {
        fx.dangNhapAdmin();

        TreeProjectionView view = fx.tree.project(truyVan(cuTo.rawId(), TreeDirection.DESCENDANTS, false));

        assertThat(view.rootId()).isEqualTo(cuTo.rawId());
        assertThat(view.nodes()).extracting(TreeNodeView::id)
                .containsExactlyInAnyOrder(cuTo.rawId(), conTraiDaKhuat.rawId(), chauConSong.rawId());
        assertThat(view.edges()).hasSize(2);
        assertThat(view.meta().nodeCount()).isEqualTo(3);
        assertThat(view.meta().edgeCount()).isEqualTo(2);
        assertThat(view.meta().truncated()).isFalse();
    }

    @Test
    @DisplayName("Độ sâu: gốc = 0, đời dưới dương, đời trên ÂM")
    void doSauGocLaKhongDoiTrenLaAm() {
        fx.dangNhapAdmin();

        TreeProjectionView xuong = fx.tree.project(truyVan(cuTo.rawId(), TreeDirection.DESCENDANTS, false));
        TreeProjectionView len = fx.tree.project(
                truyVan(chauConSong.rawId(), TreeDirection.ANCESTORS, false));

        assertThat(doSauCua(xuong, cuTo.rawId())).isZero();
        assertThat(doSauCua(xuong, conTraiDaKhuat.rawId())).isEqualTo(1);
        assertThat(doSauCua(xuong, chauConSong.rawId())).isEqualTo(2);

        assertThat(doSauCua(len, chauConSong.rawId())).isZero();
        assertThat(doSauCua(len, conTraiDaKhuat.rawId()))
                .as("quy uoc doi tren mang do sau am di thang ra contract")
                .isEqualTo(-1);
        assertThat(doSauCua(len, cuTo.rawId())).isEqualTo(-2);
    }

    @Test
    @DisplayName("Khách không thấy người còn sống: cây đứt đoạn là kết quả ĐÚNG")
    void khachThayCayDutDoan() {
        fx.dangXuat();

        TreeProjectionView view = fx.tree.project(truyVan(cuTo.rawId(), TreeDirection.DESCENDANTS, false));

        assertThat(view.nodes()).extracting(TreeNodeView::id)
                .containsExactlyInAnyOrder(cuTo.rawId(), conTraiDaKhuat.rawId());
        assertThat(view.edges())
                .as("canh chi xuat hien khi CA HAI dau deu hien thi duoc")
                .hasSize(1);
    }

    @Test
    @DisplayName("Gốc không hiển thị được thì trả 404, không tiết lộ rằng nó tồn tại")
    void gocKhongHienThiDuocThiTra404() {
        fx.dangXuat();

        assertThatThrownBy(() -> fx.tree.project(
                truyVan(chauConSong.rawId(), TreeDirection.DESCENDANTS, false)))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.NOT_FOUND);
    }

    @Test
    @DisplayName("Với Khách, số con hiển thị chỉ đếm người con CÓ MẶT trong projection")
    void voiKhachSoConChiDemNguoiCoMat() {
        fx.dangXuat();

        TreeProjectionView view = fx.tree.project(truyVan(cuTo.rawId(), TreeDirection.DESCENDANTS, false));

        TreeNodeView conTrai = nodeCua(view, conTraiDaKhuat.rawId());
        assertThat(conTrai.childCount())
                .as("de lo con so that la gian tiep noi 'ong nay con mot nguoi con ban khong duoc thay'")
                .isZero();
        assertThat(conTrai.hasMoreDescendants()).isFalse();
    }

    @Test
    @DisplayName("Với thành viên, số con là con số thật, kể cả phần chưa nạp")
    void voiThanhVienSoConLaConSoThat() {
        fx.dangNhapThanhVien(UUID.randomUUID(), GenealogyServiceFixture.P_CHI_GIAP);

        TreeProjectionView view = fx.tree.project(
                new TreeQuery(cuTo.rawId(), 1, TreeDirection.DESCENDANTS, false, false, 100));

        TreeNodeView conTrai = nodeCua(view, conTraiDaKhuat.rawId());
        assertThat(conTrai.childCount()).isEqualTo(1);
        assertThat(conTrai.hasMoreDescendants())
                .as("FE can biet con nguoi con chua nap de ve nut mo rong")
                .isTrue();
    }

    @Test
    @DisplayName("Khung xương được cache; lượt sau đánh dấu fromCache và không duyệt lại đồ thị")
    void khungXuongDuocCache() {
        fx.dangNhapAdmin();
        TreeQuery q = truyVan(cuTo.rawId(), TreeDirection.DESCENDANTS, false);

        TreeProjectionView lanMot = fx.tree.project(q);
        TreeProjectionView lanHai = fx.tree.project(q);

        assertThat(lanMot.meta().fromCache()).isFalse();
        assertThat(lanHai.meta().fromCache()).isTrue();
        assertThat(fx.treeCache.khoaDaGhi).hasSize(1);
    }

    @Test
    @DisplayName("Khoá cache KHÔNG chứa danh tính hay vai người gọi")
    void khoaCacheKhongChuaDanhTinhNguoiGoi() {
        fx.dangNhapThanhVien(UUID.randomUUID(), GenealogyServiceFixture.P_CHI_GIAP);
        fx.tree.project(truyVan(cuTo.rawId(), TreeDirection.DESCENDANTS, false));

        assertThat(fx.treeCache.khoaDaGhi).singleElement()
                .satisfies(khoa -> {
                    assertThat(khoa).startsWith("tree:v1:").contains(cuTo.rawId().toString());
                    assertThat(khoa).doesNotContain("MEMBER").doesNotContain("sub-member");
                });
    }

    @Test
    @DisplayName("Cache dùng chung mọi vai nhưng NỘI DUNG trả về vẫn được lọc tươi cho từng vai")
    void cacheDungChungNhungNoiDungVanLocTuoi() {
        TreeQuery q = truyVan(cuTo.rawId(), TreeDirection.DESCENDANTS, false);

        fx.dangNhapAdmin();
        TreeProjectionView cuaAdmin = fx.tree.project(q);
        fx.dangXuat();
        TreeProjectionView cuaKhach = fx.tree.project(q);

        assertThat(cuaKhach.meta().fromCache())
                .as("khung xuong khong chua du lieu ca nhan nen dung chung duoc")
                .isTrue();
        assertThat(cuaAdmin.nodes()).hasSize(3);
        assertThat(cuaKhach.nodes())
                .as("bo loc rieng tu phai chay lai TUOI tren moi phan hoi")
                .hasSize(2);
    }

    @Test
    @DisplayName("Vợ/chồng được kéo vào cây ở cùng độ sâu với người phối ngẫu")
    void voChongDuocKeoVaoCungDoSau() {
        fx.dangNhapAdmin();
        Person voConTrai = fx.seed(PersonFixtures.daKhuat("Vợ Con Trai", Gender.FEMALE), fx.chiGiap);
        fx.relationships.seed(Relationship.spouse(UUID.randomUUID(), conTraiDaKhuat.rawId(),
                voConTrai.rawId(), 1, null, null));

        TreeProjectionView khongKem = fx.tree.project(
                truyVan(cuTo.rawId(), TreeDirection.DESCENDANTS, false));
        TreeProjectionView coKem = fx.tree.project(
                truyVan(cuTo.rawId(), TreeDirection.DESCENDANTS, true));

        assertThat(khongKem.nodes()).extracting(TreeNodeView::id).doesNotContain(voConTrai.rawId());
        assertThat(coKem.nodes()).extracting(TreeNodeView::id).contains(voConTrai.rawId());
        assertThat(doSauCua(coKem, voConTrai.rawId()))
                .as("vo/chong dung cung mot doi tren canvas")
                .isEqualTo(doSauCua(coKem, conTraiDaKhuat.rawId()));
    }

    @Test
    @DisplayName("Người kéo vào chỉ vì kết hôn được gắn nhãn dâu (nữ) hoặc rể (nam)")
    void nguoiKetHonVaoDuocGanNhanDauRe() {
        fx.dangNhapAdmin();
        Person conDau = fx.seed(PersonFixtures.daKhuat("Con Dâu", Gender.FEMALE), fx.chiGiap);
        fx.relationships.seed(Relationship.spouse(UUID.randomUUID(), conTraiDaKhuat.rawId(),
                conDau.rawId(), 1, null, null));

        TreeProjectionView view = fx.tree.project(truyVan(cuTo.rawId(), TreeDirection.DESCENDANTS, true));

        assertThat(nodeCua(view, conDau.rawId()).badges()).contains(PersonBadge.DAU);
        assertThat(nodeCua(view, conTraiDaKhuat.rawId()).badges())
                .as("nguoi trong huyet thong khong bao gio la dau/re cua chinh dong ho minh")
                .doesNotContain(PersonBadge.DAU, PersonBadge.RE);
    }

    @Test
    @DisplayName("Bản ghi đã xoá mềm biến mất khỏi phả đồ với vai thường")
    void banGhiDaXoaMemBienMatKhoiPhaDo() {
        fx.dangNhapAdmin();
        fx.softDelete.softDelete(conTraiDaKhuat.rawId(), "trùng");
        fx.dangXuat();
        fx.dangNhapThanhVien(UUID.randomUUID(), GenealogyServiceFixture.P_CHI_GIAP);

        TreeProjectionView view = fx.tree.project(truyVan(cuTo.rawId(), TreeDirection.DESCENDANTS, false));

        assertThat(view.nodes()).extracting(TreeNodeView::id).doesNotContain(conTraiDaKhuat.rawId());
    }

    @Test
    @DisplayName("Xem kèm bản ghi đã xoá mềm chỉ dành cho Hội đồng và Quản trị hệ thống")
    void xemKemBanGhiDaXoaChiDanhChoClanWide() {
        fx.dangNhapThanhVien(UUID.randomUUID(), GenealogyServiceFixture.P_CHI_GIAP);
        TreeQuery kemDaXoa = new TreeQuery(cuTo.rawId(), 5, TreeDirection.DESCENDANTS,
                false, true, 100);

        assertThatThrownBy(() -> fx.tree.project(kemDaXoa))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.FORBIDDEN);
    }

    @Test
    @DisplayName("Chạm trần maxNodes thì cắt bớt và đánh dấu các node cần nút mở rộng")
    void chamTranMaxNodesThiCatBot() {
        fx.dangNhapAdmin();

        TreeProjectionView view = fx.tree.project(
                new TreeQuery(cuTo.rawId(), 5, TreeDirection.DESCENDANTS, false, false, 2));

        assertThat(view.meta().truncated())
                .as("cay lon la chuyen binh thuong cua mot dong ho; cham tran khong phai loi")
                .isTrue();
        assertThat(view.nodes()).hasSize(2);
        assertThat(view.meta().truncatedNodeIds()).isNotEmpty();
    }

    @Test
    @DisplayName("Trần depth và maxNodes được ép về khoảng hợp lệ ngay ở TreeQuery")
    void tranDepthVaMaxNodesDuocEpVeKhoangHopLe() {
        TreeQuery quaSau = new TreeQuery(cuTo.rawId(), 999, TreeDirection.DESCENDANTS,
                false, false, 999999);
        TreeQuery quaNong = new TreeQuery(cuTo.rawId(), -5, null, false, false, 0);

        assertThat(quaSau.depth()).isEqualTo(TreeQuery.MAX_DEPTH);
        assertThat(quaSau.maxNodes()).isEqualTo(TreeQuery.MAX_NODES);
        assertThat(quaNong.depth()).isZero();
        assertThat(quaNong.maxNodes()).isEqualTo(1);
        assertThat(quaNong.direction()).isEqualTo(TreeDirection.DESCENDANTS);
    }

    @Test
    @DisplayName("Node mang danh sách cha và vợ/chồng CÓ MẶT trong projection")
    void nodeMangDanhSachChaVaVoChongCoMat() {
        fx.dangNhapAdmin();
        Person vo = fx.seed(PersonFixtures.daKhuat("Vợ Cụ Tổ", Gender.FEMALE), fx.chiGiap);
        fx.relationships.seed(Relationship.spouse(UUID.randomUUID(), cuTo.rawId(), vo.rawId(),
                1, null, null));

        TreeProjectionView view = fx.tree.project(truyVan(cuTo.rawId(), TreeDirection.DESCENDANTS, true));

        assertThat(nodeCua(view, conTraiDaKhuat.rawId()).parentIds()).containsExactly(cuTo.rawId());
        assertThat(nodeCua(view, cuTo.rawId()).spouseIds()).containsExactly(vo.rawId());
        assertThat(nodeCua(view, cuTo.rawId()).parentIds()).isEmpty();
    }

    @Test
    @DisplayName("Hồ sơ trên node chỉ mang dữ liệu Tầng 1 khi người gọi ở Tầng 1")
    void hoSoTrenNodeChiMangTang1() {
        fx.dangNhapThanhVien(UUID.randomUUID(), GenealogyServiceFixture.P_CHI_AT);

        TreeProjectionView view = fx.tree.project(truyVan(cuTo.rawId(), TreeDirection.DESCENDANTS, false));

        assertThat(nodeCua(view, chauConSong.rawId()).person().birthYear()).isNull();
        assertThat(nodeCua(view, chauConSong.rawId()).person().avatarKey()).isNull();
        assertThat(nodeCua(view, chauConSong.rawId()).person().displayName())
                .isEqualTo("Cháu Gái Còn Sống");
    }

    private static TreeNodeView nodeCua(TreeProjectionView view, UUID id) {
        return view.nodes().stream().filter(node -> node.id().equals(id)).findFirst().orElseThrow();
    }

    private static int doSauCua(TreeProjectionView view, UUID id) {
        return nodeCua(view, id).depth();
    }
}
