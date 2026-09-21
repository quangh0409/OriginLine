package vn.giapha.events.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import vn.giapha.shared.vo.LunarDate;

/**
 * Ngày âm gửi lên khi tạo/sửa sự kiện.
 *
 * <h2>{@code leap} là trường nguy hiểm nhất của cả thân yêu cầu</h2>
 * Bỏ quên nó không sinh ra lỗi nào: ngày vẫn hợp lệ, hệ thống vẫn quy đổi được, và lời nhắc lệch
 * <b>nguyên một tháng</b>. Mặc định {@code false} là lựa chọn đúng (tháng nhuận là ngoại lệ, không
 * phải thường lệ), nhưng giao diện nhập liệu phải hỏi rõ khi tháng ấy có nhuận.
 *
 * @param year {@code null} = lặp lại hằng năm, không gắn năm nào. Bắt buộc khi
 *             {@code recurringAnnually = false}: một lễ khánh thành xảy ra đúng một lần, và "ngày
 *             12 tháng 2 âm" không có năm thì không quy đổi được sang ngày dương nào.
 * @param day  1–30; ngày 30 ở một tháng thiếu được lùi về 29 lúc quy đổi, không bị từ chối lúc ghi
 *             — xem {@code OccurrenceResolver}
 */
@Schema(name = "LunarDateInput", description = "Ngay am lich gui len (Ho Ngoc Duc, GMT+7)")
public record LunarDateInput(@Min(1) @Max(9999) Integer year,
                             @NotNull @Min(1) @Max(12) Integer month,
                             @NotNull @Min(1) @Max(30) Integer day,
                             Boolean leap) {

    /**
     * Bản thô, chưa biết sự kiện có lặp hay không.
     *
     * <p>{@code year} vắng thành {@code 0} — quy ước của V4 cho "lặp lại hằng năm". Việc chuẩn hoá
     * ngược lại (lặp hằng năm thì bỏ năm đi) và việc đòi năm cho sự kiện một lần nằm ở
     * {@code EventCommandService}, nơi cờ lặp <i>sau khi sửa</i> mới được biết chắc: một lượt
     * {@code PATCH} có thể đổi ngày mà không đụng tới cờ lặp.</p>
     */
    public LunarDate toDomain() {
        return new LunarDate(year == null ? 0 : year, month, day, Boolean.TRUE.equals(leap));
    }
}
