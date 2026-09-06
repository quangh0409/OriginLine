package vn.giapha.audit.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lưới an toàn cuối cùng trước khi một ảnh chụp đi vào {@code audit_log}: giá trị của mọi trường
 * <b>Tầng 3</b> bị thay bằng {@link #REDACTED}, tên trường giữ nguyên.
 *
 * <h2>Vì sao vẫn cần dù bên gọi đã tự lọc</h2>
 * Vì cổng audit nhận {@code Map} tự do. Bất kỳ ai dựng map bằng tay — một service mới, một job nhập
 * liệu, một đoạn debug — đều có thể vô tình chép số điện thoại vào bảng nhật ký. Chặn ở đây là chỗ
 * duy nhất chặn được <i>mọi</i> lối vào.
 *
 * <h2>Giữ tên trường, bỏ giá trị</h2>
 * Đúng theo ghi chú của {@code V5__membership_audit.sql}: "voi hanh dong ANONYMIZE chi ghi ten
 * truong bi xoa, KHONG ghi gia tri". Kiểm toán viên vẫn biết trường nào đã đổi mà không đọc được
 * nội dung.
 *
 * <p><b>Ngoại lệ có chủ ý:</b> {@code birthYear} <b>không</b> bị che — BA v2 §10 xếp năm sinh vào
 * Tầng 2, chỉ ngày sinh đầy đủ mới là Tầng 3.</p>
 */
public final class SensitiveFieldRedactor {

    /** Giá trị thay thế. ASCII thuần để không phụ thuộc encoding khi soi bằng psql. */
    public static final String REDACTED = "***";

    /** Khoá bị che, so sánh sau khi hạ chữ thường và bỏ {@code _}/{@code -}/{@code .}. */
    private static final Set<String> DENIED_KEYS = Set.of(
            "phone", "phonenumber", "mobile", "tel", "email", "zalo", "zaloid",
            "contact", "contactinfo",
            "address", "fulladdress", "currentplacefull", "homeaddress",
            "birth", "birthdate", "birthsolar", "birthlunar", "dateofbirth", "dob",
            "avatar", "avatarkey", "avatarurl", "photo", "photos", "photourl",
            "portrait", "mediakey", "mediaurl",
            "idnumber", "nationalid", "cccd", "cmnd", "passport", "taxcode",
            "password", "secret", "token", "accesstoken", "refreshtoken");

    /**
     * Khoá <b>Tầng 2</b> được miễn trừ tường minh, xét <i>trước</i> phép so khớp chuỗi con.
     *
     * <p>Cần thiết vì phép so chuỗi con quét quá tay: {@code birthYear} chứa {@code birth} nên bị
     * che, trong khi BA v2 §10 xếp <b>năm sinh</b> vào Tầng 2 (chỉ ngày sinh đầy đủ mới là Tầng 3).
     * Che luôn năm sinh không phải là "cẩn thận thêm" — nó làm hỏng nhật ký sửa đời thứ, vì thao
     * tác phả hệ hay dùng nhất chính là sửa năm sinh và khi ấy vết audit không còn nói được gì.</p>
     *
     * <p>Danh sách này phải giữ <b>rất hẹp</b>: mỗi khoá thêm vào là một lỗ khoét trên lưới an
     * toàn cuối cùng, nên chỉ nhận khoá mà BA v2 §10 nói rõ là Tầng 2.</p>
     */
    private static final Set<String> TIER2_ALLOWED_KEYS = Set.of("birthyear", "yearofbirth");

    /** Chiều sâu tối đa khi đệ quy, chặn map tự tham chiếu làm treo luồng ghi audit. */
    private static final int MAX_DEPTH = 8;

    private SensitiveFieldRedactor() {
    }

    /** {@code null} vào thì {@code null} ra — {@code before} của CREATE, {@code after} của xoá. */
    public static Map<String, Object> scrub(Map<String, Object> snapshot) {
        return snapshot == null ? null : scrubMap(snapshot, 0);
    }

    private static Map<String, Object> scrubMap(Map<String, Object> source, int depth) {
        Map<String, Object> clean = new LinkedHashMap<>(source.size());
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key != null && isDenied(key)) {
                clean.put(key, REDACTED);
            } else {
                clean.put(key, scrubValue(entry.getValue(), depth + 1));
            }
        }
        return clean;
    }

    @SuppressWarnings("unchecked")
    private static Object scrubValue(Object value, int depth) {
        if (value == null || depth > MAX_DEPTH) {
            return value == null ? null : REDACTED;
        }
        if (value instanceof Map<?, ?> nested) {
            return scrubMap((Map<String, Object>) nested, depth);
        }
        if (value instanceof Iterable<?> items) {
            List<Object> clean = new ArrayList<>();
            for (Object item : items) {
                clean.add(scrubValue(item, depth + 1));
            }
            return clean;
        }
        return value;
    }

    private static boolean isDenied(String key) {
        String normalized = key.toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace(".", "");
        if (DENIED_KEYS.contains(normalized)) {
            return true;
        }
        if (TIER2_ALLOWED_KEYS.contains(normalized)) {
            // Mien tru tuong minh, xet TRUOC phep so chuoi con ben duoi.
            return false;
        }
        // Bat cac bien the ghep: "contactPhone", "avatar_url_thumb", "spousePhotoKey"...
        for (String denied : DENIED_KEYS) {
            if (denied.length() >= 5 && normalized.contains(denied)) {
                return true;
            }
        }
        return false;
    }
}
