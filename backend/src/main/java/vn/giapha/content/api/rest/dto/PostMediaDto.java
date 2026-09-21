package vn.giapha.content.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Một tệp đính kèm của bài, trên dây.
 *
 * <p>Bản sao của {@code media.application.view.MediaAssetView} ở hợp đồng REST của
 * {@code content} — cố ý. Kiểu kia là <i>kiểu của một context khác</i>; đưa thẳng nó vào
 * {@code PostDto} nghĩa là hợp đồng công khai của bài viết sẽ thay đổi mỗi khi {@code media} đổi
 * hình dạng nội bộ. Một record năm dòng là cái giá rẻ cho việc giữ hai hợp đồng độc lập.</p>
 *
 * @param url URL {@code GET} <b>đã ký, hạn 10 phút</b>. Đây là lý do {@code PostController} đặt
 *        {@code Cache-Control: no-store} + {@code Vary: Authorization} trên mọi phản hồi — một
 *        URL đã ký nằm trong bộ nhớ đệm dùng chung là một tệp riêng tư phục vụ cho người sau
 * @param alt chữ thay ảnh. <b>Không bao giờ rỗng với ảnh</b> (WCAG 2.2 AA 1.1.1), ép từ tầng
 *        domain của {@code media} tới tận ràng buộc {@code ck_media_alt_required} — giao diện
 *        không phải viết nhánh dự phòng
 * @param durationMs thời lượng video theo mili-giây; vắng mặt với ảnh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostMediaDto(UUID id,
                           String kind,
                           String contentType,
                           long sizeBytes,
                           Integer durationMs,
                           String alt,
                           int position,
                           String url) {
}
