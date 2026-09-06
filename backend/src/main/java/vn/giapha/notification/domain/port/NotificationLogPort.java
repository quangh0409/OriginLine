package vn.giapha.notification.domain.port;

import java.util.Optional;
import java.util.UUID;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.NotificationStatus;

/**
 * Cổng ghi {@code notification_log} — vừa là nhật ký gửi, vừa là <b>chốt chống trùng</b> của
 * consumer.
 *
 * <p>Khoá chống trùng là {@code (reminder_job_id, recipient_person_id, channel)}, do chỉ mục duy
 * nhất riêng phần {@code ux_notification_log_idempotency} bảo đảm. RabbitMQ giao <i>ít nhất một
 * lần</i>, nên bản sao là chuyện bình thường chứ không phải sự cố — và cách duy nhất chặn nó an
 * toàn giữa nhiều consumer chạy song song là để cơ sở dữ liệu làm trọng tài.</p>
 */
public interface NotificationLogPort {

    /**
     * Giành quyền xử lý một thông báo.
     *
     * <p>Ghi một dòng {@code PENDING}; nếu đã có dòng cho bộ ba khoá thì <b>không</b> ghi đè mà đọc
     * lại trạng thái hiện có để bên gọi tự quyết:</p>
     * <ul>
     *   <li>trạng thái đã kết thúc ({@code SENT} / {@code SKIPPED} / {@code DEAD_LETTER}) ⇒ đây là
     *       bản sao, bỏ qua;</li>
     *   <li>{@code PENDING} / {@code FAILED} ⇒ lần thử trước dở dang hoặc thất bại, được phép gửi
     *       lại và {@code retry_count} tăng lên.</li>
     * </ul>
     */
    Claim claim(UUID reminderJobId, UUID recipientPersonId, Channel channel);

    /** Kết thúc một lần gửi: cập nhật trạng thái, mã tin của gateway, lỗi, {@code sent_at}. */
    void complete(UUID logId, NotificationStatus status, String providerMessageId, String error);

    /** Đánh dấu tin đã rơi vào {@code notify.dlq} — hết retry, cần người xử lý. */
    void markDeadLetter(UUID reminderJobId, UUID recipientPersonId, Channel channel, String error);

    /** Tra trạng thái một dòng nhật ký — phục vụ đối soát và kiểm thử. */
    Optional<NotificationStatus> statusOf(UUID reminderJobId, UUID recipientPersonId, Channel channel);

    /**
     * Kết quả giành quyền xử lý.
     *
     * @param logId        id dòng nhật ký để cập nhật ở bước kết thúc; {@code null} khi bỏ qua
     * @param firstAttempt {@code true} nếu đây là lần thử đầu tiên
     * @param alreadyFinal {@code true} nếu thông báo này đã được xử lý xong trước đó ⇒ bỏ qua
     */
    record Claim(UUID logId, boolean firstAttempt, boolean alreadyFinal, int retryCount) {

        public static Claim skip() {
            return new Claim(null, false, true, 0);
        }

        public boolean shouldSend() {
            return logId != null && !alreadyFinal;
        }
    }
}
