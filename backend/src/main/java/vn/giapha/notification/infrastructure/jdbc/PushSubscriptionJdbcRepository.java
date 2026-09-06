package vn.giapha.notification.infrastructure.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.notification.domain.PushSubscription;
import vn.giapha.notification.domain.port.PushSubscriptionRepository;

/**
 * Hiện thực {@link PushSubscriptionRepository} trên bảng {@code push_subscription}.
 *
 * <h2>Khoá định danh là {@code endpoint}, không phải {@code id}</h2>
 * Chuẩn Web Push nói vậy, và chỉ mục {@code ux_push_subscription_endpoint} được đặt trên
 * {@code md5(endpoint)} — endpoint của FCM dài hơn giới hạn khoá btree nên không index thẳng được.
 * Hệ quả: {@code ON CONFLICT} phải viết là {@code ON CONFLICT (md5(endpoint))}, đúng biểu thức của
 * chỉ mục. Viết {@code ON CONFLICT (endpoint)} sẽ lỗi lúc chạy vì không có chỉ mục nào khớp.
 *
 * <p>Ghi lại cùng endpoint là <b>cập nhật</b> (khoá client có thể đã xoay vòng), đồng thời
 * {@code failure_count} về 0 và {@code is_active} bật lại: trình duyệt vừa đăng ký lại nghĩa là
 * thiết bị đang sống trở lại.</p>
 */
@Repository
public class PushSubscriptionJdbcRepository implements PushSubscriptionRepository {

    private static final Logger log = LoggerFactory.getLogger(PushSubscriptionJdbcRepository.class);

    private static final String COLUMNS = """
            id, app_user_id, endpoint, p256dh, auth, user_agent, is_active, failure_count,
            last_used_at, expires_at, created_at
            """;

    private static final String SQL_UPSERT = """
            INSERT INTO push_subscription (id, app_user_id, endpoint, p256dh, auth, user_agent,
                                           is_active, failure_count, expires_at)
            VALUES (:id, :appUserId, :endpoint, :p256dh, :auth, :userAgent, TRUE, 0, :expiresAt)
            ON CONFLICT (md5(endpoint)) DO UPDATE
               SET app_user_id   = EXCLUDED.app_user_id,
                   p256dh        = EXCLUDED.p256dh,
                   auth          = EXCLUDED.auth,
                   user_agent    = COALESCE(EXCLUDED.user_agent, push_subscription.user_agent),
                   expires_at    = EXCLUDED.expires_at,
                   is_active     = TRUE,
                   failure_count = 0
              RETURNING
            """ + COLUMNS + """
                   , (xmax = 0) AS was_inserted
            """;

    private static final String SQL_ACTIVE_BY_USER = """
            SELECT
            """ + COLUMNS + """
              FROM push_subscription
             WHERE app_user_id = :appUserId AND is_active
             ORDER BY created_at
            """;

    private static final String SQL_OWNED = """
            SELECT
            """ + COLUMNS + """
              FROM push_subscription
             WHERE id = :id AND app_user_id = :appUserId
            """;

    private static final String SQL_DELETE_OWNED = """
            DELETE FROM push_subscription WHERE id = :id AND app_user_id = :appUserId
            """;

    private static final String SQL_DELETE_ENDPOINT = """
            DELETE FROM push_subscription WHERE md5(endpoint) = md5(:endpoint)
            """;

    private static final String SQL_TOUCH = """
            UPDATE push_subscription
               SET last_used_at = :when, failure_count = 0
             WHERE id = :id
            """;

    private static final String SQL_FAILURE = """
            UPDATE push_subscription
               SET failure_count = failure_count + 1
             WHERE id = :id
            """;

    private static final RowMapper<PushSubscription> MAPPER = (rs, rowNum) -> new PushSubscription(
            rs.getObject("id", UUID.class),
            rs.getObject("app_user_id", UUID.class),
            rs.getString("endpoint"),
            rs.getString("p256dh"),
            rs.getString("auth"),
            rs.getString("user_agent"),
            rs.getBoolean("is_active"),
            rs.getInt("failure_count"),
            toInstant(rs.getTimestamp("last_used_at")),
            toInstant(rs.getTimestamp("expires_at")),
            toInstant(rs.getTimestamp("created_at")));

    private final NamedParameterJdbcTemplate jdbc;

    public PushSubscriptionJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Upsert save(UUID appUserId, String endpoint, String p256dh, String auth, String userAgent,
                       Instant expiresAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("appUserId", appUserId)
                .addValue("endpoint", endpoint)
                .addValue("p256dh", p256dh)
                .addValue("auth", auth)
                .addValue("userAgent", trimTo(userAgent, 255))
                .addValue("expiresAt", expiresAt == null ? null : Timestamp.from(expiresAt));
        // `xmax = 0` là mẹo chuẩn của Postgres để phân biệt INSERT với UPDATE trong một câu UPSERT:
        // dòng vừa được chèn mới có xmax bằng 0. Cần nó để trả đúng 201 hay 200 theo hợp đồng.
        return jdbc.query(SQL_UPSERT, params, (ResultSetExtractor<Upsert>) rs -> {
            if (!rs.next()) {
                throw new IllegalStateException("UPSERT push_subscription khong tra ve dong nao");
            }
            return new Upsert(MAPPER.mapRow(rs, 0), rs.getBoolean("was_inserted"));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<PushSubscription> activeByAppUser(UUID appUserId) {
        return jdbc.query(SQL_ACTIVE_BY_USER, new MapSqlParameterSource("appUserId", appUserId), MAPPER);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PushSubscription> findOwned(UUID id, UUID appUserId) {
        List<PushSubscription> found = jdbc.query(SQL_OWNED, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("appUserId", appUserId), MAPPER);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Override
    @Transactional
    public boolean deleteOwned(UUID id, UUID appUserId) {
        return jdbc.update(SQL_DELETE_OWNED, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("appUserId", appUserId)) > 0;
    }

    @Override
    @Transactional
    public boolean deleteByEndpoint(String endpoint) {
        boolean deleted = jdbc.update(SQL_DELETE_ENDPOINT,
                new MapSqlParameterSource("endpoint", endpoint)) > 0;
        if (deleted) {
            log.info("Da xoa dang ky Web Push bi thu hoi (push service tra 404/410)");
        }
        return deleted;
    }

    @Override
    @Transactional
    public void touchLastUsed(UUID id, Instant when) {
        jdbc.update(SQL_TOUCH, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("when", Timestamp.from(when)));
    }

    @Override
    @Transactional
    public void recordFailure(UUID id) {
        jdbc.update(SQL_FAILURE, new MapSqlParameterSource("id", id));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
