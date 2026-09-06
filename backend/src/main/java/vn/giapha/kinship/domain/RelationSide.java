package vn.giapha.kinship.domain;

/**
 * Bên quan hệ — chiều so khớp {@code kinship_rule.side} (migration V3).
 *
 * <ul>
 *   <li>{@link #PATERNAL} — bên nội.</li>
 *   <li>{@link #MATERNAL} — bên ngoại.</li>
 *   <li>{@link #BLOOD} — huyết thống, <b>không</b> phân biệt nội/ngoại. Đóng vai trò
 *       <i>ký tự đại diện</i>: một luật ghi {@code BLOOD} phủ cả PATERNAL lẫn MATERNAL,
 *       nhưng luật ghi đúng bên luôn thắng nhờ {@code priority} nhỏ hơn.</li>
 *   <li>{@link #IN_LAW} — quan hệ qua hôn nhân (dâu/rể/thông gia).</li>
 * </ul>
 *
 * <p>Cách xác định bên (V3 §"Cách xác định side"):
 * {@code genDelta >= 0} thì đi từ ego lên tới LCA — bước đầu tiên qua CHA là PATERNAL, qua MẸ là
 * MATERNAL. {@code genDelta < 0} thì xét nhánh nối vào ego: qua CON TRAI là PATERNAL (cháu nội),
 * qua CON GÁI là MATERNAL (cháu ngoại).</p>
 */
public enum RelationSide {

    PATERNAL,
    MATERNAL,
    BLOOD,
    IN_LAW;

    /**
     * {@code true} nếu giá trị khai báo trên luật này phủ được giá trị thực tế của quan hệ.
     * {@code BLOOD} phủ PATERNAL/MATERNAL/BLOOD; các giá trị còn lại phải khớp chính xác.
     */
    public boolean covers(RelationSide actual) {
        if (actual == null) {
            return false;
        }
        if (this == actual) {
            return true;
        }
        return this == BLOOD && (actual == PATERNAL || actual == MATERNAL);
    }

    /** Suy bên nội/ngoại từ giới tính của người nối trên đường đi. Không rõ giới thì trả BLOOD. */
    public static RelationSide fromParentGender(vn.giapha.shared.vo.Gender gender) {
        if (gender == null) {
            return BLOOD;
        }
        return switch (gender) {
            case MALE -> PATERNAL;
            case FEMALE -> MATERNAL;
            default -> BLOOD;
        };
    }

    public static RelationSide fromDb(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
