package vn.giapha.genealogy.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import vn.giapha.genealogy.domain.DatePrecision;

/**
 * Ngày <b>song lịch</b> - dùng cho cả chiều đọc lẫn chiều ghi, đúng như schema {@code DateDual}
 * của contract.
 *
 * <p>Với ngày mất, {@code lunar} là <b>nguồn chân lý để tính giỗ</b>: dòng họ giỗ theo ngày âm,
 * còn ngày dương tương ứng đổi theo từng năm nên chỉ là dữ liệu dẫn xuất.</p>
 *
 * <p>{@code precision} nói mức chính xác thật sự của dữ liệu gốc. Gia phả giấy thường chỉ còn năm,
 * đôi khi chỉ còn ngày tháng âm mà mất năm - giao diện hiển thị theo mức này, đừng bịa ngày
 * {@code 01} rồi trình bày như thể đã biết chắc.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DateDualDto(LocalDate solar, LunarDateDto lunar, DatePrecision precision) {

    /**
     * Ngày âm lịch Việt Nam (GMT+7, thuật toán Hồ Ngọc Đức).
     *
     * @param leap cờ <b>tháng nhuận</b>. Bỏ qua cờ này là nguồn bug số một của hệ thống: giỗ lệch
     *        nguyên một tháng mà không có lỗi nào được ném ra.
     * @param canChi can chi của năm; chỉ đọc, do context {@code calendar} sinh
     * @param yearLabel nhãn hiển thị đầy đủ; chỉ đọc
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LunarDateDto(Integer year, Integer month, Integer day, Boolean leap,
                               String canChi, String yearLabel) {
    }
}
