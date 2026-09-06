package vn.giapha.notification.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.giapha.notification.application.view.NotificationReadAllResultView;

/** Kết quả "đánh dấu đã đọc tất cả" — trả kèm {@code unreadCount} để FE cập nhật badge chuông ngay. */
@Schema(name = "NotificationReadAllResult", description = "Ket qua danh dau da doc tat ca")
public record NotificationReadAllResultDto(
        @Schema(description = "So tin thuc su chuyen sang da doc o luot goi nay")
        int markedCount,
        @Schema(description = "So tin chua doc con lai cua toan hop thu")
        long unreadCount) {

    public static NotificationReadAllResultDto from(NotificationReadAllResultView view) {
        return new NotificationReadAllResultDto(view.markedCount(), view.unreadCount());
    }
}
