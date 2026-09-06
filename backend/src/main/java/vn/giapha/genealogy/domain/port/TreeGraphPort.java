package vn.giapha.genealogy.domain.port;

import java.util.List;
import java.util.UUID;
import vn.giapha.genealogy.domain.GraphNodeRef;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.LcaResult;
import vn.giapha.genealogy.domain.RelType;

/**
 * Cổng ra <b>đồ thị phả hệ</b> — nơi duy nhất trong hệ thống được phép duyệt cây.
 *
 * <h2>Chiều cạnh: đọc kỹ trước khi viết truy vấn mới</h2>
 * Cạnh {@code PARENT} là <b>cha → con</b>: {@code (p)-[:PARENT]->(c)} đọc là "p là cha/mẹ của c".
 * Vì vậy:
 * <ul>
 *   <li>đi <b>xuống</b> con cháu: {@code (root)-[:PARENT*0..N]->(d)};</li>
 *   <li>đi <b>lên</b> tổ tiên: {@code (ego)<-[:PARENT*0..N]-(anc)} — <b>mũi tên ngược</b>.</li>
 * </ul>
 * TDD v1.0 §6 viết truy vấn LCA theo chiều xuôi và mâu thuẫn với chính câu {@code CREATE} ngay
 * phía trên nó; chạy thật trên Apache AGE thì nó trả <b>0 dòng với mọi cặp người</b> — engine danh
 * xưng sẽ im lặng không tìm ra quan hệ nào mà không hề văng lỗi. Bản đúng nằm ở
 * {@code V7__graph.sql} §7.5(c) và TDD v1.1; đừng chép từ chỗ khác.
 *
 * <h2>Duyệt cây không dùng recursive CTE</h2>
 * Mọi phép duyệt ở đây chạy bằng Cypher <b>bên trong Postgres</b> (Apache AGE), không phải
 * {@code WITH RECURSIVE}. Đây là ràng buộc kiến trúc của BA v2 §12, không phải sở thích.
 */
public interface TreeGraphPort {

    /**
     * Tạo đỉnh cho một nhân khẩu mới. Đỉnh chỉ giữ 4 thuộc tính tra cứu
     * ({@code id}, {@code gender}, {@code generation}, {@code is_deleted}); hồ sơ đầy đủ nằm ở
     * bảng {@code person}.
     */
    void createPersonNode(UUID personId, String gender, Integer generation);

    /** Đồng bộ lại thuộc tính tra cứu trên đỉnh sau khi hồ sơ đổi. */
    void syncPersonNode(UUID personId, String gender, Integer generation, boolean deleted);

    /**
     * Nối <b>cha/mẹ → con</b>. {@code type} là {@code BIO} hoặc {@code ADOPT} — hai loại cạnh
     * khác nhau chứ không phải một cờ, vì suy luận danh xưng đối xử khác nhau với con nuôi.
     */
    void linkParent(UUID parentId, UUID childId, RelType relType);

    /** Nối vợ/chồng. Cạnh vô hướng về ngữ nghĩa nhưng lưu một chiều; adapter đọc cả hai hướng. */
    void linkSpouse(UUID fromPersonId, UUID toPersonId, Integer spouseOrder,
                    String validFrom, String validTo);

    /** Nối quan hệ thừa kế hương hoả: {@code from} để lại, {@code to} nối dõi. */
    void linkHeir(UUID fromPersonId, UUID toPersonId, HeirKind heirKind);

    /** Gỡ một cạnh khỏi đồ thị. Chỉ dùng khi bản chiếu tương ứng cũng bị xoá mềm cùng transaction. */
    void unlink(UUID fromPersonId, UUID toPersonId, RelType relType);

    /**
     * Tổ tiên của một người, gần nhất trước, tối đa {@code maxDepth} đời.
     * Đi ngược cạnh {@code PARENT}; không bao gồm chính người đó.
     */
    List<GraphNodeRef> ancestors(UUID personId, int maxDepth);

    /** Con cháu tới {@code maxDepth} đời, kèm độ sâu; <b>bao gồm</b> chính gốc ở độ sâu 0. */
    List<GraphNodeRef> descendants(UUID personId, int maxDepth);

    /**
     * Tổ chung gần nhất của hai người.
     *
     * <p>Người đã xoá mềm <b>không được</b> làm tổ chung, nhưng vẫn được đi <b>xuyên qua</b> để
     * không làm đứt đường nối giữa các đời.</p>
     */
    LcaResult lca(UUID firstPersonId, UUID secondPersonId);

    /** {@code true} nếu {@code candidateAncestorId} là tổ tiên của (hoặc chính là) {@code personId}. */
    boolean isAncestorOf(UUID candidateAncestorId, UUID personId);

    boolean nodeExists(UUID personId);
}
