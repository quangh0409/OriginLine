package vn.giapha.genealogy.domain;

import java.util.UUID;
import vn.giapha.shared.domain.ValueObject;

/**
 * Một đỉnh trả về từ phép duyệt đồ thị, kèm khoảng cách đời so với gốc.
 *
 * <p>{@code depth} <b>âm là đời trên</b> (khi duyệt về phía tổ tiên), 0 là chính gốc, dương là
 * đời dưới. Quy ước này đi thẳng ra {@code TreeNode.depth} của contract nên đừng đổi dấu ở tầng
 * trên.</p>
 *
 * <p>Đỉnh chỉ mang id và độ sâu — hồ sơ đầy đủ nằm ở bảng {@code person}. Cố ý không kéo dữ liệu
 * cá nhân ra khỏi đồ thị: node AGE chỉ giữ 4 thuộc tính tra cứu, và mọi trường hồ sơ đều phải đi
 * qua bộ lọc phân tầng riêng tư ở tầng application.</p>
 */
public record GraphNodeRef(UUID personId, int depth) implements ValueObject {
}
