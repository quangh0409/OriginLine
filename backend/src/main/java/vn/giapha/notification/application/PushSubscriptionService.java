package vn.giapha.notification.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.notification.application.view.PushSubscriptionView;
import vn.giapha.notification.domain.PushSubscription;
import vn.giapha.notification.domain.Recipient;
import vn.giapha.notification.domain.port.PushSubscriptionRepository;
import vn.giapha.notification.domain.port.VapidKeyProvider;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Quản lý đăng ký Web Push của <b>chính người đang đăng nhập</b>
 * ({@code POST/DELETE /api/v1/push/subscriptions}).
 *
 * <p><b>Idempotent theo {@code endpoint}</b>: trình duyệt tự cấp lại subscription sau khi xoá cache
 * hoặc cập nhật service worker, và mỗi lần như vậy app sẽ gọi lại API này. Nếu tạo bản mới mỗi lần
 * thì một người dùng sẽ tích luỹ hàng chục bản ghi cho cùng một máy và nhận đúng ngần ấy thông báo
 * trùng nhau.</p>
 *
 * <p>Người từ chối quyền push vẫn nhận đủ thông báo in-app — đăng ký ở đây <b>không</b> là điều kiện
 * tiên quyết của bất cứ thứ gì.</p>
 */
@Service
public class PushSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(PushSubscriptionService.class);

    /** Mã lỗi ổn định để giao diện ẩn công tắc Web Push thay vì hiện nút bấm vào là hỏng. */
    public static final String CODE_WEBPUSH_NOT_CONFIGURED = "WEBPUSH_NOT_CONFIGURED";

    private final PushSubscriptionRepository subscriptions;
    private final CurrentAccountService currentAccount;
    private final VapidKeyProvider vapidKeys;

    public PushSubscriptionService(PushSubscriptionRepository subscriptions,
                                   CurrentAccountService currentAccount,
                                   VapidKeyProvider vapidKeys) {
        this.subscriptions = subscriptions;
        this.currentAccount = currentAccount;
        this.vapidKeys = vapidKeys;
    }

    /**
     * Khoá công khai VAPID cho {@code PushManager.subscribe}.
     *
     * <p>Chưa cấu hình thì ném lỗi nghiệp vụ (422) thay vì trả chuỗi rỗng: một khoá rỗng khiến
     * {@code subscribe()} thất bại ở client với thông điệp khó hiểu, còn mã lỗi ổn định thì cho giao
     * diện biết mà ẩn hẳn tính năng. Thông báo in-app không bị ảnh hưởng.</p>
     */
    @Transactional(readOnly = true)
    public String vapidPublicKey() {
        return vapidKeys.publicKeyBase64Url().orElseThrow(() -> new DomainException(
                CODE_WEBPUSH_NOT_CONFIGURED,
                "May chu chua cau hinh khoa VAPID nen chua bat duoc Web Push."
                        + " Thong bao in-app van hoat dong binh thuong."));
    }

    /**
     * @param expirationEpochMillis {@code expirationTime} do trình duyệt cấp; thường {@code null}
     * @return bản ghi kèm cờ {@code created} để controller chọn 201 hay 200
     */
    @Transactional
    public Registration register(String endpoint, String p256dh, String auth, String userAgent,
                                 Long expirationEpochMillis) {
        Recipient me = currentAccount.require();
        Instant expiresAt = expirationEpochMillis == null ? null : Instant.ofEpochMilli(expirationEpochMillis);
        PushSubscriptionRepository.Upsert upsert =
                subscriptions.save(me.appUserId(), endpoint, p256dh, auth, userAgent, expiresAt);
        log.info("{} dang ky Web Push cho app_user {}",
                upsert.created() ? "Tao moi" : "Cap nhat", me.appUserId());
        return new Registration(toView(upsert.subscription(), endpoint), upsert.created());
    }

    /**
     * Xoá một đăng ký của chính mình. Đăng ký không tồn tại <b>hoặc thuộc người khác</b> đều trả
     * 404 — 403 sẽ xác nhận rằng id đó có thật.
     */
    @Transactional
    public void unregister(UUID subscriptionId) {
        Recipient me = currentAccount.require();
        if (!subscriptions.deleteOwned(subscriptionId, me.appUserId())) {
            throw NotFoundException.of("PushSubscription", subscriptionId);
        }
        log.info("Da huy dang ky Web Push {} cua app_user {}", subscriptionId, me.appUserId());
    }

    /** Danh sách thiết bị của chính mình — màn hình cài đặt. */
    @Transactional(readOnly = true)
    public List<PushSubscriptionView> myDevices(String currentEndpoint) {
        Recipient me = currentAccount.require();
        List<PushSubscription> found = subscriptions.activeByAppUser(me.appUserId());
        List<PushSubscriptionView> views = new ArrayList<>(found.size());
        for (PushSubscription subscription : found) {
            views.add(toView(subscription, currentEndpoint));
        }
        return List.copyOf(views);
    }

    private static PushSubscriptionView toView(PushSubscription subscription, String currentEndpoint) {
        return new PushSubscriptionView(
                subscription.id(),
                subscription.endpoint(),
                subscription.userAgent(),
                subscription.createdAt(),
                subscription.lastUsedAt(),
                subscription.endpoint().equals(currentEndpoint));
    }

    /** @param created {@code true} = tạo mới (HTTP 201), {@code false} = cập nhật bản cũ (HTTP 200) */
    public record Registration(PushSubscriptionView subscription, boolean created) {
    }
}
