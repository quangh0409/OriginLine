package vn.giapha.events.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Một sự kiện dòng họ trên dây, khớp schema {@code EventDto} của {@code contracts/openapi.yaml}.
 *
 * @param eventType           mã theo <b>hợp đồng</b>, không phải mã trong cơ sở dữ liệu — xem
 *                            {@link vn.giapha.events.api.rest.EventTypeApiMapper}
 * @param nextOccurrenceSolar ngày dương của lần xảy ra sắp tới, do máy chủ quy đổi. Giao diện
 *                            <b>không</b> tự quy đổi âm–dương: sai cờ tháng nhuận là lệch nguyên một
 *                            tháng mà không có lỗi nào được ném ra.
 * @param adjustmentNote      lý do ngày dương lệch khỏi ngày âm chép trong gia phả (tháng thiếu,
 *                            tháng nhuận không có). Trường mở rộng ngoài hợp đồng, thêm vào vì đưa
 *                            ra một ngày khác với sổ mà không giải thích là cách nhanh nhất để cả họ
 *                            mất tin vào hệ thống.
 * @param graveId             mộ phần liên quan — Giai đoạn 3, hiện luôn {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Event", description = "Su kien gio/le cua dong ho")
public record EventDto(UUID id,
                       String eventType,
                       String title,
                       EventPersonDto person,
                       LunarDateDto lunarDate,
                       LocalDate nextOccurrenceSolar,
                       Integer nextOccurrenceLunarYear,
                       Integer daysUntil,
                       BranchRefDto targetBranch,
                       boolean isClanLevel,
                       List<Integer> reminderOffsets,
                       String location,
                       UUID graveId,
                       String note,
                       String adjustmentNote) {
}
