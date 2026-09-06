package vn.giapha.kinship.domain;

/**
 * Cấp áp dụng của một bộ luật danh xưng. Thứ tự khai báo <b>chính là</b> thứ tự hợp nhất
 * {@code DEFAULT → REGION → CLAN → BRANCH}: cấp sau ghi đè cấp trước theo {@code relation_code}.
 */
public enum RuleScope {

    /** Bộ gốc do hệ thống sở hữu (miền Bắc). Không sửa được qua API. */
    DEFAULT,
    /** Bộ theo vùng miền Bắc/Trung/Nam. */
    REGION,
    /** Bộ của cả dòng họ. */
    CLAN,
    /** Bộ của một chi/ngành cụ thể. */
    BRANCH;

    /** Độ ưu tiên ghi đè: số lớn hơn ghi đè số nhỏ hơn. */
    public int overrideRank() {
        return ordinal();
    }

    public static RuleScope fromDb(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
