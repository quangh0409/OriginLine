package vn.giapha.genealogy.domain.port;

import java.time.LocalDate;
import vn.giapha.shared.vo.LunarDate;

/**
 * Quy đổi âm ↔ dương để điền nốt vế còn thiếu của một mốc song lịch.
 *
 * <p>Thuật toán thật (Hồ Ngọc Đức, GMT+7, xử lý tháng nhuận và tiết khí) thuộc context
 * {@code calendar} (W4). Genealogy khai báo cổng này để không phải phụ thuộc biên dịch vào một
 * context đang được làm song song; adapter nối dây nằm ở {@code genealogy.infrastructure}.</p>
 *
 * <p><b>Cảnh báo:</b> hiện thực mặc định của Giai đoạn 1 <b>không quy đổi</b> — nó giữ nguyên
 * những gì client gửi. Sai tháng nhuận làm giỗ lệch nguyên một tháng mà không hề có lỗi nào được
 * ném ra, nên thà không đoán còn hơn đoán sai.</p>
 */
public interface LunarCalendarPort {

    /** Âm lịch tương ứng một ngày dương; {@code null} nếu chưa quy đổi được. */
    LunarDate toLunar(LocalDate solar);

    /** Ngày dương tương ứng một ngày âm; {@code null} nếu chưa quy đổi được. */
    LocalDate toSolar(LunarDate lunar);
}
