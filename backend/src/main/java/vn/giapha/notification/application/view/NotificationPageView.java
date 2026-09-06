package vn.giapha.notification.application.view;

import java.util.List;

/**
 * Trang hộp thư kèm số chưa đọc.
 *
 * <p>{@code unreadCount} nằm ở <b>cấp phản hồi</b> chứ không trong {@link PageMetaView}, và luôn là
 * tổng số chưa đọc của <b>toàn hộp thư</b> — không phụ thuộc bộ lọc hay trang hiện tại. Giao diện
 * dùng nó cho badge chuông hiện trên mọi màn hình; nếu con số này chạy theo bộ lọc thì badge sẽ
 * nhảy loạn mỗi lần người dùng đổi tab.</p>
 */
public record NotificationPageView(List<NotificationView> items, PageMetaView page, long unreadCount) {
}
