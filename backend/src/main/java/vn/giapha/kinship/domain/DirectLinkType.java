package vn.giapha.kinship.domain;

/**
 * Loại cạnh khớp trực tiếp, dùng cho các quan hệ mà LCA vô nghĩa hoặc phải thắng tuyệt đối
 * ({@code kinship_rule.direct_link}).
 *
 * <p>Ánh xạ sang đồ thị AGE: {@code PARENT {type:'BIO'}} · {@code PARENT {type:'ADOPT'}} ·
 * {@code SPOUSE} · {@code HEIR {heir_type:...}}. Trong bảng {@code relationship} tương ứng
 * {@code rel_type} = PARENT_BIO / PARENT_ADOPT / SPOUSE / HEIR.</p>
 */
public enum DirectLinkType {

    SPOUSE,
    PARENT_BIO,
    PARENT_ADOPT,
    HEIR;

    public static DirectLinkType fromDb(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /** Ánh xạ nhãn cạnh AGE + thuộc tính {@code type} sang loại cạnh của rule engine. */
    public static DirectLinkType fromEdge(String edgeLabel, String edgeType) {
        if (edgeLabel == null) {
            return null;
        }
        return switch (edgeLabel.toUpperCase(java.util.Locale.ROOT)) {
            case "SPOUSE" -> SPOUSE;
            case "HEIR" -> HEIR;
            case "PARENT" -> "ADOPT".equalsIgnoreCase(edgeType) ? PARENT_ADOPT : PARENT_BIO;
            default -> null;
        };
    }
}
