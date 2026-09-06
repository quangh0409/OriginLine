package vn.giapha.notification.application.view;

/**
 * Kết quả "đánh dấu đã đọc tất cả".
 *
 * @param markedCount số tin thực sự chuyển sang đã đọc ở lượt gọi này; 0 là kết quả hợp lệ
 * @param unreadCount số chưa đọc còn lại — luôn 0 ngay sau lượt gọi, nhưng vẫn đọc lại từ cơ sở dữ
 *                    liệu chứ không suy ra, vì một tin mới có thể vừa rơi vào hộp thư
 */
public record NotificationReadAllResultView(int markedCount, long unreadCount) {
}
