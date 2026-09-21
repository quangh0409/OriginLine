package vn.giapha.genealogy.api.rest.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Thân yêu cầu của {@code PUT /api/v1/persons/{id}/avatar}.
 *
 * <h2>Một {@code mediaId}, không phải một chuỗi khoá</h2>
 * Cột {@code person.avatar_key} có từ V2 nhưng <b>chưa bao giờ có lối tải lên</b> cho tới đợt này,
 * nên mọi hợp đồng trước đó chỉ cho phép client tự điền một chuỗi — tức một trường trỏ vào hư
 * không. Nhận {@code mediaId} thay vì {@code String} làm ràng buộc "tệp phải đã tồn tại thật trên
 * kho và đã được backend nhìn thấy" trở thành một sự thật của kiểu, không phải một dòng ghi chú.
 *
 * <p>Luồng đầy đủ ở phía client: {@code POST /api/v1/media/upload-tickets} →
 * {@code PUT} thẳng lên {@code uploadUrl} → {@code POST /api/v1/media/{id}/confirm} (kèm
 * {@code alt}) → {@code PUT /api/v1/persons/{id}/avatar} với {@code mediaId} nhận được.</p>
 */
public record SetAvatarRequest(
        @NotNull(message = "Phai co mediaId cua tep da xac nhan") UUID mediaId) {
}
