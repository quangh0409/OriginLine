package vn.giapha.notification.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.giapha.notification.application.view.NotificationReadResultView;

/**
 * Kết quả đánh dấu đã đọc, khớp schema {@code NotificationReadResult}.
 *
 * @param readAt      thời điểm đọc <b>lần đầu</b>; gọi lại không cập nhật giá trị này
 * @param unreadCount số chưa đọc còn lại, để giao diện cập nhật badge ngay mà không phải gọi lại
 *                    danh sách
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "NotificationReadResult", description = "Ket qua danh dau da doc")
public record NotificationReadResultDto(UUID id, boolean isRead, Instant readAt, long unreadCount) {

    public static NotificationReadResultDto from(NotificationReadResultView view) {
        return new NotificationReadResultDto(view.id(), view.read(), view.readAt(), view.unreadCount());
    }
}
