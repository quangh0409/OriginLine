package vn.giapha.shared.vo;

import vn.giapha.shared.domain.ValueObject;

/**
 * Ngày <b>âm lịch</b> Việt Nam (múi giờ GMT+7).
 *
 * <p>Đây là nguồn chân lý của ngày giỗ: {@code death_lunar} chứ không phải ngày dương. Ngày dương
 * tương ứng đổi theo từng năm nên chỉ là dữ liệu dẫn xuất.</p>
 *
 * <p><b>Bẫy:</b> {@code leapMonth} bắt buộc phải được giữ đúng. Bỏ qua cờ tháng nhuận thì ngày giỗ
 * lệch cả tháng mà hệ thống không hề báo lỗi. Thuật toán quy đổi (Hồ Ngọc Đức) nằm ở context
 * {@code vn.giapha.calendar}; VO này cố tình không biết cách tự quy đổi.</p>
 *
 * @param year  năm âm lịch
 * @param month tháng âm lịch 1–12
 * @param day   ngày âm lịch 1–30
 * @param leapMonth {@code true} nếu đây là tháng nhuận
 */
public record LunarDate(int year, int month, int day, boolean leapMonth) implements ValueObject {

    public LunarDate {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Thang am lich phai trong khoang 1-12, nhan duoc: " + month);
        }
        if (day < 1 || day > 30) {
            throw new IllegalArgumentException("Ngay am lich phai trong khoang 1-30, nhan duoc: " + day);
        }
    }

    public static LunarDate of(int year, int month, int day) {
        return new LunarDate(year, month, day, false);
    }

    public static LunarDate ofLeap(int year, int month, int day) {
        return new LunarDate(year, month, day, true);
    }

    /** So khớp ngày–tháng (bỏ qua năm) — dùng để tìm giỗ hằng năm. */
    public boolean sameDayAndMonth(LunarDate other) {
        return other != null && other.day == day && other.month == month && other.leapMonth == leapMonth;
    }

    @Override
    public String toString() {
        return "%02d/%02d/%d%s".formatted(day, month, year, leapMonth ? " (nhuận)" : "");
    }
}
