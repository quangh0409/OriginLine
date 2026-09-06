package vn.giapha.genealogy.domain;

/**
 * Mức chia sẻ <b>do chính chủ thể chọn</b> (Nghị định 13/2023 · BA v2 §10).
 *
 * <p>Chỉ nới thêm quyền xem cho người khác, không bao giờ thu hẹp quyền của {@code ADMIN}.
 * Khớp {@code ck_person_privacy} ở V2 (cột lưu {@code DEFAULT|TIER_1|TIER_2|TIER_3}) qua
 * {@link #dbValue()} — tên trong contract và tên trong CSDL cố ý khác nhau nên phép ánh xạ
 * nằm gọn ở đây thay vì rải rác trong mapper.</p>
 */
public enum PrivacyLevel {

    /** Theo phân tầng chuẩn BA v2 §10. */
    DEFAULT("DEFAULT"),

    /** Cho thành viên <b>cùng chi/ngành</b> xem thêm Tầng 3 (để tiện liên hệ). */
    BRANCH_OPT_IN("TIER_2"),

    /** Cho <b>mọi thành viên đã đăng nhập</b> xem thêm Tầng 3. */
    CLAN_OPT_IN("TIER_3"),

    /** Siết hơn mặc định: chỉ Tầng 1 kể cả với người cùng chi. Mặc định của trẻ vị thành niên. */
    RESTRICTED("TIER_1");

    private final String dbValue;

    PrivacyLevel(String dbValue) {
        this.dbValue = dbValue;
    }

    /** Giá trị lưu ở cột {@code person.privacy_level} (ràng buộc CHECK của V2). */
    public String dbValue() {
        return dbValue;
    }

    public static PrivacyLevel fromDbValue(String value) {
        if (value == null) {
            return DEFAULT;
        }
        for (PrivacyLevel level : values()) {
            if (level.dbValue.equals(value) || level.name().equals(value)) {
                return level;
            }
        }
        return DEFAULT;
    }
}
