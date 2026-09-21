package vn.giapha.events.infrastructure.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.events.domain.ReminderJob;
import vn.giapha.events.domain.ReminderStatus;
import vn.giapha.events.domain.port.ReminderJobRepository;

/**
 * Hiện thực {@link ReminderJobRepository} bằng SQL thuần.
 *
 * <h2>Vì sao không dùng JPA ở đây</h2>
 * Bảng này cần đúng hai thứ mà JPA không diễn đạt được sạch sẽ:
 * <ul>
 *   <li>{@code INSERT ... ON CONFLICT DO NOTHING} — chống trùng phải do <b>cơ sở dữ liệu</b> quyết,
 *       không phải do một phép {@code SELECT} trước đó ở tầng Java. Hai instance scheduler cùng chạy
 *       sẽ cùng đọc thấy "chưa có" rồi cùng ghi;</li>
 *   <li>{@code UPDATE ... FROM (SELECT ... FOR UPDATE SKIP LOCKED) RETURNING} — giành job trong
 *       <b>một</b> câu lệnh. {@code SKIP LOCKED} cho instance thứ hai đi lấy job khác thay vì nằm
 *       chờ khoá; không có nó thì mùa giỗ hàng nghìn job sẽ chạy tuần tự như chỉ có một máy.</li>
 * </ul>
 */
@Repository
public class ReminderJobJdbcRepository implements ReminderJobRepository {

    private static final String SQL_INSERT = """
            INSERT INTO reminder_job (id, event_id, occurrence_year, due_solar_date, offset_days,
                                      fire_at, status, attempt_count)
            VALUES (:id, :eventId, :occurrenceYear, :dueSolarDate, :offsetDays,
                    :fireAt, :status, 0)
            ON CONFLICT (event_id, occurrence_year, offset_days) DO NOTHING
            """;

    private static final String SQL_CLAIM_DUE = """
            UPDATE reminder_job AS j
               SET status = 'QUEUED',
                   attempt_count = j.attempt_count + 1,
                   dispatched_at = :now
              FROM (
                    SELECT id
                      FROM reminder_job
                     WHERE status = 'PENDING'
                       AND fire_at <= :now
                     ORDER BY fire_at
                     LIMIT :limit
                     FOR UPDATE SKIP LOCKED
                   ) AS due
             WHERE j.id = due.id
         RETURNING j.id, j.event_id, j.occurrence_year, j.due_solar_date, j.offset_days,
                   j.fire_at, j.status, j.attempt_count, j.last_error, j.dispatched_at
            """;

    /**
     * Hai trang thai, khong phai mot. Xem javadoc cua
     * {@code ReminderJobRepository#deleteUnsentByEvent}: mot dong CANCELLED van chiem khoa
     * ux_reminder_job_occurrence, nen bo sot no la lam luot sinh ke tiep im lang khong ghi gi.
     */
    private static final String SQL_DELETE_UNSENT = """
            DELETE FROM reminder_job
             WHERE event_id = :eventId
               AND status IN ('PENDING', 'CANCELLED')
            """;

    private static final String SQL_MARK_STATUS = """
            UPDATE reminder_job
               SET status = :status,
                   last_error = :error
             WHERE id = :id
            """;

    private static final RowMapper<ReminderJob> ROW_MAPPER = (rs, rowNum) -> new ReminderJob(
            rs.getObject("id", UUID.class),
            rs.getObject("event_id", UUID.class),
            rs.getInt("occurrence_year"),
            rs.getObject("due_solar_date", LocalDate.class),
            rs.getInt("offset_days"),
            toInstant(rs.getTimestamp("fire_at")),
            ReminderStatus.fromDbValue(rs.getString("status")),
            rs.getInt("attempt_count"),
            rs.getString("last_error"),
            toInstant(rs.getTimestamp("dispatched_at")));

    private final NamedParameterJdbcTemplate jdbc;

    public ReminderJobJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(ReminderJob job) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", job.id())
                .addValue("eventId", job.eventId())
                .addValue("occurrenceYear", job.occurrenceYear())
                .addValue("dueSolarDate", job.dueSolarDate())
                .addValue("offsetDays", job.offsetDays())
                .addValue("fireAt", Timestamp.from(job.fireAt()))
                .addValue("status", job.status().name());
        return jdbc.update(SQL_INSERT, params) > 0;
    }

    @Override
    @Transactional
    public List<ReminderJob> claimDue(Instant now, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("limit", Math.max(1, limit));
        return jdbc.query(SQL_CLAIM_DUE, params, ROW_MAPPER);
    }

    /**
     * {@code ix_reminder_job_event_unsent} (V20) là chỉ mục riêng phần đỡ đúng câu này — không có
     * nó thì mỗi lần sửa một sự kiện là một lượt quét toàn bảng {@code reminder_job}. Vị từ của
     * chỉ mục phải <b>khớp đúng</b> mệnh đề {@code WHERE} ở trên: bản V18 chỉ phủ {@code PENDING},
     * nên khi câu lệnh mở rộng sang {@code CANCELLED} thì chỉ mục cũ thôi dùng được.
     */
    @Override
    @Transactional
    public int deleteUnsentByEvent(UUID eventId) {
        if (eventId == null) {
            return 0;
        }
        return jdbc.update(SQL_DELETE_UNSENT, new MapSqlParameterSource("eventId", eventId));
    }

    @Override
    @Transactional
    public void markStatus(UUID jobId, ReminderStatus status, String error) {
        jdbc.update(SQL_MARK_STATUS, new MapSqlParameterSource()
                .addValue("id", jobId)
                .addValue("status", status.name())
                .addValue("error", truncate(error)));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /** {@code last_error} là TEXT, nhưng không có lý do gì để nhét cả stack trace vào đó. */
    private static String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String flat = value.replaceAll("\\s+", " ").trim();
        return flat.length() <= 500 ? flat : flat.substring(0, 500);
    }
}
