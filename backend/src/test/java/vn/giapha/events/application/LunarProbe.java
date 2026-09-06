package vn.giapha.events.application;

import static org.junit.jupiter.api.Assertions.fail;

import java.time.LocalDate;
import vn.giapha.calendar.application.LunarCalendarService;
import vn.giapha.shared.vo.LunarDate;

/**
 * Dò dữ liệu âm lịch <b>từ chính bộ quy đổi</b> thay vì chép số liệu vào bài test.
 *
 * <h2>Vì sao không hằng số hoá "năm 2025 nhuận tháng 6"</h2>
 * Bài test ở đây kiểm <b>chính sách nhắc giỗ</b> (không có tháng nhuận thì dùng tháng thường; không
 * có ngày 30 thì lùi 29), không kiểm thuật toán Hồ Ngọc Đức — context {@code calendar} đã có 908
 * bài test đối chiếu fixture cho việc đó. Nhúng một cặp số liệu thiên văn vào đây chỉ tạo ra một
 * bản sao thứ hai của sự thật, và khi nó sai thì bài test đỏ ở đúng chỗ không có lỗi.
 *
 * <p>Cách làm: hỏi {@link LunarCalendarService} xem năm nào <i>thật sự</i> có tháng nhuận ấy, năm
 * nào không, rồi dựng kịch bản quanh câu trả lời. Không dò ra được là hỏng giả định của bài test —
 * {@code fail} ngay chứ không lặng lẽ bỏ qua.</p>
 */
final class LunarProbe {

    /** Dải năm dò: đủ rộng để chắc chắn gặp cả năm nhuận lẫn năm không nhuận (chu kỳ ~19 năm). */
    private static final int FIRST_YEAR = 2000;
    private static final int LAST_YEAR = 2040;

    private final LunarCalendarService calendar;

    LunarProbe(LunarCalendarService calendar) {
        this.calendar = calendar;
    }

    /** Năm âm lịch <b>có</b> tháng {@code month} nhuận. */
    int yearWithLeapMonth(int month) {
        for (int year = FIRST_YEAR; year <= LAST_YEAR; year++) {
            if (calendar.toSolar(new LunarDate(year, month, 1, true)) != null) {
                return year;
            }
        }
        return fail("Khong tim thay nam nao nhuan thang " + month + " trong " + FIRST_YEAR + ".." + LAST_YEAR);
    }

    /** Năm âm lịch <b>không</b> có tháng {@code month} nhuận. */
    int yearWithoutLeapMonth(int month) {
        for (int year = FIRST_YEAR; year <= LAST_YEAR; year++) {
            if (calendar.toSolar(new LunarDate(year, month, 1, true)) == null
                    && calendar.toSolar(new LunarDate(year, month, 1, false)) != null) {
                return year;
            }
        }
        return fail("Khong tim thay nam nao KHONG nhuan thang " + month);
    }

    /** Năm mà tháng {@code month} là <b>tháng thiếu</b> (không có ngày 30). */
    int yearWithShortMonth(int month) {
        for (int year = FIRST_YEAR; year <= LAST_YEAR; year++) {
            if (calendar.toSolar(new LunarDate(year, month, 30, false)) == null
                    && calendar.toSolar(new LunarDate(year, month, 29, false)) != null) {
                return year;
            }
        }
        return fail("Khong tim thay nam nao thang " + month + " thieu ngay 30");
    }

    /** Năm mà tháng {@code month} là <b>tháng đủ</b> (có ngày 30). */
    int yearWithFullMonth(int month) {
        for (int year = FIRST_YEAR; year <= LAST_YEAR; year++) {
            if (calendar.toSolar(new LunarDate(year, month, 30, false)) != null) {
                return year;
            }
        }
        return fail("Khong tim thay nam nao thang " + month + " co ngay 30");
    }

    LocalDate solarOf(int year, int month, int day, boolean leap) {
        return calendar.toSolar(new LunarDate(year, month, day, leap));
    }

    LunarDate lunarOf(LocalDate solar) {
        return calendar.toLunar(solar);
    }
}
