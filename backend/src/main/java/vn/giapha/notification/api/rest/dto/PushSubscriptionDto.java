package vn.giapha.notification.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.giapha.notification.application.view.PushSubscriptionView;

/**
 * Một đăng ký thiết bị, khớp schema {@code PushSubscriptionDto}.
 *
 * <p><b>Không trả {@code keys}.</b> Khoá mã hoá payload chỉ nằm ở máy chủ; client vốn đã có chúng,
 * còn ai đọc trộm được phản hồi thì được thêm đúng thứ cần để giả mạo push tới thiết bị đó.</p>
 *
 * @param locale luôn {@code null} ở Giai đoạn 1 — {@code push_subscription} chưa có cột {@code locale}
 *               (xem {@link PushSubscriptionCreateRequest})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PushSubscription", description = "Dang ky thiet bi nhan Web Push")
public record PushSubscriptionDto(UUID id,
                                  String endpoint,
                                  String userAgent,
                                  String locale,
                                  Instant createdAt,
                                  Instant lastUsedAt,
                                  boolean isCurrentDevice) {

    public static PushSubscriptionDto from(PushSubscriptionView view) {
        return new PushSubscriptionDto(view.id(), view.endpoint(), view.userAgent(), null,
                view.createdAt(), view.lastUsedAt(), view.currentDevice());
    }
}
