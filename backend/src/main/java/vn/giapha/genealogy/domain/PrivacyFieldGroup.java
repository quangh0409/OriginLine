package vn.giapha.genealogy.domain;

import java.util.Locale;

/**
 * <b>Nhóm trường riêng tư</b> — đơn vị nhỏ nhất mà chủ thể được chọn mức chia sẻ.
 *
 * <p>Năm nhóm đầu là danh sách <b>đã chốt</b> với Hội đồng Tộc biểu (màn "Hồ sơ người sống" trong
 * {@code design/}); nhóm thứ sáu ({@link #HONOUR}) thêm ở V17 theo design/07-checklist §2. Chúng
 * thay cho mô hình cũ — một enum {@code PrivacyLevel} áp cho cả con người,
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
    OCCUPATION("occupation", true),

    /** <b>Nơi ở cấp tỉnh</b> — trường {@code currentPlaceProvince}. */
    RESIDENCE_PROVINCE("residenceProvince", true),

    /** <b>Địa chỉ đầy đủ</b> — trường {@code currentPlaceFull}. */
    RESIDENCE_FULL("residenceFull", true),

    /** <b>Liên hệ</b> — điện thoại · email · Zalo, đi thành một khối. */
    CONTACT("contact", true),

    /** <b>Ngày sinh đầy đủ &amp; ảnh</b> — {@code birth} ở mức ngày, và {@code avatarKey}. */
    BIRTH_DETAIL_AND_PHOTO("birthDetailAndPhoto", true),

    /**
     * <b>Vinh danh</b> (V17) — bảng {@code honour}: đỗ đạt · chức tước · thành tích · khen thưởng.
     *
     * <h2>Vì sao vinh danh là một nhóm trường chứ không phải dữ liệu công khai</h2>
     * Vinh danh của người <b>đã khuất</b> là dữ liệu công khai như mọi dữ liệu người đã khuất — lớp
     * này không đụng tới, {@code PersonVisibility#allows} đã trả {@code true} cho họ. Vinh danh của
     * người <b>còn sống</b> thì khác hẳn: "Thạc sĩ, Đại học Y Hà Nội, 2019" dựng được gần trọn một
     * hồ sơ định danh, và Nghị định 13/2023 xếp nó vào dữ liệu cá nhân. Ép một công tắc riêng cho
     * nó là cách duy nhất để chính chủ quyết — và mô hình V8 làm được điều đó mà không cần di trú
     * dữ liệu, vì nhóm vắng mặt đọc ra {@link ShareScope#PRIVATE}.
     *
     * <p><b>Cái duy nhất phải nhớ khi thêm nhóm:</b> ràng buộc
     * {@code ck_person_privacy_consent} ở CSDL liệt kê tường minh danh sách khoá, nên nhóm mới
     * <i>vẫn</i> cần một migration thay hàm {@code is_valid_privacy_consent} — xem
     * {@code V17__content_post_honour.sql} §17.3. "Không cần migration" đúng với dữ liệu, không
     * đúng với ràng buộc.</p>
     */
    HONOUR("honour", false);

    private final String jsonKey;
    private final boolean grantsDirectoryListing;

    PrivacyFieldGroup(String jsonKey, boolean grantsDirectoryListing) {
        this.jsonKey = jsonKey;
        this.grantsDirectoryListing = grantsDirectoryListing;
    }

    /**
     * Nhóm này có <b>suy ra được</b> từ giá trị {@code privacy_level} cũ (trước V8) hay không.
     *
     * <h2>Chỉ đúng với năm nhóm gốc, và đó là điều phải giữ</h2>
     * Bốn mức cũ nói về <i>toàn bộ Tầng 3</i> như nó tồn tại ở thời điểm ấy, nên ánh xạ chúng sang
     * năm nhóm gốc không lộ thêm gì — {@code PrivacyConsentMigrationIT} đã chứng minh điều đó. Một
     * nhóm <b>thêm về sau</b> ({@link #HONOUR} là nhóm đầu tiên) thì khác hẳn: mức cũ chưa bao giờ
     * nói gì về nó, nên gán cho nó {@code BRANCH}/{@code CLAN} là <b>suy diễn một sự đồng ý chưa
     * từng được đưa ra</b>. Nó phải bắt đầu ở {@link ShareScope#PRIVATE}, đúng như với một hàng
     * chưa từng chạm tới.
     *
     * <p>Hàm SQL {@code privacy_consent_from_legacy(text)} phải khớp <b>từng chữ</b> với nhánh
     * này — xem {@code V17__content_post_honour.sql} §17.4.</p>
     */
    public boolean derivableFromLegacyLevel() {
        return this != HONOUR;
    }

    /**
     * Nhóm này có đủ để một người <b>còn sống</b> xuất hiện trong <i>danh bạ</i> dòng họ hay không.
     *
     * <h2>Vì sao câu hỏi này tồn tại</h2>
     * {@code DirectoryService} cho một người lọt vào danh bạ khi họ đã mở <b>ít nhất một</b> nhóm,
     * và nó duyệt {@code values()} chứ không duyệt một danh sách chép tay — cố ý, để nhóm thêm về
     * sau không bị quên. Nhưng {@link #HONOUR} là nhóm đầu tiên mà câu trả lời đúng là
     * <b>không</b>: danh bạ là chỗ tra <i>người liên hệ được</i> (nghề nghiệp, tỉnh, ảnh), còn vinh
     * danh là thành tích hiện trên trang chủ và trong hồ sơ. Một cụ bật "cho cả họ xem bằng tiến sĩ
     * của tôi" <b>chưa</b> đồng ý có tên trong danh bạ thành viên — và vì mọi trường danh bạ của họ
     * vẫn bị giấu, họ sẽ xuất hiện ở đó như một dòng trống, tức vừa lộ vừa vô dụng.
     *
     * <p>Đặt cờ ở enum chứ không đặt một câu {@code if} trong {@code DirectoryService}: nhóm thứ
     * bảy sẽ buộc người thêm nó phải trả lời câu hỏi này ngay tại chỗ khai báo.</p>
     */
    public boolean grantsDirectoryListing() {
        return grantsDirectoryListing;
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
