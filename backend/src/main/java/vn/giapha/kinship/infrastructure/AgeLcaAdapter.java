package vn.giapha.kinship.infrastructure;

import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;
import vn.giapha.kinship.domain.DirectLink;
import vn.giapha.kinship.domain.DirectLinkType;
import vn.giapha.kinship.domain.LcaCalculator;
import vn.giapha.kinship.domain.LcaPort;
import vn.giapha.kinship.domain.LcaResult;
import vn.giapha.kinship.domain.ParentEdge;
import vn.giapha.kinship.domain.PersonLookupPort;
import vn.giapha.kinship.domain.PersonView;
import vn.giapha.kinship.domain.SpouseLink;
import vn.giapha.shared.vo.PersonId;

/**
 * Hiện thực {@link LcaPort} bằng Cypher chạy trong PostgreSQL qua Apache AGE (đồ thị
 * {@code giapha_graph}).
 *
 * <p><b>Chiều cạnh — đã có tiền lệ sai, đừng lặp lại.</b> Cạnh {@code PARENT} đi cha → con:
 * {@code (p)-[:PARENT]->(c)}. Muốn tìm tổ tiên phải dùng <b>mũi tên ngược</b>
 * {@code <-[:PARENT*0..]-}. Bản viết xuôi chiều của TDD v1.0 trả về 0 dòng với mọi cặp người mà
 * không hề văng lỗi — tức là engine danh xưng sẽ im lặng nói "không có quan hệ". Bản dùng ở đây là
 * bản đã vá ở {@code V7__graph.sql} §7.5(c).</p>
 *
 * <p><b>Phân công:</b> adapter chỉ kéo <b>tập cạnh</b> trong bao đóng tổ tiên rồi giao cho
 * {@link LcaCalculator} ở domain. Chọn LCA khi hoà, dựng lại đường đi, cờ con nuôi, loại người đã
 * xoá mềm — tất cả nằm ở domain nên test được không cần CSDL, và fixture test dùng lại đúng lớp đó
 * nên hành vi không thể lệch với production.</p>
 *
 * <p>Context {@code genealogy} có {@code AgeTreeGraphAdapter} riêng. Hai adapter cố ý tách nhau
 * (TDD §3); không dùng chung file, không import chéo.</p>
 */
@Repository
public class AgeLcaAdapter implements LcaPort {

    /**
     * Chặn trên độ sâu duyệt tổ tiên. Gia phả Việt Nam hiếm khi chép quá 20 đời; đặt trần để một
     * chu trình do nhập liệu sai không kéo sập truy vấn.
     */
    private static final int MAX_ANCESTOR_DEPTH = 25;

    private static final String ANCESTOR_EDGES_CYPHER = """
            MATCH (a:Person {id: $id})<-[:PARENT*0..%d]-(c:Person)<-[r:PARENT]-(p:Person)
            RETURN c.id AS child_id, p.id AS parent_id, r.type AS rel_type
            """.formatted(MAX_ANCESTOR_DEPTH);

    private static final String SPOUSES_CYPHER = """
            MATCH (a:Person {id: $id})-[r:SPOUSE]-(s:Person)
            RETURN s.id AS spouse_id, r.spouse_order AS spouse_order,
                   r.valid_from AS valid_from, r.valid_to AS valid_to
            """;

    private static final String PARENT_EDGE_FORWARD_CYPHER = """
            MATCH (a:Person {id: $from})-[r:PARENT]->(b:Person {id: $to})
            RETURN r.type AS rel_type
            """;

    private static final String PARENT_EDGE_BACKWARD_CYPHER = """
            MATCH (a:Person {id: $from})<-[r:PARENT]-(b:Person {id: $to})
            RETURN r.type AS rel_type
            """;

    private static final String HEIR_EDGE_FORWARD_CYPHER = """
            MATCH (a:Person {id: $from})-[r:HEIR]->(b:Person {id: $to})
            RETURN r.heir_type AS heir_type
            """;

    private static final String HEIR_EDGE_BACKWARD_CYPHER = """
            MATCH (a:Person {id: $from})<-[r:HEIR]-(b:Person {id: $to})
            RETURN r.heir_type AS heir_type
            """;

    private static final String SPOUSE_EDGE_CYPHER = """
            MATCH (a:Person {id: $from})-[r:SPOUSE]-(b:Person {id: $to})
            RETURN r.spouse_order AS spouse_order, r.valid_to AS valid_to
            """;

    private final JdbcTemplate jdbc;
    private final PersonLookupPort personLookup;

    public AgeLcaAdapter(JdbcTemplate jdbc, PersonLookupPort personLookup) {
        this.jdbc = jdbc;
        this.personLookup = personLookup;
    }

    @Override
    public Optional<LcaResult> findLca(PersonId ego, PersonId alter) {
        List<ParentEdge> egoEdges = ancestorEdges(ego);
        List<ParentEdge> alterEdges = ancestorEdges(alter);

        Set<PersonId> ids = new LinkedHashSet<>();
        ids.add(ego);
        ids.add(alter);
        for (ParentEdge edge : egoEdges) {
            ids.add(edge.parent());
            ids.add(edge.child());
        }
        for (ParentEdge edge : alterEdges) {
            ids.add(edge.parent());
            ids.add(edge.child());
        }
        Map<PersonId, PersonView> people = personLookup.byIds(ids);

        return LcaCalculator.compute(ego, alter, egoEdges, alterEdges, people::get);
    }

    /** Toàn bộ cạnh cha–con nằm trong bao đóng tổ tiên của một người (gồm cả các đời trung gian). */
    private List<ParentEdge> ancestorEdges(PersonId person) {
        String sql = AgeCypher.sql(ANCESTOR_EDGES_CYPHER,
                "child_id ag_catalog.agtype, parent_id ag_catalog.agtype, rel_type ag_catalog.agtype");
        List<ParentEdge> edges = jdbc.query(sql, rs -> {
            List<ParentEdge> rows = new ArrayList<>();
            while (rs.next()) {
                String child = AgeCypher.text(rs.getObject("child_id"));
                String parent = AgeCypher.text(rs.getObject("parent_id"));
                String type = AgeCypher.text(rs.getObject("rel_type"));
                if (child == null || parent == null) {
                    continue;
                }
                rows.add(new ParentEdge(PersonId.of(parent), PersonId.of(child),
                        "ADOPT".equalsIgnoreCase(type)));
            }
            return rows;
        }, agtypeParam(Map.of("id", person.value().toString())));
        return edges == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(edges));
    }

    @Override
    public List<DirectLink> directLinks(PersonId ego, PersonId alter) {
        List<DirectLink> links = new ArrayList<>();
        Object param = agtypeParam(Map.of(
                "from", ego.value().toString(),
                "to", alter.value().toString()));

        // Cha/mẹ ruột hoặc nuôi: ego là cha/mẹ của alter (cạnh thuận)...
        for (String type : queryColumn(PARENT_EDGE_FORWARD_CYPHER, "rel_type", param)) {
            links.add(new DirectLink(parentType(type), normalizeParentSubtype(type), false, true, ego, alter));
        }
        // ...và ngược lại: alter là cha/mẹ (nuôi) của ego. direct_link_reversed = TRUE.
        for (String type : queryColumn(PARENT_EDGE_BACKWARD_CYPHER, "rel_type", param)) {
            links.add(new DirectLink(parentType(type), normalizeParentSubtype(type), true, true, alter, ego));
        }
        for (String heirType : queryColumn(HEIR_EDGE_FORWARD_CYPHER, "heir_type", param)) {
            links.add(new DirectLink(DirectLinkType.HEIR, heirType, false, true, ego, alter));
        }
        for (String heirType : queryColumn(HEIR_EDGE_BACKWARD_CYPHER, "heir_type", param)) {
            links.add(new DirectLink(DirectLinkType.HEIR, heirType, true, true, alter, ego));
        }

        String spouseSql = AgeCypher.sql(SPOUSE_EDGE_CYPHER,
                "spouse_order ag_catalog.agtype, valid_to ag_catalog.agtype");
        List<DirectLink> spouseLinks = jdbc.query(spouseSql, rs -> {
            List<DirectLink> rows = new ArrayList<>();
            while (rs.next()) {
                boolean active = AgeCypher.text(rs.getObject("valid_to")) == null;
                // Cạnh SPOUSE là vô hướng về mặt nghiệp vụ (from_person_id chỉ là "người ghi
                // trước"), nên luôn chuẩn hoá reversed = false.
                rows.add(new DirectLink(DirectLinkType.SPOUSE, null, false, active, ego, alter));
            }
            return rows;
        }, param);
        if (spouseLinks != null) {
            links.addAll(spouseLinks);
        }
        return links;
    }

    @Override
    public List<SpouseLink> spousesOf(PersonId person) {
        String sql = AgeCypher.sql(SPOUSES_CYPHER,
                "spouse_id ag_catalog.agtype, spouse_order ag_catalog.agtype, "
                        + "valid_from ag_catalog.agtype, valid_to ag_catalog.agtype");
        List<SpouseLink> links = jdbc.query(sql, rs -> {
            List<SpouseLink> rows = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            while (rs.next()) {
                String spouse = AgeCypher.text(rs.getObject("spouse_id"));
                if (spouse == null || !seen.add(spouse)) {
                    continue;
                }
                LocalDate validTo = parseDate(AgeCypher.text(rs.getObject("valid_to")));
                rows.add(new SpouseLink(person, PersonId.of(spouse),
                        AgeCypher.integer(rs.getObject("spouse_order")),
                        parseDate(AgeCypher.text(rs.getObject("valid_from"))),
                        validTo, validTo == null));
            }
            return rows;
        }, agtypeParam(Map.of("id", person.value().toString())));
        return links == null ? List.of() : links;
    }

    private List<String> queryColumn(String cypher, String column, Object param) {
        String sql = AgeCypher.sql(cypher, column + " ag_catalog.agtype");
        List<String> values = jdbc.query(sql, rs -> {
            List<String> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(AgeCypher.text(rs.getObject(column)));
            }
            return rows;
        }, param);
        return values == null ? List.of() : values;
    }

    private static DirectLinkType parentType(String edgeType) {
        return "ADOPT".equalsIgnoreCase(edgeType) ? DirectLinkType.PARENT_ADOPT : DirectLinkType.PARENT_BIO;
    }

    private static String normalizeParentSubtype(String edgeType) {
        return "ADOPT".equalsIgnoreCase(edgeType) ? "ADOPT" : "BIO";
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.substring(0, Math.min(10, raw.length())));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Map tham số cho {@code cypher()}. Gửi dưới dạng {@link Types#OTHER} để PostgreSQL tự ép sang
     * {@code agtype} theo chữ ký hàm — đây là cách duy nhất giữ được truy vấn tham số hoá; nối
     * chuỗi vào câu Cypher là điều tuyệt đối không làm.
     */
    private static SqlParameterValue agtypeParam(Map<String, ?> values) {
        return new SqlParameterValue(Types.OTHER, AgeCypher.params(values));
    }
}
