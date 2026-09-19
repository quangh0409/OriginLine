package vn.giapha.genealogy.domain;

import java.util.Locale;

/**
 * <b>Nhóm trường riêng tư</b> — đơn vị nhỏ nhất mà chủ thể được chọn mức chia sẻ.
 *
 * <p>Năm nhóm này là danh sách <b>đã chốt</b> với Hội đồng Tộc biểu (màn "Hồ sơ người sống" trong
 * {@code design/}). Chúng thay cho mô hình cũ — một enum {@code PrivacyLevel} áp cho cả con người,
 * nới hoặc siết toàn bộ Tầng 3 một lượt. Vẽ ba công tắc trên giao diện rồi ánh xạ ngược về một
 * mức duy nhất là <b>nói dối người dùng về quyền riêng tư của chính họ</b>.</p>
 *
 * <h2>Vì sao khối liên hệ không tách lẻ</h2>
 * {@link #CONTACT} gộp điện thoại · email · Zalo thành <b>một khối</b>. Tách lẻ nghe có vẻ tinh tế
 * hơn nhưng thực tế chỉ tạo ảo giác kiểm soát: ba giá trị này dẫn tới cùng một con người và ai có
 * một cái thường suy ra được cái kia. Chốt một công tắc cho cả khối, đúng như bản thiết kế.
 *
 * <h2>Vì sao ngày sinh đầy đủ đi cùng ảnh</h2>
 * {@link #BIRTH_DETAIL_AND_PHOTO} là hai dữ liệu định danh mạnh nhất còn lại sau liên hệ: ngày
 * sinh đầy đủ (ngày + tháng, không chỉ năm) và ảnh chân dung. Chúng luôn được nhắc tới cùng nhau
 * trong Nghị định 13/2023 và bản thiết kế cũng gộp thành một công tắc.
 *
 * <p><b>Trường KHÔNG thuộc nhóm nào</b> (nguyên quán, tiểu sử, {@code attributes}, các lớp tên
 * phụ) không do người dùng điều khiển; chúng theo luật cố định trong
 * {@code PrivacyTierService} — xem javadoc ở đó. Thêm một nhóm mới vào enum này là thay đổi
 * contract: phải cập nhật {@code contracts/openapi.yaml} và giao diện cùng lúc.</p>
 */
public enum PrivacyFieldGroup {

    /** <b>Nghề nghiệp &amp; nơi làm việc</b> — trường {@code occupation}. */
    OCCUPATION("occupation"),

    /** <b>Nơi ở cấp tỉnh</b> — trường {@code currentPlaceProvince}. */
    RESIDENCE_PROVINCE("residenceProvince"),

    /** <b>Địa chỉ đầy đủ</b> — trường {@code currentPlaceFull}. */
    RESIDENCE_FULL("residenceFull"),

    /** <b>Liên hệ</b> — điện thoại · email · Zalo, đi thành một khối. */
    CONTACT("contact"),

    /** <b>Ngày sinh đầy đủ &amp; ảnh</b> — {@code birth} ở mức ngày, và {@code avatarKey}. */
    BIRTH_DETAIL_AND_PHOTO("birthDetailAndPhoto");

    private final String jsonKey;

    PrivacyFieldGroup(String jsonKey) {
        this.jsonKey = jsonKey;
    }

    /**
     * Khoá dùng trong JSON của contract <b>và</b> trong cột {@code person.privacy_consent}.
     *
     * <p>Cố ý khác tên hằng Java: contract dùng camelCase, còn tên hằng phải đọc được trong code
     * Việt–Anh lẫn lộn. Phép ánh xạ nằm gọn ở đây thay vì rải rác trong mapper.</p>
     */
    public String jsonKey() {
        return jsonKey;
    }

    /** Phân giải từ khoá JSON hoặc tên hằng; không nhận diện được thì {@code null}. */
    public static PrivacyFieldGroup fromKey(String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        for (PrivacyFieldGroup group : values()) {
            if (group.jsonKey.equals(trimmed)
                    || group.name().equals(trimmed.toUpperCase(Locale.ROOT))) {
                return group;
            }
        }
        return null;
    }
}
