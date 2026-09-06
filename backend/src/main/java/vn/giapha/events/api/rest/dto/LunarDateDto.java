package vn.giapha.events.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.giapha.shared.vo.LunarDate;

/**
 * Ngày âm lịch trên dây, khớp schema {@code LunarDate} của {@code contracts/openapi.yaml}.
 *
 * @param leap cờ tháng nhuận — <b>bắt buộc phải giữ đúng</b>. Bỏ qua nó thì ngày giỗ lệch cả tháng
 *             mà không hệ thống nào báo lỗi.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "LunarDate", description = "Ngay am lich Viet Nam (Ho Ngoc Duc, GMT+7)")
public record LunarDateDto(Integer year, int month, int day, boolean leap) {

    public static LunarDateDto from(LunarDate lunar) {
        if (lunar == null) {
            return null;
        }
        // year = 0 nghĩa là "lặp lại hằng năm, không gắn năm nào" — trả null thay vì số 0 để giao
        // diện không hiển thị "năm 0".
        return new LunarDateDto(lunar.year() == 0 ? null : lunar.year(),
                lunar.month(), lunar.day(), lunar.leapMonth());
    }
}
