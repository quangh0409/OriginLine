package vn.giapha.notification.application;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.giapha.notification.application.view.WebPushTestSendView;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.DeliveryResult;
import vn.giapha.notification.domain.NotificationCategory;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.Recipient;
import vn.giapha.notification.domain.port.NotificationProvider;
import vn.giapha.notification.domain.port.PushSubscriptionRepository;

/**
 * Gửi <b>một tin thử</b> qua Web Push tới đúng các thiết bị của <b>chính người đang gọi</b>.
 *
 * <h2>Vì sao cần</h2>
 * "Đã cấu hình khoá" và "thông báo tới được máy" là hai việc khác nhau: khoá có thể hợp lệ mà
 * subscription đã bị thu hồi, hoặc push service từ chối vì claim {@code aud}. Không có nút thử thì
 * cách duy nhất để biết là <b>đợi tới ngày giỗ</b> — tức là biết khi đã muộn. Đây cũng là chỗ để
 * xác nhận sau khi vừa đổi khoá.
 *
 * <h2>Chỉ gửi cho chính mình</h2>
 * Người nhận <b>luôn</b> là tài khoản đang gọi, không nhận tham số người nhận. Một endpoint quản
 * trị gửi được cho người khác sẽ sớm được dùng để "thử" trên điện thoại của người trong họ lúc
 * nửa đêm.
 *
 * <h2>Không đi qua RabbitMQ, không ghi {@code notification_log}</h2>
 * Cố ý gọi thẳng adapter. Qua hàng đợi thì endpoint chỉ trả được "đã xếp hàng", tức là đúng cái
 * không cần biết; còn kết quả thật (401 vì VAPID sai, 410 vì máy đã gỡ app) nằm lại trong log của
 * consumer. Ở đây kết quả được trả thẳng cho người bấm nút. Đổi lại, tin thử <b>không</b> xuất
 * hiện trong trung tâm thông báo — nó là phép đo hạ tầng, không phải một thông báo của dòng họ.
 */
@Service
public class WebPushTestSendService {

    private static final Logger log = LoggerFactory.getLogger(WebPushTestSendService.class);

    private final CurrentAccountService currentAccount;
    private final PushSubscriptionRepository subscriptions;
    private final NotificationProvider webPush;

    public WebPushTestSendService(CurrentAccountService currentAccount,
                                  PushSubscriptionRepository subscriptions,
                                  List<NotificationProvider> providers) {
        this.currentAccount = currentAccount;
        this.subscriptions = subscriptions;
        this.webPush = providers.stream()
                .filter(provider -> provider.channel() == Channel.WEBPUSH)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Khong tim thay adapter cho kenh WEBPUSH - kiem tra WebPushAdapter co la bean khong"));
    }

    /**
     * @return kết quả thật của lượt gửi, kể cả khi thất bại — <b>không</b> nuốt lỗi thành "đã gửi"
     */
    public WebPushTestSendView sendToSelf() {
        Recipient me = currentAccount.require();
        int soThietBi = subscriptions.activeByAppUser(me.appUserId()).size();

        DeliveryResult result = webPush.send(new NotificationMessage(
                null,                       // không có reminderJobId -> không có khoá chống trùng
                null,
                me.personId(),
                me,
                Channel.WEBPUSH,
                NotificationCategory.SYSTEM,
                "Thu thong bao day",
                "Tin thu do quan tri vien gui. Neu ban thay tin nay, kenh Web Push dang hoat dong.",
                "/settings",
                null,
                null));

        log.info("Gui thu Web Push cho app_user {}: {} thiet bi -> {}",
                me.appUserId(), soThietBi, result.outcome());
        return new WebPushTestSendView(
                result.outcome() == vn.giapha.notification.domain.DeliveryOutcome.SENT,
                result.outcome().name(),
                result.detail(),
                soThietBi);
    }
}
