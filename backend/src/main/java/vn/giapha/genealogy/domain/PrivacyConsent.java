package vn.giapha.genealogy.domain;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import vn.giapha.shared.domain.ValueObject;

/**
 * <b>Bản đồng thuận riêng tư của một nhân khẩu</b>: mỗi {@link PrivacyFieldGroup} một
 * {@link ShareScope} độc lập (Nghị định 13/2023, BA v2 §10).
 *
 * <p>Thay cho {@code PrivacyLevel} cũ — một mức áp cho cả con người, nới hoặc siết toàn bộ Tầng 3
 * một lượt. Ở đây người dùng chọn được nghề nghiệp "cả họ xem", điện thoại "cùng chi", ngày sinh
 * đầy đủ "riêng tư", đúng như bản thiết kế đã hứa.</p>
 *
 * <h2>Ba bất biến, mỗi cái đều có lý do cụ thể</h2>
 * <ol>
 *   <li><b>Bất biến về giá trị (immutable).</b> {@link #with} trả bản sao mới. Một
 *       {@code PrivacyConsent} bị sửa tại chỗ giữa lúc lọc một phản hồi 500 node là loại rò rỉ
 *       không tái hiện được.</li>
 *   <li><b>Không có "chưa chọn".</b> Nhóm vắng mặt trong map luôn đọc ra {@link ShareScope#PRIVATE}
 *       ({@link #scopeOf}). Nhờ vậy thêm một nhóm trường mới ở phiên bản sau <b>tự động</b> bắt đầu
 *       ở mức kín cho toàn bộ dữ liệu đang có — không cần migration, không có cửa sổ lộ.</li>
 *   <li><b>Không có {@code null} ở giá trị.</b> {@code null} bị chuẩn hoá thành {@code PRIVATE}
 *       ngay ở cổng vào, nên không nơi nào phải nhớ kiểm {@code null} trước khi so mức.</li>
 * </ol>
 *
 * <p><b>Lưu ý:</b> đây chỉ là <i>ý chí của chủ thể</i>. Nó không nói gì về Khách (không bao giờ
 * thấy người còn sống), trẻ vị thành niên (ẩn tối đa bất kể chọn gì), hay người đã khuất (công
 * khai, không áp mô hình đồng thuận). Quyết định cuối cùng nằm ở
 * {@code PrivacyTierService}.</p>
 */
public final class PrivacyConsent implements ValueObject {

    /** Mọi nhóm ở mức Riêng tư — trạng thái khởi đầu của mọi nhân khẩu mới. */
    private static final PrivacyConsent ALL_PRIVATE = new PrivacyConsent(new EnumMap<>(PrivacyFieldGroup.class));

    private final EnumMap<PrivacyFieldGroup, ShareScope> scopes;

    private PrivacyConsent(EnumMap<PrivacyFieldGroup, ShareScope> scopes) {
        this.scopes = scopes;
    }

    /** Mặc định là KÍN: mọi nhóm trường ở {@link ShareScope#PRIVATE}. */
    public static PrivacyConsent allPrivate() {
        return ALL_PRIVATE;
    }

    /**
     * Dựng từ một map bất kỳ. Khoá {@code null}, khoá lạ và giá trị {@code null} bị bỏ qua (đọc ra
     * {@code PRIVATE}); mức {@code PRIVATE} không được lưu lại vì nó đã là nghĩa của "vắng mặt".
     */
    public static PrivacyConsent of(Map<PrivacyFieldGroup, ShareScope> values) {
        if (values == null || values.isEmpty()) {
            return ALL_PRIVATE;
        }
        EnumMap<PrivacyFieldGroup, ShareScope> map = new EnumMap<>(PrivacyFieldGroup.class);
        for (Map.Entry<PrivacyFieldGroup, ShareScope> entry : values.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            ShareScope scope = entry.getValue() == null ? ShareScope.PRIVATE : entry.getValue();
            if (scope != ShareScope.PRIVATE) {
                map.put(entry.getKey(), scope);
            }
        }
        return map.isEmpty() ? ALL_PRIVATE : new PrivacyConsent(map);
    }

    /**
     * Đọc từ JSON thô của cột {@code person.privacy_consent} ({@code {"contact":"BRANCH", ...}}).
     *
     * <p>Khoá lạ bị bỏ qua, giá trị lạ đọc thành {@code PRIVATE} — xem
     * {@link ShareScope#fromValue}. Dữ liệu hỏng phải dẫn tới <b>kín hơn</b>, không bao giờ mở
     * hơn.</p>
     */
    public static PrivacyConsent fromJson(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return ALL_PRIVATE;
        }
        EnumMap<PrivacyFieldGroup, ShareScope> map = new EnumMap<>(PrivacyFieldGroup.class);
        for (Map.Entry<String, Object> entry : json.entrySet()) {
            PrivacyFieldGroup group = PrivacyFieldGroup.fromKey(entry.getKey());
            if (group == null) {
                continue;
            }
            ShareScope scope = ShareScope.fromValue(entry.getValue());
            if (scope != ShareScope.PRIVATE) {
                map.put(group, scope);
            }
        }
        return map.isEmpty() ? ALL_PRIVATE : new PrivacyConsent(map);
    }

    /**
     * Chuyển một {@code PrivacyLevel} cũ sang mô hình mới, <b>không làm lộ thêm gì</b> so với luật
     * cũ. Xem {@link PrivacyLevel} để có bảng đối chiếu và lý do.
     */
    public static PrivacyConsent fromLegacy(PrivacyLevel legacy) {
        return legacy == null ? ALL_PRIVATE : legacy.toConsent();
    }

    /** Mức của một nhóm; nhóm chưa từng được chọn ⇒ {@link ShareScope#PRIVATE}. */
    public ShareScope scopeOf(PrivacyFieldGroup group) {
        if (group == null) {
            return ShareScope.PRIVATE;
        }
        ShareScope scope = scopes.get(group);
        return scope == null ? ShareScope.PRIVATE : scope;
    }

    /** Bản sao có một nhóm được đặt lại mức. */
    public PrivacyConsent with(PrivacyFieldGroup group, ShareScope scope) {
        Objects.requireNonNull(group, "PrivacyFieldGroup khong duoc null");
        EnumMap<PrivacyFieldGroup, ShareScope> copy = new EnumMap<>(scopes);
        ShareScope resolved = scope == null ? ShareScope.PRIVATE : scope;
        if (resolved == ShareScope.PRIVATE) {
            copy.remove(group);
        } else {
            copy.put(group, resolved);
        }
        return copy.isEmpty() ? ALL_PRIVATE : new PrivacyConsent(copy);
    }

    /**
     * <b>Hợp nhất</b> một tập thay đổi từng phần: nhóm có mặt trong {@code changes} được đặt lại,
     * nhóm vắng mặt <b>giữ nguyên</b>.
     *
     * <p>Đây là ngữ nghĩa của {@code PATCH /persons/{id}} với khối {@code privacy}: giao diện năm
     * công tắc chỉ cần gửi công tắc vừa gạt. Muốn đóng hết thì gửi {@code clearFields: ["privacy"]}
     * để quay về {@link #allPrivate()}, chứ không phải gửi một object rỗng.</p>
     */
    public PrivacyConsent merge(Map<PrivacyFieldGroup, ShareScope> changes) {
        if (changes == null || changes.isEmpty()) {
            return this;
        }
        PrivacyConsent result = this;
        for (Map.Entry<PrivacyFieldGroup, ShareScope> entry : changes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            result = result.with(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /** {@code true} nếu mọi nhóm đều ở mức Riêng tư. */
    public boolean isAllPrivate() {
        return scopes.isEmpty();
    }

    /** Bản đồ <b>đầy đủ năm nhóm</b> — dùng cho API trả về chính chủ để dựng năm công tắc. */
    public Map<PrivacyFieldGroup, ShareScope> asMap() {
        EnumMap<PrivacyFieldGroup, ShareScope> full = new EnumMap<>(PrivacyFieldGroup.class);
        for (PrivacyFieldGroup group : PrivacyFieldGroup.values()) {
            full.put(group, scopeOf(group));
        }
        return full;
    }

    /**
     * Dạng JSON để ghi xuống cột {@code privacy_consent}.
     *
     * <p>Ghi <b>đủ năm khoá</b> chứ không chỉ khoá khác mặc định: một hàng có đủ khoá đọc được
     * bằng mắt trong {@code psql} khi đi điều tra sự cố riêng tư, và phân biệt được "đã di trú"
     * với "chưa từng chạm tới".</p>
     */
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        for (PrivacyFieldGroup group : PrivacyFieldGroup.values()) {
            json.put(group.jsonKey(), scopeOf(group).name());
        }
        return json;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof PrivacyConsent that && scopes.equals(that.scopes);
    }

    @Override
    public int hashCode() {
        return scopes.hashCode();
    }

    /** Chỉ gồm mức chia sẻ — <b>không</b> chứa dữ liệu cá nhân, nên an toàn để ghi log. */
    @Override
    public String toString() {
        return "PrivacyConsent" + asMap();
    }
}
