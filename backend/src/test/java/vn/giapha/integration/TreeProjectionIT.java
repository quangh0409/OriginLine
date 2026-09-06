package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.giapha.genealogy.application.LinkRelationshipService;
import vn.giapha.genealogy.application.TreeProjectionService;
import vn.giapha.genealogy.application.command.LinkRelationshipCommand;
import vn.giapha.genealogy.application.command.TreeQuery;
import vn.giapha.genealogy.application.view.TreeDirection;
import vn.giapha.genealogy.application.view.TreeNodeView;
import vn.giapha.genealogy.application.view.TreeProjectionView;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.genealogy.domain.port.TreeCachePort;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.Gender;

/**
 * Projection phả đồ (W7) trên Apache AGE thật + Redis thật.
 *
 * <h2>Ba nhịp phải giữ đúng ranh giới</h2>
 * <ol>
 *   <li>duyệt đồ thị ra <b>khung xương</b> (id + độ sâu) — phần được cache;</li>
 *   <li>nạp hồ sơ theo lô;</li>
 *   <li>lọc phân tầng riêng tư — luôn chạy <b>tươi</b>.</li>
 * </ol>
 *
 * <p>Ca test giá trị nhất ở đây là {@link #cacheDungChungNhungKhongLamRoRiNguoiSong()}: khung
 * xương được chia sẻ giữa mọi vai, nên nếu nhịp 3 vô tình bị gộp vào nhịp 1 thì một lượt duyệt của
 * Quản trị sẽ làm rò dữ liệu người còn sống sang phiên của Khách — và không để lại dấu vết nào
 * trong log.</p>
 */
@DisplayName("Projection phả đồ + cache Redis")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class TreeProjectionIT extends AbstractIntegrationTest {

    @Autowired
    private TreeProjectionService treeProjection;

    @Autowired
    private LinkRelationshipService linkRelationship;

    @Autowired
    private TreeCachePort treeCache;

    @Autowired
    private StringRedisTemplate redis;

    private UUID chiGiap;
    private UUID cuTo;
    private UUID baCuTo;
    private UUID conTruong;
    private UUID conThu;
    private UUID chauDich;
    private UUID chauUt;
    /** Đã gieo nhưng CHƯA nối vào cây — dùng cho ca kiểm tra dọn cache sau mutation. */
    private UUID chatChuaNoi;

    @BeforeEach
    void setUpClan() {
        treeCache.evictAll();
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        authenticateAs("sub-admin", "ADMIN");

        cuTo = seed(PersonFixtures.deceased("Nguyễn Phúc Thuỷ Tổ", 1950)
                .birthYear(1880).branch(chiGiap).generation(1));
        baCuTo = seed(PersonFixtures.deceased("Trần Thị Tổ Mẫu", 1955)
                .gender(Gender.FEMALE).birthYear(1885).branch(chiGiap).generation(1));
        conTruong = seed(PersonFixtures.deceased("Nguyễn Văn Trưởng", 1990)
                .birthYear(1910).branch(chiGiap).generation(2));
        conThu = seed(PersonFixtures.living("Nguyễn Văn Thứ")
                .birthYear(1950).branch(chiGiap).generation(2));
        chauDich = seed(PersonFixtures.living("Nguyễn Văn Đích")
                .birthYear(1975).branch(chiGiap).generation(3));
        chauUt = seed(PersonFixtures.living("Nguyễn Văn Út")
                .birthYear(1978).branch(chiGiap).generation(3));
        chatChuaNoi = seed(PersonFixtures.living("Nguyễn Văn Chắt")
                .birthYear(2000).branch(chiGiap).generation(4));

        linkRelationship.link(new LinkRelationshipCommand(cuTo, baCuTo, RelType.SPOUSE, null, 1,
                null, null, null));
        link(cuTo, conTruong);
        link(cuTo, conThu);
        link(conTruong, chauDich);
        link(conTruong, chauUt);
        // Lệnh nối cuối cùng đã dọn cache; các ca test bên dưới bắt đầu từ cache lạnh.
    }

    private void link(UUID cha, UUID con) {
        linkRelationship.link(new LinkRelationshipCommand(cha, con, RelType.PARENT_BIO, null, null,
                null, null, null));
    }

    private TreeQuery descendantsOf(UUID root) {
        return new TreeQuery(root, 3, TreeDirection.DESCENDANTS, true, false, 500);
    }

    private static Set<UUID> idsOf(TreeProjectionView view) {
        return view.nodes().stream().map(TreeNodeView::id).collect(Collectors.toSet());
    }

    private static TreeNodeView nodeOf(TreeProjectionView view, UUID id) {
        return view.nodes().stream().filter(node -> node.id().equals(id)).findFirst().orElse(null);
    }

    // =====================================================================================
    // Nội dung projection
    // =====================================================================================

    @Test
    @DisplayName("lượt duyệt đầu tiên dựng đủ node, cạnh và độ sâu — vợ/chồng đứng cùng đời")
    void duyetLanDau_traDuNodeCanhVaDoSau() {
        authenticateAs("sub-admin", "ADMIN");

        TreeProjectionView view = treeProjection.project(descendantsOf(cuTo));

        assertThat(idsOf(view)).containsExactlyInAnyOrder(cuTo, baCuTo, conTruong, conThu,
                chauDich, chauUt);
        assertThat(idsOf(view)).as("người chưa nối vào cây không xuất hiện trên phả đồ")
                .doesNotContain(chatChuaNoi);
        assertThat(view.meta().fromCache()).as("lượt đầu không thể đến từ cache").isFalse();

        assertThat(nodeOf(view, cuTo).depth()).isZero();
        assertThat(nodeOf(view, conTruong).depth()).isEqualTo(1);
        assertThat(nodeOf(view, chauDich).depth()).isEqualTo(2);
        assertThat(nodeOf(view, baCuTo).depth())
                .as("vợ/chồng nhận cùng độ sâu với người phối ngẫu vì đứng cùng một đời")
                .isZero();

        assertThat(nodeOf(view, chauDich).parentIds()).containsExactly(conTruong);
        assertThat(nodeOf(view, cuTo).spouseIds()).containsExactly(baCuTo);

        assertThat(view.edges()).hasSize(5);
        assertThat(view.edges().stream().filter(edge -> edge.relType() == RelType.SPOUSE).count())
                .isEqualTo(1);
        assertThat(view.meta().nodeCount()).isEqualTo(6);
        assertThat(view.meta().truncated()).isFalse();
    }

    @Test
    @DisplayName("chiều tổ tiên trả độ sâu ÂM — quy ước 'âm là đời trên' của contract")
    void chieuToTien_doSauLaSoAm() {
        authenticateAs("sub-admin", "ADMIN");

        TreeProjectionView view = treeProjection.project(
                new TreeQuery(chauDich, 3, TreeDirection.ANCESTORS, false, false, 500));

        assertThat(nodeOf(view, chauDich).depth()).isZero();
        assertThat(nodeOf(view, conTruong).depth()).isEqualTo(-1);
        assertThat(nodeOf(view, cuTo).depth()).isEqualTo(-2);
    }

    // =====================================================================================
    // Cache
    // =====================================================================================

    @Test
    @DisplayName("lượt thứ hai lấy khung xương từ Redis và cho kết quả giống hệt")
    void duyetLanHai_layTuCache_ketQuaGiongHet() {
        authenticateAs("sub-admin", "ADMIN");
        TreeProjectionView lanDau = treeProjection.project(descendantsOf(cuTo));

        TreeProjectionView lanHai = treeProjection.project(descendantsOf(cuTo));

        assertThat(lanDau.meta().fromCache()).isFalse();
        assertThat(lanHai.meta().fromCache()).as("phải là cache HIT").isTrue();
        assertThat(idsOf(lanHai)).isEqualTo(idsOf(lanDau));
        assertThat(lanHai.edges()).hasSameSizeAs(lanDau.edges());

        Set<String> keys = redis.keys("giapha:tree:*");
        assertThat(keys).as("khung xương phải nằm dưới tiền tố giapha:tree:").isNotEmpty();
        assertThat(keys).anyMatch(key -> key.contains(cuTo.toString()));
        // Khoá cache cố ý KHÔNG chứa danh tính người gọi: khung xương giống nhau với mọi vai.
        assertThat(keys).noneMatch(key -> key.contains("sub-admin"));
    }

    @Test
    @DisplayName("khung xương cache dùng chung cho mọi vai nhưng KHÔNG làm rò rỉ người còn sống")
    void cacheDungChungNhungKhongLamRoRiNguoiSong() {
        authenticateAs("sub-admin", "ADMIN");
        TreeProjectionView cuaQuanTri = treeProjection.project(descendantsOf(cuTo));
        assertThat(idsOf(cuaQuanTri)).contains(conThu, chauDich, chauUt);

        authenticateAsGuest();
        TreeProjectionView cuaKhach = treeProjection.project(descendantsOf(cuTo));

        assertThat(cuaKhach.meta().fromCache())
                .as("khung xương vẫn được tái dùng - phần đắt là phép duyệt Cypher").isTrue();
        assertThat(idsOf(cuaKhach))
                .as("Khách chỉ được thấy người đã khuất")
                .containsExactlyInAnyOrder(cuTo, baCuTo, conTruong);
        assertThat(idsOf(cuaKhach)).doesNotContain(conThu, chauDich, chauUt);
    }

    @Test
    @DisplayName("số con hiển thị cho Khách chỉ đếm người đang hiện diện, không để lộ số con còn sống")
    void khach_soConChiDemNguoiHienDien() {
        authenticateAs("sub-admin", "ADMIN");
        TreeProjectionView cuaQuanTri = treeProjection.project(descendantsOf(cuTo));
        assertThat(nodeOf(cuaQuanTri, conTruong).childCount())
                .as("Quản trị thấy con số thật").isEqualTo(2);

        authenticateAsGuest();
        TreeProjectionView cuaKhach = treeProjection.project(descendantsOf(cuTo));

        TreeNodeView conTruongCuaKhach = nodeOf(cuaKhach, conTruong);
        assertThat(conTruongCuaKhach.childCount())
                .as("để lộ 'còn 2 người con bạn không được thấy' cũng là để lộ dữ liệu")
                .isZero();
        assertThat(conTruongCuaKhach.hasMoreDescendants()).isFalse();
    }

    @Test
    @DisplayName("nối thêm một cạnh thì cache bị dọn và lượt duyệt kế tiếp thấy người mới")
    void noiThemCanh_donCache_luotSauThayNguoiMoi() {
        authenticateAs("sub-admin", "ADMIN");
        treeProjection.project(descendantsOf(cuTo));
        assertThat(treeProjection.project(descendantsOf(cuTo)).meta().fromCache()).isTrue();

        link(chauDich, chatChuaNoi);

        TreeProjectionView sauKhiNoi = treeProjection.project(descendantsOf(cuTo));
        assertThat(sauKhiNoi.meta().fromCache())
                .as("cache phải bị vô hiệu hoá sau mutation, nếu không cây sẽ hiển thị dữ liệu cũ")
                .isFalse();
        assertThat(idsOf(sauKhiNoi)).contains(chatChuaNoi);
        assertThat(nodeOf(sauKhiNoi, chatChuaNoi).depth()).isEqualTo(3);
    }

    // =====================================================================================
    // Cửa chặn
    // =====================================================================================

    @Test
    @DisplayName("gốc không hiển thị được thì trả 404 chứ không tiết lộ rằng nó tồn tại")
    void gocKhongHienThiDuoc_tra404() {
        authenticateAsGuest();

        assertThatThrownBy(() -> treeProjection.project(descendantsOf(chauDich)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("chỉ vai toàn dòng họ được xem bản ghi đã xoá mềm trên phả đồ")
    void includeDeleted_chiChoVaiToanDongHo() {
        authenticateAs("sub-thanhvien", "MEMBER");

        assertThatThrownBy(() -> treeProjection.project(
                new TreeQuery(cuTo, 3, TreeDirection.DESCENDANTS, true, true, 500)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("chạm trần maxNodes thì cắt cây và đánh dấu truncated, không ném lỗi")
    void chamTranMaxNodes_catCayVaDanhDau() {
        authenticateAs("sub-admin", "ADMIN");

        TreeProjectionView view = treeProjection.project(
                new TreeQuery(cuTo, 3, TreeDirection.DESCENDANTS, false, false, 2));

        assertThat(view.meta().truncated()).isTrue();
        assertThat(view.nodes()).hasSizeLessThanOrEqualTo(2);
        List<UUID> ids = view.nodes().stream().map(TreeNodeView::id).toList();
        assertThat(ids).contains(cuTo);
    }
}
