package vn.giapha.demo.writer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.giapha.demo.model.DemoPerson;
import vn.giapha.demo.model.DemoRelation;

/**
 * Ghi đỉnh và cạnh vào graph AGE {@code giapha_graph}.
 *
 * <p><b>Không tự mở transaction.</b> Lớp này luôn được gọi từ trong ranh giới transaction của
 * {@link DemoDataWriter}, trên cùng một {@code DataSource} với phần ghi bảng — đó là cách bất biến
 * "cạnh AGE và dòng {@code relationship} nằm trong CÙNG một transaction" được giữ.</p>
 *
 * <h2>Ghi theo lô bằng UNWIND</h2>
 * <p>1.500 đỉnh cộng ~3.000 cạnh mà mỗi cái một lượt gọi thì phần lớn thời gian là round-trip. Mỗi
 * câu dưới đây nhận một mảng agtype và tự lặp bên trong máy chủ.</p>
 *
 * <h2>Vì sao phải đếm số dòng trả về</h2>
 * <p>Cypher {@code MATCH ... CREATE} <b>không báo lỗi</b> khi MATCH không khớp — nó lặng lẽ không
 * tạo cạnh nào. Nếu không đếm, một lỗi lệch id sẽ đi qua toàn bộ quy trình mà không ai hay, để lại
 * một cái cây thiếu cành bên cạnh một bảng {@code relationship} đầy đủ. Đúng kiểu lệch mà cái nhãn
 * "bảng chỉ là bản chiếu" sẽ che mất. Đếm hụt là ném lỗi; vì tất cả nằm trong một transaction nên
 * cả mẻ dữ liệu bị huỷ chứ không để lại nửa vời.</p>
 */
@Component
@Profile("demo")
class DemoGraphWriter {

    private static final Logger log = LoggerFactory.getLogger(DemoGraphWriter.class);

    /** Đủ lớn để round-trip không còn đáng kể, đủ nhỏ để chuỗi agtype không phình bộ nhớ. */
    private static final int BATCH_SIZE = 250;

    private final JdbcTemplate jdbc;
    private final String graphName;

    DemoGraphWriter(JdbcTemplate jdbc, @Value("${giapha.graph.name:giapha_graph}") String graphName) {
        // Ten do thi di thang vao chuoi Cypher nen phai la dinh danh an toan, khong phai du lieu.
        if (!graphName.matches("^[a-z_][a-z0-9_]*$")) {
            throw new IllegalArgumentException("Ten do thi khong hop le: " + graphName);
        }
        this.jdbc = jdbc;
        this.graphName = graphName;
    }

    /** Số đỉnh {@code Person} hiện có — dùng để khẳng định đồ thị rỗng trước khi nạp. */
    long personNodeCount() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM cypher('" + graphName
                        + "', $$ MATCH (p:Person) RETURN p $$) AS (v ag_catalog.agtype)",
                Long.class);
        return count == null ? 0L : count;
    }

    /**
     * Tổng số cạnh của cả ba nhãn — đối chiếu trực tiếp với số dòng {@code relationship}.
     * Đây là phép đo duy nhất chứng minh bản chiếu và nguồn chân lý không lệch nhau.
     */
    long edgeCount() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM cypher('" + graphName
                        + "', $$ MATCH ()-[e]->() RETURN e $$) AS (v ag_catalog.agtype)",
                Long.class);
        return count == null ? 0L : count;
    }

    /**
     * Xoá sạch đồ thị. CHỈ dùng khi nạp lại dữ liệu demo — không phải một thao tác nghiệp vụ.
     * Nhân khẩu thật không bao giờ bị xoá cứng: xoá mềm là bất biến của {@code genealogy}.
     */
    void deleteAll() {
        jdbc.query("SELECT * FROM cypher('" + graphName
                + "', $$ MATCH (n) DETACH DELETE n RETURN 1 $$) AS (v ag_catalog.agtype)", rs -> {
                    // AGE bat buoc co menh de RETURN va bat buoc doc het cursor.
                });
    }

    void writePersonNodes(List<DemoPerson> persons) {
        List<Map<String, Object>> rows = new ArrayList<>(persons.size());
        for (DemoPerson person : persons) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", person.id().toString());
            row.put("gender", person.gender());
            row.put("generation", person.generation());
            row.put("is_deleted", person.deleted());
            rows.add(row);
        }
        int created = runBatches("""
                UNWIND $rows AS row
                CREATE (p:Person {id: row.id, gender: row.gender,
                                  generation: row.generation, is_deleted: row.is_deleted})
                RETURN p.id
                """, rows);
        require(created, rows.size(), "dinh Person");
        log.info("AGE: da tao {} dinh Person trong do thi {}", created, graphName);
    }

    /** Ghi cạnh theo đúng ba nhãn của V7: {@code PARENT} · {@code SPOUSE} · {@code HEIR}. */
    void writeEdges(List<DemoRelation> relations) {
        List<Map<String, Object>> parents = new ArrayList<>();
        List<Map<String, Object>> spouses = new ArrayList<>();
        List<Map<String, Object>> heirs = new ArrayList<>();

        for (DemoRelation relation : relations) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("from", relation.from().toString());
            row.put("to", relation.to().toString());
            switch (relation.edgeLabel()) {
                case "PARENT" -> {
                    // Chieu canh: cha/me -> con. Con nuoi van la canh PARENT, phan biet bang type.
                    row.put("type", relation.parentEdgeType());
                    parents.add(row);
                }
                case "SPOUSE" -> {
                    row.put("spouse_order", relation.spouseOrder());
                    row.put("valid_from", asText(relation.validFrom()));
                    row.put("valid_to", asText(relation.validTo()));
                    spouses.add(row);
                }
                case "HEIR" -> {
                    row.put("heir_type", relation.heirType());
                    heirs.add(row);
                }
                default -> throw new IllegalStateException(
                        "Nhan canh AGE khong ho tro: " + relation.edgeLabel());
            }
        }

        int parentEdges = runBatches("""
                UNWIND $rows AS row
                MATCH (p:Person {id: row.from}), (c:Person {id: row.to})
                CREATE (p)-[e:PARENT {type: row.type}]->(c)
                RETURN id(e)
                """, parents);
        require(parentEdges, parents.size(), "canh PARENT");

        int spouseEdges = runBatches("""
                UNWIND $rows AS row
                MATCH (a:Person {id: row.from}), (b:Person {id: row.to})
                CREATE (a)-[e:SPOUSE {spouse_order: row.spouse_order,
                                      valid_from: row.valid_from, valid_to: row.valid_to}]->(b)
                RETURN id(e)
                """, spouses);
        require(spouseEdges, spouses.size(), "canh SPOUSE");

        int heirEdges = runBatches("""
                UNWIND $rows AS row
                MATCH (a:Person {id: row.from}), (b:Person {id: row.to})
                CREATE (a)-[e:HEIR {heir_type: row.heir_type}]->(b)
                RETURN id(e)
                """, heirs);
        require(heirEdges, heirs.size(), "canh HEIR");

        log.info("AGE: da tao {} canh PARENT, {} canh SPOUSE, {} canh HEIR",
                parentEdges, spouseEdges, heirEdges);
    }

    // ---------------------------------------------------------------------------------

    private int runBatches(String cypher, List<Map<String, Object>> rows) {
        String sql = "SELECT * FROM cypher('" + graphName + "', $$" + cypher
                + "$$, ?) AS (v ag_catalog.agtype)";
        int total = 0;
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<Map<String, Object>> batch =
                    rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            // Doc ve danh sach id vua tao: so phan tu chinh la so dinh/canh THUC SU duoc tao ra.
            total += jdbc.queryForList(sql, String.class, DemoAgtype.rows(batch)).size();
        }
        return total;
    }

    private static void require(int actual, int expected, String what) {
        if (actual != expected) {
            throw new IllegalStateException(
                    "AGE tao thieu %s: mong doi %d, thuc te %d — do thi va bang relationship se lech nhau"
                            .formatted(what, expected, actual));
        }
    }

    /** Ngày trên cạnh AGE lưu dạng chuỗi ISO; {@code null} thì AGE bỏ hẳn thuộc tính. */
    private static String asText(LocalDate date) {
        return date == null ? null : date.toString();
    }
}
