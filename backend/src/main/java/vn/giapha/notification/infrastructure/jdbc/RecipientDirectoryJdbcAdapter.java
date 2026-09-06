package vn.giapha.notification.infrastructure.jdbc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.notification.domain.Recipient;
import vn.giapha.notification.domain.port.RecipientDirectory;

/**
 * Hiện thực {@link RecipientDirectory}: ai được nhắc, tính bằng {@code ltree}.
 *
 * <h2>Toán tử {@code <@} chứ không phải dấu bằng</h2>
 * {@code child.path <@ root.path} nghĩa là "hậu duệ hoặc chính nó". Đây là cách duy nhất đúng để
 * hiểu "thành viên cùng chi hoặc nhánh": giỗ của Chi Nhất phải tới cả Ngành Trưởng và các nhánh bên
 * dưới. So {@code primary_branch_id = :branchId} thay cho phép so cây là lỗi âm thầm — nó luôn trả
 * ít người hơn và không bao giờ báo lỗi. Chỉ mục {@code ix_branch_path_gist} đỡ đúng phép so này.
 *
 * <h2>Ba điều kiện lọc, mỗi điều kiện một lý do</h2>
 * <ul>
 *   <li>{@code app_user.status = 'ACTIVE'} — người nhận phải là <b>thành viên</b> có tài khoản. Hộp
 *       thư và Web Push đều gắn với tài khoản; ghi tin cho một nhân khẩu không có tài khoản là tạo
 *       ra dữ liệu không ai đọc được.</li>
 *   <li>{@code person.is_alive = TRUE} — không nhắc giỗ người đã khuất.</li>
 *   <li>{@code person.is_deleted = FALSE} — hồ sơ đã xoá mềm vẫn nằm trong cây để giữ liên kết,
 *       nhưng không nhận thông báo.</li>
 * </ul>
 *
 * <h2>Nợ kiến trúc đã biết</h2>
 * {@code app_user} thuộc {@code membership} (W6), {@code person}/{@code branch} thuộc
 * {@code genealogy} (W2); cả hai chưa mở mặt tiền công khai nên adapter Giai đoạn 1 đọc thẳng bằng
 * SQL. Không có phụ thuộc Java nào sang context khác — ranh giới module vẫn sạch. Chỉ SELECT.
 */
@Repository
public class RecipientDirectoryJdbcAdapter implements RecipientDirectory {

    private static final String SQL_BRANCH_MEMBERS = """
            SELECT p.id AS person_id, u.id AS app_user_id, u.locale
              FROM person p
              JOIN app_user u ON u.person_id = p.id AND u.status = 'ACTIVE'
              JOIN branch  b ON b.id = p.primary_branch_id AND b.is_deleted = FALSE
              JOIN branch  root ON root.id = :branchId
             WHERE p.is_deleted = FALSE
               AND p.is_alive = TRUE
               AND b.path <@ root.path
             ORDER BY p.id
            """;

    private static final String SQL_CLAN_MEMBERS = """
            SELECT p.id AS person_id, u.id AS app_user_id, u.locale
              FROM person p
              JOIN app_user u ON u.person_id = p.id AND u.status = 'ACTIVE'
             WHERE p.is_deleted = FALSE
               AND p.is_alive = TRUE
             ORDER BY p.id
            """;

    private static final String SQL_BY_PERSON = """
            SELECT p.id AS person_id, u.id AS app_user_id, u.locale
              FROM person p
              JOIN app_user u ON u.person_id = p.id
             WHERE p.id = :personId
            """;

    private static final String SQL_BY_SUB = """
            SELECT u.person_id AS person_id, u.id AS app_user_id, u.locale
              FROM app_user u
             WHERE u.keycloak_sub = :sub
               AND u.person_id IS NOT NULL
            """;

    private static final RowMapper<Recipient> MAPPER = (rs, rowNum) -> new Recipient(
            rs.getObject("person_id", UUID.class),
            rs.getObject("app_user_id", UUID.class),
            rs.getString("locale"));

    private final NamedParameterJdbcTemplate jdbc;

    public RecipientDirectoryJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Recipient> membersOfBranch(UUID branchId, boolean clanLevel) {
        if (clanLevel) {
            return jdbc.query(SQL_CLAN_MEMBERS, new MapSqlParameterSource(), MAPPER);
        }
        if (branchId == null) {
            // Danh sách rỗng ở đây nghĩa là "không có ai", tuyệt đối không được diễn giải ngược
            // thành "tất cả mọi người". Bên gọi đã quy đổi trường hợp thiếu phạm vi thành clanLevel.
            return List.of();
        }
        return jdbc.query(SQL_BRANCH_MEMBERS, new MapSqlParameterSource("branchId", branchId), MAPPER);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Recipient> byPersonId(UUID personId) {
        if (personId == null) {
            return Optional.empty();
        }
        return first(jdbc.query(SQL_BY_PERSON, new MapSqlParameterSource("personId", personId), MAPPER));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Recipient> byKeycloakSub(String keycloakSub) {
        if (keycloakSub == null || keycloakSub.isBlank()) {
            return Optional.empty();
        }
        return first(jdbc.query(SQL_BY_SUB, new MapSqlParameterSource("sub", keycloakSub), MAPPER));
    }

    private static Optional<Recipient> first(List<Recipient> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
