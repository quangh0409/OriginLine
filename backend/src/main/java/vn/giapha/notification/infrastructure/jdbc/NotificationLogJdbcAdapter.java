package vn.giapha.notification.infrastructure.jdbc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.NotificationStatus;
import vn.giapha.notification.domain.port.NotificationLogPort;

/**
 * Hiện thực {@link NotificationLogPort} — nhật ký gửi <b>và</b> chốt chống trùng của consumer.
 *
 * <h2>Vì sao {@code ON CONFLICT} phải mang cả mệnh đề {@code WHERE}</h2>
 * {@code ux_notification_log_idempotency} là chỉ mục duy nhất <b>riêng phần</b>
 * ({@code WHERE reminder_job_id IS NOT NULL}). Postgres chỉ khớp một {@code ON CONFLICT} với chỉ mục
 * riêng phần khi câu lệnh lặp lại đúng vị từ ấy; thiếu nó thì lỗi
 * {@code there is no unique or exclusion constraint matching the ON CONFLICT specification} — và lỗi
 * này chỉ xuất hiện lúc chạy thật, không lúc biên dịch.
 *
 * <h2>{@code REQUIRES_NEW}: bắt buộc, không phải trang trí</h2>
 * Phép giành quyền phải <b>commit ngay</b> để consumer thứ hai nhìn thấy. Nằm chung transaction với
 * lời gọi gateway thì hoặc dòng chưa commit khi bản sao tới (chống trùng vô tác dụng), hoặc
 * connection Postgres bị giữ suốt thời gian chờ mạng.
 *
 * <p>Tin không có {@code reminder_job_id} (thông báo hệ thống, không sinh từ lịch nhắc) thì
 * <b>không có</b> khoá chống trùng — chỉ mục riêng phần không áp. Đó là quyết định của schema và
 * lớp này không giả vờ ngược lại: nó ghi một dòng mới mỗi lần.</p>
 */
@Repository
public class NotificationLogJdbcAdapter implements NotificationLogPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationLogJdbcAdapter.class);

    private static final String SQL_CLAIM = """
            INSERT INTO notification_log (id, reminder_job_id, recipient_person_id, channel,
                                          status, retry_count)
            VALUES (:id, :jobId, :personId, :channel, 'PENDING', 0)
            ON CONFLICT (reminder_job_id, recipient_person_id, channel)
                 WHERE reminder_job_id IS NOT NULL
              DO NOTHING
            """;

    private static final String SQL_INSERT_UNKEYED = """
            INSERT INTO notification_log (id, reminder_job_id, recipient_person_id, channel,
                                          status, retry_count)
            VALUES (:id, NULL, :personId, :channel, 'PENDING', 0)
            """;

    private static final String SQL_EXISTING = """
            SELECT id, status, retry_count
              FROM notification_log
             WHERE reminder_job_id = :jobId
               AND recipient_person_id = :personId
               AND channel = :channel
            """;

    private static final String SQL_REOPEN = """
            UPDATE notification_log
               SET status = 'PENDING',
                   retry_count = retry_count + 1
             WHERE id = :id
            """;

    private static final String SQL_COMPLETE = """
            UPDATE notification_log
               SET status = :status,
                   provider = :provider,
                   provider_msg_id = :providerMsgId,
                   error = :error,
                   sent_at = CASE WHEN :status = 'SENT' THEN now() ELSE sent_at END
             WHERE id = :id
            """;

    private static final String SQL_DEAD_LETTER = """
            UPDATE notification_log
               SET status = 'DEAD_LETTER',
                   error = :error
             WHERE reminder_job_id = :jobId
               AND recipient_person_id = :personId
               AND channel = :channel
            """;

    private static final String SQL_STATUS = """
            SELECT status
              FROM notification_log
             WHERE reminder_job_id = :jobId
               AND recipient_person_id = :personId
               AND channel = :channel
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationLogJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(UUID reminderJobId, UUID recipientPersonId, Channel channel) {
        UUID logId = UUID.randomUUID();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", logId)
                .addValue("jobId", reminderJobId)
                .addValue("personId", recipientPersonId)
                .addValue("channel", channel.name());

        if (reminderJobId == null) {
            jdbc.update(SQL_INSERT_UNKEYED, params);
            return new Claim(logId, true, false, 0);
        }

        if (jdbc.update(SQL_CLAIM, params) > 0) {
            return new Claim(logId, true, false, 0);
        }

        List<Map<String, Object>> existing = jdbc.queryForList(SQL_EXISTING, params);
        if (existing.isEmpty()) {
            // Không chèn được mà cũng không đọc thấy: một consumer khác vừa chèn và chưa commit.
            // Bỏ qua lượt này — tin sẽ được giao lại, hoặc bản kia sẽ gửi. Ghi log để nếu chuyện này
            // xảy ra thường xuyên thì biết mà xem lại prefetch.
            log.debug("Tranh chap khi gianh quyen gui job={} nguoi nhan={} kenh={} - bo qua luot nay",
                    reminderJobId, recipientPersonId, channel);
            return Claim.skip();
        }

        Map<String, Object> row = existing.get(0);
        NotificationStatus status = NotificationStatus.fromDbValue((String) row.get("status"));
        if (status.isFinal()) {
            return Claim.skip();
        }
        UUID existingId = (UUID) row.get("id");
        int retryCount = ((Number) row.get("retry_count")).intValue() + 1;
        jdbc.update(SQL_REOPEN, new MapSqlParameterSource("id", existingId));
        return new Claim(existingId, false, false, retryCount);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID logId, NotificationStatus status, String providerMessageId, String error) {
        if (logId == null) {
            return;
        }
        jdbc.update(SQL_COMPLETE, new MapSqlParameterSource()
                .addValue("id", logId)
                .addValue("status", status.name())
                // `provider` để trống ở Giai đoạn 1: mỗi kênh mới có đúng một adapter nên cột này
                // chưa phân biệt được gì. Giai đoạn 2 có hai nhà cung cấp SMS thì mới có ý nghĩa.
                .addValue("provider", null)
                .addValue("providerMsgId", providerMessageId)
                .addValue("error", error));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDeadLetter(UUID reminderJobId, UUID recipientPersonId, Channel channel, String error) {
        if (reminderJobId == null) {
            return;
        }
        jdbc.update(SQL_DEAD_LETTER, new MapSqlParameterSource()
                .addValue("jobId", reminderJobId)
                .addValue("personId", recipientPersonId)
                .addValue("channel", channel.name())
                .addValue("error", error));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationStatus> statusOf(UUID reminderJobId, UUID recipientPersonId,
                                                 Channel channel) {
        List<String> found = jdbc.queryForList(SQL_STATUS, new MapSqlParameterSource()
                .addValue("jobId", reminderJobId)
                .addValue("personId", recipientPersonId)
                .addValue("channel", channel.name()), String.class);
        return found.isEmpty() ? Optional.empty()
                : Optional.of(NotificationStatus.fromDbValue(found.get(0)));
    }
}
