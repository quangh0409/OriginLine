package vn.giapha.dataimport.domain;

import java.util.Locale;
import vn.giapha.shared.vo.Gender;

/**
 * Đọc các ô có <b>từ vựng tiếng Việt</b> thành giá trị của miền: giới, còn sống, quan hệ, bậc hôn
 * phối, loại kế tự.
 *
 * <h2>Vì sao rộng rãi ở đây mà nghiêm ở chỗ khác</h2>
 * Danh sách chọn trong Excel <b>không phải bảo mật</b>: dán (Ctrl+V) đè lên một ô bỏ qua toàn bộ
 * data validation, và bảo vệ trang không mật khẩu thì gỡ trong ba giây. Nên server kiểm lại từ
 * đầu — nhưng kiểm lại không có nghĩa là bắt bẻ. "Nam", "nam", "M", "Trai" đều là một người đàn
 * ông, và bắt Trưởng chi sửa 40 ô vì gõ "Trai" là cách nhanh nhất để họ bỏ cuộc ở lần tải thứ ba.
 *
 * <p>Ranh giới: rộng rãi với <b>từ vựng</b>, nghiêm khắc với <b>sự kiện</b>. Đoán "Trai" là nam thì
 * an toàn; đoán một ngày giỗ thì không bao giờ.</p>
 */
public final class CellCodec {

    private CellCodec() {
    }

    /** Quan hệ với cha/mẹ. Ánh xạ sang {@code RelType} của genealogy ở tầng adapter. */
    public enum ParentRel {
        /** Con ruột. */
        BIO,
        /** Con nuôi. Cạnh con nuôi vẫn tính vào đường đi phả hệ. */
        ADOPT
    }

    /** Loại kế tự — trùng đúng {@code heir_type} của lược đồ. */
    public enum HeirType {
        /** Cháu đích tôn. */
        DICH_TON,
        /** Thừa tự. */
        THUA_TU,
        /** Kế tự — lập người nối dõi cho một cụ tuyệt tự. */
        KE_TU
    }

    /** Lý do kết thúc hôn phối. */
    public enum EndReason {
        DIVORCE, DEATH, ANNULLED, OTHER
    }

    /**
     * Giới tính. Ô trống hoặc không hiểu được thì trả {@link Gender#UNKNOWN} — và bộ kiểm sinh
     * {@link IssueCode#IMP_UNKNOWN_GENDER} chứ không chặn: sổ cũ thiếu giới là chuyện thường.
     */
    public static Gender gioi(String raw) {
        String s = key(raw);
        if (s == null) {
            return Gender.UNKNOWN;
        }
        return switch (s) {
            case "nam", "m", "male", "trai", "ong", "con trai" -> Gender.MALE;
            case "nu", "f", "female", "gai", "ba", "con gai" -> Gender.FEMALE;
            default -> Gender.UNKNOWN;
        };
    }

    /**
     * Còn sống.
     *
     * @return {@code null} khi ô trống — <b>khác hẳn</b> với "đã mất". Ô trống nghĩa là người nhập
     *         chưa trả lời, và bộ kiểm phải suy từ sự có mặt của ngày giỗ thay vì mặc định là còn
     *         sống. Mặc định sai ở đây sinh ra một phả đồ toàn người "còn sống" từ đời thứ ba.
     */
    public static Boolean conSong(String raw) {
        String s = key(raw);
        if (s == null) {
            return null;
        }
        return switch (s) {
            case "co", "con", "con song", "song", "x", "yes", "y", "true", "1" -> Boolean.TRUE;
            case "khong", "da mat", "mat", "khong con", "no", "n", "false", "0", "qua doi" ->
                    Boolean.FALSE;
            default -> null;
        };
    }

    /** Quan hệ với cha/mẹ; mặc định là con ruột vì đó là đại đa số. */
    public static ParentRel quanHe(String raw) {
        String s = key(raw);
        if (s == null) {
            return ParentRel.BIO;
        }
        return switch (s) {
            case "nuoi", "con nuoi", "nuoi duong", "adopt", "nghia tu" -> ParentRel.ADOPT;
            default -> ParentRel.BIO;
        };
    }

    /**
     * Bậc hôn phối: "vợ cả" là 1, "vợ hai"/"vợ lẽ" là 2, và một con số thì lấy nguyên.
     *
     * <p>"Vợ lẽ" gộp vào bậc 2 là <b>xấp xỉ</b>, không phải sự thật: một người có thể có vợ cả và
     * hai bà lẽ. Nhưng bịa ra một bậc cho mỗi bà còn tệ hơn, nên khi có từ hai bà cùng bậc thì
     * {@link IssueCode#IMP_SPOUSE_ORDER_CONFLICT} bắt người nhập tự đánh số lại. Máy không tự sửa
     * con số của người.</p>
     */
    public static Integer bac(String raw) {
        String s = key(raw);
        if (s == null) {
            return null;
        }
        Integer so = so(s);
        if (so != null) {
            return so >= 1 ? so : null;
        }
        return switch (s) {
            case "vo ca", "ca", "chinh that", "vo chinh", "nhat" -> 1;
            case "vo hai", "hai", "vo le", "le", "thu hai", "nhi" -> 2;
            case "vo ba", "ba", "thu ba", "tam" -> 3;
            case "vo tu", "tu", "thu tu" -> 4;
            default -> null;
        };
    }

    public static HeirType loaiKeTu(String raw) {
        String s = key(raw);
        if (s == null) {
            return null;
        }
        return switch (s) {
            case "dich ton", "chau dich ton", "dichton" -> HeirType.DICH_TON;
            case "thua tu", "thuatu" -> HeirType.THUA_TU;
            case "ke tu", "ketu", "noi doi", "lap tu" -> HeirType.KE_TU;
            default -> null;
        };
    }

    public static EndReason lyDoKetThuc(String raw) {
        String s = key(raw);
        if (s == null) {
            return null;
        }
        if (s.contains("ly hon") || s.contains("ly di") || s.contains("divorce")) {
            return EndReason.DIVORCE;
        }
        if (s.contains("mat") || s.contains("qua doi") || s.contains("goa") || s.contains("tu tran")) {
            return EndReason.DEATH;
        }
        if (s.contains("huy") || s.contains("annul")) {
            return EndReason.ANNULLED;
        }
        return EndReason.OTHER;
    }

    /**
     * Một số nguyên trong ô.
     *
     * <p>Chịu được đuôi {@code .0} mà Excel hay gắn vào khi ô là kiểu số — POI trả về
     * {@code "1945.0"} chứ không phải {@code "1945"}, và đó là nguồn của một lớp lỗi "năm sinh
     * không đọc được" rất khó hiểu nếu không biết.</p>
     */
    public static Integer so(String raw) {
        String s = TextNormalizer.normalize(raw);
        if (s == null) {
            return null;
        }
        s = s.replace(",", ".");
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        s = s.replaceAll("[^0-9-]", "");
        if (s.isEmpty() || "-".equals(s)) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String key(String raw) {
        String s = ImportColumn.khoa(raw);
        return s.isEmpty() ? null : s.toLowerCase(Locale.ROOT);
    }
}
