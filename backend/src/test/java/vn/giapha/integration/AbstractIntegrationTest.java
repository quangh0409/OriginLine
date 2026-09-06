package vn.giapha.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.testcontainers.DockerClientFactory;
import vn.giapha.TestcontainersConfiguration;
import vn.giapha.genealogy.infrastructure.graph.AgtypeCodec;

/**
 * Nền chung cho mọi test tích hợp: hạ tầng THẬT (PostgreSQL + Apache AGE, RabbitMQ, Redis) qua
 * Testcontainers, cộng vài tiện ích dựng dữ liệu chi/nhánh và giả lập người gọi.
 *
 * <h2>Vì sao KHÔNG đánh dấu {@code @Transactional} lên test</h2>
 * Bất biến quan trọng nhất mà bộ test này canh là "cạnh AGE và dòng {@code relationship} cùng
 * commit hoặc cùng rollback". Nếu bản thân test chạy trong một transaction bao ngoài rồi tự
 * rollback, thì mọi thao tác ghi của service sẽ <b>gộp</b> vào transaction đó và ta không bao giờ
 * quan sát được ranh giới commit thật. Vì thế test ghi thật vào CSDL, và dọn dẹp bằng
 * {@link #resetDatabaseAndGraph()} trước mỗi ca.
 *
 * <h2>Bẫy Apache AGE (README §4)</h2>
 * Mọi phiên làm việc phải có {@code LOAD 'age'} + {@code search_path = ag_catalog, ...}. Ở đây điều
 * đó do {@code spring.datasource.hikari.connection-init-sql} lo, nên {@link JdbcTemplate} tiêm vào
 * test dùng chung pool và gõ Cypher được ngay. Đừng tự mở {@code DriverManager.getConnection} trong
 * test - connection đó không đi qua Hikari và sẽ chết với "function cypher(...) does not exist".
 *
 * <h2>Bảng nào được xoá, bảng nào không</h2>
 * Chỉ xoá dữ liệu nghiệp vụ do test sinh ra. Hai nhóm dữ liệu hạt giống phải giữ nguyên vì
 * migration chỉ nạp một lần cho cả container: {@code role} (V5) và
 * {@code kinship_rule_set}/{@code kinship_rule} (R__seed).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    // LUU Y CHO NGUOI VIET LOP IT MOI: @EnabledIf cua JUnit KHONG duoc ke thua tu lop cha truu
    // tuong (khac han @SpringBootTest/@Import, von duoc Spring tim theo ca cay ke thua). Dat no o
    // day thi vo tac dung va lop se co chay khi may khong co Docker, roi chet vi loi context. Vi
    // vay MOI lop IT cu the phai tu mang:
    //     @EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")

    /** Tên đồ thị AGE - khớp {@code giapha.graph.name} và {@code V7__graph.sql}. */
    protected static final String GRAPH = "giapha_graph";

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected vn.giapha.genealogy.domain.port.TreeGraphPort graph;

    /** Gieo một nhân khẩu vào cả bảng {@code person} lẫn đồ thị AGE — xem {@link PersonFixtures}. */
    protected UUID seed(PersonFixtures.Builder builder) {
        return builder.seed(jdbc, graph);
    }

    /**
     * Không có Docker thì bỏ qua cả lớp thay vì để Spring context chết giữa chừng - máy chỉ làm
     * việc với domain thuần vẫn phải chạy được {@code mvn test}.
     */
    public static boolean dockerAvailable() {
        try {
            // Goi som, truoc khi bat ky connection JDBC nao duoc mo: xem javadoc cua phuong thuc.
            TestcontainersConfiguration.normalizeVietnamTimeZone();
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @BeforeEach
    void resetDatabaseAndGraph() {
        SecurityContextHolder.clearContext();
        // CASCADE kéo theo mọi bảng tham chiếu (event, reminder_job, notification_log,
        // push_subscription...) nên không phải liệt kê tay và không sợ sót khi W5/W6 thêm bảng.
        jdbc.execute("TRUNCATE TABLE relationship, person_name, person, branch,"
                + " app_user, branch_assignment, change_request, audit_log"
                + " RESTART IDENTITY CASCADE");
        // Đồ thị phải sạch cùng lúc với bảng, nếu không ca test sau sẽ thấy đỉnh mồ côi của ca
        // trước và mọi phép đếm "cân bằng graph/bảng" đều vô nghĩa.
        cypher("MATCH (n) DETACH DELETE n", Map.of());
    }

    // -------------------------------------------------------------------------------------
    // Người gọi
    // -------------------------------------------------------------------------------------

    /**
     * Đặt người gọi hiện tại.
     *
     * <p>Tiền tố {@code ROLE_} là cố ý: nó mô phỏng đúng thứ {@code KeycloakRealmRoleConverter}
     * sinh ra, và {@code CurrentUserProvider} sẽ cắt tiền tố đó đi. Test mà quên tiền tố vẫn chạy
     * (provider chấp nhận cả hai) nhưng sẽ không còn phản ánh luồng thật.</p>
     */
    protected void authenticateAs(String keycloakSub, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(keycloakSub)
                .claim("preferred_username", keycloakSub)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }

    /** Khách vãng lai: không token. Theo BA v2 §10 khách không được thấy bất kỳ người sống nào. */
    protected void authenticateAsGuest() {
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------------------
    // Dữ liệu nền
    // -------------------------------------------------------------------------------------

    /** Thêm một chi/ngành. {@code path} phải là ltree hợp lệ và nhãn cuối phải bằng {@code slug}. */
    protected UUID insertBranch(String name, String path, UUID parentId, String kind) {
        String slug = path.substring(path.lastIndexOf('.') + 1);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO branch (id, name, slug, path, parent_id, branch_kind)"
                + " VALUES (?, ?, ?, CAST(? AS ltree), ?, ?)", id, name, slug, path, parentId, kind);
        return id;
    }

    /** Thêm một tài khoản đã ghép (hoặc chưa ghép) với nhân khẩu. */
    protected UUID insertAppUser(String keycloakSub, UUID personId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user (id, keycloak_sub, person_id, display_name, status)"
                + " VALUES (?, ?, ?, ?, 'ACTIVE')", id, keycloakSub, personId, keycloakSub);
        return id;
    }

    /**
     * Giao một chi cho tài khoản theo vai trò.
     *
     * <p>{@code branchId = null} nghĩa là phạm vi toàn dòng họ - và {@code AppUserDirectory} cố ý
     * <b>không</b> trả về dòng đó ở {@code managedBranches()}, nên đừng dùng nó để mô phỏng ADMIN.</p>
     */
    protected void assignBranchRole(UUID appUserId, String roleCode, UUID branchId) {
        UUID roleId = jdbc.queryForObject("SELECT id FROM role WHERE code = ?", UUID.class, roleCode);
        jdbc.update("INSERT INTO branch_assignment (app_user_id, role_id, branch_id) VALUES (?, ?, ?)",
                appUserId, roleId, branchId);
    }

    // -------------------------------------------------------------------------------------
    // Đồ thị AGE
    // -------------------------------------------------------------------------------------

    /**
     * Chạy một câu Cypher trả về đúng một cột {@code agtype} và trả danh sách chuỗi thô.
     *
     * <p>Tham số đi qua {@link AgtypeCodec#params} - đối số thứ ba của {@code cypher()} phải là một
     * tham số kiểu chưa xác định, xem javadoc của codec để biết hai lối đi sai đã thử.</p>
     */
    protected List<String> cypher(String query, Map<String, Object> params) {
        String sql = "SELECT * FROM cypher('" + GRAPH + "', $$" + query + "$$, ?) AS (v agtype)";
        return jdbc.queryForList(sql, String.class, AgtypeCodec.params(params));
    }

    /** Số cạnh {@code label} đi từ {@code from} tới {@code to} trong đồ thị. */
    protected int graphEdgeCount(String label, UUID from, UUID to) {
        return cypher("MATCH (a:Person {id: $from})-[r:" + label + "]->(b:Person {id: $to}) RETURN r",
                Map.of("from", from.toString(), "to", to.toString())).size();
    }

    /** Thuộc tính của cạnh đầu tiên khớp - để đối chiếu bản chiếu quan hệ với cạnh thật. */
    protected JsonNode graphEdgeProperties(String label, UUID from, UUID to) {
        List<String> rows = cypher(
                "MATCH (a:Person {id: $from})-[r:" + label + "]->(b:Person {id: $to}) RETURN r",
                Map.of("from", from.toString(), "to", to.toString()));
        return rows.isEmpty() ? null : AgtypeCodec.properties(rows.get(0));
    }

    protected boolean graphNodeExists(UUID personId) {
        return !cypher("MATCH (p:Person {id: $id}) RETURN p.id",
                Map.of("id", personId.toString())).isEmpty();
    }

    protected int graphNodeCount() {
        List<String> rows = cypher("MATCH (n:Person) RETURN count(n)", Map.of());
        return rows.isEmpty() ? 0 : AgtypeCodec.toInt(rows.get(0));
    }

    /** Tổng số cạnh trong đồ thị, không phân biệt nhãn. */
    protected int graphEdgeTotal() {
        List<String> rows = cypher("MATCH ()-[r]->() RETURN count(r)", Map.of());
        return rows.isEmpty() ? 0 : AgtypeCodec.toInt(rows.get(0));
    }

    protected int countRows(String table) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
