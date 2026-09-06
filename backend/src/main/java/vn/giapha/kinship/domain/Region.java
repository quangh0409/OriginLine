package vn.giapha.kinship.domain;

/**
 * Vùng miền cho bộ luật cấp {@link RuleScope#REGION}. Cách xưng hô miền Trung/Nam khác miền Bắc ở
 * nhiều chỗ (ví dụ chồng của cô/dì: miền Bắc gọi "chú", miền Trung/Nam gọi "dượng"), nên khác biệt
 * đó là <b>dữ liệu</b> ở bộ REGION chứ không phải nhánh {@code if} trong code.
 */
public enum Region {

    BAC,
    TRUNG,
    NAM;

    public static Region fromDb(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
