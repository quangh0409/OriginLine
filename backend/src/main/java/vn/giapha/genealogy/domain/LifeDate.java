package vn.giapha.genealogy.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import vn.giapha.shared.domain.ValueObject;
import vn.giapha.shared.vo.DateDual;
import vn.giapha.shared.vo.LunarDate;

/**
 * Mốc sinh / mất trong gia phả: <b>song lịch</b> cộng <b>mức chính xác</b>.
 *
 * <p>Bọc {@link DateDual} của shared kernel thay vì thay thế nó — {@code DateDual} bắt buộc phải
 * có ít nhất một trong hai lịch, còn {@link DatePrecision} là thứ contract cần mà shared kernel
 * chưa có (và genealogy không được sửa shared).</p>
 *
 * <p><b>Quy tắc nghiệp vụ:</b> với ngày mất, {@link #lunar()} là <b>nguồn chân lý để tính giỗ</b>;
 * ngày dương chỉ là tham chiếu vì nó đổi theo từng năm.</p>
 *
 * @param dual      cặp ngày dương/âm, không null
 * @param precision mức chính xác của dữ liệu gốc, không null
 */
public record LifeDate(DateDual dual, DatePrecision precision) implements ValueObject {

    public LifeDate {
        Objects.requireNonNull(dual, "LifeDate.dual khong duoc null");
        Objects.requireNonNull(precision, "LifeDate.precision khong duoc null");
    }

    public static LifeDate of(LocalDate solar, LunarDate lunar, DatePrecision precision) {
        DatePrecision effective = precision == null ? DatePrecision.DAY : precision;
        return new LifeDate(new DateDual(solar, lunar, effective != DatePrecision.DAY), effective);
    }

    public static LifeDate ofSolar(LocalDate solar) {
        return of(solar, null, DatePrecision.DAY);
    }

    public static LifeDate ofLunar(LunarDate lunar) {
        return of(null, lunar, DatePrecision.DAY);
    }

    public LocalDate solar() {
        return dual.solar();
    }

    public LunarDate lunar() {
        return dual.lunar();
    }

    public Optional<Integer> year() {
        if (dual.solar() != null) {
            return Optional.of(dual.solar().getYear());
        }
        return dual.lunar() == null ? Optional.empty() : Optional.of(dual.lunar().year());
    }

    /**
     * Hạ mức chi tiết xuống chỉ còn năm — dùng cho phân tầng riêng tư: người còn sống ở Tầng 2
     * chỉ được thấy <b>năm</b> sinh, không thấy ngày đầy đủ.
     *
     * <p>Trả {@code null} khi không suy ra nổi năm nào.</p>
     */
    public LifeDate coarsenToYear() {
        Integer y = year().orElse(null);
        if (y == null) {
            return null;
        }
        return new LifeDate(new DateDual(LocalDate.of(y, 1, 1), null, true), DatePrecision.YEAR);
    }
}
