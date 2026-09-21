package vn.giapha.content.application.command;

import java.util.UUID;

/**
 * Sửa bản nháp.
 *
 * <p>Trường {@code null} nghĩa là <b>giữ nguyên</b> (ngữ nghĩa PATCH của cả sản phẩm).</p>
 *
 * @param expectedVersion lấy từ {@code ETag} của lần đọc gần nhất, mang lên qua {@code If-Match}.
 *        <b>Bắt buộc</b>: một bài viết có thể đang mở trên hai thiết bị của cùng một người, và ghi
 *        đè im lặng là loại mất dữ liệu không ai phát hiện ra
 */
public record UpdatePostCommand(UUID postId, String title, String body, Long expectedVersion) {
}
