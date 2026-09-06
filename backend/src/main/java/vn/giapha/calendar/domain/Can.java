package vn.giapha.calendar.domain;

/**
 * <b>Thiên Can</b> — chu kỳ 10, vế thứ nhất của tên can chi.
 *
 * <p>Thứ tự khai báo chính là thứ tự chu kỳ; {@link #ordinal()} dùng trực tiếp trong công thức
 * nên <b>không được đảo</b>.</p>
 */
public enum Can {

    GIAP("Giáp"),
    AT("Ất"),
    BINH("Bính"),
    DINH("Đinh"),
    MAU("Mậu"),
    KY("Kỷ"),
    CANH("Canh"),
    TAN("Tân"),
    NHAM("Nhâm"),
    QUY("Quý");

    private final String vietnameseName;

    Can(String vietnameseName) {
        this.vietnameseName = vietnameseName;
    }

    /** Tên tiếng Việt có dấu, ví dụ "Giáp". */
    public String vietnameseName() {
        return vietnameseName;
    }

    /** Phần tử thứ {@code index} của chu kỳ, tự quy về khoảng hợp lệ. */
    public static Can of(long index) {
        return values()[(int) Math.floorMod(index, 10L)];
    }
}
