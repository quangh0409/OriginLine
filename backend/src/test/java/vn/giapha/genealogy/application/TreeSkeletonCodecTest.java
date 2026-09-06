package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.view.TreeDirection;
import vn.giapha.genealogy.domain.GraphNodeRef;

/**
 * <b>W7 — khung xương phả đồ</b>: thứ duy nhất được phép cache.
 *
 * <p>Khung xương chỉ gồm id đỉnh và độ sâu, không một mẩu hồ sơ nào. Đó là lý do nó dùng chung
 * được cho mọi vai: hồ sơ trả về khác nhau theo người gọi (phân tầng riêng tư), nên cache thứ đã
 * lọc thì sớm muộn cũng có người nhận bản cache của một vai khác — một lỗi rò rỉ dữ liệu không để
 * lại dấu vết nào trong log.
 *
 * <p>Mã hoá là văn bản thuần để cache có thể là bất cứ thứ gì lưu được chuỗi. Giải mã hỏng thì trả
 * {@code null}: gọi lại phép duyệt còn hơn dựng một cây sai từ dữ liệu không đọc nổi.
 */
class TreeSkeletonCodecTest {

    private final UUID goc = UUID.randomUUID();
    private final UUID conA = UUID.randomUUID();
    private final UUID chauB = UUID.randomUUID();

    private TreeSkeleton khungXuong() {
        return new TreeSkeleton(goc, 3, TreeDirection.DESCENDANTS,
                List.of(new GraphNodeRef(goc, 0), new GraphNodeRef(conA, 1),
                        new GraphNodeRef(chauB, 2)),
                true, List.of(chauB));
    }

    @Test
    @DisplayName("Mã hoá rồi giải mã cho lại đúng khung xương ban đầu")
    void maHoaRoiGiaiMaChoLaiDungKhungXuong() {
        TreeSkeleton sau = TreeSkeleton.decode(khungXuong().encode());

        assertThat(sau).isNotNull();
        assertThat(sau.rootId()).isEqualTo(goc);
        assertThat(sau.depth()).isEqualTo(3);
        assertThat(sau.direction()).isEqualTo(TreeDirection.DESCENDANTS);
        assertThat(sau.truncated()).isTrue();
        assertThat(sau.truncatedNodeIds()).containsExactly(chauB);
        assertThat(sau.nodes()).containsExactly(new GraphNodeRef(goc, 0),
                new GraphNodeRef(conA, 1), new GraphNodeRef(chauB, 2));
    }

    @Test
    @DisplayName("Độ sâu ÂM (đời trên) được giữ nguyên dấu qua vòng mã hoá")
    void doSauAmDuocGiuNguyenDau() {
        TreeSkeleton truoc = new TreeSkeleton(goc, 2, TreeDirection.ANCESTORS,
                List.of(new GraphNodeRef(goc, 0), new GraphNodeRef(conA, -1),
                        new GraphNodeRef(chauB, -2)),
                false, List.of());

        TreeSkeleton sau = TreeSkeleton.decode(truoc.encode());

        assertThat(sau.nodes()).extracting(GraphNodeRef::depth)
                .as("dau am di thang ra TreeNode.depth cua contract; mat dau la lat nguoc ca cay")
                .containsExactly(0, -1, -2);
    }

    @Test
    @DisplayName("Khung xương rỗng vẫn mã hoá và giải mã được")
    void khungXuongRongVanMaHoaDuoc() {
        TreeSkeleton truoc = new TreeSkeleton(goc, 1, TreeDirection.BOTH, List.of(), false, List.of());

        TreeSkeleton sau = TreeSkeleton.decode(truoc.encode());

        assertThat(sau).isNotNull();
        assertThat(sau.nodes()).isEmpty();
        assertThat(sau.truncatedNodeIds()).isEmpty();
        assertThat(sau.truncated()).isFalse();
    }

    @Test
    @DisplayName("Chuỗi hỏng, rỗng hoặc null cho ra null — thà duyệt lại còn hơn dựng cây sai")
    void chuoiHongChoRaNull() {
        assertThat(TreeSkeleton.decode(null)).isNull();
        assertThat(TreeSkeleton.decode("")).isNull();
        assertThat(TreeSkeleton.decode("   ")).isNull();
        assertThat(TreeSkeleton.decode("rác không đọc nổi")).isNull();
        assertThat(TreeSkeleton.decode("v1|khong-phai-uuid|3|DESCENDANTS|0|")).isNull();
    }

    @Test
    @DisplayName("Bản cache của phiên bản định dạng khác bị bỏ qua")
    void banCachePhienBanKhacBiBoQua() {
        String cuaPhienBanKhac = khungXuong().encode().replaceFirst("^v1", "v9");

        assertThat(TreeSkeleton.decode(cuaPhienBanKhac))
                .as("doi dinh dang ma khong doi phien ban thi cache cu se dung nen cay sai")
                .isNull();
    }

    @Test
    @DisplayName("Khung xương KHÔNG chứa bất kỳ dữ liệu cá nhân nào — chỉ id và độ sâu")
    void khungXuongKhongChuaDuLieuCaNhan() {
        String encoded = khungXuong().encode();

        assertThat(TreeSkeleton.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("rootId", "depth", "direction", "nodes", "truncated",
                        "truncatedNodeIds");
        assertThat(encoded).doesNotContain("name").doesNotContain("gender").doesNotContain("birth");
    }

    @Test
    @DisplayName("nodeIds trả đúng danh sách id theo thứ tự duyệt")
    void nodeIdsTraDungThuTuDuyet() {
        assertThat(khungXuong().nodeIds()).containsExactly(goc, conA, chauB);
    }

    @Test
    @DisplayName("Danh sách node và node bị cắt là bản sao bất biến")
    void danhSachLaBanSaoBatBien() {
        List<GraphNodeRef> nguon = new java.util.ArrayList<>(List.of(new GraphNodeRef(goc, 0)));
        TreeSkeleton skeleton = new TreeSkeleton(goc, 1, TreeDirection.DESCENDANTS, nguon,
                false, null);

        nguon.add(new GraphNodeRef(conA, 1));

        assertThat(skeleton.nodes()).hasSize(1);
        assertThat(skeleton.truncatedNodeIds()).isEmpty();
    }
}
