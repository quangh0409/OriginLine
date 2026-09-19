package vn.giapha.membership.infrastructure.invite;

import java.sql.Timestamp;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.membership.domain.port.InviteThrottlePort;

/**
 * Hiện thực {@link InviteThrottlePort} trên bảng {@code invitation_attempt}.
 *
 * <h2>{@link Propagation#REQUIRES_NEW} là cả điểm của lớp này</h2>
 * Lần thử cần ghi lại được <b>chính lúc</b> nó thất bại, mà thất bại thì nơi gọi sắp ném ngoại lệ
 * và transaction bao ngoài sắp rollback. Nếu dòng đếm nằm trong transaction ấy thì nó rollback
 * theo, và bộ đếm sẽ mãi mãi bằng không — tức là giới hạn tần suất trông như đang chạy mà không
 * chặn gì cả. Đây đúng là loại lỗi không ai phát hiện ra cho tới lúc cần tới nó.
 *
 * <h2>Dọn rác tại chỗ, không cần job</h2>
 * Mỗi lần ghi xoá luôn các dòng quá {@value #RETENTION_HOURS} giờ. Ở quy mô này (vài chục lượt một
 * ngày cho cả dòng họ) phép xoá ấy rẻ hơn nhiều so với việc phải nhớ đăng ký, giám sát và sửa một
 * job nền chỉ để dọn một bảng đếm.
 */
@Repository
public class InviteThrottleJdbcAdapter implements InviteThrottlePort {

    private static final Logger log = LoggerFactory.getLogger(InviteThrottleJdbcAdapter.class);

    /** Giữ đủ dài để điều tra một đợt dò, đủ ngắn để bảng không phình. */
    private static final int RETENTION_HOURS = 24;

    private static final String SQL_COUNT = """
            SELECT count(*) FROM invitation_attempt
             WHERE client_key = :key
               AND outcome = 'FAILED'
               AND at >= :since
            """;

    private static final String SQL_INSERT = """
            INSERT INTO invitation_attempt (client_key, outcome) VALUES (:key, :outcome)
            """;

    private static final String SQL_PRUNE = """
            DELETE FROM invitation_attempt
             WHERE at < now() - CAST(:hours || ' hours' AS interval)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public InviteThrottleJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public int failuresSince(String clientKeyHash, Instant since) {
        Integer count = jdbc.queryForObject(SQL_COUNT, new MapSqlParameterSource()
                .addValue("key", clientKeyHash)
                .addValue("since", Timestamp.from(since)), Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String clientKeyHash, String outcome) {
        jdbc.update(SQL_INSERT, new MapSqlParameterSource()
                .addValue("key", clientKeyHash)
                .addValue("outcome", outcome));
        int pruned = jdbc.update(SQL_PRUNE,
                new MapSqlParameterSource("hours", String.valueOf(RETENTION_HOURS)));
        if (pruned > 0) {
            log.debug("Don {} dong invitation_attempt qua han", pruned);
        }
    }
}
