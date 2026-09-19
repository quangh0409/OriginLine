package vn.giapha.genealogy.infrastructure.search;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.application.GenealogyRosterPort;

/**
 * Hiện thực {@link GenealogyRosterPort} bằng SQL thuần trên bảng {@code person}.
 *
 * <h2>Hai câu SQL, và vì sao mỗi câu có hai biến thể</h2>
 * Bộ lọc phạm vi chi/ngành được viết thành <b>hai hằng chuỗi riêng</b> thay vì một câu có
 * {@code (:prefix IS NULL OR …)}. Lý do rất cụ thể: tham số {@code NULL} không kèm kiểu làm
 * PostgreSQL trả {@code could not determine data type of parameter}, và nếu vá bằng
 * {@code CAST(:prefix AS text)} thì câu lệnh đúng nhưng kế hoạch thực thi mất khả năng dùng chỉ
 * mục GiST của {@code ltree}. Hai câu tách bạch thì cả hai vấn đề đều không tồn tại.
 *
 * <h2>{@code b.path <@ CAST(:prefix AS ltree)} — chiều của toán tử là chỗ dễ sai nhất</h2>
 * {@code <@} đọc là "nằm trong". Viết ngược thành {@code @>} sẽ lấy đúng các <b>tổ tiên</b> của
 * chi đang xét thay vì cây con của nó, và triệu chứng không phải lỗi mà là một gốc mặc định sai
 * một cách im lặng. {@code NativeSqlPinningIT} ghim đúng loại bẫy này cho câu SQL của
 * {@code membership}; câu ở đây được {@code DefaultTreeRootIT} phủ.
 *
 * <h2>Không lọc theo {@code privacy_consent}</h2>
 * V8 §8.4 chốt là không có chỉ mục trên cột đó và không truy vấn nào được lọc theo nó. Adapter này
 * tuân thủ: nó chỉ biết "còn sống" và "chưa xoá mềm". Mọi câu hỏi về đồng thuận được trả lời trong
 * tiến trình bởi {@code PersonVisibility}.
 */
@Repository
public class GenealogyRosterJdbcAdapter implements GenealogyRosterPort {

    private static final Logger log = LoggerFactory.getLogger(GenealogyRosterJdbcAdapter.class);

    /**
     * Thứ tự ưu tiên gốc mặc định — xem {@link GenealogyRosterPort#rootCandidates}.
     *
     * <p>{@code COALESCE(generation, 2147483647)} chứ không phải {@code NULLS LAST}: nhân khẩu chưa
     * gán đời thứ phải xếp <b>cuối</b>, và viết bằng {@code COALESCE} thì thứ tự ấy đúng kể cả khi
     * ai đó thêm một khoá sắp xếp {@code DESC} vào phía trước — lúc đó {@code NULLS LAST} sẽ lặng
     * lẽ đảo nghĩa. {@code p.is_alive ASC} xếp {@code FALSE} trước {@code TRUE}, tức <b>người đã
     * khuất lên đầu</b>: đó là ứng viên gốc mà mọi vai, kể cả Khách, đều nhìn thấy được.</p>
     */
    private static final String ORDER_BY = """
             ORDER BY COALESCE(p.generation, 2147483647) ASC,
                      p.is_alive ASC,
                      p.birth_solar ASC NULLS LAST,
                      p.created_at ASC,
                      p.id ASC
             LIMIT :limit
            """;

    private static final String ROOTS_IN_CLAN = """
            SELECT p.id
              FROM person p
             WHERE p.is_deleted = FALSE
            """ + ORDER_BY;

    private static final String ROOTS_IN_BRANCH = """
            SELECT p.id
              FROM person p
              JOIN branch b ON b.id = p.primary_branch_id
             WHERE p.is_deleted = FALSE
               AND b.is_deleted = FALSE
               AND b.path <@ CAST(:prefix AS ltree)
            """ + ORDER_BY;

    /**
     * Người còn sống, thứ tự tất định.
     *
     * <p>Sắp theo {@code created_at} rồi {@code id} chứ không theo tên: tên nằm ở bảng
     * {@code person_name} và kéo nó vào đây chỉ để sắp xếp là thêm một {@code JOIN} cho một thứ
     * tự mà tầng application đằng nào cũng sắp lại sau khi lọc. Cái duy nhất câu này phải bảo đảm
     * là <b>ổn định</b>, để phân trang không lặp hay bỏ sót người giữa hai lượt gọi.</p>
     */
    private static final String LIVING = """
            SELECT p.id
              FROM person p
             WHERE p.is_deleted = FALSE
               AND p.is_alive = TRUE
             ORDER BY p.created_at ASC, p.id ASC
             LIMIT :limit
            """;

    /**
     * Lọc tên không dấu trên <b>mọi lớp tên</b>.
     *
     * <p>{@code vn_unaccent(:q)} ở vế phải chứ không phải {@code lower(:q)}: người dùng gõ
     * "Nguyễn" hay "nguyen" đều phải ra cùng kết quả, và chỉ có hàm của CSDL mới bảo đảm phép bỏ
     * dấu ở hai vế là <i>cùng một</i> phép. Ký tự {@code %} và {@code _} do người dùng gõ được
     * thoát bằng {@code ESCAPE '\'} — không thoát thì một dấu gạch dưới lọt vào ô tìm kiếm sẽ âm
     * thầm khớp mọi ký tự.</p>
     */
    private static final String LIVING_BY_NAME = """
            SELECT DISTINCT pn.person_id AS id
              FROM person_name pn
              JOIN person p ON p.id = pn.person_id
             WHERE p.is_deleted = FALSE
               AND p.is_alive = TRUE
               AND pn.name_unaccented LIKE ('%' || vn_unaccent(:q) || '%') ESCAPE '\\'
             LIMIT :limit
            """;

    /** Ký tự đại diện của {@code LIKE} và chính ký tự thoát, phải được thoát trước khi ghép. */
    private static final java.util.regex.Pattern LIKE_META =
            java.util.regex.Pattern.compile("([\\\\%_])");

    private final NamedParameterJdbcTemplate jdbc;

    public GenealogyRosterJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> rootCandidates(String branchPathPrefix, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("limit", limit);
        String sql;
        if (branchPathPrefix == null || branchPathPrefix.isBlank()) {
            sql = ROOTS_IN_CLAN;
        } else {
            sql = ROOTS_IN_BRANCH;
            params.addValue("prefix", branchPathPrefix);
        }
        List<UUID> ids = jdbc.query(sql, params, (rs, row) -> rs.getObject("id", UUID.class));
        log.debug("Ung vien goc pha do trong pham vi {}: {}", branchPathPrefix, ids.size());
        return ids;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> livingPersonIds(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return jdbc.query(LIVING, new MapSqlParameterSource("limit", limit),
                (rs, row) -> rs.getObject("id", UUID.class));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> livingPersonIdsMatchingName(String q, int limit) {
        if (q == null || q.isBlank() || limit <= 0) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("q", LIKE_META.matcher(q.trim()).replaceAll("\\\\$1"))
                .addValue("limit", limit);
        return jdbc.query(LIVING_BY_NAME, params, (rs, row) -> rs.getObject("id", UUID.class));
    }
}
