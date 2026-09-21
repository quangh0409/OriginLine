package vn.giapha.events.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Thân yêu cầu {@code PATCH /api/v1/events/&#123;id&#125;}.
 *
 * <p>Mọi trường đều tuỳ chọn. Trường <b>vắng mặt</b> trong JSON giữ nguyên giá trị cũ; trường có
 * mặt và bằng {@code null} thì xoá giá trị. Hai chuyện ấy khác nhau và không phân biệt được chỉ
 * bằng kiểu dữ liệu, nên controller đọc thân yêu cầu qua {@code JsonNode} để biết khoá nào thực sự
 * có mặt — cùng cách {@code PersonController} đã dùng.</p>
 *
 * <p>Bắt buộc gửi {@code If-Match} với {@code ETag} lấy từ {@code GET}. Ghi mù lên bản của người
 * khác bị từ chối bằng <b>412</b>: lịch việc họ là thứ nhiều người cùng biên tập trước mỗi mùa
 * giỗ chạp.</p>
 */
@Schema(name = "UpdateEventRequest", description = "Sua su kien dong ho (PATCH)")
public record UpdateEventRequest(String eventType,
                                 @Size(max = 200) String title,
                                 @Size(max = 4000) String description,
                                 @Valid LunarDateInput lunarDate,
                                 LocalDate solarDate,
                                 Boolean recurringAnnually,
                                 UUID scopeBranchId,
                                 Boolean clanWide,
                                 UUID personId,
                                 @Size(max = 255) String location) {
}
