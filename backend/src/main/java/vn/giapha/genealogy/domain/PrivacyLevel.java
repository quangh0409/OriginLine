package vn.giapha.genealogy.domain;

/**
 * <b>DI SẢN — đã bị {@link PrivacyConsent} thay thế.</b>
 *
 * <p>Enum này từng là mức chia sẻ áp cho <b>cả con người</b>: bốn giá trị nới hoặc siết toàn bộ
 * Tầng 3 một lượt. Bản thiết kế đã duyệt (màn "Hồ sơ người sống") hứa người dùng chọn được
 * <b>từng nhóm trường</b> — nghề nghiệp "cả họ xem", điện thoại "cùng chi", ngày sinh đầy đủ
 * "riêng tư" — và mô hình một mức không diễn đạt nổi điều đó. Vẽ năm công tắc trên giao diện rồi
 * ánh xạ ngược về một enum là nói dối người dùng về quyền riêng tư của chính họ, nên không có lựa
 * chọn "làm gần đúng".</p>
 *
 * <p><b>Vai trò duy nhất còn lại:</b> đọc cột di sản {@code person.privacy_level} và dịch sang
 * {@link PrivacyConsent} qua {@link #toConsent()}. Không lối nào trong hệ thống còn dùng nó để
 * quyết định hiển thị. Contract API đã bỏ hẳn trường {@code privacyLevel}; xem
 * {@code contracts/openapi.yaml} schema {@code PrivacySettings}.</p>
 *
 * <h2>Bảng di trú — và vì sao nó lệch về phía kín</h2>
 * <table>
 *   <caption>Ánh xạ {@code PrivacyLevel → PrivacyConsent}</caption>
 *   <tr><th>Giá trị cũ</th><th>Năm nhóm trường</th><th>Lý do</th></tr>
 *   <tr><td>{@code DEFAULT}</td><td>tất cả {@code PRIVATE}</td>
 *       <td><b>Không phải lựa chọn của người dùng</b> — nó là sự <i>vắng mặt</i> của lựa chọn.
 *       Việc mở Tầng 2 cho người cùng chi là quyết định của hệ thống, không phải đồng thuận của
 *       chủ thể. Mô hình mới mặc định kín, nên "chưa chọn" phải thành "riêng tư"; thà hỏi lại còn
 *       hơn lộ.</td></tr>
 *   <tr><td>{@code RESTRICTED}</td><td>tất cả {@code PRIVATE}</td>
 *       <td>Chủ thể đã chủ động siết; giữ nguyên ý chí đó.</td></tr>
 *   <tr><td>{@code BRANCH_OPT_IN}</td><td>tất cả {@code BRANCH}</td>
 *       <td>Đồng thuận rõ ràng "cho người cùng chi xem". Luật cũ cho người cùng chi thấy trọn
 *       Tầng 3, nên năm nhóm ở mức {@code BRANCH} <b>không lộ thêm gì</b>.</td></tr>
 *   <tr><td>{@code CLAN_OPT_IN}</td><td>tất cả {@code CLAN}</td>
 *       <td>Đồng thuận rõ ràng "cho cả họ xem". Luật cũ cho mọi thành viên đã đăng nhập thấy trọn
 *       Tầng 3, nên năm nhóm ở mức {@code CLAN} cũng không lộ thêm gì.</td></tr>
 * </table>
 *
 * <p>Phép di trú này <b>đơn điệu theo hướng kín</b>: với bốn giá trị cũ, tập trường mà một người
 * xem bất kỳ nhìn thấy sau di trú luôn là tập con của tập trước đó (tiểu sử và
 * {@code attributes} của người còn sống bị siết thêm về mức chỉ chính chủ + Hội đồng, vì chúng
 * không thuộc nhóm trường nào mà người dùng điều khiển được). Bất biến này được canh bằng
 * {@code PrivacyConsentMigrationIT}.</p>
 *
 * @deprecated dùng {@link PrivacyConsent}. Giữ lại để đọc dữ liệu cũ và để migration {@code V8}
 *         đối chiếu; sẽ gỡ khi cột {@code person.privacy_level} được drop ở một phiên bản sau.
 */
@Deprecated(since = "V8", forRemoval = true)
public enum PrivacyLevel {

    /** Theo phân tầng chuẩn BA v2 §10 — <b>sự vắng mặt của lựa chọn</b>, không phải đồng thuận. */
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

    /** Giá trị lưu ở cột di sản {@code person.privacy_level} (ràng buộc CHECK của V2). */
    public String dbValue() {
        return dbValue;
    }

    /**
     * Bản đồng thuận tương đương ở mô hình mới. Xem bảng di trú trong javadoc của lớp.
     *
     * <p>Phải khớp <b>từng dòng</b> với hàm SQL {@code privacy_consent_from_legacy(text)} trong
     * {@code V8__privacy_consent.sql}: hàm đó di trú dữ liệu đã có, còn phương thức này di trú
     * những hàng mà tiến trình đọc lên sau đó. Lệch nhau là hai người xem cùng một hồ sơ thấy hai
     * kết quả khác nhau tuỳ đường đi.</p>
     */
    public PrivacyConsent toConsent() {
        ShareScope scope = switch (this) {
            case DEFAULT, RESTRICTED -> ShareScope.PRIVATE;
            case BRANCH_OPT_IN -> ShareScope.BRANCH;
            case CLAN_OPT_IN -> ShareScope.CLAN;
        };
        PrivacyConsent consent = PrivacyConsent.allPrivate();
        for (PrivacyFieldGroup group : PrivacyFieldGroup.values()) {
            // Nhom THEM VE SAU khong duoc suy ra tu muc cu: muc cu chua bao gio noi gi ve no,
            // nen gan cho no BRANCH/CLAN la SUY DIEN MOT SU DONG Y CHUA TUNG DUOC DUA RA.
            // Bat dau tu HONOUR (V17). Day cung la nhanh ma ham SQL
            // privacy_consent_from_legacy() phai khop tung chu — PrivacyConsentMigrationIT canh.
            consent = consent.with(group, group.derivableFromLegacyLevel() ? scope : ShareScope.PRIVATE);
        }
        return consent;
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
