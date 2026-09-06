package vn.giapha.notification.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.NotificationStatus;
import vn.giapha.notification.domain.port.NotificationLogPort;

/**
 * Bảng {@code notification_log} trong bộ nhớ — <b>và cùng lúc là chốt chống trùng</b>.
 *
 * <p>Chép đúng ngữ nghĩa của {@code NotificationLogJdbcAdapter.claim}: khoá là
 * {@code (reminder_job_id, recipient_person_id, channel)} (chỉ mục duy nhất riêng phần
 * {@code ux_notification_log_idempotency}); dòng đã ở trạng thái kết thúc thì lượt giao lại bị bỏ,
 * còn dòng {@code PENDING}/{@code FAILED} thì được gửi lại và {@code retry_count} tăng.</p>
 *
 * <p>Tin không có {@code reminderJobId} (thông báo gửi đích danh, chưa dùng ở Giai đoạn 1) không
 * tham gia khoá — đúng như mệnh đề {@code WHERE reminder_job_id IS NOT NULL} của chỉ mục.</p>
 */
public final class InMemoryNotificationLog implements NotificationLogPort {

    private final Map<String, Row> byKey = new LinkedHashMap<>();
    private final Map<UUID, Row> byId = new LinkedHashMap<>();

    /** Số lần {@code claim} được gọi — để đếm số lượt giao lại của RabbitMQ. */
    public int claimCalls;

    @Override
    public Claim claim(UUID reminderJobId, UUID recipientPersonId, Channel channel) {
        claimCalls++;
        if (reminderJobId == null) {
            Row row = newRow(null, recipientPersonId, channel);
            return new Claim(row.id, true, false, 0);
        }
        String key = key(reminderJobId, recipientPersonId, channel);
        Row existing = byKey.get(key);
        if (existing == null) {
            Row row = newRow(reminderJobId, recipientPersonId, channel);
            byKey.put(key, row);
            return new Claim(row.id, true, false, 0);
        }
        if (existing.status.isFinal()) {
            return Claim.skip();
        }
        existing.retryCount++;
        existing.status = NotificationStatus.PENDING;
        return new Claim(existing.id, false, false, existing.retryCount);
    }

    @Override
    public void complete(UUID logId, NotificationStatus status, String providerMessageId, String error) {
        if (logId == null) {
            return;
        }
        Row row = byId.get(logId);
        if (row != null) {
            row.status = status;
            row.providerMessageId = providerMessageId;
            row.error = error;
        }
    }

    @Override
    public void markDeadLetter(UUID reminderJobId, UUID recipientPersonId, Channel channel, String error) {
        if (reminderJobId == null) {
            return;
        }
        Row row = byKey.get(key(reminderJobId, recipientPersonId, channel));
        if (row == null) {
            row = newRow(reminderJobId, recipientPersonId, channel);
            byKey.put(key(reminderJobId, recipientPersonId, channel), row);
        }
        row.status = NotificationStatus.DEAD_LETTER;
        row.error = error;
    }

    @Override
    public Optional<NotificationStatus> statusOf(UUID reminderJobId, UUID recipientPersonId,
                                                 Channel channel) {
        Row row = byKey.get(key(reminderJobId, recipientPersonId, channel));
        return row == null ? Optional.empty() : Optional.of(row.status);
    }

    public Optional<String> errorOf(UUID reminderJobId, UUID recipientPersonId, Channel channel) {
        Row row = byKey.get(key(reminderJobId, recipientPersonId, channel));
        return row == null ? Optional.empty() : Optional.ofNullable(row.error);
    }

    public List<NotificationStatus> statuses() {
        List<NotificationStatus> all = new ArrayList<>();
        byId.values().forEach(row -> all.add(row.status));
        return List.copyOf(all);
    }

    public int size() {
        return byId.size();
    }

    private Row newRow(UUID reminderJobId, UUID recipientPersonId, Channel channel) {
        Row row = new Row(UUID.randomUUID(), reminderJobId, recipientPersonId, channel);
        byId.put(row.id, row);
        return row;
    }

    private static String key(UUID reminderJobId, UUID recipientPersonId, Channel channel) {
        return reminderJobId + "|" + recipientPersonId + "|" + channel;
    }

    private static final class Row {
        private final UUID id;
        private final UUID reminderJobId;
        private final UUID recipientPersonId;
        private final Channel channel;
        private NotificationStatus status = NotificationStatus.PENDING;
        private int retryCount;
        private String providerMessageId;
        private String error;

        private Row(UUID id, UUID reminderJobId, UUID recipientPersonId, Channel channel) {
            this.id = id;
            this.reminderJobId = reminderJobId;
            this.recipientPersonId = recipientPersonId;
            this.channel = channel;
        }
    }
}
