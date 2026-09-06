package vn.giapha.events.application.command;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import vn.giapha.events.domain.EventType;

/**
 * Tham số của {@code GET /api/v1/events}.
 *
 * <p>Khoảng lọc {@code from}/{@code to} áp lên <b>ngày dương của lần xảy ra sắp tới</b> — đó là thứ
 * người dùng nhìn thấy trên lịch, không phải ngày âm gốc. {@code upcomingDays} là đường tắt; gửi
 * kèm {@code from}/{@code to} thì {@code from}/{@code to} thắng.</p>
 *
 * @param branchId lọc theo chi/ngành, <b>bao gồm nhánh con</b>; sự kiện cấp dòng họ luôn xuất hiện
 */
public record EventQuery(LocalDate from,
                         LocalDate to,
                         Integer upcomingDays,
                         List<EventType> types,
                         UUID branchId,
                         UUID personId,
                         int page,
                         int size,
                         String sort) {

    /** Trần mặc định khi không có bộ lọc nào: một năm tới, đủ phủ trọn một vòng giỗ chạp. */
    public static final int DEFAULT_UPCOMING_DAYS = 365;

    /** Khoảng ngày dương thực tế sau khi áp thứ tự ưu tiên giữa {@code from}/{@code to} và {@code upcomingDays}. */
    public Range resolveRange(LocalDate today) {
        if (from != null || to != null) {
            return new Range(from == null ? today : from, to);
        }
        int days = upcomingDays == null ? DEFAULT_UPCOMING_DAYS : Math.max(1, upcomingDays);
        return new Range(today, today.plusDays(days));
    }

    /** @param end {@code null} = không giới hạn phía sau */
    public record Range(LocalDate start, LocalDate end) {

        public boolean contains(LocalDate date) {
            if (date == null) {
                return false;
            }
            if (start != null && date.isBefore(start)) {
                return false;
            }
            return end == null || !date.isAfter(end);
        }
    }
}
