package vn.giapha.content.application.command;

/**
 * Tạo bản nháp bài viết.
 *
 * <p>Không có trường tác giả: tác giả <b>luôn</b> là người đang gọi. Cho phép truyền tác giả nghĩa
 * là mở một đường đăng bài nhân danh người khác, và không có màn hình nào trong bản thiết kế cần
 * đường ấy.</p>
 */
public record CreatePostCommand(String title, String body) {
}
