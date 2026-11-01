package vn.giapha.content.api.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Thân {@code POST /api/v1/posts} — tạo bản nháp.
 *
 * <p>Không có trường tác giả: tác giả luôn là người đang đăng nhập. Xem
 * {@code CreatePostCommand}.</p>
 *
 * <p><b>Chỉ tiêu đề là bắt buộc.</b> Đây là lượt tạo <i>nháp</i>, và màn soạn gửi nó ngay khi
 * người viết rời ô tiêu đề — trước khi có chữ nào ở thân bài. Bản trước đòi cả thân bài ở đây và
 * mỗi người viết mới đều nhận một {@code 400} ngay ô đầu tiên; lý do đầy đủ ở đầu lớp
 * {@code content.domain.Post}.</p>
 *
 * <p><b>Không có ảnh ở đợt này.</b> Backend chưa có SDK S3/MinIO nào nên không có lối tải ảnh lên;
 * một trường khoá ảnh do client tự điền là một trường trỏ vào hư không. Xem khối ghi chú đầu
 * {@code V17__content_post_honour.sql}.</p>
 */
public record CreatePostRequest(
        @NotBlank(message = "Tieu de bai viet khong duoc rong")
        @Size(max = 250, message = "Tieu de bai viet toi da 250 ky tu")
        String title,

        // KHÔNG @NotBlank — nháp được phép chưa có thân (xem đầu lớp Post). Luật "phải có thân"
        // nằm ở POST /posts/{id}/submit. Vắng mặt hay null đều đọc thành chuỗi rỗng.
        @Size(max = 200_000, message = "Than bai viet qua dai")
        String body) {
}
