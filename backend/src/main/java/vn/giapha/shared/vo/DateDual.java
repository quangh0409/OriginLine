package vn.giapha.shared.vo;

import java.time.LocalDate;
import java.util.Optional;
import vn.giapha.shared.domain.ValueObject;

/**
 * Ngày <b>song lịch</b>: dương lịch và/hoặc âm lịch, kèm cờ "chỉ biết áng chừng".
 *
 * <p>Gia phả thật luôn thiếu dữ liệu: có cụ chỉ còn ngày giỗ âm lịch, có người chỉ nhớ năm sinh.
 * Vì vậy cả hai vế đều cho phép null, nhưng không được null cả hai.</p>
 *
 * @param solar     ngày dương lịch, có thể null
 * @param lunar     ngày âm lịch, có thể null
 * @param approximate {@code true} khi ngày chỉ là ước đoán (ví dụ chỉ biết năm)
 */
public record DateDual(LocalDate solar, LunarDate lunar, boolean approximate) implements ValueObject {

    public DateDual {
        if (solar == null && lunar == null) {
            throw new IllegalArgumentException("DateDual phai co it nhat mot trong hai lich (duong hoac am)");
        }
    }

    public static DateDual ofSolar(LocalDate solar) {
        return new DateDual(solar, null, false);
    }

    public static DateDual ofLunar(LunarDate lunar) {
        return new DateDual(null, lunar, false);
    }

    public static DateDual of(LocalDate solar, LunarDate lunar) {
        return new DateDual(solar, lunar, false);
    }

    public static DateDual approximateSolar(LocalDate solar) {
        return new DateDual(solar, null, true);
    }

    public Optional<LocalDate> solarDate() {
        return Optional.ofNullable(solar);
    }

    public Optional<LunarDate> lunarDate() {
        return Optional.ofNullable(lunar);
    }

    public boolean hasLunar() {
        return lunar != null;
    }

    public boolean hasSolar() {
        return solar != null;
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        if (solar != null) {
            text.append(solar);
        }
        if (lunar != null) {
            text.append(text.isEmpty() ? "" : " | ").append("ÂL ").append(lunar);
        }
        if (approximate) {
            text.append(" (~)");
        }
        return text.toString();
    }
}
