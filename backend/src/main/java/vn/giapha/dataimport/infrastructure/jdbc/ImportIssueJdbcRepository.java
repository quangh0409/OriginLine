package vn.giapha.dataimport.infrastructure.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.port.ImportIssueRepository;

/**
 * Hiện thực {@link ImportIssueRepository}.
 *
 * <h2>Xoá sạch rồi ghi lại — không tích luỹ</h2>
 * Nếu lỗi của lần kiểm trước còn nằm lại, người nhập sửa xong, chạy lại, và vẫn thấy y nguyên
 * danh sách cũ. Họ sẽ kết luận bộ kiểm hỏng và thôi không đọc nữa — và từ lúc đó mọi cảnh báo,
 * kể cả cảnh báo đúng, đều vô giá trị. Đây là một quyết định về lòng tin, không phải về dữ liệu.
 *
 * <h2>Thứ tự đọc ra phải tất định</h2>
 * {@code ORDER BY sheet, row_no, code, field} — nghiệm thu đòi hai lần chạy trên cùng một tệp cho
 * danh sách giống hệt nhau, và một {@code ORDER BY} thiếu là đủ để bài kiểm ấy lúc xanh lúc đỏ.
 */
@Repository
public class ImportIssueJdbcRepository implements ImportIssueRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;

    public ImportIssueJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void replaceAll(UUID batchId, List<ImportIssue> issues) {
        jdbc.update("DELETE FROM import_issue WHERE batch_id = ?", batchId);
        if (issues.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                INSERT INTO import_issue (batch_id, severity, code, message, sheet, row_no, field,
                        context)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ImportIssue issue = issues.get(i);
                ps.setObject(1, batchId);
                ps.setString(2, issue.severity().name());
                ps.setString(3, issue.code().name());
                ps.setString(4, issue.message());
                ps.setString(5, issue.sheet());
                if (issue.rowNo() == null) {
                    ps.setNull(6, Types.INTEGER);
                } else {
                    ps.setInt(6, issue.rowNo());
                }
                ps.setString(7, issue.field());
                ps.setString(8, toJson(issue.context()));
            }

            @Override
            public int getBatchSize() {
                return issues.size();
            }
        });
    }

    /** Cùng một câu SELECT cho cả hai lối đọc — hai câu khác nhau sẽ lệch thứ tự sớm muộn. */
    private static final String SELECT_THEO_LO = """
            SELECT id, severity, code, message, sheet, row_no, field, context::text AS context
              FROM import_issue
             WHERE batch_id = ?
             ORDER BY sheet, row_no NULLS LAST, code, field NULLS FIRST, message
            """;

    @Override
    public List<ImportIssue> byBatch(UUID batchId) {
        return jdbc.query(SELECT_THEO_LO, MAPPER, batchId);
    }

    @Override
    public List<Luu> byBatchWithId(UUID batchId) {
        return jdbc.query(SELECT_THEO_LO, LUU_MAPPER, batchId);
    }

    @Override
    public int demTheoMa(UUID batchId, IssueCode code) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM import_issue WHERE batch_id = ? AND code = ?",
                Integer.class, batchId, code.name());
        return n == null ? 0 : n;
    }

    private static final RowMapper<ImportIssue> MAPPER = (rs, i) -> new ImportIssue(
            IssueCode.valueOf(rs.getString("code")),
            rs.getString("sheet"),
            (Integer) rs.getObject("row_no"),
            rs.getString("field"),
            rs.getString("message"),
            fromJson(rs.getString("context")));

    private static final RowMapper<Luu> LUU_MAPPER = (rs, i) ->
            new Luu(rs.getObject("id", UUID.class), MAPPER.mapRow(rs, i));

    private static String toJson(Map<String, Object> context) {
        try {
            return JSON.writeValueAsString(context == null ? Map.of() : context);
        } catch (Exception ex) {
            throw new IllegalStateException("Khong serialize duoc context cua loi nhap lieu", ex);
        }
    }

    private static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, JSON_MAP);
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
