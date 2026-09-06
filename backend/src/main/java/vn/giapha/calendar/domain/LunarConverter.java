package vn.giapha.calendar.domain;

import java.time.LocalDate;
import vn.giapha.shared.vo.LunarDate;

/**
 * Quy đổi Âm–Dương theo <b>thuật toán Hồ Ngọc Đức</b>, mặc định múi giờ Việt Nam GMT+7.
 *
 * <p>POJO thuần, <b>zero dependency</b>: không Spring, không JPA, không thư viện ngoài. Toàn bộ
 * là số học nên chạy được ở bất cứ đâu — nhắc giỗ (W5), tra sao hạn (Giai đoạn 3), sinh dữ liệu demo.</p>
 *
 * <h2>Quy luật (Hồ Ngọc Đức, "Thuật toán tính âm lịch")</h2>
 * <ol>
 *   <li>Ngày đầu tháng âm lịch là ngày chứa điểm <b>Sóc</b> (new moon).</li>
 *   <li><b>Đông chí</b> luôn rơi vào tháng 11 âm lịch.</li>
 *   <li>Năm nhuận là năm mà khoảng cách giữa hai điểm Sóc trước hai Đông chí liên tiếp {@code > 365} ngày.</li>
 *   <li>Trong năm nhuận, tháng đầu tiên sau Đông chí <b>không chứa Trung khí</b> là tháng nhuận.</li>
 *   <li>Mọi tính toán quy về kinh tuyến 105° đông, tức GMT+7.</li>
 * </ol>
 *
 * <h2>Vì sao múi giờ là tham số chứ không phải hằng số</h2>
 * <p>Điểm Sóc là một <i>thời điểm</i>; nó rơi vào ngày nào lại phụ thuộc múi giờ dùng để cắt ngày.
 * Lệch một giờ giữa Hà Nội (GMT+7) và Bắc Kinh (GMT+8) đủ để cả tháng âm lịch xê dịch một ngày —
 * và trong vài năm còn làm tháng nhuận rơi vào chỗ khác, khiến hai lịch lệch nhau <b>cả tháng</b>
 * (xem {@code divergence.csv}, đợt 23/11/1984 – 19/4/1985). Ngoài ra âm lịch chính thức của Việt Nam
 * trước 1968 <b>không</b> tính theo GMT+7 — xem {@link VietnamLunarZone}.</p>
 *
 * <h2>Độ chính xác</h2>
 * <p>Công thức Sóc và kinh độ Mặt Trời ở đây là bản rút gọn (Meeus), sai số cỡ vài phút đến ~15 phút.
 * Khi điểm Sóc rơi sát nửa đêm địa phương, kết quả có thể lệch một ngày so với bảng tra chính xác hơn
 * của Hồ Ngọc Đức. Toàn bộ các mốc lệch đã biết được liệt kê trong
 * {@code src/test/resources/lunar/known-limitations.csv} và bị khoá lại bằng test — mốc sớm nhất là
 * năm <b>2054</b>, nên ngày giỗ tính cho hiện tại và vài chục năm tới không bị ảnh hưởng.</p>
 *
 * @see VietnamLunarZone múi giờ lịch sử của âm lịch Việt Nam
 * @see LunarAnniversary quy đổi ngày giỗ hằng năm
 */
public final class LunarConverter {

    /** Múi giờ tính âm lịch Việt Nam hiện hành: kinh tuyến 105° đông. */
    public static final double VIETNAM_ZONE_HOURS = 7.0;

    /** Múi giờ tính âm lịch Trung Quốc: kinh tuyến 120° đông. Dùng để đối chiếu, không phải mặc định. */
    public static final double CHINA_ZONE_HOURS = 8.0;

    /** Cận dưới được hỗ trợ. Trước mốc này Việt Nam dùng các phép lịch cổ không tái lập được bằng công thức. */
    public static final int MIN_SUPPORTED_YEAR = 1800;

    /** Cận trên được hỗ trợ, khớp phạm vi bảng tra chính thức của Hồ Ngọc Đức. */
    public static final int MAX_SUPPORTED_YEAR = 2199;

    private static final double SYNODIC_MONTH = 29.530588853;
    private static final double NEW_MOON_EPOCH_JD = 2415021.076998695;

    private LunarConverter() {
    }

    // ------------------------------------------------------------------ Dương → Âm

    /** Quy đổi ngày dương sang ngày âm theo múi giờ Việt Nam hiện hành (GMT+7). */
    public static LunarDate toLunar(LocalDate solar) {
        return toLunar(solar, VIETNAM_ZONE_HOURS);
    }

    /**
     * Quy đổi ngày dương sang ngày âm ở một múi giờ bất kỳ.
     *
     * @param solar           ngày dương lịch
     * @param zoneOffsetHours độ lệch so với UTC, ví dụ {@code 7.0} cho Việt Nam, {@code 8.0} cho Trung Quốc
     */
    public static LunarDate toLunar(LocalDate solar, double zoneOffsetHours) {
        requireSupported(solar);
        long dayNumber = toJulianDayNumber(solar);
        long monthStart = monthStartOnOrBefore(dayNumber, zoneOffsetHours);

        int solarYear = solar.getYear();
        long a11 = lunarMonth11(solarYear, zoneOffsetHours);
        long b11 = a11;
        int lunarYear;
        if (a11 >= monthStart) {
            lunarYear = solarYear;
            a11 = lunarMonth11(solarYear - 1, zoneOffsetHours);
        } else {
            lunarYear = solarYear + 1;
            b11 = lunarMonth11(solarYear + 1, zoneOffsetHours);
        }

        int lunarDay = (int) (dayNumber - monthStart + 1);
        int diff = (int) ((monthStart - a11) / 29);
        boolean leap = false;
        int lunarMonth = diff + 11;
        if (b11 - a11 > 365) {
            int leapMonthDiff = leapMonthOffset(a11, zoneOffsetHours);
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10;
                leap = diff == leapMonthDiff;
            }
        }
        if (lunarMonth > 12) {
            lunarMonth -= 12;
        }
        if (lunarMonth >= 11 && diff < 4) {
            lunarYear -= 1;
        }
        return new LunarDate(lunarYear, lunarMonth, lunarDay, leap);
    }

    // ------------------------------------------------------------------ Âm → Dương

    /** Quy đổi ngày âm sang ngày dương theo múi giờ Việt Nam hiện hành (GMT+7). */
    public static LocalDate toSolar(LunarDate lunar) {
        return toSolar(lunar, VIETNAM_ZONE_HOURS);
    }

    /**
     * Quy đổi ngày âm sang ngày dương ở một múi giờ bất kỳ.
     *
     * <p><b>Không im lặng làm tròn.</b> Nếu ngày âm không tồn tại — tháng nhuận không có trong năm đó,
     * hoặc ngày 30 của một tháng thiếu — phương thức ném {@link NoSuchLunarDateException} thay vì
     * trả về ngày của tháng kế tiếp. Ngày giỗ tính sai một tháng mà không báo lỗi là kịch bản tệ nhất;
     * bên gọi phải quyết định cách lùi (xem {@link LunarAnniversary}).</p>
     *
     * @throws NoSuchLunarDateException khi ngày âm không tồn tại trong năm âm lịch đó
     */
    public static LocalDate toSolar(LunarDate lunar, double zoneOffsetHours) {
        long monthStart = monthStartJd(lunar, zoneOffsetHours);
        int length = (int) (nextMonthStartJd(lunar, zoneOffsetHours) - monthStart);
        if (lunar.day() > length) {
            throw new NoSuchLunarDateException(
                    "Thang %d%s nam %d chi co %d ngay, khong co ngay %d"
                            .formatted(lunar.month(), lunar.leapMonth() ? " nhuan" : "", lunar.year(), length, lunar.day()));
        }
        return fromJulianDayNumber(monthStart + lunar.day() - 1);
    }

    // ------------------------------------------------------------------ Tra cứu cấu trúc năm âm lịch

    /** {@code true} nếu năm âm lịch có 13 tháng. */
    public static boolean isLeapYear(int lunarYear) {
        return isLeapYear(lunarYear, VIETNAM_ZONE_HOURS);
    }

    public static boolean isLeapYear(int lunarYear, double zoneOffsetHours) {
        return leapMonthOf(lunarYear, zoneOffsetHours) != 0;
    }

    /** Số hiệu tháng nhuận của năm âm lịch, hoặc {@code 0} nếu năm thường. */
    public static int leapMonthOf(int lunarYear) {
        return leapMonthOf(lunarYear, VIETNAM_ZONE_HOURS);
    }

    /**
     * Số hiệu tháng nhuận của năm âm lịch, hoặc {@code 0} nếu năm thường.
     *
     * <p>Tháng nhuận mang tên tháng đứng trước nó, nên "nhuận 2" nghĩa là có hai tháng 2 liên tiếp.</p>
     *
     * <p><b>Bẫy:</b> tháng nhuận <i>không</i> luôn nằm giữa năm. Năm 2033 nhuận <b>tháng 11</b>
     * (bắt đầu 22/12/2033). Vì luật nhuận được xét trên khoảng giữa hai tháng 11 âm lịch liên tiếp,
     * tháng nhuận 1–10 của năm {@code y} nằm ở khoảng {@code [tháng11(y-1), tháng11(y)]}, còn tháng
     * nhuận 11–12 lại nằm ở khoảng sau đó. Phải soi cả hai khoảng, nếu chỉ soi một thì năm 2033
     * bị báo là năm thường.</p>
     */
    public static int leapMonthOf(int lunarYear, double zoneOffsetHours) {
        int inEarlyInterval = leapMonthBetween(
                lunarMonth11(lunarYear - 1, zoneOffsetHours), lunarMonth11(lunarYear, zoneOffsetHours), zoneOffsetHours);
        if (inEarlyInterval >= 1 && inEarlyInterval <= 10) {
            return inEarlyInterval;
        }
        int inLateInterval = leapMonthBetween(
                lunarMonth11(lunarYear, zoneOffsetHours), lunarMonth11(lunarYear + 1, zoneOffsetHours), zoneOffsetHours);
        if (inLateInterval == 11 || inLateInterval == 12) {
            return inLateInterval;
        }
        return 0;
    }

    /** Số hiệu tháng nhuận nằm giữa hai tháng 11 âm lịch liên tiếp, hoặc {@code 0} nếu khoảng đó không nhuận. */
    private static int leapMonthBetween(long a11, long b11, double zoneOffsetHours) {
        if (b11 - a11 <= 365) {
            return 0;
        }
        int month = leapMonthOffset(a11, zoneOffsetHours) - 2;
        if (month < 0) {
            month += 12;
        }
        return month == 0 ? 12 : month;
    }

    /** Số ngày của một tháng âm lịch: 29 (tháng thiếu) hoặc 30 (tháng đủ). */
    public static int lengthOfMonth(LunarDate lunar) {
        return lengthOfMonth(lunar, VIETNAM_ZONE_HOURS);
    }

    public static int lengthOfMonth(LunarDate lunar, double zoneOffsetHours) {
        return (int) (nextMonthStartJd(lunar, zoneOffsetHours) - monthStartJd(lunar, zoneOffsetHours));
    }

    /** {@code true} nếu năm âm lịch đó thật sự có tháng nhuận mang số hiệu này. */
    public static boolean hasLeapMonth(int lunarYear, int month) {
        return hasLeapMonth(lunarYear, month, VIETNAM_ZONE_HOURS);
    }

    public static boolean hasLeapMonth(int lunarYear, int month, double zoneOffsetHours) {
        return leapMonthOf(lunarYear, zoneOffsetHours) == month;
    }

    // ------------------------------------------------------------------ Julian day

    /** Số ngày Julius của một ngày dương lịch (lịch Gregory cho mọi mốc từ 15/10/1582). */
    public static long toJulianDayNumber(LocalDate solar) {
        return julianDayNumber(solar.getDayOfMonth(), solar.getMonthValue(), solar.getYear());
    }

    /** Đảo ngược của {@link #toJulianDayNumber(LocalDate)}. */
    public static LocalDate fromJulianDayNumber(long jd) {
        long b;
        long c;
        if (jd > 2299160) {
            long a = jd + 32044;
            b = (4 * a + 3) / 146097;
            c = a - (b * 146097) / 4;
        } else {
            b = 0;
            c = jd + 32082;
        }
        long d = (4 * c + 3) / 1461;
        long e = c - (1461 * d) / 4;
        long m = (5 * e + 2) / 153;
        int day = (int) (e - (153 * m + 2) / 5 + 1);
        int month = (int) (m + 3 - 12 * (m / 10));
        int year = (int) (b * 100 + d - 4800 + m / 10);
        return LocalDate.of(year, month, day);
    }

    // ------------------------------------------------------------------ Nội bộ

    private static long julianDayNumber(int dd, int mm, int yy) {
        long a = (14 - mm) / 12;
        long y = yy + 4800 - a;
        long m = mm + 12 * a - 3;
        long jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
        }
        return jd;
    }

    /**
     * Ngày mùng một của tháng âm lịch chứa {@code dayNumber}.
     *
     * <p><b>Chỗ này lệch khỏi bản thuật toán được chép lại khắp nơi — và đó là chủ ý.</b>
     * Bản gốc ước lượng chỉ số tuần trăng {@code k} rồi lùi tối đa <i>một</i> bước. Khi điểm Sóc rơi
     * ngay sau nửa đêm địa phương, một bước là không đủ và hàm trả về <b>ngày âm bằng 0</b> — một giá trị
     * vô nghĩa mà không hề báo lỗi. Đã kiểm chứng: bản gốc sinh ra ngày 0 tại 13/4/1877, 16/3/1885,
     * 7/5/2054, 9/4/2062 ở GMT+7 và thêm 7 mốc nữa ở GMT+8, trong đó có <b>26/3/2009</b>.
     * Ở đây lùi bằng vòng lặp cho tới khi tìm được điểm Sóc thật sự không vượt quá {@code dayNumber}.
     * Sau khi sửa, bản hiện thực khớp <i>toàn bộ</i> 18.628 ngày của bảng tra Đài Thiên văn Hồng Kông
     * giai đoạn 1990–2040 ở GMT+8, và không còn ngày âm không hợp lệ nào trong 1800–2200.</p>
     */
    private static long monthStartOnOrBefore(long dayNumber, double zoneOffsetHours) {
        long k = (long) Math.floor((dayNumber - NEW_MOON_EPOCH_JD) / SYNODIC_MONTH);
        long monthStart = newMoonDay(k + 1, zoneOffsetHours);
        while (monthStart > dayNumber) {
            monthStart = newMoonDay(k, zoneOffsetHours);
            k--;
        }
        return monthStart;
    }

    private static long monthStartJd(LunarDate lunar, double zoneOffsetHours) {
        requireSupported(lunar.year());
        long a11;
        long b11;
        if (lunar.month() < 11) {
            a11 = lunarMonth11(lunar.year() - 1, zoneOffsetHours);
            b11 = lunarMonth11(lunar.year(), zoneOffsetHours);
        } else {
            a11 = lunarMonth11(lunar.year(), zoneOffsetHours);
            b11 = lunarMonth11(lunar.year() + 1, zoneOffsetHours);
        }
        long k = (long) Math.floor(0.5 + (a11 - NEW_MOON_EPOCH_JD) / SYNODIC_MONTH);
        int offset = lunar.month() - 11;
        if (offset < 0) {
            offset += 12;
        }
        if (b11 - a11 > 365) {
            int leapOffset = leapMonthOffset(a11, zoneOffsetHours);
            int leapMonth = leapOffset - 2;
            if (leapMonth < 0) {
                leapMonth += 12;
            }
            if (leapMonth == 0) {
                leapMonth = 12;
            }
            if (lunar.leapMonth() && lunar.month() != leapMonth) {
                throw new NoSuchLunarDateException(
                        "Nam am lich %d khong nhuan thang %d (thang nhuan cua nam nay la thang %d)"
                                .formatted(lunar.year(), lunar.month(), leapMonth));
            }
            if (lunar.leapMonth() || offset >= leapOffset) {
                offset += 1;
            }
        } else if (lunar.leapMonth()) {
            throw new NoSuchLunarDateException(
                    "Nam am lich %d la nam thuong, khong co thang nhuan %d".formatted(lunar.year(), lunar.month()));
        }
        return newMoonDay(k + offset, zoneOffsetHours);
    }

    private static long nextMonthStartJd(LunarDate lunar, double zoneOffsetHours) {
        long start = monthStartJd(lunar, zoneOffsetHours);
        long k = (long) Math.floor(0.5 + (start - NEW_MOON_EPOCH_JD) / SYNODIC_MONTH);
        long next = newMoonDay(k + 1, zoneOffsetHours);
        while (next <= start) {
            k++;
            next = newMoonDay(k + 1, zoneOffsetHours);
        }
        return next;
    }

    /** Ngày (theo múi giờ đã cho) chứa điểm Sóc thứ {@code k} tính từ Sóc ngày 1/1/1900. */
    private static long newMoonDay(long k, double zoneOffsetHours) {
        return (long) Math.floor(newMoonInstant(k) + 0.5 + zoneOffsetHours / 24.0);
    }

    /**
     * Thời điểm Sóc thứ {@code k} tính bằng số ngày Julius (Meeus, "Astronomical Algorithms").
     * Giữ nguyên hằng số của bản gốc Hồ Ngọc Đức — đã đối chiếu từng số với bản port Python
     * {@code camlich/cal/amlich.py} (c) 2006 Hồ Ngọc Đức.
     */
    private static double newMoonInstant(long k) {
        double t = k / 1236.85;
        double t2 = t * t;
        double t3 = t2 * t;
        double dr = Math.PI / 180.0;
        double jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3;
        jd1 = jd1 + 0.00033 * Math.sin((166.56 + 132.87 * t - 0.009173 * t2) * dr);
        double sunMeanAnomaly = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3;
        double moonMeanAnomaly = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3;
        double moonLatitudeArg = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3;

        double c1 = (0.1734 - 0.000393 * t) * Math.sin(sunMeanAnomaly * dr)
                + 0.0021 * Math.sin(2 * dr * sunMeanAnomaly);
        c1 = c1 - 0.4068 * Math.sin(moonMeanAnomaly * dr) + 0.0161 * Math.sin(dr * 2 * moonMeanAnomaly);
        c1 = c1 - 0.0004 * Math.sin(dr * 3 * moonMeanAnomaly);
        c1 = c1 + 0.0104 * Math.sin(dr * 2 * moonLatitudeArg)
                - 0.0051 * Math.sin(dr * (sunMeanAnomaly + moonMeanAnomaly));
        c1 = c1 - 0.0074 * Math.sin(dr * (sunMeanAnomaly - moonMeanAnomaly))
                + 0.0004 * Math.sin(dr * (2 * moonLatitudeArg + sunMeanAnomaly));
        c1 = c1 - 0.0004 * Math.sin(dr * (2 * moonLatitudeArg - sunMeanAnomaly))
                - 0.0006 * Math.sin(dr * (2 * moonLatitudeArg + moonMeanAnomaly));
        c1 = c1 + 0.0010 * Math.sin(dr * (2 * moonLatitudeArg - moonMeanAnomaly))
                + 0.0005 * Math.sin(dr * (2 * moonMeanAnomaly + sunMeanAnomaly));

        double deltat;
        if (t < -11) {
            deltat = 0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3;
        } else {
            deltat = -0.000278 + 0.000265 * t + 0.000262 * t2;
        }
        return jd1 + c1 - deltat;
    }

    /**
     * Kinh độ Mặt Trời (radian) tại thời điểm {@code jd}. Dùng chung cho tiết khí và cho luật tháng nhuận.
     * Package-private để {@link SolarTerms} dùng lại — không nhân bản công thức ra hai chỗ.
     */
    static double sunLongitude(double jd) {
        double t = (jd - 2451545.0) / 36525.0;
        double t2 = t * t;
        double dr = Math.PI / 180.0;
        double m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2;
        double l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2;
        double dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * Math.sin(dr * m);
        dl = dl + (0.019993 - 0.000101 * t) * Math.sin(dr * 2 * m) + 0.000290 * Math.sin(dr * 3 * m);
        double l = (l0 + dl) * dr;
        return l - Math.PI * 2 * Math.floor(l / (Math.PI * 2));
    }

    /**
     * Chỉ số cung 30° của Mặt Trời lúc nửa đêm địa phương: 0..11.
     * Hai ngày Sóc liên tiếp cho cùng một chỉ số nghĩa là tháng đó <b>không chứa Trung khí</b> — tháng nhuận.
     */
    private static int sunLongitudeIndex(long dayNumber, double zoneOffsetHours) {
        return (int) Math.floor(sunLongitude(dayNumber - 0.5 - zoneOffsetHours / 24.0) / Math.PI * 6);
    }

    /** Ngày bắt đầu tháng 11 âm lịch (tháng chứa Đông chí) của năm dương {@code solarYear}. */
    private static long lunarMonth11(int solarYear, double zoneOffsetHours) {
        long off = julianDayNumber(31, 12, solarYear) - 2415021L;
        long k = (long) Math.floor(off / SYNODIC_MONTH);
        long newMoon = newMoonDay(k, zoneOffsetHours);
        if (sunLongitudeIndex(newMoon, zoneOffsetHours) >= 9) {
            newMoon = newMoonDay(k - 1, zoneOffsetHours);
        }
        return newMoon;
    }

    /** Vị trí tháng nhuận tính từ tháng 11 âm lịch bắt đầu ngày {@code a11}. */
    private static int leapMonthOffset(long a11, double zoneOffsetHours) {
        long k = (long) Math.floor((a11 - NEW_MOON_EPOCH_JD) / SYNODIC_MONTH + 0.5);
        int i = 1;
        int arc = sunLongitudeIndex(newMoonDay(k + i, zoneOffsetHours), zoneOffsetHours);
        int last;
        do {
            last = arc;
            i++;
            arc = sunLongitudeIndex(newMoonDay(k + i, zoneOffsetHours), zoneOffsetHours);
        } while (arc != last && i < 14);
        return i - 1;
    }

    private static void requireSupported(LocalDate solar) {
        requireSupported(solar.getYear());
    }

    private static void requireSupported(int year) {
        if (year < MIN_SUPPORTED_YEAR || year > MAX_SUPPORTED_YEAR) {
            throw new IllegalArgumentException(
                    "Nam %d nam ngoai pham vi ho tro %d-%d cua bo quy doi am lich"
                            .formatted(year, MIN_SUPPORTED_YEAR, MAX_SUPPORTED_YEAR));
        }
    }
}
