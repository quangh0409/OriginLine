package vn.giapha.notification.infrastructure.jdbc;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.notification.domain.InboxEntry;
import vn.giapha.notification.domain.InboxItem;
import vn.giapha.notification.domain.NotificationCategory;
import vn.giapha.notification.domain.port.InboxPort;

/**
 * Hiện thực {@link InboxPort} trên bảng {@code notification_inbox}.
 *
 * <h2>Hai chỗ đáng chú ý</h2>
 * <ul>
 *   <li><b>Ghi là {@code INSERT ... ON CONFLICT DO NOTHING}</b> trên chỉ mục riêng phần
 *       {@code ux_notification_inbox_idempotency}. Đây là lớp chặn trùng thứ hai, độc lập với
 *       {@code notification_log}: hai lớp phòng thủ cho cùng một điều — người trong họ không được
 *       nhận hai tin cho cùng một cái giỗ.</li>
 *   <li><b>Đánh dấu đã đọc là {@code WHERE read_at IS NULL}</b>, nên gọi lại không ghi đè thời điểm
 *       đọc lần đầu. Câu {@code UPDATE} luôn kèm {@code recipient_person_id} — phân quyền nằm ngay
 *       trong mệnh đề {@code WHERE} chứ không ở một phép kiểm tra riêng dễ quên.</li>
 * </ul>
 *
 * <p>Danh sách nối thêm hai phép {@code LEFT JOIN} để lấy {@code personId} và
 * {@code reminderOffsetDays} mà hợp đồng OpenAPI yêu cầu — xem {@link InboxEntry}.</p>
 */
@Repository
public class InboxJdbcAdapter implements InboxPort {

    private static final String SQL_INSERT = """
            INSERT INTO notification_inbox (id, recipient_person_id, event_id, reminder_job_id,
                                            title, body, link_url, category)
            VALUES (:id, :personId, :eventId, :jobId, :title, :body, :linkUrl, :category)
            ON CONFLICT (reminder_job_id, recipient_person_id)
                 WHERE reminder_job_id IS NOT NULL
              DO NOTHING
            """;

    private static final String SELECT_COLUMNS = """
            SELECT ni.id, ni.recipient_person_id, ni.event_id, ni.reminder_job_id, ni.title, ni.body,
                   ni.link_url, ni.category, ni.created_at, ni.read_at,
                   e.person_id  AS subject_person_id,
                   rj.offset_days AS reminder_offset_days
              FROM notification_inbox ni
              LEFT JOIN event        e  ON e.id  = ni.event_id
              LEFT JOIN reminder_job rj ON rj.id = ni.reminder_job_id
            """;

    private static final String WHERE_OWNED_AND_FILTERED = """
             WHERE ni.recipient_person_id = :personId
               AND (:unreadOnly = FALSE OR ni.read_at IS NULL)
               AND (:readOnly   = FALSE OR ni.read_at IS NOT NULL)
               AND (CAST(:category AS varchar) IS NULL OR ni.category = CAST(:category AS varchar))
            """;

    private static final String SQL_PAGE = SELECT_COLUMNS + WHERE_OWNED_AND_FILTERED + """
             ORDER BY ni.created_at DESC, ni.id DESC
             LIMIT :limit OFFSET :offset
            """;

    private static final String SQL_COUNT = """
            SELECT count(*)
              FROM notification_inbox ni
            """ + WHERE_OWNED_AND_FILTERED;

    private static final String SQL_COUNT_UNREAD = """
            SELECT count(*)
              FROM notification_inbox ni
             WHERE ni.recipient_person_id = :personId
               AND ni.read_at IS NULL
            """;

    private static final String SQL_ONE = SELECT_COLUMNS + """
             WHERE ni.id = :id AND ni.recipient_person_id = :personId
            """;

    private static final String SQL_MARK_READ = """
            UPDATE notification_inbox
               SET read_at = now()
             WHERE id = :id
               AND recipient_person_id = :personId
               AND read_at IS NULL
            """;

    private static final String SQL_MARK_ALL_READ = """
            UPDATE notification_inbox
               SET read_at = now()
             WHERE recipient_person_id = :personId
               AND read_at IS NULL
            """;

    private static final RowMapper<InboxEntry> ENTRY_MAPPER = (rs, rowNum) -> {
        InboxItem item = new InboxItem(
                rs.getObject("id", UUID.class),
                rs.getObject("recipient_person_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getObject("reminder_job_id", UUID.class),
                rs.getString("title"),
                rs.getString("body"),
                rs.getString("link_url"),
                NotificationCategory.fromDbValue(rs.getString("category")),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("read_at")));
        return new InboxEntry(item,
                rs.getObject("subject_person_id", UUID.class),
                (Integer) rs.getObject("reminder_offset_days"));
    };

    private final NamedParameterJdbcTemplate jdbc;

    public InboxJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(InboxItem item) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", item.id() == null ? UUID.randomUUID() : item.id())
                .addValue("personId", item.recipientPersonId())
                .addValue("eventId", item.eventId())
                .addValue("jobId", item.reminderJobId())
                .addValue("title", item.title())
                .addValue("body", item.body() == null ? "" : item.body())
                .addValue("linkUrl", item.linkUrl())
                .addValue("category", item.category().name());
        return jdbc.update(SQL_INSERT, params) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InboxEntry> findPage(UUID recipientPersonId, ReadFilter filter,
                                     NotificationCategory category, int page, int size) {
        MapSqlParameterSource params = filterParams(recipientPersonId, filter, category)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        return jdbc.query(SQL_PAGE, params, ENTRY_MAPPER);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPage(UUID recipientPersonId, ReadFilter filter, NotificationCategory category) {
        Long count = jdbc.queryForObject(SQL_COUNT,
                filterParams(recipientPersonId, filter, category), Long.class);
        return count == null ? 0L : count;
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(UUID recipientPersonId) {
        Long count = jdbc.queryForObject(SQL_COUNT_UNREAD,
                new MapSqlParameterSource("personId", recipientPersonId), Long.class);
        return count == null ? 0L : count;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InboxItem> findOwned(UUID id, UUID recipientPersonId) {
        return findEntry(id, recipientPersonId).map(InboxEntry::item);
    }

    @Override
    @Transactional
    public Optional<InboxItem> markRead(UUID id, UUID recipientPersonId) {
        // Không kiểm tra kết quả UPDATE để quyết định 404: 0 dòng cũng có thể nghĩa là "đã đọc rồi",
        // và đó là lời gọi hợp lệ. Đọc lại bản ghi mới phân biệt được "không có" với "đã đọc".
        jdbc.update(SQL_MARK_READ, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("personId", recipientPersonId));
        return findOwned(id, recipientPersonId);
    }

    @Override
    @Transactional
    public int markAllRead(UUID recipientPersonId) {
        return jdbc.update(SQL_MARK_ALL_READ,
                new MapSqlParameterSource("personId", recipientPersonId));
    }

    private Optional<InboxEntry> findEntry(UUID id, UUID recipientPersonId) {
        List<InboxEntry> found = jdbc.query(SQL_ONE, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("personId", recipientPersonId), ENTRY_MAPPER);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private static MapSqlParameterSource filterParams(UUID recipientPersonId, ReadFilter filter,
                                                      NotificationCategory category) {
        return new MapSqlParameterSource()
                .addValue("personId", recipientPersonId)
                .addValue("unreadOnly", filter == ReadFilter.UNREAD)
                .addValue("readOnly", filter == ReadFilter.READ)
                .addValue("category", category == null ? null : category.name());
    }

    private static java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
