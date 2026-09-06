package vn.giapha.notification.application.view;

import java.time.Instant;
import java.util.UUID;

/**
 * Một đăng ký thiết bị nhìn từ phía API.
 *
 * <p><b>Không bao giờ chứa {@code p256dh} / {@code auth}.</b> Khoá mã hoá payload chỉ nằm ở máy chủ;
 * trả chúng ra là rò rỉ vô ích — client vốn đã có chúng, còn ai xem trộm phản hồi thì được thêm
 * đúng thứ cần để giả mạo push tới thiết bị đó.</p>
 *
 * @param currentDevice đăng ký này khớp {@code endpoint} mà client vừa gửi lên
 */
public record PushSubscriptionView(UUID id,
                                   String endpoint,
                                   String userAgent,
                                   Instant createdAt,
                                   Instant lastUsedAt,
                                   boolean currentDevice) {
}
