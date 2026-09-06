package vn.giapha.genealogy.infrastructure.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.giapha.genealogy.domain.GraphNodeRef;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.LcaResult;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.genealogy.domain.port.TreeGraphPort;

/**
 * Hiện thực {@link TreeGraphPort} bằng <b>Apache AGE</b> — Cypher chạy trong PostgreSQL, đúng
 * quyết định kiến trúc của BA v2 §12 (Neo4j đã bị loại, chỉ còn là phương án dự phòng).
 *
 * <h2>Chiều cạnh — chỗ TDD v1.0 viết sai và đã được vá ở v1.1</h2>
 * Cạnh là <b>cha → con</b>: {@code (p)-[:PARENT]->(c)}. Vì vậy mọi phép đi lên tổ tiên trong file
 * này dùng <b>mũi tên ngược</b> {@code <-[:PARENT*0..]-}. Bản mẫu chiều xuôi ở TDD v1.0 trả 0 dòng
 * với mọi cặp người mà không hề văng lỗi — nghĩa là engine danh xưng sẽ "không tìm thấy quan hệ"
 * một cách im lặng. Bản đúng là {@code V7__graph.sql} §7.5(c).
 *
 * <h2>Hai điều bắt buộc khi thêm truy vấn mới</h2>
 * <ol>
 *   <li><b>Tham số hoá</b> qua đối số thứ ba của {@code cypher()} ({@link AgtypeCodec#params}).
 *       Không bao giờ nối chuỗi người dùng vào Cypher.</li>
 *   <li><b>Độ sâu phải là hằng số đã kiểm chứng.</b> AGE không nhận tham số ở phần {@code *0..N}
 *       của đường đi biến-độ-dài, nên số này được nội suy vào chuỗi — và vì thế nó bắt buộc đi qua
 *       {@link #boundedDepth(int)} trước.</li>
 * </ol>
 */
@Component
public class AgeTreeGraphAdapter implements TreeGraphPort {

    private static final Logger log = LoggerFactory.getLogger(AgeTreeGraphAdapter.class);

    /** Trần độ sâu cho mọi phép duyệt — khớp {@code depth} tối đa 10 của contract. */
    public static final int MAX_DEPTH = 10;

    private final JdbcTemplate jdbc;
    private final String graphName;

    public AgeTreeGraphAdapter(JdbcTemplate jdbc, @Value("${giapha.graph.name:giapha_graph}") String graphName) {
        this.jdbc = jdbc;
        // Ten do thi di vao chuoi Cypher nen phai la dinh danh an toan, khong phai du lieu.
        if (!graphName.matches("^[a-z_][a-z0-9_]*$")) {
            throw new IllegalArgumentException("Ten do thi khong hop le: " + graphName);
        }
        this.graphName = graphName;
    }

    // -------------------------------------------------------------------------------------
    // Ghi
    // -------------------------------------------------------------------------------------

    @Override
    public void createPersonNode(UUID personId, String gender, Integer generation) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", personId.toString());
        params.put("gender", gender);
        params.put("generation", generation);
        execute("""
                CREATE (p:Person {id: $id, gender: $gender, generation: $generation, is_deleted: false})
                RETURN p.id
                """, params);
        log.debug("Da tao dinh Person {} trong do thi {}", personId, graphName);
    }

    @Override
    public void syncPersonNode(UUID personId, String gender, Integer generation, boolean deleted) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", personId.toString());
        params.put("gender", gender);
        params.put("generation", generation);
        params.put("deleted", deleted);
        execute("""
                MATCH (p:Person {id: $id})
                SET p.gender = $gender, p.generation = $generation, p.is_deleted = $deleted
                RETURN p.id
                """, params);
    }

    @Override
    public void linkParent(UUID parentId, UUID childId, RelType relType) {
        if (relType == null || !relType.isParentEdge()) {
            throw new IllegalArgumentException("linkParent chi nhan PARENT_BIO hoac PARENT_ADOPT");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("parentId", parentId.toString());
        params.put("childId", childId.toString());
        params.put("type", relType.edgeSubType());
        execute("""
                MATCH (p:Person {id: $parentId}), (c:Person {id: $childId})
                CREATE (p)-[r:PARENT {type: $type}]->(c)
                RETURN r
                """, params);
    }

    @Override
    public void linkSpouse(UUID fromPersonId, UUID toPersonId, Integer spouseOrder,
                           String validFrom, String validTo) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("fromId", fromPersonId.toString());
        params.put("toId", toPersonId.toString());
        params.put("spouseOrder", spouseOrder);
        params.put("validFrom", validFrom);
        params.put("validTo", validTo);
        execute("""
                MATCH (a:Person {id: $fromId}), (b:Person {id: $toId})
                CREATE (a)-[r:SPOUSE {spouse_order: $spouseOrder, valid_from: $validFrom, valid_to: $validTo}]->(b)
                RETURN r
                """, params);
    }

    @Override
    public void linkHeir(UUID fromPersonId, UUID toPersonId, HeirKind heirKind) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("fromId", fromPersonId.toString());
        params.put("toId", toPersonId.toString());
        params.put("heirType", heirKind == null ? null : heirKind.name());
        execute("""
                MATCH (a:Person {id: $fromId}), (b:Person {id: $toId})
                CREATE (a)-[r:HEIR {heir_type: $heirType}]->(b)
                RETURN r
                """, params);
    }

    @Override
    public void unlink(UUID fromPersonId, UUID toPersonId, RelType relType) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("fromId", fromPersonId.toString());
        params.put("toId", toPersonId.toString());
        String label = relType.edgeLabel();
        String filter = relType.isParentEdge() ? " WHERE r.type = '" + relType.edgeSubType() + "'" : "";
        // Nhan canh va type deu la hang so lay tu enum, khong phai du lieu nguoi dung.
        execute("MATCH (a:Person {id: $fromId})-[r:" + label + "]->(b:Person {id: $toId})"
                + filter + " DELETE r RETURN 1", params);
    }

    // -------------------------------------------------------------------------------------
    // Đọc
    // -------------------------------------------------------------------------------------

    /**
     * Tổ tiên, gần nhất trước. Độ sâu trả về là <b>số âm</b> để khớp quy ước
     * {@code TreeNode.depth} của contract ("âm là đời trên").
     */
    @Override
    public List<GraphNodeRef> ancestors(UUID personId, int maxDepth) {
        int depth = boundedDepth(maxDepth);
        if (depth == 0) {
            return List.of();
        }
        String cypher = """
                MATCH path = (x:Person {id: $id})<-[:PARENT*1..%d]-(anc:Person)
                WHERE anc.is_deleted = false
                RETURN anc.id AS id, length(path) AS dist
                """.formatted(depth);
        return queryNodes(cypher, Map.of("id", personId.toString()), true);
    }

    /** Con cháu tới {@code maxDepth} đời, <b>bao gồm</b> chính gốc ở độ sâu 0 (nhờ {@code *0..}). */
    @Override
    public List<GraphNodeRef> descendants(UUID personId, int maxDepth) {
        int depth = boundedDepth(maxDepth);
        String cypher = """
                MATCH path = (r:Person {id: $id})-[:PARENT*0..%d]->(d:Person)
                WHERE d.is_deleted = false
                RETURN d.id AS id, length(path) AS dist
                """.formatted(depth);
        return queryNodes(cypher, Map.of("id", personId.toString()), false);
    }

    /**
     * Tổ chung gần nhất — bản đúng ở {@code V7__graph.sql} §7.5(c).
     *
     * <p>{@code *0..} bao cả chính đỉnh xuất phát, nên trường hợp "A là tổ tiên trực hệ của B" cho
     * {@code distanceFrom = 0}. Người đã xoá mềm không được làm tổ chung ({@code WHERE
     * anc.is_deleted = false}) nhưng vẫn được đi xuyên qua, để cây không đứt ở giữa.</p>
     */
    @Override
    public LcaResult lca(UUID firstPersonId, UUID secondPersonId) {
        String cypher = """
                MATCH path1 = (a:Person {id: $id1})<-[:PARENT*0..]-(anc:Person),
                      path2 = (b:Person {id: $id2})<-[:PARENT*0..]-(anc)
                WHERE anc.is_deleted = false
                RETURN anc.id AS lca, length(path1) AS dist_a, length(path2) AS dist_b
                ORDER BY (length(path1) + length(path2)) ASC
                LIMIT 1
                """;
        Map<String, Object> params = Map.of(
                "id1", firstPersonId.toString(),
                "id2", secondPersonId.toString());
        String sql = "SELECT * FROM cypher('" + graphName + "', $$" + cypher
                + "$$, ?) AS (lca agtype, dist_a agtype, dist_b agtype)";
        List<LcaResult> rows = jdbc.query(sql,
                (rs, rowNum) -> new LcaResult(
                        AgtypeCodec.toUuid(rs.getString("lca")),
                        nullSafe(AgtypeCodec.toInt(rs.getString("dist_a"))),
                        nullSafe(AgtypeCodec.toInt(rs.getString("dist_b")))),
                AgtypeCodec.params(params));
        return rows.isEmpty() ? LcaResult.NONE : rows.get(0);
    }

    /**
     * Kiểm tra chu trình trước khi nối cha–con: nối {@code parent → child} là bất hợp lệ nếu
     * {@code child} vốn đã là tổ tiên của {@code parent} (contract: {@code RELATIONSHIP_CYCLE}).
     * Bao cả trường hợp trùng chính nó nhờ {@code *0..}.
     */
    @Override
    public boolean isAncestorOf(UUID candidateAncestorId, UUID personId) {
        String cypher = """
                MATCH (x:Person {id: $id})<-[:PARENT*0..]-(anc:Person {id: $ancId})
                RETURN anc.id AS id LIMIT 1
                """;
        Map<String, Object> params = Map.of(
                "id", personId.toString(),
                "ancId", candidateAncestorId.toString());
        String sql = "SELECT * FROM cypher('" + graphName + "', $$" + cypher
                + "$$, ?) AS (id agtype)";
        return !jdbc.queryForList(sql, String.class, AgtypeCodec.params(params)).isEmpty();
    }

    @Override
    public boolean nodeExists(UUID personId) {
        String sql = "SELECT * FROM cypher('" + graphName
                + "', $$ MATCH (p:Person {id: $id}) RETURN p.id $$, ?) AS (id agtype)";
        return !jdbc.queryForList(sql, String.class,
                AgtypeCodec.params(Map.of("id", personId.toString()))).isEmpty();
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ
    // -------------------------------------------------------------------------------------

    private List<GraphNodeRef> queryNodes(String cypher, Map<String, Object> params, boolean negateDepth) {
        String sql = "SELECT * FROM cypher('" + graphName + "', $$" + cypher
                + "$$, ?) AS (id agtype, dist agtype)";
        List<GraphNodeRef> refs = new ArrayList<>();
        jdbc.query(sql, rs -> {
            UUID id = AgtypeCodec.toUuid(rs.getString("id"));
            int dist = nullSafe(AgtypeCodec.toInt(rs.getString("dist")));
            refs.add(new GraphNodeRef(id, negateDepth ? -dist : dist));
        }, AgtypeCodec.params(params));
        return refs;
    }

    private void execute(String cypher, Map<String, Object> params) {
        String sql = "SELECT * FROM cypher('" + graphName + "', $$" + cypher
                + "$$, ?) AS (v agtype)";
        jdbc.query(sql, rs -> {
            // Chi de rut can ket qua: AGE bat buoc phai co menh de RETURN va phai doc het cursor.
        }, AgtypeCodec.params(params));
    }

    /**
     * Chốt chặn cho con số duy nhất được phép nội suy vào chuỗi Cypher.
     *
     * <p>AGE không nhận tham số ở phần {@code *0..N} của đường đi biến-độ-dài. Ép về khoảng
     * {@code [0, MAX_DEPTH]} tại đây nghĩa là dù tầng trên có quên kiểm tra thì cũng không có
     * đường nào để một chuỗi lạ chui vào câu truy vấn.</p>
     */
    private static int boundedDepth(int requested) {
        if (requested < 0) {
            return 0;
        }
        return Math.min(requested, MAX_DEPTH);
    }

    private static int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }
}
