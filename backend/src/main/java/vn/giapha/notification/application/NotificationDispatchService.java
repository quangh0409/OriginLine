package vn.giapha.notification.application;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.NotificationCategory;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.Recipient;
import vn.giapha.notification.domain.port.NotificationPublisher;
import vn.giapha.notification.domain.port.RecipientDirectory;

/**
 * Mặt tiền công khai của context {@code notification}: nhận yêu cầu nhắc từ {@code events}, phân
 * giải người nhận, rồi đẩy <b>một tin cho mỗi người nhận, mỗi kênh</b> lên RabbitMQ.
 *
 * <h2>Vì sao phân giải người nhận ở đây chứ không ở consumer</h2>
 * Nếu đẩy một tin "cho cả chi" rồi để consumer bung ra, thì một người lỗi sẽ kéo cả chi vào DLQ, và
 * lần retry sẽ gửi lại cho những người đã nhận. Bung ngay tại đây khiến mỗi tin là một đơn vị
 * <b>retry độc lập</b> và khớp đúng với khoá chống trùng {@code reminder_job_id + recipient}.
 *
 * <h2>Vì sao mỗi kênh một tin</h2>
 * {@code notify.inapp} và {@code notify.webpush} là hai queue tách rời có chủ ý: hộp thư in-app là
 * nguồn chân lý, Web Push chỉ là lớp đẩy thêm. Gateway push chết (hoặc hết hạn ngạch, hoặc chứng
 * chỉ VAPID sai) không được phép làm người trong họ mất luôn thông báo in-app. Gộp chung một tin,
 * một consumer là mất đúng tính chất ấy.
 *
 * <h2>Không chặn luồng web</h2>
 * Phương thức này chỉ ghi vào broker rồi trả về. Điểm gọi Giai đoạn 1 là scheduler, nhưng hợp đồng
 * vẫn giữ nguyên nếu sau này có API gọi tới.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    /** Kênh phát cho một lượt nhắc ở Giai đoạn 1. Thêm {@code Channel.ZALO} là đủ cho Giai đoạn 2. */
    private static final List<Channel> REMINDER_CHANNELS = List.of(Channel.INAPP, Channel.WEBPUSH);

    private final RecipientDirectory recipients;
    private final NotificationPublisher publisher;

    public NotificationDispatchService(RecipientDirectory recipients, NotificationPublisher publisher) {
        this.recipients = recipients;
        this.publisher = publisher;
    }

    /**
     * Phát một lượt nhắc tới toàn bộ thành viên trong phạm vi.
     *
     * @return số người nhận đã được đẩy tin (chưa phải số người đã nhận được)
     */
    public int dispatchReminder(ReminderDispatch request) {
        List<Recipient> targets = resolveRecipients(request);
        if (targets.isEmpty()) {
            // Không phải lỗi kỹ thuật, nhưng gần như luôn là lỗi dữ liệu: một cái giỗ không có ai
            // được nhắc thì cả tính năng coi như không tồn tại với chi đó. Ghi WARN để nó nổi lên
            // trong bảng theo dõi thay vì nằm im.
            log.warn("Lich nhac {} (su kien {}) khong tim duoc nguoi nhan nao: branch={} clanLevel={}."
                            + " Kiem tra lai app_user.status va person.primary_branch_id cua chi nay.",
                    request.reminderJobId(), request.eventId(), request.targetBranchId(), request.clanLevel());
            return 0;
        }
        for (Recipient recipient : targets) {
            for (Channel channel : REMINDER_CHANNELS) {
                publisher.publish(toMessage(request, recipient, channel));
            }
        }
        log.info("Da day lich nhac {} (su kien {}, D-{}) toi {} nguoi nhan tren {} kenh",
                request.reminderJobId(), request.eventId(), request.offsetDays(),
                targets.size(), REMINDER_CHANNELS.size());
        return targets.size();
    }

    /**
     * Sự kiện không phải cấp dòng họ mà cũng không gắn chi nào là dữ liệu thiếu — ràng buộc
     * {@code ck_event_scope} chỉ cấm đặt <i>cả hai</i>, không bắt buộc phải có <i>một</i>.
     *
     * <p>Chọn cách xử lý rộng (nhắc cả họ) thay vì hẹp (không nhắc ai): thừa một thông báo thì có
     * người phàn nàn và dữ liệu được sửa; thiếu một thông báo thì không ai biết cho tới khi cái giỗ
     * đã qua.</p>
     *
     * <h2>Nhưng "có người phàn nàn rồi dữ liệu được sửa" phải có một lối SỬA thật</h2>
     * {@code EventScope} chỉ đóng cửa với bản ghi <b>mới</b>; các dòng có trước vẫn nằm đó và vẫn
     * đi qua đúng nhánh này, mỗi lần là một lượt nhắc cho cả 1.500 người. Lối sửa là
     * {@code POST /api/v1/admin/events/backfill-scope}
     * ({@code vn.giapha.events.application.EventScopeBackfillService}) — chạy lại được nhiều lần,
     * suy chi từ hồ sơ nhân khẩu, và đánh dấu tường minh những dòng thật sự là cấp dòng họ.
     *
     * <p>Nhánh này <b>ở lại</b> sau lượt dọn ấy: nó là lớp đỡ cuối, và dòng {@code WARN} dưới đây
     * là thứ nói cho người vận hành biết đã tới lúc chạy lệnh dọn lần nữa.</p>
     */
    private List<Recipient> resolveRecipients(ReminderDispatch request) {
        boolean clanLevel = request.clanLevel();
        if (!clanLevel && request.targetBranchId() == null) {
            log.warn("Su kien {} khong phai cap dong ho nhung cung khong gan chi/nganh nao -"
                    + " tam nhac ca ho. Can bo sung target_branch_id.", request.eventId());
            clanLevel = true;
        }
        return recipients.membersOfBranch(request.targetBranchId(), clanLevel);
    }

    private static NotificationMessage toMessage(ReminderDispatch request, Recipient recipient,
                                                 Channel channel) {
        return new NotificationMessage(
                request.reminderJobId(),
                request.eventId(),
                request.subjectPersonId(),
                recipient,
                channel,
                NotificationCategory.REMINDER,
                request.title().forLocale(recipient.locale()),
                request.body() == null ? null : request.body().forLocale(recipient.locale()),
                request.deepLink(),
                request.offsetDays(),
                request.dueSolarDate());
    }
}
