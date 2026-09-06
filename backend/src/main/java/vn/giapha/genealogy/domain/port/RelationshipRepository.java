package vn.giapha.genealogy.domain.port;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import vn.giapha.genealogy.domain.Relationship;

/**
 * Kho <b>bản chiếu</b> quan hệ.
 *
 * <p>Nhắc lại cho người sửa code sau: nguồn chân lý là cạnh trong đồ thị AGE. Mọi lệnh ghi ở đây
 * phải đi kèm một lệnh tương ứng trên {@link TreeGraphPort} <b>trong cùng một transaction</b>.
 * Gọi một mình repository này là tạo ra dữ liệu lệch mà không có gì báo lỗi.</p>
 */
public interface RelationshipRepository {

    Relationship save(Relationship relationship);

    /** Mọi cạnh còn hiệu lực có một đầu là {@code personId}. */
    List<Relationship> byPerson(UUID personId);

    /**
     * Mọi cạnh còn hiệu lực mà <b>cả hai đầu</b> đều nằm trong {@code personIds}.
     * Đây chính là điều kiện contract đặt ra cho {@code TreeProjection.edges}.
     */
    List<Relationship> betweenAll(Collection<UUID> personIds);

    /** Mọi cạnh còn hiệu lực có ít nhất một đầu nằm trong tập — dùng để tìm vợ/chồng cần nạp thêm. */
    List<Relationship> touchingAny(Collection<UUID> personIds);

    boolean existsParentEdge(UUID parentId, UUID childId);
}
