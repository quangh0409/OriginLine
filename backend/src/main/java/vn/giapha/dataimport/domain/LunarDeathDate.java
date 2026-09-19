package vn.giapha.dataimport.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ngày giỗ âm lịch <b>như người nhập gõ vào một ô Excel</b> — chưa được kiểm là có tồn tại không.
 *
 * <h2>Vì sao không dùng thẳng {@code LunarDate} của shared kernel</h2>
 * {@code LunarDate} bắt buộc có {@code year}. Nhưng "mất ngày 15 tháng 8, không rõ năm" là chuyện
 * <b>rất thường</b> trong sổ cũ, và nó vẫn đủ để cúng giỗ — cả họ giỗ theo ngày và tháng âm, năm
 * chỉ dùng khi muốn biết cụ mất năm nào. Ép người nhập bịa ra một năm để qua được ô nhập là cách
 * chắc chắn nhất để có một cuốn gia phả đầy năm sai.
 *
 * <h2>Quy ước gõ (ghi trong trang Hướng dẫn của mẫu)</h2>
 * <pre>
 *   15/8            ngày 15 tháng 8 âm, không rõ năm
 *   15/8 nhuận      ngày 15 tháng 8 nhuận
 *   15/8/1945       có năm
 *   15/8 nhuận 1945 đủ cả
 * </pre>
 * Giải quyết tháng nhuận bằng <b>quy ước gõ</b> chứ không bằng một cột riêng: rẻ hơn, ít cột hơn,
 * và cột thứ mười hai trên một trang đã chật là thứ người nhập sẽ bỏ trống.
 *
 * @param year {@code null} khi sổ chỉ chép ngày và tháng — hợp lệ, không phải thiếu sót
 * @param leap tháng nhuận. <b>Bỏ qua cờ này thì ngày giỗ lệch cả tháng mà không ai báo lỗi.</b>
 */
public record LunarDeathDate(Integer year, int month, int day, boolean leap) {

    /**
     * {@code 15/8}, {@code 15-8}, {@code 15.8} rồi tuỳ chọn năm, và từ "nhuận" đặt ở bất kỳ đâu
     * sau phần ngày/tháng. Chấp cả {@code nhuan} không dấu vì bàn phím điện thoại hay nuốt dấu.
     */
    private static final Pattern MAU = Pattern.compile(
            "^(\\d{1,2})\\s*[/\\-.]\\s*(\\d{1,2})"
                    + "(?:\\s*[/\\-.]?\\s*(\\d{3,4}))?"
                    + "(?:\\s*(nhuận|nhuan|n))?"
                    + "(?:\\s*[/\\-.]?\\s*(\\d{3,4}))?\\s*$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Một con số trần trụi 4–6 chữ số: <b>gần như chắc chắn</b> là số sê-ri ngày tháng của Excel,
     * không phải ngày âm. Xem {@link IssueCode#IMP_LUNAR_DATE_IS_SERIAL}.
     */
    private static final Pattern SO_SE_RI = Pattern.compile("^\\d{4,6}(?:[.,]\\d+)?$");

    /**
     * Dấu mà bộ đọc tệp gắn vào một ô <b>đã bị Excel biến thành ngày dương lịch</b>.
     *
     * <p>Để định dạng ô mặc định thì Excel nuốt {@code 15/8} thành một ngày dương của năm hiện tại.
     * Nếu để bộ đọc hiển thị nó thành 15/08/2026 thì chỗ này sẽ vui vẻ hiểu là ngày 15 tháng 8
     * <b>năm âm 2026</b> — một ngày giỗ sai, không lỗi, không log. Đó là lý do dấu này tồn tại.</p>
     */
    public static final String DAU_NGAY_DUONG = "#EXCEL_DATE#";

    public LunarDeathDate {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Thang am phai trong 1-12, nhan duoc: " + month);
        }
        if (day < 1 || day > 30) {
            throw new IllegalArgumentException("Ngay am phai trong 1-30, nhan duoc: " + day);
        }
    }

    /** Kết quả đọc một ô: hoặc ra ngày, hoặc ra <b>lý do</b> không đọc được. */
    public record KetQua(LunarDeathDate value, IssueCode loi) {

        public boolean thanhCong() {
            return value != null;
        }

        public static KetQua ok(LunarDeathDate value) {
            return new KetQua(Objects.requireNonNull(value), null);
        }

        public static KetQua loi(IssueCode code) {
            return new KetQua(null, Objects.requireNonNull(code));
        }
    }

    /**
     * Đọc ô "Ngày mất âm".
     *
     * <p><b>Không bao giờ đoán.</b> Gặp số sê-ri của Excel thì báo lỗi chứ không quy đổi ngược:
     * quy đổi ngược là đoán ngày giỗ, và ngày giỗ sai không bao giờ tự lộ ra — nó chỉ lặng lẽ
     * khiến cả họ đi cúng nhầm ngày, mỗi năm một lần.</p>
     *
     * @param raw giá trị ô, đã đi qua {@link TextNormalizer#normalize(String)}
     * @return {@link Optional#empty()} khi ô để trống (hợp lệ — người còn sống không có ngày giỗ)
     */
    public static Optional<KetQua> doc(String raw) {
        String s = TextNormalizer.normalize(raw);
        if (s == null) {
            return Optional.empty();
        }
        if (s.startsWith(DAU_NGAY_DUONG) || SO_SE_RI.matcher(s).matches()) {
            return Optional.of(KetQua.loi(IssueCode.IMP_LUNAR_DATE_IS_SERIAL));
        }
        Matcher m = MAU.matcher(s);
        if (!m.matches()) {
            return Optional.of(KetQua.loi(IssueCode.IMP_LUNAR_DATE_UNPARSEABLE));
        }
        int day = Integer.parseInt(m.group(1));
        int month = Integer.parseInt(m.group(2));
        boolean leap = m.group(4) != null;
        String namTruoc = m.group(3);
        String namSau = m.group(5);
        Integer year = namTruoc != null ? Integer.valueOf(namTruoc)
                : namSau != null ? Integer.valueOf(namSau) : null;

        if (day < 1 || day > 30 || month < 1 || month > 12) {
            // Ngay 31 hay thang 13 la sai VE HINH DANG, khong phai "ngay am khong ton tai trong
            // nam do" — hai chuyen khac nhau nen tra ve hai ma khac nhau.
            return Optional.of(KetQua.loi(IssueCode.IMP_LUNAR_DATE_UNPARSEABLE));
        }
        return Optional.of(KetQua.ok(new LunarDeathDate(year, month, day, leap)));
    }

    public boolean coNam() {
        return year != null;
    }

    /** Dạng chuẩn để hiển thị lại cho người nhập, đúng quy ước gõ của trang Hướng dẫn. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append(day).append('/').append(month);
        if (leap) {
            sb.append(" nhuận");
        }
        if (year != null) {
            sb.append('/').append(year);
        }
        return sb.toString();
    }
}
