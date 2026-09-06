package vn.giapha.events.domain;

import java.time.LocalDate;
import java.util.Objects;
import vn.giapha.shared.vo.LunarDate;

/**
 * Một <b>lần xảy ra</b> cụ thể của sự kiện: ngày dương đã quy đổi cho một năm nhất định.
 *
 * @param dueSolarDate   ngày dương của lần giỗ/lễ này
 * @param occurrenceYear năm <b>dương lịch</b> của {@code dueSolarDate} — thành phần khoá chống trùng
 *                       {@code ux_reminder_job_occurrence} (xem chú thích cột trong V4)
 * @param resolvedLunar  ngày âm thực sự được dùng sau khi áp chính sách thay thế; {@code null} với
 *                       sự kiện theo dương lịch
 * @param adjustment     vì sao lệch (nếu có) — để nói rõ trong nội dung thông báo
 */
public record EventOccurrence(LocalDate dueSolarDate,
                              int occurrenceYear,
                              LunarDate resolvedLunar,
                              OccurrenceAdjustment adjustment) {

    public EventOccurrence {
        Objects.requireNonNull(dueSolarDate, "dueSolarDate khong duoc null");
        Objects.requireNonNull(adjustment, "adjustment khong duoc null");
    }

    public static EventOccurrence exact(LocalDate solar, LunarDate lunar) {
        return new EventOccurrence(solar, solar.getYear(), lunar, OccurrenceAdjustment.EXACT);
    }

    public static EventOccurrence adjusted(LocalDate solar, LunarDate lunar, OccurrenceAdjustment adjustment) {
        return new EventOccurrence(solar, solar.getYear(), lunar, adjustment);
    }
}
