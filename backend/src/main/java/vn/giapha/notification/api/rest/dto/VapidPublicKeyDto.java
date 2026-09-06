package vn.giapha.notification.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Khoá công khai VAPID, khớp schema {@code VapidPublicKey}.
 *
 * <p>Base64url không padding, truyền thẳng vào {@code applicationServerKey} của
 * {@code PushManager.subscribe}. Không có endpoint này thì frontend buộc phải nhúng cứng khoá vào
 * bản build — đổi khoá là phải build lại, và dev/staging/prod dùng khoá khác nhau sẽ lệch.</p>
 *
 * <p><b>Khoá bí mật VAPID không bao giờ rời máy chủ</b> và không xuất hiện ở bất kỳ phản hồi nào.</p>
 */
@Schema(name = "VapidPublicKey", description = "Khoa cong khai VAPID (base64url khong padding)")
public record VapidPublicKeyDto(String publicKey) {
}
