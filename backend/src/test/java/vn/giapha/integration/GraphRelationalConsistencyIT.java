package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.giapha.genealogy.application.GenealogyConflictException;
import vn.giapha.genealogy.application.LinkRelationshipService;
import vn.giapha.genealogy.application.command.LinkRelationshipCommand;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.vo.Gender;

/**
 * <b>Bất biến quan trọng nhất của cả kiến trúc</b> (CLAUDE.md "Graph/relational consistency",
 * {@code V2__core.sql} §BẤT BIẾN 2): cạnh trong đồ thị Apache AGE và dòng trong bảng
 * {@code relationship} được ghi trong <b>cùng một transaction</b>. Đồ thị là nguồn chân lý, bảng là
 * bản chiếu để có khoá ngoại, nhật ký và truy vấn SQL thuần.
 *
 * <p>Vì sao đây là hạng mục chặn "ship được": ghi lệch một bên thì <b>không có gì báo lỗi</b>. Phả
 * đồ (đọc từ graph) và báo cáo (đọc từ bảng) sẽ nói hai điều khác nhau, và không ai biết bên nào
 * đúng. Không có ràng buộc CSDL nào bắt được chuyện đó — chỉ có test này.</p>
 *
 * <p><b>Test cố ý không {@code @Transactional}</b>: xem javadoc {@link AbstractIntegrationTest}.
 * Ranh giới commit thật là thứ đang được đo, nên không được bọc nó trong một transaction khác.</p>
 */
@DisplayName("Bất biến ghi đôi graph AGE ↔ bảng relationship")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class GraphRelationalConsistencyIT extends AbstractIntegrationTest {

    @Autowired
    private LinkRelationshipService linkRelationship;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private TransactionTemplate tx;
    private UUID chiGiap;

    @BeforeEach
    void setUpClan() {
        tx = new TransactionTemplate(transactionManager);
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        authenticateAs("sub-admin", "ADMIN");
    }

    private LinkRelationshipCommand parentEdge(UUID cha, UUID con, RelType relType) {
        return new LinkRelationshipCommand(cha, con, relType, null, null, null, null, null);
    }

    // =====================================================================================
    // 1. Đường thành công: cả hai bên đều có mặt và khớp nhau
    // =====================================================================================

    @Test
    @DisplayName("nối cha–con ghi đủ cạnh AGE lẫn dòng relationship, và hai bên khớp nhau")
    void noiChaCon_ghiDuCanhAgeVaBanChieu() {
        UUID cha = seed(PersonFixtures.living("Nguyễn Văn Cả").branch(chiGiap).generation(1));
        UUID con = seed(PersonFixtures.living("Nguyễn Văn Hai").branch(chiGiap).generation(2));

        linkRelationship.link(parentEdge(cha, con, RelType.PARENT_BIO));

        // (a) Bản chiếu quan hệ
        Map<String, Object> row = jdbc.queryForMap("SELECT from_person_id, to_person_id, rel_type"
                + " FROM relationship WHERE to_person_id = ?", con);
        assertThat(row.get("from_person_id")).isEqualTo(cha);
        assertThat(row.get("rel_type")).isEqualTo("PARENT_BIO");

        // (b) Đồ thị
        assertThat(graphEdgeCount("PARENT", cha, con)).isEqualTo(1);
        JsonNode edge = graphEdgeProperties("PARENT", cha, con);
        assertThat(edge.path("type").asText())
                .as("cạnh PARENT phải mang type BIO để phân biệt con ruột với con nuôi")
                .isEqualTo("BIO");
    }

    @Test
    @DisplayName("con nuôi mang type ADOPT trên cạnh AGE và rel_type PARENT_ADOPT trên bảng")
    void conNuoi_khopOCaHaiNoi() {
        UUID cha = seed(PersonFixtures.living("Nguyễn Văn Cả").branch(chiGiap).generation(1));
        UUID conNuoi = seed(PersonFixtures.living("Nguyễn Văn Nuôi").branch(chiGiap).generation(2));

        linkRelationship.link(parentEdge(cha, conNuoi, RelType.PARENT_ADOPT));

        assertThat(graphEdgeProperties("PARENT", cha, conNuoi).path("type").asText())
                .isEqualTo("ADOPT");
        assertThat(jdbc.queryForObject("SELECT rel_type FROM relationship WHERE to_person_id = ?",
                String.class, conNuoi)).isEqualTo("PARENT_ADOPT");
    }

    @Test
    @DisplayName("đa thê: spouse_order ghi giống hệt nhau ở cạnh AGE và ở bảng relationship")
    void daThe_thuTuVoKhopGiuaCanhAgeVaBanChieu() {
        UUID chong = seed(PersonFixtures.living("Nguyễn Văn Chồng").branch(chiGiap).generation(2));
        UUID voCa = seed(PersonFixtures.living("Trần Thị Cả").gender(Gender.FEMALE)
                .branch(chiGiap).generation(2));
        UUID voHai = seed(PersonFixtures.living("Lê Thị Hai").gender(Gender.FEMALE)
                .branch(chiGiap).generation(2));

        linkRelationship.link(new LinkRelationshipCommand(chong, voCa, RelType.SPOUSE, null, 1,
                null, null, "chính thất"));
        linkRelationship.link(new LinkRelationshipCommand(chong, voHai, RelType.SPOUSE, null, 2,
                null, null, "kế thất"));

        assertThat(graphEdgeProperties("SPOUSE", chong, voCa).path("spouse_order").asInt()).isEqualTo(1);
        assertThat(graphEdgeProperties("SPOUSE", chong, voHai).path("spouse_order").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT spouse_order FROM relationship WHERE to_person_id = ?",
                Integer.class, voCa)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT spouse_order FROM relationship WHERE to_person_id = ?",
                Integer.class, voHai)).isEqualTo(2);
    }

    @Test
    @DisplayName("đích tôn thừa tự: cạnh HEIR mang heir_type trên cả hai nơi")
    void keTu_canhHeirKhopOCaHaiNoi() {
        UUID cuTo = seed(PersonFixtures.deceased("Nguyễn Phúc Tổ", 1970).branch(chiGiap).generation(1));
        UUID dichTon = seed(PersonFixtures.living("Nguyễn Văn Đích").branch(chiGiap).generation(3));

        linkRelationship.link(new LinkRelationshipCommand(cuTo, dichTon, RelType.HEIR,
                HeirKind.DICH_TON, null, null, null, "đích tôn thừa tự"));

        assertThat(graphEdgeProperties("HEIR", cuTo, dichTon).path("heir_type").asText())
                .isEqualTo("DICH_TON");
        assertThat(jdbc.queryForObject("SELECT heir_type FROM relationship WHERE to_person_id = ?",
                String.class, dichTon)).isEqualTo("DICH_TON");
    }

    // =====================================================================================
    // 2. Đường thất bại: rollback phải xoá CẢ HAI bên
    // =====================================================================================

    @Test
    @DisplayName("transaction rollback thì cạnh AGE biến mất cùng dòng relationship — không có trạng thái lệch")
    void transactionRollback_xoaCaCanhAgeLanDongRelationship() {
        UUID cha = seed(PersonFixtures.living("Nguyễn Văn Cả").branch(chiGiap).generation(1));
        UUID con = seed(PersonFixtures.living("Nguyễn Văn Hai").branch(chiGiap).generation(2));

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            linkRelationship.link(parentEdge(cha, con, RelType.PARENT_BIO));
            // Đẩy Hibernate xuống CSDL để chắc chắn CẢ HAI bên đều đã hiện diện trong transaction
            // này. Không flush thì dòng relationship còn nằm trong persistence context và test sẽ
            // "xanh" vì một lý do sai: nó chưa bao giờ được ghi, chứ không phải đã ghi rồi lùi.
            entityManager.flush();
            assertThat(graphEdgeCount("PARENT", cha, con))
                    .as("giữa transaction: cạnh AGE đã có").isEqualTo(1);
            assertThat(countRows("relationship"))
                    .as("giữa transaction: bản chiếu đã có").isEqualTo(1);
            throw new IllegalStateException("ép lỗi ở nửa sau transaction");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("ép lỗi ở nửa sau transaction");

        assertThat(graphEdgeCount("PARENT", cha, con))
                .as("sau rollback: cạnh AGE phải biến mất").isZero();
        assertThat(countRows("relationship"))
                .as("sau rollback: dòng relationship phải biến mất").isZero();
        assertThat(graphNodeExists(cha)).as("đỉnh của hai người đã commit trước đó vẫn còn").isTrue();
        assertThat(graphNodeExists(con)).isTrue();
    }

    @Test
    @DisplayName("lỗi nghiệp vụ ở lệnh nối thứ hai kéo lùi cả lệnh nối thứ nhất, ở cả hai nơi")
    void loiONuaSauMotUseCase_keoLuiCanhDaGhiOCaHaiNoi() {
        UUID cha = seed(PersonFixtures.living("Nguyễn Văn Cả").branch(chiGiap).generation(1));
        UUID con = seed(PersonFixtures.living("Nguyễn Văn Hai").branch(chiGiap).generation(2));

        // Kịch bản thật, không phải ngoại lệ dựng tay: một lô nối quan hệ trong đó cạnh thứ hai
        // trùng cạnh thứ nhất. Cạnh đầu đã ghi xong vào cả graph lẫn bảng rồi mới đổ.
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            linkRelationship.link(parentEdge(cha, con, RelType.PARENT_BIO));
            entityManager.flush();
            linkRelationship.link(parentEdge(cha, con, RelType.PARENT_BIO));
        })).isInstanceOf(DomainException.class);

        assertThat(countRows("relationship")).isZero();
        assertThat(graphEdgeTotal()).as("cạnh đầu tiên cũng phải biến mất").isZero();
        assertThat(countRows("person")).as("hai nhân khẩu commit trước đó không bị ảnh hưởng")
                .isEqualTo(2);
        assertThat(graphNodeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("cạnh tạo chu trình bị chặn và không để lại dấu vết ở cả hai nơi")
    void canhTaoChuTrinh_biChanVaKhongDeLaiTrangThaiLech() {
        UUID cha = seed(PersonFixtures.living("Nguyễn Văn Cả").branch(chiGiap).generation(1));
        UUID con = seed(PersonFixtures.living("Nguyễn Văn Hai").branch(chiGiap).generation(2));
        linkRelationship.link(parentEdge(cha, con, RelType.PARENT_BIO));

        // Con làm cha của chính cha mình: mọi phép duyệt (LCA, danh xưng, phả đồ) sẽ chạy vô tận.
        assertThatThrownBy(() -> linkRelationship.link(parentEdge(con, cha, RelType.PARENT_BIO)))
                .isInstanceOf(GenealogyConflictException.class);

        assertThat(countRows("relationship")).isEqualTo(1);
        assertThat(graphEdgeTotal()).isEqualTo(1);
        assertThat(graphEdgeCount("PARENT", con, cha)).isZero();
    }

    // =====================================================================================
    // 3. Quét cân bằng trên một phả hệ nhỏ nhưng đủ các ca khó
    // =====================================================================================

    @Test
    @DisplayName("phả hệ có đa thê, con nuôi và kế tự: mọi dòng relationship đều có cạnh AGE tương ứng")
    void quetCanBang_moiDongRelationshipDeuCoCanhAgeTuongUng() {
        UUID ong = seed(PersonFixtures.deceased("Nguyễn Phúc Tổ", 1970).branch(chiGiap).generation(1));
        UUID baCa = seed(PersonFixtures.deceased("Trần Thị Chính Thất", 1975)
                .gender(Gender.FEMALE).branch(chiGiap).generation(1));
        UUID baHai = seed(PersonFixtures.deceased("Lê Thị Kế Thất", 1990)
                .gender(Gender.FEMALE).branch(chiGiap).generation(1));
        UUID chaRuot = seed(PersonFixtures.deceased("Nguyễn Văn Trưởng", 2000)
                .branch(chiGiap).generation(2));
        UUID conNuoi = seed(PersonFixtures.living("Nguyễn Văn Nghĩa").branch(chiGiap).generation(2));
        UUID dichTon = seed(PersonFixtures.living("Nguyễn Văn Đích").branch(chiGiap).generation(3));

        linkRelationship.link(new LinkRelationshipCommand(ong, baCa, RelType.SPOUSE, null, 1,
                null, null, null));
        linkRelationship.link(new LinkRelationshipCommand(ong, baHai, RelType.SPOUSE, null, 2,
                null, null, null));
        linkRelationship.link(parentEdge(ong, chaRuot, RelType.PARENT_BIO));
        linkRelationship.link(parentEdge(ong, conNuoi, RelType.PARENT_ADOPT));
        linkRelationship.link(parentEdge(chaRuot, dichTon, RelType.PARENT_BIO));
        linkRelationship.link(new LinkRelationshipCommand(ong, dichTon, RelType.HEIR,
                HeirKind.DICH_TON, null, null, null, "đích tôn thừa tự"));

        assertThat(countRows("relationship"))
                .as("số cạnh trong đồ thị phải bằng đúng số dòng bản chiếu")
                .isEqualTo(graphEdgeTotal());
        assertThat(countRows("person")).isEqualTo(graphNodeCount());

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT from_person_id, to_person_id, rel_type FROM relationship");
        assertThat(rows).hasSize(6);
        for (Map<String, Object> row : rows) {
            RelType relType = RelType.valueOf((String) row.get("rel_type"));
            UUID from = (UUID) row.get("from_person_id");
            UUID to = (UUID) row.get("to_person_id");
            assertThat(graphEdgeCount(relType.edgeLabel(), from, to))
                    .as("dòng relationship %s %s→%s không có cạnh AGE tương ứng", relType, from, to)
                    .isGreaterThanOrEqualTo(1);
        }
    }
}
