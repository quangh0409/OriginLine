package vn.giapha.notification.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.DeliveryResult;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.NotificationStatus;
import vn.giapha.notification.domain.port.NotificationLogPort;
import vn.giapha.notification.domain.port.NotificationProvider;

/**
 * Gửi <b>một</b> thông báo tới <b>một</b> người qua <b>một</b> kênh — thân của consumer RabbitMQ.
 *
 * <h2>Idempotent bằng cơ sở dữ liệu, không bằng thiện chí</h2>
 * RabbitMQ giao <i>ít nhất một lần</i>: mạng chớp, consumer chết sau khi gửi mà chưa kịp ack, hoặc
 * queue được replay — bản sao là chuyện thường ngày. Chốt chặn là chỉ mục duy nhất
 * {@code ux_notification_log_idempotency (reminder_job_id, recipient_person_id, channel)}:
 * {@link NotificationLogPort#claim} <i>ghi</i> để giành quyền chứ không <i>đọc</i> để kiểm tra.
 * Mẫu "SELECT xem đã gửi chưa rồi mới gửi" hỏng ngay khi có hai consumer chạy song song, mà prefetch
 * mặc định của dự án là 10.
 *
 * <p>Người trong họ nhận hai tin nhắc cùng một cái giỗ sẽ mất tin vào hệ thống nhanh hơn nhiều so
 * với nhận muộn một tin.</p>
 *
 * <h2>Ranh giới transaction: cố ý không có</h2>
 * Ba bước — giành quyền, gọi gateway, ghi kết quả — <b>không</b> nằm chung một transaction. Gọi HTTP
 * ra ngoài bên trong một transaction đang mở là cách giữ connection Postgres suốt thời gian chờ
 * mạng; mùa giỗ vài nghìn tin song song sẽ vét cạn pool. Mỗi bước tự commit; nếu tiến trình chết
 * giữa chừng thì dòng nhật ký còn ở {@code PENDING} và lần giao lại sẽ tiếp tục đúng chỗ đó.
 *
 * <h2>Retry hay không retry</h2>
 * Chỉ ném ngoại lệ với lỗi <b>tạm thời</b> — đó là tín hiệu duy nhất để Spring AMQP retry rồi cuối
 * cùng đẩy sang {@code notify.dlq}. Lỗi vĩnh viễn (subscription bị thu hồi, payload sai) được ghi
 * nhận rồi nuốt: retry chúng chỉ đốt hạn ngạch gateway và cuối cùng vẫn vào DLQ, làm nhiễu đúng cái
 * hàng đợi mà người vận hành cần nhìn.
 */
@Service
public class NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);

    private final Map<Channel, NotificationProvider> providers = new EnumMap<>(Channel.class);
    private final NotificationLogPort logs;

    public NotificationDeliveryService(List<NotificationProvider> providers, NotificationLogPort logs) {
        for (NotificationProvider provider : providers) {
            NotificationProvider previous = this.providers.put(provider.channel(), provider);
            if (previous != null) {
                throw new IllegalStateException("Hai provider cung dang ky kenh " + provider.channel()
                        + ": " + previous.getClass().getName() + " va " + provider.getClass().getName());
            }
        }
        this.logs = logs;
        log.info("Kenh thong bao dang hoat dong: {}", this.providers.keySet());
    }

    /**
     * @throws NotificationRetryException khi lỗi <b>tạm thời</b> — để Spring AMQP retry theo backoff
     *                                    rồi đẩy sang {@code notify.dlq}
     */
    public void deliver(NotificationMessage message) {
        NotificationLogPort.Claim claim = logs.claim(
                message.reminderJobId(), message.recipient().personId(), message.channel());
        if (!claim.shouldSend()) {
            log.debug("Bo qua ban sao: job={} nguoi nhan={} kenh={} (da xu ly xong truoc do)",
                    message.reminderJobId(), message.recipient().personId(), message.channel());
            return;
        }

        NotificationProvider provider = providers.get(message.channel());
        if (provider == null) {
            // Kênh chưa có adapter (ví dụ ZALO trước Giai đoạn 2). Không phải lỗi tin nhắn nên
            // không retry và không đẩy sang DLQ — chỉ ghi lại để biết có tin bị bỏ.
            log.warn("Chua co provider cho kenh {} - bo qua job {}", message.channel(), message.reminderJobId());
            logs.complete(claim.logId(), NotificationStatus.SKIPPED, null,
                    "Chua co adapter cho kenh " + message.channel());
            return;
        }

        DeliveryResult result;
        try {
            result = provider.send(message);
        } catch (RuntimeException ex) {
            // Provider lẽ ra không được ném; nếu vẫn ném thì coi là lỗi tạm thời — chọn hướng thử
            // lại thay vì bỏ, và để DLQ giữ tin nếu thử mãi không được.
            log.error("Provider {} nem ngoai le ngoai hop dong khi gui job {}: {}",
                    message.channel(), message.reminderJobId(), ex.getMessage(), ex);
            logs.complete(claim.logId(), NotificationStatus.FAILED, null, safeDetail(ex.getMessage()));
            throw new NotificationRetryException("Loi khong luong truoc o provider " + message.channel(), ex);
        }

        logs.complete(claim.logId(), result.toStatus(), result.providerMessageId(),
                safeDetail(result.detail()));

        switch (result.outcome()) {
            case SENT -> log.debug("Da gui job={} nguoi nhan={} kenh={}",
                    message.reminderJobId(), message.recipient().personId(), message.channel());
            case SKIPPED -> log.debug("Bo qua job={} kenh={}: {}",
                    message.reminderJobId(), message.channel(), result.detail());
            case PERMANENT -> log.warn("Loi vinh vien, khong thu lai: job={} kenh={} - {}",
                    message.reminderJobId(), message.channel(), result.detail());
            case RETRYABLE -> throw new NotificationRetryException(
                    "Loi tam thoi o kenh " + message.channel() + ": " + result.detail());
        }
    }

    /** Cắt ngắn và bỏ xuống dòng: cột {@code error} bị đọc bởi mọi quản trị viên kỹ thuật. */
    private static String safeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        String flat = detail.replaceAll("\\s+", " ").trim();
        return flat.length() <= 500 ? flat : flat.substring(0, 500);
    }
}
