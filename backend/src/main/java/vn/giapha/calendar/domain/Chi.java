package vn.giapha.calendar.domain;

/**
 * <b>Địa Chi</b> — chu kỳ 12, vế thứ hai của tên can chi, đồng thời là 12 con giáp và 12 canh giờ.
 *
 * <p><b>Khác biệt văn hoá đáng nhớ:</b> chi {@link #MAO} ở Việt Nam là con <b>Mèo</b>, ở Trung Quốc
 * là con Thỏ. Đừng dịch con giáp qua tiếng Anh rồi dịch ngược lại — {@link #zodiacVietnamese()} giữ
 * cách gọi của người Việt.</p>
 *
 * <p>Thứ tự khai báo là thứ tự chu kỳ; {@link #ordinal()} dùng trực tiếp trong công thức.</p>
 */
public enum Chi {

    TY("Tý", "Chuột", 23),
    SUU("Sửu", "Trâu", 1),
    DAN("Dần", "Hổ", 3),
    MAO("Mão", "Mèo", 5),
    THIN("Thìn", "Rồng", 7),
    TY_RAN("Tỵ", "Rắn", 9),
    NGO("Ngọ", "Ngựa", 11),
    MUI("Mùi", "Dê", 13),
    THAN("Thân", "Khỉ", 15),
    DAU("Dậu", "Gà", 17),
    TUAT("Tuất", "Chó", 19),
    HOI("Hợi", "Lợn", 21);

    private final String vietnameseName;
    private final String zodiacVietnamese;
    private final int startHour;

    Chi(String vietnameseName, String zodiacVietnamese, int startHour) {
        this.vietnameseName = vietnameseName;
        this.zodiacVietnamese = zodiacVietnamese;
        this.startHour = startHour;
    }

    /** Tên chi có dấu, ví dụ "Mão". */
    public String vietnameseName() {
        return vietnameseName;
    }

    /** Con giáp theo cách gọi của người Việt, ví dụ "Mèo" cho chi Mão. */
    public String zodiacVietnamese() {
        return zodiacVietnamese;
    }

    /** Giờ bắt đầu của canh giờ này (0–23). Giờ Tý bắt đầu lúc 23h hôm trước. */
    public int startHour() {
        return startHour;
    }

    /** Phần tử thứ {@code index} của chu kỳ, tự quy về khoảng hợp lệ. */
    public static Chi of(long index) {
        return values()[(int) Math.floorMod(index, 12L)];
    }

    /** Canh giờ chứa giờ đồng hồ {@code hour} (0–23). */
    public static Chi ofHour(int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Gio phai trong khoang 0-23, nhan duoc: " + hour);
        }
        return of((hour + 1) / 2);
    }
}
