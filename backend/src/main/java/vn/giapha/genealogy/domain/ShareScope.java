package vn.giapha.genealogy.domain;

/**
 * <b>Mức chia sẻ của một nhóm trường</b> — do chính chủ thể chọn (Nghị định 13/2023, BA v2 §10).
 *
 * <p>Đây là nửa "người dùng quyết" của mô hình đồng thuận. Nửa còn lại — Khách không thấy người
 * còn sống, trẻ vị thành niên ẩn tối đa, người đã khuất công khai — là <b>ranh giới pháp lý</b>,
 * không nằm trong tay người dùng và không biểu diễn được bằng enum này.</p>
 *
 * <h2>Mặc định là KÍN</h2>
 * Không có giá trị nào ở đây mang nghĩa "theo mặc định hệ thống". Thiếu lựa chọn ⇒ {@link #PRIVATE}.
 * Một nhóm trường mới thêm về sau cũng bắt đầu ở {@code PRIVATE}: người dùng chủ động mở, hệ thống
 * không tự mở hộ.
 *
 * <p><b>Thứ tự khai báo là thứ tự nới dần</b> ({@code PRIVATE < BRANCH < CLAN}) và
 * {@link #atLeast(ShareScope)} dựa vào {@code ordinal()}. Chèn giá trị mới vào giữa là đổi ngữ
 * nghĩa của mọi phép so sánh — chỉ được thêm vào <b>cuối</b> nếu nó rộng hơn {@link #CLAN}.</p>
 */
public enum ShareScope {

    /** <b>Riêng tư</b> — chỉ chính chủ và Hội đồng Tộc biểu / Admin. Mặc định của mọi nhóm trường. */
    PRIVATE,

    /** <b>Cùng chi</b> — thành viên đã đăng nhập nằm trong phạm vi {@code ltree} của chi/ngành. */
    BRANCH,

    /** <b>Cả họ xem</b> — mọi thành viên đã đăng nhập. Khách vẫn không thấy gì. */
    CLAN;

    /** {@code true} nếu mức này rộng bằng hoặc rộng hơn {@code other}. */
    public boolean atLeast(ShareScope other) {
        return other == null || ordinal() >= other.ordinal();
    }

    /** Mức kín hơn trong hai mức — dùng khi phải siết (ví dụ trẻ vị thành niên). */
    public ShareScope narrowestOf(ShareScope other) {
        if (other == null) {
            return this;
        }
        return ordinal() <= other.ordinal() ? this : other;
    }

    /**
     * Đọc từ chuỗi (JSON của cột {@code person.privacy_consent}, body API).
     *
     * <p><b>Không nhận diện được ⇒ {@link #PRIVATE}.</b> Fail-closed là lựa chọn bắt buộc ở đây:
     * một giá trị lạ (dữ liệu hỏng, client cũ, lỗi chính tả) mà bị hiểu thành "cả họ xem" là rò rỉ
     * thầm lặng.</p>
     */
    public static ShareScope fromValue(Object raw) {
        if (raw == null) {
            return PRIVATE;
        }
        if (raw instanceof ShareScope scope) {
            return scope;
        }
        String text = raw.toString().trim().toUpperCase(java.util.Locale.ROOT);
        for (ShareScope scope : values()) {
            if (scope.name().equals(text)) {
                return scope;
            }
        }
        return PRIVATE;
    }
}
