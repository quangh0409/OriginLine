package vn.giapha.kinship.infrastructure;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.kinship.domain.PersonLookupPort;
import vn.giapha.kinship.domain.PersonView;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * Đọc bản chiếu nhân khẩu phục vụ suy luận danh xưng, bằng SQL chỉ-đọc trên bảng {@code person}.
 *
 * <p><b>Vì sao không gọi context {@code genealogy}:</b> ở Giai đoạn 1 context đó chưa công bố
 * application service tra cứu, và rule engine chỉ cần bốn cột ({@code gender},
 * {@code birth_order}, {@code birth_solar}, {@code is_deleted}) cộng tên hiển thị. Adapter này
 * <b>không import lớp nào</b> của {@code genealogy} nên ranh giới bounded context vẫn nguyên vẹn ở
 * mức mã nguồn; khi {@code genealogy} có service công khai thì chỉ đổi thân hàm ở đây.</p>
 *
 * <p><b>Riêng tư:</b> {@code display_name} lấy nguyên tên đã lưu. Bộ lọc phân tầng hiển thị
 * (W6, BA v2 §10) phải được áp ở tầng API trước khi trả cho người dùng — đừng coi lớp này đã lọc.</p>
 */
@Repository
public class PersonLookupJdbcAdapter implements PersonLookupPort {

    private static final String BASE_SQL = """
            SELECT p.id                AS id,
                   p.gender            AS gender,
                   p.generation        AS generation,
                   p.birth_order       AS birth_order,
                   p.birth_solar       AS birth_solar,
                   p.is_deleted        AS is_deleted,
                   p.primary_branch_id AS branch_id,
                   b.path::text        AS branch_path,
                   (SELECT pn.full_name
                      FROM person_name pn
                     WHERE pn.person_id = p.id
                     ORDER BY pn.is_primary DESC, pn.created_at ASC
                     LIMIT 1)          AS display_name
              FROM person p
              LEFT JOIN branch b ON b.id = p.primary_branch_id
             WHERE p.id IN (:ids)
            """;

    private static final RowMapper<PersonView> MAPPER = PersonLookupJdbcAdapter::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    public PersonLookupJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PersonView> byId(PersonId id) {
        if (id == null) {
            return Optional.empty();
        }
        List<PersonView> rows = jdbc.query(BASE_SQL,
                new MapSqlParameterSource("ids", List.of(id.value())), MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<PersonId, PersonView> byIds(Collection<PersonId> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Set<UUID> uuids = new LinkedHashSet<>();
        for (PersonId id : ids) {
            if (id != null) {
                uuids.add(id.value());
            }
        }
        if (uuids.isEmpty()) {
            return Map.of();
        }
        List<PersonView> rows = jdbc.query(BASE_SQL, new MapSqlParameterSource("ids", uuids), MAPPER);
        Map<PersonId, PersonView> result = new HashMap<>(rows.size());
        for (PersonView view : rows) {
            result.put(view.id(), view);
        }
        return result;
    }

    private static PersonView mapRow(ResultSet rs, int rowNum) throws SQLException {
        Date birthSolar = rs.getDate("birth_solar");
        Integer generation = (Integer) rs.getObject("generation");
        Integer birthOrder = (Integer) rs.getObject("birth_order");
        UUID branchId = rs.getObject("branch_id", UUID.class);
        return new PersonView(
                PersonId.of(rs.getObject("id", UUID.class)),
                Gender.fromCode(rs.getString("gender")),
                generation,
                birthOrder,
                birthSolar == null ? null : birthSolar.toLocalDate(),
                rs.getBoolean("is_deleted"),
                rs.getString("display_name"),
                branchId,
                rs.getString("branch_path"));
    }
}
