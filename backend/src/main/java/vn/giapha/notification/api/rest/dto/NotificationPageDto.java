package vn.giapha.notification.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import vn.giapha.notification.application.view.NotificationPageView;
import vn.giapha.notification.application.view.NotificationView;

/**
 * Trang hộp thư, khớp schema {@code NotificationPage}.
 *
 * @param unreadCount <b>tổng số chưa đọc của toàn hộp thư</b>, không phụ thuộc bộ lọc hay trang hiện
 *                    tại. Nằm ở cấp phản hồi chứ không trong {@code page} vì giao diện dùng nó cho
 *                    badge chuông hiện trên mọi màn hình.
 */
@Schema(name = "NotificationPage", description = "Trang hop thu kem so chua doc")
public record NotificationPageDto(List<NotificationDto> items, PageMetaDto page, long unreadCount) {

    public static NotificationPageDto from(NotificationPageView view) {
        List<NotificationDto> items = new ArrayList<>(view.items().size());
        for (NotificationView item : view.items()) {
            items.add(NotificationDto.from(item));
        }
        return new NotificationPageDto(List.copyOf(items), PageMetaDto.from(view.page()),
                view.unreadCount());
    }
}
