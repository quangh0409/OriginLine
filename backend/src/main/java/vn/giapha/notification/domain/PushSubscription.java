package vn.giapha.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Một đăng ký Web Push của một thiết bị (chuẩn Web Push / RFC 8291).
 *
 * <p>{@link #endpoint()} là <b>định danh duy nhất</b> của đăng ký theo chuẩn — không phải
 * {@link #id()}. Gửi lại cùng endpoint là cập nhật, không tạo bản mới; ràng buộc thật nằm ở
 * {@code ux_push_subscription_endpoint} trên {@code md5(endpoint)} (endpoint dài hơn giới hạn khoá
 * btree nên không index thẳng được).</p>
 *
 * <p>{@code p256dh} và {@code auth} là khoá <b>của client</b>. Khoá riêng VAPID của máy chủ nằm ở
 * biến môi trường và không bao giờ chạm tới cơ sở dữ liệu.</p>
 */
public record PushSubscription(UUID id,
                               UUID appUserId,
                               String endpoint,
                               String p256dh,
                               String auth,
                               String userAgent,
                               boolean active,
                               int failureCount,
                               Instant lastUsedAt,
                               Instant expiresAt,
                               Instant createdAt) {

    public PushSubscription {
        Objects.requireNonNull(appUserId, "PushSubscription.appUserId khong duoc null");
        Objects.requireNonNull(endpoint, "PushSubscription.endpoint khong duoc null");
        Objects.requireNonNull(p256dh, "PushSubscription.p256dh khong duoc null");
        Objects.requireNonNull(auth, "PushSubscription.auth khong duoc null");
    }

    /**
     * Gốc (scheme://host[:port]) của endpoint — giá trị claim {@code aud} của JWT VAPID.
     *
     * <p>Sai {@code aud} thì push service trả 401 và không có thông báo nào tới nơi; đây là lỗi
     * cấu hình VAPID phổ biến nhất.</p>
     */
    public String audience() {
        java.net.URI uri = java.net.URI.create(endpoint);
        StringBuilder audience = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() > 0) {
            audience.append(':').append(uri.getPort());
        }
        return audience.toString();
    }
}
