package vn.giapha.content.api.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Thân {@code POST /api/v1/posts} — tạo bản nháp.
 *
 * <p>Không có trường tác giả: tác giả luôn là người đang đăng nhập. Xem
 * {@code CreatePostCommand}.</p>
 *
 * <p><b>Không có ảnh ở đợt này.</b> Backend chưa có SDK S3/MinIO nào nên không có lối tải ảnh lên;
 * một trường khoá ảnh do client tự điền là một trường trỏ vào hư không. Xem khối ghi chú đầu
 * {@code V17__content_post_honour.sql}.</p>
 */
public record CreatePostRequest(
        @NotBlank(message = "Tieu de bai viet khong duoc rong")
        @Size(max = 250, message = "Tieu de bai viet toi da 250 ky tu")
        String title,

        @NotBlank(message = "Than bai viet khong duoc rong")
        @Size(max = 200_000, message = "Than bai viet qua dai")
        String body) {
}
