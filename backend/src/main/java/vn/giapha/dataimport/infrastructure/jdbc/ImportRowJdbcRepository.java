package vn.giapha.dataimport.infrastructure.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.dataimport.domain.CellCodec;
import vn.giapha.dataimport.domain.LunarDeathDate;
import vn.giapha.dataimport.domain.MarriageRow;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.PlannedAction;
import vn.giapha.dataimport.domain.port.ImportRowRepository;
import vn.giapha.shared.vo.Gender;

/**
 * Hiện thực {@link ImportRowRepository}: ghi và đọc các dòng chờ theo lô.
 *
 * <h2>Xoá sạch rồi ghi lại, không ghép thêm từng phần</h2>
 * Một lô là <b>một ảnh chụp của một tệp</b>. Ghép thêm dòng vào một lô đã có nghĩa là lô ấy không
 * còn tương ứng với tệp nào cả, và mọi phép đối chiếu về sau ("lô này sinh ra những ai") mất chỗ
 * dựa. Tải một tệp đã sửa lên thì sinh lô mới, không sửa lô cũ.
 *
 * <h2>Giữ cả raw lẫn normalized</h2>
 * Chép hai lần cùng một nội dung trông thừa, nhưng {@code raw} là nguyên văn ô gốc và là
 * <b>bằng chứng duy nhất</b> khi ba tháng sau có tranh cãi "tôi gõ đúng mà" — nó phân biệt "họ chép
 * sai sổ" với "ta phân tích sai tệp", hai chuyện có cách chữa hoàn toàn khác nhau.
 */
@Repository
public class ImportRowJdbcRepository implements ImportRowRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private static final String INSERT_PERSON = """
            INSERT INTO import_person_row (batch_id, row_no, external_code, raw, normalized,
                    full_name, taboo_name, posthumous_name, han_nom_name, gender, generation,
                    father_code, mother_code, parent_rel, is_alive, birth_year,
                    death_lunar_day, death_lunar_month, death_lunar_leap, death_lunar_year,
                    native_place, native_place_code, heir_of_code, heir_kind, planned_action)
            VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_MARRIAGE = """
            INSERT INTO import_marriage_row (batch_id, row_no, husband_code, wife_code,
                    spouse_order, valid_from_year, valid_to_year, end_reason, raw)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
            """;

    private final JdbcTemplate jdbc;

    public ImportRowJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void replacePersonRows(UUID batchId, List<PersonRow> rows) {
        jdbc.update("DELETE FROM import_person_row WHERE batch_id = ?", batchId);
        if (rows.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT_PERSON, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PersonRow r = rows.get(i);
                int c = 1;
                ps.setObject(c++, batchId);
                ps.setInt(c++, r.rowNo());
                ps.setString(c++, r.externalCode() == null ? "" : r.externalCode());
                ps.setString(c++, toJson(r.raw()));
                ps.setString(c++, toJson(r.normalized()));
                ps.setString(c++, r.fullName());
                ps.setString(c++, r.tabooName());
                ps.setString(c++, r.posthumousName());
                ps.setString(c++, r.hanNomName());
                ps.setString(c++, r.gender() == null ? null : r.gender().name());
                setInt(ps, c++, r.generation());
                ps.setString(c++, r.fatherCode());
                ps.setString(c++, r.motherCode());
                ps.setString(c++, r.parentRel() == null ? null : r.parentRel().name());
                if (r.alive() == null) {
                    ps.setNull(c++, Types.BOOLEAN);
                } else {
                    ps.setBoolean(c++, r.alive());
                }
                setInt(ps, c++, r.birthYear());
                LunarDeathDate d = r.death();
                setInt(ps, c++, d == null ? null : d.day());
                setInt(ps, c++, d == null ? null : d.month());
                ps.setBoolean(c++, d != null && d.leap());
                setInt(ps, c++, d == null ? null : d.year());
                ps.setString(c++, r.nativePlace());
                ps.setString(c++, r.nativePlaceCode());
                ps.setString(c++, r.heirOfCode());
                ps.setString(c++, r.heirType() == null ? null : r.heirType().name());
                ps.setString(c, r.plannedAction().name());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    @Override
    @Transactional
    public void replaceMarriageRows(UUID batchId, List<MarriageRow> rows) {
        jdbc.update("DELETE FROM import_marriage_row WHERE batch_id = ?", batchId);
        if (rows.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT_MARRIAGE, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                MarriageRow r = rows.get(i);
                int c = 1;
                ps.setObject(c++, batchId);
                ps.setInt(c++, r.rowNo());
                ps.setString(c++, r.husbandCode());
                ps.setString(c++, r.wifeCode());
                setInt(ps, c++, r.spouseOrder());
                setInt(ps, c++, r.validFromYear());
                setInt(ps, c++, r.validToYear());
                ps.setString(c++, r.endReason() == null ? null : r.endReason().name());
                ps.setString(c, toJson(r.raw()));
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    @Override
    public List<PersonRow> personRows(UUID batchId) {
        return jdbc.query("""
                SELECT * FROM import_person_row WHERE batch_id = ? ORDER BY row_no
                """, PERSON_MAPPER, batchId);
    }

    @Override
    public List<MarriageRow> marriageRows(UUID batchId) {
        return jdbc.query("""
                SELECT * FROM import_marriage_row WHERE batch_id = ? ORDER BY row_no
                """, MARRIAGE_MAPPER, batchId);
    }

    @Override
    @Transactional
    public void updateResolutions(UUID batchId, List<PersonRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                UPDATE import_person_row
                   SET resolved_person_id = ?, planned_action = ?
                 WHERE batch_id = ? AND row_no = ?
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PersonRow r = rows.get(i);
                ps.setObject(1, r.resolvedPersonId());
                ps.setString(2, r.plannedAction().name());
                ps.setObject(3, batchId);
                ps.setInt(4, r.rowNo());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    private static void setInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, value);
        }
    }

    private static String toJson(Map<String, String> map) {
        try {
            return JSON.writeValueAsString(map == null ? Map.of() : map);
        } catch (Exception ex) {
            throw new IllegalStateException("Khong serialize duoc o goc sang jsonb", ex);
        }
    }

    private static Map<String, String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, STRING_MAP);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private static final RowMapper<PersonRow> PERSON_MAPPER = (rs, i) -> {
        Integer day = (Integer) rs.getObject("death_lunar_day");
        Integer month = (Integer) rs.getObject("death_lunar_month");
        LunarDeathDate death = (day == null || month == null) ? null
                : new LunarDeathDate((Integer) rs.getObject("death_lunar_year"), month, day,
                        rs.getBoolean("death_lunar_leap"));
        String gender = rs.getString("gender");
        String parentRel = rs.getString("parent_rel");
        String heirKind = rs.getString("heir_kind");
        String code = rs.getString("external_code");
        return new PersonRow(
                rs.getInt("row_no"),
                code == null || code.isEmpty() ? null : code,
                fromJson(rs.getString("raw")),
                fromJson(rs.getString("normalized")),
                rs.getString("full_name"),
                rs.getString("taboo_name"),
                rs.getString("posthumous_name"),
                rs.getString("han_nom_name"),
                gender == null ? Gender.UNKNOWN : Gender.valueOf(gender),
                (Integer) rs.getObject("generation"),
                rs.getString("father_code"),
                rs.getString("mother_code"),
                parentRel == null ? null : CellCodec.ParentRel.valueOf(parentRel),
                (Boolean) rs.getObject("is_alive"),
                (Integer) rs.getObject("birth_year"),
                death,
                rs.getString("native_place"),
                rs.getString("native_place_code"),
                rs.getString("heir_of_code"),
                heirKind == null ? null : CellCodec.HeirType.valueOf(heirKind),
                rs.getObject("resolved_person_id", UUID.class),
                PlannedAction.valueOf(rs.getString("planned_action")));
    };

    private static final RowMapper<MarriageRow> MARRIAGE_MAPPER = (rs, i) -> {
        String endReason = rs.getString("end_reason");
        return new MarriageRow(
                rs.getInt("row_no"),
                rs.getString("husband_code"),
                rs.getString("wife_code"),
                (Integer) rs.getObject("spouse_order"),
                (Integer) rs.getObject("valid_from_year"),
                (Integer) rs.getObject("valid_to_year"),
                endReason == null ? null : CellCodec.EndReason.valueOf(endReason),
                fromJson(rs.getString("raw")));
    };
}
