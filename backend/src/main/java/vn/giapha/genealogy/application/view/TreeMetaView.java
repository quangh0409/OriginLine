package vn.giapha.genealogy.application.view;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Siêu dữ liệu của một lượt dựng phả đồ.
 *
 * @param truncated        {@code true} ⇒ projection <b>đã bị cắt</b> vì chạm {@code maxNodes};
 *                         giao diện phải xử lý, đừng coi là đã nhận đủ cây
 * @param truncatedNodeIds các node bị cắt nhánh — chính là nơi cần nút mở rộng
 * @param fromCache        khung xương lấy từ cache hay vừa duyệt lại đồ thị; <b>không</b> nói gì
 *                         về hồ sơ, vì hồ sơ luôn được nạp và lọc tươi
 */
public record TreeMetaView(int depth, TreeDirection direction, int nodeCount, int edgeCount,
                           boolean truncated, List<UUID> truncatedNodeIds, Instant generatedAt,
                           boolean fromCache) {
}
