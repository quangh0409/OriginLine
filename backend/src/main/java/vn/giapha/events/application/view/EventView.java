package vn.giapha.events.application.view;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import vn.giapha.events.domain.EventSubject;
import vn.giapha.events.domain.EventType;
import vn.giapha.shared.vo.LunarDate;

/**
 * Một sự kiện dòng họ nhìn từ phía API.
 *
 * <p><b>Ngày âm là dữ liệu gốc, ngày dương là kết quả quy đổi của máy chủ.</b> Giao diện tuyệt đối
 * không tự quy đổi âm–dương: sai cờ tháng nhuận thì lệch nguyên một tháng mà không có lỗi nào được
 * ném ra — hợp đồng OpenAPI cũng ghi đúng cảnh báo này.</p>
 *
 * @param nextOccurrenceSolar    ngày dương của lần xảy ra sắp tới; {@code null} nếu không quy đổi được
 * @param nextOccurrenceLunarYear năm âm lịch của lần xảy ra sắp tới
 * @param daysUntil              số ngày còn lại tính từ hôm nay (GMT+7); âm = đã qua
 * @param adjustmentNote         lý do ngày dương lệch khỏi ngày âm trong sổ; {@code null} khi không lệch
 */
public record EventView(UUID id,
                        EventType type,
                        String title,
                        EventSubject subject,
                        LunarDate lunarDate,
                        LocalDate nextOccurrenceSolar,
                        Integer nextOccurrenceLunarYear,
                        Integer daysUntil,
                        EventSubject.BranchSnapshot targetBranch,
                        boolean clanLevel,
                        List<Integer> reminderOffsets,
                        String location,
                        String note,
                        String adjustmentNote) {
}
