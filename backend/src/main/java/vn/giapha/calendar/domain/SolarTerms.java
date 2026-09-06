package vn.giapha.calendar.domain;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * Tính ngày dương lịch của 24 {@link SolarTerm tiết khí} theo múi giờ Việt Nam.
 *
 * <p>Tiết khí là một <b>thời điểm</b>, không phải một ngày: nó là lúc kinh độ Mặt Trời đạt đúng
 * một bội số của 15°. Ngày nào "là" tiết khí thì phụ thuộc múi giờ dùng để cắt ngày, y hệt điểm Sóc.
 * Vì vậy mọi phương thức ở đây đều nhận múi giờ, mặc định GMT+7.</p>
 *
 * <p>POJO thuần, zero dependency. Dùng lại đúng hàm kinh độ Mặt Trời của {@link LunarConverter}
 * để hai nơi không bao giờ lệch nhau.</p>
 *
 * <p><b>Độ chính xác:</b> công thức rút gọn có sai số cỡ 10–15 phút. Khi thời điểm tiết khí rơi sát
 * nửa đêm, ngày trả về có thể lệch một ngày so với số liệu đài thiên văn. Đã đối chiếu 120 mốc của
 * 5 năm mẫu với Đài Thiên văn Hồng Kông: khớp 120/120. Trên diện rộng hơn có đúng một mốc lệch đã
 * biết (Đại tuyết 2020), được ghi trong {@code known-limitations.csv}.</p>
 */
public final class SolarTerms {

    /** Số vòng lặp chia đôi — thừa sức đưa sai số xuống dưới một giây. */
    private static final int BISECTION_STEPS = 60;

    private SolarTerms() {
    }

    /** Ngày dương lịch (GMT+7) của một tiết khí trong năm dương lịch đã cho. */
    public static LocalDate dateOf(SolarTerm term, int solarYear) {
        return dateOf(term, solarYear, LunarConverter.VIETNAM_ZONE_HOURS);
    }

    /** Ngày dương lịch của một tiết khí trong năm dương lịch đã cho, ở múi giờ bất kỳ. */
    public static LocalDate dateOf(SolarTerm term, int solarYear, double zoneOffsetHours) {
        double instant = instantOf(term, solarYear, zoneOffsetHours);
        return LunarConverter.fromJulianDayNumber(localDayNumber(instant, zoneOffsetHours));
    }

    /** Cả 24 tiết khí của một năm dương lịch, theo thứ tự khai báo trong {@link SolarTerm}. */
    public static Map<SolarTerm, LocalDate> allOf(int solarYear) {
        return allOf(solarYear, LunarConverter.VIETNAM_ZONE_HOURS);
    }

    public static Map<SolarTerm, LocalDate> allOf(int solarYear, double zoneOffsetHours) {
        Map<SolarTerm, LocalDate> result = new EnumMap<>(SolarTerm.class);
        for (SolarTerm term : SolarTerm.values()) {
            result.put(term, dateOf(term, solarYear, zoneOffsetHours));
        }
        return result;
    }

    /**
     * Tiết khí đang có hiệu lực vào ngày này — tức mốc gần nhất đã đi qua.
     *
     * <p>Ví dụ ngày 10/4 nằm sau Thanh minh và trước Cốc vũ thì trả về {@link SolarTerm#THANH_MINH}.</p>
     */
    public static SolarTerm at(LocalDate solarDate) {
        return at(solarDate, LunarConverter.VIETNAM_ZONE_HOURS);
    }

    public static SolarTerm at(LocalDate solarDate, double zoneOffsetHours) {
        // Do kinh do Mat Troi luc nua dem CUOI ngay dia phuong (= dau ngay hom sau), khong phai dau
        // ngay. Day la diem de sai nhat cua ca lop:
        //
        //   Tiet khi la mot THOI DIEM trong ngay, gan nhu khong bao gio dung 00:00. dateOf() tra ve
        //   ngay CHUA thoi diem do. Neu at() do luc dau ngay thi tai chinh ngay ay Mat Troi VAN CON
        //   o cung truoc, nen at(dateOf(T)) tra ve tiet khi lien truoc T — sai deu 24/24 moc, va keo
        //   theo isTermDay() khong bao gio dung.
        //
        // Do luc cuoi ngay thi moi thoi diem tiet khi roi trong ngay deu da di qua, va bat bien
        // at(dateOf(T)) == T duoc giu.
        double jd = LunarConverter.toJulianDayNumber(solarDate) + 0.5 - zoneOffsetHours / 24.0;
        double degrees = Math.toDegrees(LunarConverter.sunLongitude(jd));
        int index = (int) Math.floor(degrees / 15.0);
        return SolarTerm.ofLongitude(index * 15);
    }

    /** {@code true} nếu ngày này chính là ngày bắt đầu một tiết khí. */
    public static boolean isTermDay(LocalDate solarDate) {
        SolarTerm term = at(solarDate);
        return dateOf(term, solarDate.getYear()).equals(solarDate)
                || dateOf(term, solarDate.getYear() - 1).equals(solarDate)
                || dateOf(term, solarDate.getYear() + 1).equals(solarDate);
    }

    /** Ngày Thanh minh — mốc chạp mả / tảo mộ của năm. */
    public static LocalDate thanhMinh(int solarYear) {
        return dateOf(SolarTerm.THANH_MINH, solarYear);
    }

    /** Ngày Đông chí — mốc mà luật tháng 11 và tháng nhuận của âm lịch dựa vào. */
    public static LocalDate dongChi(int solarYear) {
        return dateOf(SolarTerm.DONG_CHI, solarYear);
    }

    // ------------------------------------------------------------------ Nội bộ

    /**
     * Thời điểm (số ngày Julius) mà kinh độ Mặt Trời đạt đúng mốc của tiết khí, trong năm dương lịch
     * đã cho tại múi giờ đã cho. Tìm bằng chia đôi trên hàm hiệu kinh độ đã chuẩn hoá về (-180°, 180°].
     */
    private static double instantOf(SolarTerm term, int solarYear, double zoneOffsetHours) {
        double target = term.longitudeDegrees();
        // Moc goc: Xuan phan (0 do) roi vao khoang 20/3. Moi tiet khi cach nhau ~15,22 ngay.
        double equinox = LunarConverter.toJulianDayNumber(LocalDate.of(solarYear, 3, 20));
        int index = Math.floorMod(term.longitudeDegrees(), 360) / 15;
        double guess = equinox + index * 15.2184;

        double instant = bisect(target, guess);
        // Cac tiet khi tu Lap xuan (315 do) den Kinh trap (345 do) roi vao dau nam duong lich,
        // truoc Xuan phan - phai lui mot nam de van nam trong solarYear.
        if (yearOf(instant, zoneOffsetHours) > solarYear) {
            instant = bisect(target, guess - 365.2422);
        } else if (yearOf(instant, zoneOffsetHours) < solarYear) {
            instant = bisect(target, guess + 365.2422);
        }
        return instant;
    }

    private static double bisect(double targetDegrees, double guessJd) {
        double low = guessJd - 20;
        double high = guessJd + 20;
        for (int i = 0; i < BISECTION_STEPS; i++) {
            double mid = (low + high) / 2;
            if (normalizedDifference(mid, targetDegrees) < 0) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2;
    }

    /** Hiệu kinh độ đã chuẩn hoá về khoảng (-180°, 180°] để phép chia đôi không vấp chỗ quay vòng 360°. */
    private static double normalizedDifference(double jd, double targetDegrees) {
        double diff = Math.toDegrees(LunarConverter.sunLongitude(jd)) - targetDegrees;
        while (diff > 180) {
            diff -= 360;
        }
        while (diff <= -180) {
            diff += 360;
        }
        return diff;
    }

    private static long localDayNumber(double instantJd, double zoneOffsetHours) {
        return (long) Math.floor(instantJd + 0.5 + zoneOffsetHours / 24.0);
    }

    private static int yearOf(double instantJd, double zoneOffsetHours) {
        return LunarConverter.fromJulianDayNumber(localDayNumber(instantJd, zoneOffsetHours)).getYear();
    }
}
