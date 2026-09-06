package vn.giapha.calendar.domain;

import java.time.LocalDate;

/**
 * Múi giờ dùng để tính <b>âm lịch pháp định của Việt Nam</b> qua từng thời kỳ.
 *
 * <p>Nguồn: Hồ Ngọc Đức, <i>"Âm lịch Việt Nam qua các thời kỳ lịch sử"</i>, dựa trên công trình
 * <i>Lịch và lịch Việt Nam</i> của Hoàng Xuân Hãn.</p>
 *
 * <h2>Vì sao gia phả cần thứ này</h2>
 * <p>Ngày giỗ của các cụ được ghi theo <b>cuốn lịch đang lưu hành lúc đó</b>. Cụ mất năm 1965 thì
 * ngày âm trong gia phả là ngày âm của lịch 1965 — mà lịch miền Bắc năm 1965 tính theo <b>múi giờ
 * thứ 8</b>, không phải GMT+7. Nếu hệ thống cứ lấy GMT+7 quy đổi ngược ngày dương của cụ sang ngày âm
 * thì ra một ngày âm khác với ngày ghi trong gia phả, và mọi năm sau đó cả họ giỗ lệch một ngày.
 * Đây là loại sai không bao giờ tự lộ ra: không có exception, không có log, chỉ có ngày sai.</p>
 *
 * <h2>Các mốc</h2>
 * <table border="1">
 *   <caption>Múi giờ tính âm lịch pháp định</caption>
 *   <tr><th>Thời kỳ</th><th>Múi giờ</th><th>Ghi chú</th></tr>
 *   <tr><td>1813–1945</td><td>+8</td><td>Lịch Hiệp Kỷ, trùng lịch Trung Quốc</td></tr>
 *   <tr><td>1946–1967</td><td>+8</td><td>Làm lịch theo Vạn niên thư; miền Nam cũng dùng múi 8</td></tr>
 *   <tr><td>1968–1975</td><td>+7</td><td>Miền Bắc chuyển sang múi 7; <b>miền Nam vẫn múi 8</b></td></tr>
 *   <tr><td>Từ 1976</td><td>+7</td><td>Cả nước dùng múi 7</td></tr>
 * </table>
 *
 * <p>Giai đoạn 1968–1975 hai miền dùng hai lịch khác nhau. Lớp này trả về múi giờ của <b>lịch miền
 * Bắc</b> — đúng như bảng tra chính thức của Hồ Ngọc Đức. Nếu dòng họ ở miền Nam và có ngày giỗ rơi
 * vào giai đoạn này thì phải hỏi lại gia đình, đừng đoán: dùng {@link #hasTwoOfficialCalendars(LocalDate)}
 * để phát hiện và cảnh báo người nhập liệu.</p>
 *
 * <p>Trước 1813 (kỳ 1645–1812 Việt Nam dùng lịch Đại Thống riêng, và xa hơn nữa) âm lịch Việt Nam
 * <b>không tái lập được</b> bằng công thức thiên văn hiện đại ở bất kỳ múi giờ nào — phải tra bảng
 * của Hoàng Xuân Hãn. Xem {@link #isReconstructible(LocalDate)}.</p>
 */
public final class VietnamLunarZone {

    /** Từ năm này âm lịch miền Bắc được tính theo múi giờ thứ 7 (nghị định đổi giờ 8/8/1967). */
    public static final int FIRST_YEAR_GMT7 = 1968;

    /** Từ năm này cả nước thống nhất tính âm lịch theo múi giờ thứ 7. */
    public static final int FIRST_YEAR_NATIONWIDE_GMT7 = 1976;

    /** Trước năm này, lịch Việt Nam là lịch cổ, không tái lập được bằng công thức thiên văn. */
    public static final int FIRST_RECONSTRUCTIBLE_YEAR = 1813;

    private VietnamLunarZone() {
    }

    /**
     * Múi giờ cần dùng khi quy đổi ngày dương {@code solarDate} sang âm lịch pháp định của Việt Nam.
     *
     * @return {@code 7.0} từ 1968 trở đi, {@code 8.0} trước đó
     */
    public static double offsetHoursFor(LocalDate solarDate) {
        return solarDate.getYear() >= FIRST_YEAR_GMT7
                ? LunarConverter.VIETNAM_ZONE_HOURS
                : LunarConverter.CHINA_ZONE_HOURS;
    }

    /** Múi giờ theo năm dương lịch. Tiện cho các chỗ chỉ biết năm. */
    public static double offsetHoursForYear(int solarYear) {
        return solarYear >= FIRST_YEAR_GMT7 ? LunarConverter.VIETNAM_ZONE_HOURS : LunarConverter.CHINA_ZONE_HOURS;
    }

    /**
     * {@code true} nếu ngày này rơi vào giai đoạn hai miền dùng <b>hai lịch chính thức khác nhau</b>
     * (1968–1975). Tầng nhập liệu nên hỏi lại người dùng thay vì tự chọn một lịch.
     */
    public static boolean hasTwoOfficialCalendars(LocalDate solarDate) {
        int year = solarDate.getYear();
        return year >= FIRST_YEAR_GMT7 && year < FIRST_YEAR_NATIONWIDE_GMT7;
    }

    /**
     * {@code true} nếu âm lịch Việt Nam của ngày này tái lập được bằng công thức thiên văn.
     * Trước 1813 phải tra bảng lịch cổ; kết quả tính máy chỉ là xấp xỉ và không được coi là chuẩn.
     */
    public static boolean isReconstructible(LocalDate solarDate) {
        return solarDate.getYear() >= FIRST_RECONSTRUCTIBLE_YEAR;
    }
}
