package vn.giapha.demo.generator;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import vn.giapha.calendar.application.LunarCalendarService;
import vn.giapha.shared.vo.LunarDate;

/**
 * Bản cài đặt thật của {@link LunarDateSource}: quy đổi qua
 * {@link LunarCalendarService} — API công khai của context {@code calendar} (W4).
 *
 * <h2>Vì sao dữ liệu giả phải đi qua đúng service thật</h2>
 * <p>Bộ dữ liệu demo có ~3.000 mốc ngày (sinh + mất) trải từ thế kỷ 19 tới nay, đủ tháng nhuận, đủ
 * hai lần đổi múi giờ. Cho chúng chạy qua chính thuật toán Hồ Ngọc Đức đang phục vụ nhắc giỗ nghĩa
 * là mỗi lần sinh dữ liệu là một <b>bài kiểm tra chéo</b> cho W4: converter mà ném lỗi hay trả
 * ngày vô lý thì lộ ra ngay tại đây, chứ không đợi tới lúc cả họ đi giỗ nhầm ngày.</p>
 *
 * <h2>{@code null} là câu trả lời hợp lệ, không phải sự cố</h2>
 * <p>{@code LunarCalendarService} trả {@code null} cho ngày trước
 * {@code VietnamLunarZone.FIRST_RECONSTRUCTIBLE_YEAR} (1813) — lịch cổ Việt Nam không tái lập được
 * bằng công thức thiên văn. Thuỷ tổ bộ dữ liệu sinh năm 1790 nên vài cụ đời 1–2 <b>sẽ</b> không có
 * ngày âm. Đó chính là hiện thực của gia phả chép tay: ngày âm khuyết là chuyện thường, đoán bừa
 * mới là lỗi. Lớp này đếm số ca ấy để {@code DemoDataSeeder} ghi log, không tự bịa ngày thay thế.</p>
 */
public final class CalendarLunarDateSource implements LunarDateSource {

    private final LunarCalendarService calendar;
    private final AtomicInteger converted = new AtomicInteger();
    private final AtomicInteger unconvertible = new AtomicInteger();

    public CalendarLunarDateSource(LunarCalendarService calendar) {
        this.calendar = calendar;
    }

    @Override
    public LunarDate toLunar(LocalDate solar) {
        LunarDate lunar = calendar.toLunar(solar);
        if (lunar == null) {
            unconvertible.incrementAndGet();
        } else {
            converted.incrementAndGet();
        }
        return lunar;
    }

    /** Số mốc quy đổi được. */
    public int converted() {
        return converted.get();
    }

    /** Số mốc phải để trống ngày âm (trước 1813 hoặc ngoài dải thuật toán). */
    public int unconvertible() {
        return unconvertible.get();
    }
}
