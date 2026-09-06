package vn.giapha.shared.vo;

/**
 * Giới tính của nhân khẩu.
 *
 * <p><b>Quy tắc nghiệp vụ (BA v2 §12):</b> con gái và bên ngoại được ghi nhận đầy đủ và ngang bằng
 * con trai — giá trị này chỉ để mô tả, tuyệt đối không dùng làm điều kiện lọc khi dựng cây.</p>
 */
public enum Gender {

    /** Nam. */
    MALE("M", "Nam"),

    /** Nữ. */
    FEMALE("F", "Nữ"),

    /** Khác. */
    OTHER("O", "Khác"),

    /** Chưa rõ — rất phổ biến với các đời xa chép lại từ gia phả giấy. */
    UNKNOWN("U", "Chưa rõ");

    private final String code;
    private final String labelVi;

    Gender(String code, String labelVi) {
        this.code = code;
        this.labelVi = labelVi;
    }

    public String code() {
        return code;
    }

    public String labelVi() {
        return labelVi;
    }

    public static Gender fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        for (Gender gender : values()) {
            if (gender.code.equalsIgnoreCase(code) || gender.name().equalsIgnoreCase(code)) {
                return gender;
            }
        }
        return UNKNOWN;
    }
}
