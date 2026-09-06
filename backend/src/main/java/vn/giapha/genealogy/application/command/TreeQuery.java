package vn.giapha.genealogy.application.command;

import java.util.UUID;
import vn.giapha.genealogy.application.view.TreeDirection;

/**
 * Tham số dựng một nhánh phả đồ.
 *
 * <p>Hai trần tồn tại vì hai lý do khác nhau: {@code depth} chặn phép duyệt đồ thị nổ theo cấp số
 * nhân, còn {@code maxNodes} chặn kích thước phản hồi. Chạm trần nào cũng chỉ dẫn tới
 * {@code meta.truncated = true} chứ không phải lỗi - cây lớn là chuyện bình thường của một dòng
 * họ, giao diện nạp tiếp bằng nút mở rộng.</p>
 *
 * @param includeDeleted kèm bản ghi đã xoá mềm; chỉ {@code ADMIN} và {@code COUNCIL}
 */
public record TreeQuery(UUID rootId, int depth, TreeDirection direction, boolean includeSpouses,
                        boolean includeDeleted, int maxNodes) {

    /** Trần độ sâu, khớp {@code maximum: 10} của contract. */
    public static final int MAX_DEPTH = 10;

    /** Trần số node tuyệt đối, khớp {@code maximum: 2000} của contract. */
    public static final int MAX_NODES = 2000;

    public TreeQuery {
        direction = direction == null ? TreeDirection.DESCENDANTS : direction;
        depth = Math.max(0, Math.min(depth, MAX_DEPTH));
        maxNodes = Math.max(1, Math.min(maxNodes, MAX_NODES));
    }
}
