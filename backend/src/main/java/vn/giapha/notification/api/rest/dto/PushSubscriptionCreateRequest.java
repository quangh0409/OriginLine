package vn.giapha.notification.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Thân của {@code POST /api/v1/push/subscriptions} — chính là {@code PushSubscription.toJSON()} của
 * trình duyệt (Web Push / RFC 8291), cộng vài trường phụ trợ.
 *
 * <p>{@code endpoint} là <b>khoá định danh duy nhất</b> theo chuẩn: gửi lại cùng endpoint là cập
 * nhật bản cũ, không tạo bản mới.</p>
 *
 * @param locale ngôn ngữ mong muốn cho thiết bị này. <b>Chưa được lưu</b>: bảng
 *               {@code push_subscription} không có cột tương ứng và migration thuộc sở hữu của W1.
 *               Hiện thông báo dùng {@code app_user.locale}. Nhận trường này để hợp đồng không vỡ
 *               và để không phải đổi client khi cột được thêm.
 */
@Schema(name = "PushSubscriptionCreateRequest", description = "Dang ky thiet bi nhan Web Push")
public record PushSubscriptionCreateRequest(
        @NotBlank @Size(max = 2000) String endpoint,
        @NotNull @Valid Keys keys,
        Long expirationTime,
        @Size(max = 255) String userAgent,
        @Size(max = 8) String locale) {

    /**
     * Khoá mã hoá payload của client (RFC 8291).
     *
     * <p>Đây là khoá <b>của trình duyệt</b>. Khoá riêng VAPID của máy chủ nằm trong biến môi trường
     * và không bao giờ đi qua API này theo cả hai chiều.</p>
     */
    public record Keys(@NotBlank @Size(max = 255) String p256dh,
                       @NotBlank @Size(max = 255) String auth) {
    }
}
