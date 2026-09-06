package vn.giapha.genealogy.application.view;

import java.util.List;
import java.util.UUID;

/**
 * Đồ thị <b>phẳng</b> (node + cạnh) của một nhánh cây — nạp thẳng vào React Flow.
 *
 * <p>Cố ý không lồng nhau: cây lồng theo hình dạng client tự chọn là việc của GraphQL. Phẳng thì
 * ghép được nhiều lần gọi vào cùng một canvas mà không phải hợp nhất cấu trúc đệ quy.</p>
 *
 * <p>{@code edges} chỉ chứa cạnh mà <b>cả hai đầu</b> đều nằm trong {@code nodes}. Với Khách, mọi
 * người còn sống biến mất nên cây có thể <b>đứt đoạn hợp lệ</b> — đó là kết quả đúng, không phải
 * lỗi dữ liệu.</p>
 */
public record TreeProjectionView(UUID rootId, List<TreeNodeView> nodes, List<TreeEdgeView> edges,
                                 TreeMetaView meta) {
}
