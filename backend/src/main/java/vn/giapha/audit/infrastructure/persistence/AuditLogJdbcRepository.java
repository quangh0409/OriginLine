package vn.giapha.audit.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.giapha.audit.domain.AuditActor;
import vn.giapha.audit.domain.AuditEntry;
import vn.giapha.audit.domain.RequestFingerprint;
import vn.giapha.audit.domain.port.AuditLogRepository;

/**
 * Hiện thực {@link AuditLogRepository} bằng SQL thuần.
 *
 * <h2>Vì sao {@code JdbcTemplate} chứ không JPA</h2>
 * {@code audit_log} không có {@code updated_at}/{@code version} và có trigger chặn UPDATE — nó là
 * bảng <b>chỉ ghi thêm</b>. Đưa nó thành entity JPA là mời gọi Hibernate làm đúng những việc mà
 * bảng này cấm: dirty-checking, merge, cascade. Thêm nữa khoá chính là {@code IDENTITY} kiểu
 * {@code BIGINT}, {@code changed_fields} là {@code TEXT[]} và {@code ip_address} là {@code inet} —
 * cả ba đều khiến JPA rườm rà hơn một câu INSERT.
 *
 * <h2>{@code ip_address} là {@code inet}, không phải text</h2>
 * Tham số được gửi ở dạng {@code unknown} rồi ép bằng {@code CAST(? AS inet)}; Postgres tự phân
 * giải cả IPv4 lẫn IPv6. Một chuỗi không phải IP hợp lệ sẽ bị CSDL từ chối và giết cả transaction,
 * nên {@link #normalizeIp} sàng trước ở phía Java: dấu vết HTTP là thứ phụ, nó không được phép làm
 * hỏng giao dịch nghiệp vụ mà nó đang ghi vết.
 */
@Repository
public class AuditLogJdbcRepository implements AuditLogRepository {

    private static final String SQL_INSERT = """
            INSERT INTO audit_log (entity_type, entity_id, action,
                                   actor_user_id, actor_person_id,
                                   before, after, changed_fields,
                                   ip_address, user_agent, request_id, note)
            VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?,
                    CAST(? AS inet), ?, ?, ?)
            """;

    private static final String SQL_BY_ENTITY = """
            SELECT id, entity_type, entity_id, action, actor_user_id, actor_person_id,
                   at, changed_fields, request_id, note
              FROM audit_log
             WHERE entity_type = ? AND entity_id = ?
             ORDER BY at DESC, id DESC
             LIMIT ? OFFSET ?
            """;

    private static final String SQL_BY_ACTOR = """
            SELECT id, entity_type, entity_id, action, actor_user_id, actor_person_id,
                   at, changed_fields, request_id, note
              FROM audit_log
             WHERE actor_user_id = ?
             ORDER BY at DESC, id DESC
             LIMIT ? OFFSET ?
            """;

    private static final RowMapper<AuditLogRow> ROW_MAPPER = (ResultSet rs, int rowNum) ->
            new AuditLogRow(
                    rs.getLong("id"),
                    rs.getString("entity_type"),
                    rs.getString("entity_id"),
                    rs.getString("action"),
                    rs.getObject("actor_user_id", UUID.class),
                    rs.getObject("actor_person_id", UUID.class),
                    rs.getTimestamp("at") == null ? null : rs.getTimestamp("at").toInstant(),
                    readTextArray(rs.getArray("changed_fields")),
                    rs.getString("request_id"),
                    rs.getString("note"));

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditLogJdbcRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(AuditEntry entry, AuditActor actor, RequestFingerprint fingerprint) {
        AuditActor writer = actor == null ? AuditActor.system() : actor;
        RequestFingerprint trace = fingerprint == null ? RequestFingerprint.none() : fingerprint;

        String beforeJson = toJson(entry.before());
        String afterJson = toJson(entry.after());
        String[] fields = entry.changedFields().isEmpty()
                ? null
                : entry.changedFields().toArray(String[]::new);

        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(SQL_INSERT);
            ps.setString(1, entry.entityType());
            ps.setString(2, entry.entityId());
            ps.setString(3, entry.action().name());
            setUuid(ps, 4, writer.appUserId());
            setUuid(ps, 5, writer.personId());
            setNullable(ps, 6, beforeJson);
            setNullable(ps, 7, afterJson);
            if (fields == null) {
                ps.setNull(8, Types.ARRAY);
            } else {
                ps.setArray(8, connection.createArrayOf("text", fields));
            }
            setNullable(ps, 9, normalizeIp(trace.ipAddress()));
            setNullable(ps, 10, trace.userAgent());
            setNullable(ps, 11, trace.requestId());
            setNullable(ps, 12, entry.note());
            return ps;
        });
    }

    @Override
    public List<AuditLogRow> byEntity(String entityType, String entityId, int limit, int offset) {
        return jdbc.query(SQL_BY_ENTITY, ROW_MAPPER, entityType, entityId, limit, offset);
    }

    @Override
    public List<AuditLogRow> byActor(UUID appUserId, int limit, int offset) {
        return jdbc.query(SQL_BY_ACTOR, ROW_MAPPER, appUserId, limit, offset);
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ
    // -------------------------------------------------------------------------------------

    private static List<String> readTextArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object raw = array.getArray();
        if (raw instanceof String[] values) {
            return List.of(values);
        }
        return List.of();
    }

    private String toJson(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            // Khong dua noi dung snapshot vao thong diep loi: no chinh la thu co the chua du lieu ca nhan.
            throw new IllegalStateException("Khong serialize duoc anh chup audit thanh jsonb", ex);
        }
    }

    /**
     * Chỉ nhận thứ trông giống một địa chỉ IP; mọi thứ khác thành {@code null}.
     *
     * <p>Header {@code X-Forwarded-For} có thể mang cả danh sách proxy, một cổng ở cuối, hoặc rác
     * do client tự đặt. Cột {@code inet} sẽ từ chối chúng, và một câu INSERT bị từ chối ở đây sẽ
     * kéo đổ cả giao dịch phả hệ đang chạy.</p>
     */
    static String normalizeIp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        int comma = value.indexOf(',');
        if (comma >= 0) {
            value = value.substring(0, comma).trim();
        }
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            if (close > 1) {
                value = value.substring(1, close);
            }
        } else if (value.chars().filter(c -> c == ':').count() == 1) {
            // "203.0.113.9:52344" - IPv4 kem cong; IPv6 co nhieu dau hai cham nen khong dinh nham.
            value = value.substring(0, value.indexOf(':'));
        }
        if (value.isEmpty() || value.length() > 45) {
            return null;
        }
        boolean looksLikeIp = value.chars()
                .allMatch(c -> Character.isLetterOrDigit(c) || c == '.' || c == ':' || c == '%');
        return looksLikeIp && (value.indexOf('.') >= 0 || value.indexOf(':') >= 0) ? value : null;
    }

    private static void setUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    private static void setNullable(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}
