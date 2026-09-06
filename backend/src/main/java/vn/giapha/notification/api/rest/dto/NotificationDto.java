package vn.giapha.notification.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.giapha.notification.application.view.NotificationView;

/**
 * Một dòng hộp thư in-app, khớp schema {@code NotificationDto}.
 *
 * @param body <b>không bao giờ</b> chứa dữ liệu Tầng 3 (số điện thoại, email, địa chỉ) — thông báo
 *             hiện trên màn hình khoá của thiết bị
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Notification", description = "Thong bao in-app")
public record NotificationDto(UUID id,
                              String category,
                              String title,
                              String body,
                              UUID eventId,
                              UUID personId,
                              String deepLink,
                              Instant createdAt,
                              boolean isRead,
                              Instant readAt,
                              Integer reminderOffsetDays) {

    public static NotificationDto from(NotificationView view) {
        return new NotificationDto(
                view.id(),
                NotificationCategoryApiMapper.toApi(view.category()),
                view.title(),
                view.body(),
                view.eventId(),
                view.subjectPersonId(),
                view.deepLink(),
                view.createdAt(),
                view.read(),
                view.readAt(),
                view.reminderOffsetDays());
    }
}
