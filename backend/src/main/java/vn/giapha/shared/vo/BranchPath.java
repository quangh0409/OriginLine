package vn.giapha.shared.vo;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import vn.giapha.shared.domain.ValueObject;

/**
 * Đường dẫn phân cấp chi/ngành/cành/nhánh, ánh xạ thẳng sang kiểu {@code ltree} của PostgreSQL.
 * Đây đồng thời là <b>chiều phân quyền hạng nhất</b>: Trưởng Chi chỉ thao tác được trên cây con
 * nằm dưới path được giao.
 *
 * <p><b>Bẫy đã biết:</b> nhãn {@code ltree} chỉ nhận {@code [A-Za-z0-9_]} — không nhận dấu tiếng
 * Việt, dấu cách hay gạch ngang. Vì vậy path luôn được sinh từ <b>slug không dấu</b>
 * ({@link #label(String)}), còn tên hiển thị ("Chi Ất — Ngành Trưởng") giữ ở cột riêng của bảng
 * {@code branch}. Đừng bao giờ ghép tên tiếng Việt thẳng vào path.</p>
 *
 * <p>Ví dụ: {@code goc.chi_giap.nganh_truong}</p>
 */
public record BranchPath(String value) implements ValueObject {

    private static final Pattern VALID_PATH = Pattern.compile("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$");
    private static final Pattern NON_LABEL_CHARS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern DIACRITIC_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    public BranchPath {
        Objects.requireNonNull(value, "BranchPath khong duoc null");
        if (!VALID_PATH.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "BranchPath khong hop le voi ltree (chi nhan [A-Za-z0-9_] ngan cach boi dau cham): " + value);
        }
    }

    public static BranchPath of(String value) {
        return new BranchPath(value);
    }

    /**
     * Chuyển tên chi/ngành tiếng Việt thành một nhãn {@code ltree} hợp lệ.
     * Ví dụ: {@code "Ngành Trưởng"} → {@code "nganh_truong"}, {@code "Chi Đức"} → {@code "chi_duc"}.
     */
    public static String label(String vietnameseName) {
        Objects.requireNonNull(vietnameseName, "Ten chi/nganh khong duoc null");
        String normalized = Normalizer.normalize(vietnameseName, Normalizer.Form.NFD);
        normalized = DIACRITIC_MARKS.matcher(normalized).replaceAll("");
        normalized = normalized.replace('\u0111', 'd').replace('\u0110', 'D');
        normalized = NON_LABEL_CHARS.matcher(normalized.toLowerCase(Locale.ROOT)).replaceAll("_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Khong sinh duoc nhan ltree tu ten: " + vietnameseName);
        }
        return normalized;
    }

    /** Nối thêm một nhánh con, nhãn được chuẩn hoá tự động. */
    public BranchPath child(String vietnameseName) {
        return new BranchPath(value + "." + label(vietnameseName));
    }

    /** {@code true} nếu path này là tổ tiên của (hoặc trùng) {@code other} — tương đương {@code @>} của ltree. */
    public boolean isAncestorOf(BranchPath other) {
        if (other == null) {
            return false;
        }
        return other.value.equals(value) || other.value.startsWith(value + ".");
    }

    /** Số cấp của path — cũng là chiều sâu chi/ngành. */
    public int depth() {
        return value.split("\\.").length;
    }

    /** Nhãn cuối cùng của path. */
    public String leaf() {
        int lastDot = value.lastIndexOf('.');
        return lastDot < 0 ? value : value.substring(lastDot + 1);
    }

    @Override
    public String toString() {
        return value;
    }
}
