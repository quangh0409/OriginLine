package vn.giapha.notification.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import vn.giapha.notification.application.view.WebPushConfigView;

/**
 * Trạng thái cấu hình Web Push trả cho quản trị viên.
 *
 * <p><b>Không có trường nào chứa khoá riêng VAPID</b>, và sẽ không bao giờ có: {@code privateKeySet}
 * chỉ là một cờ boolean. Khoá công khai thì trả nguyên văn — nó vốn đã công khai qua
 * {@code GET /api/v1/push/public-key}, và quản trị viên cần nó để đối chiếu với khoá mà trình duyệt
 * đang dùng khi truy nguyên lỗi 401.</p>
 */
@Schema(name = "WebPushConfigStatus",
        description = "Tinh trang cau hinh Web Push (VAPID) - chi System Admin")
public record WebPushConfigStatusDto(
        @Schema(description = "Da san sang gui hay chua") boolean ready,
        @Schema(description = "Cong tac giapha.webpush.enabled") boolean enabled,
        @Schema(description = "Da dat GIAPHA_WEBPUSH_PUBLIC_KEY") boolean publicKeySet,
        @Schema(description = "Da dat GIAPHA_WEBPUSH_PRIVATE_KEY (chi co, khong bao gio co gia tri)")
        boolean privateKeySet,
        @Schema(description = "Khoa cong khai dang phuc vu; null khi chua san sang") String publicKey,
        @Schema(description = "Claim sub cua JWT VAPID") String subject,
        @Schema(description = "TTL cua ban tin push, giay") int ttlSeconds,
        @Schema(description = "Tom tat tinh trang, mot cau") String summary,
        @Schema(description = "Cac buoc can lam; rong khi da san sang") List<String> remediation) {

    public static WebPushConfigStatusDto from(WebPushConfigView view) {
        return new WebPushConfigStatusDto(view.ready(), view.enabled(), view.publicKeySet(),
                view.privateKeySet(), view.publicKey(), view.subject(), view.ttlSeconds(),
                view.summary(), view.remediation());
    }
}
