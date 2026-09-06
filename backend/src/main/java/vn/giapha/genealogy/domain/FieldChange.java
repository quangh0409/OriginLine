package vn.giapha.genealogy.domain;

/**
 * Ba trạng thái của một trường trong lệnh cập nhật một phần, thay cho hai trạng thái mà
 * {@code null} biểu diễn được.
 *
 * <ul>
 *   <li>{@link #keep()} — trường <b>vắng mặt</b> trong body ⇒ giữ nguyên giá trị cũ.</li>
 *   <li>{@code set(v)} — trường có giá trị ⇒ ghi đè.</li>
 *   <li>{@link #clear()} — trường có tên trong {@code clearFields} ⇒ xoá trắng về {@code null}.</li>
 * </ul>
 *
 * <p>Đây chính là lý do contract chọn {@code clearFields} thay vì gửi {@code null}: qua các lớp
 * JSON client, "gửi null" và "không gửi" lẫn vào nhau quá dễ. Kiểu này đẩy sự phân biệt đó lên
 * thành thứ trình biên dịch nhìn thấy được, thay vì một quy ước ngầm.</p>
 *
 * @param present {@code true} khi client thực sự nói gì đó về trường này
 * @param value   giá trị mới; {@code null} kèm {@code present = true} nghĩa là xoá trắng
 */
public record FieldChange<T>(boolean present, T value) {

    private static final FieldChange<?> KEEP = new FieldChange<>(false, null);
    private static final FieldChange<?> CLEAR = new FieldChange<>(true, null);

    @SuppressWarnings("unchecked")
    public static <T> FieldChange<T> keep() {
        return (FieldChange<T>) KEEP;
    }

    @SuppressWarnings("unchecked")
    public static <T> FieldChange<T> clear() {
        return (FieldChange<T>) CLEAR;
    }

    public static <T> FieldChange<T> set(T value) {
        return new FieldChange<>(true, value);
    }

    /** Tiện dụng cho mapper: có giá trị thì ghi đè, không có thì giữ nguyên (không bao giờ xoá). */
    public static <T> FieldChange<T> setIfNotNull(T value) {
        return value == null ? keep() : set(value);
    }
}
