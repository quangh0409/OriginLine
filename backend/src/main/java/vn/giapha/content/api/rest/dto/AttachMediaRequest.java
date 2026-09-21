package vn.giapha.content.api.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Thân yêu cầu của {@code PUT /api/v1/posts/{id}/media} — <b>đặt cả danh sách</b>, đúng thứ tự.
 *
 * <h2>{@code PUT} chứ không {@code PATCH}, và không có lệnh "thêm một tệp"</h2>
 * Thứ tự là một phần của dữ liệu ("ảnh toàn cảnh trước, ảnh dâng hương sau"), nên một API
 * thêm/bớt từng tệp sẽ cần thêm một lệnh sắp xếp lại — hai lệnh cho một thao tác mà người dùng
 * nghĩ là một. Với trần 12 tệp thì gửi lại cả danh sách rẻ hơn nhiều.
 *
 * <p>Danh sách <b>rỗng</b> là hợp lệ và có nghĩa rõ ràng: gỡ hết ảnh khỏi bài. {@code null} thì
 * không — nó là một yêu cầu thiếu trường, và đoán ý ở đây sẽ biến một lỗi client thành một lần
 * mất ảnh im lặng.</p>
 *
 * <p>Nằm ở {@code content} chứ không ở {@code media}: đây là hợp đồng của một điểm cuối thuộc
 * {@code content}. Đặt nó bên kia sẽ buộc {@code content} phụ thuộc vào gói {@code media.api} —
 * một gói không phải {@code @NamedInterface} và không bao giờ nên là.</p>
 */
public record AttachMediaRequest(
        @NotNull(message = "Phai gui danh sach tep, du la danh sach rong")
        @Size(max = 12, message = "Mot bai gan toi da 12 tep") List<UUID> mediaIds) {
}
