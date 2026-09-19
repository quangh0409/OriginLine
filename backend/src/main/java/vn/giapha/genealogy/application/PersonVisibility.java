package vn.giapha.genealogy.application;

import vn.giapha.genealogy.domain.PrivacyConsent;
import vn.giapha.genealogy.domain.PrivacyFieldGroup;
import vn.giapha.genealogy.domain.ShareScope;

/**
 * <b>Quyết định hiển thị đã tính xong</b> cho <i>một</i> cặp (hồ sơ, người gọi).
 *
 * <h2>Vì sao kiểu này tồn tại</h2>
 * Trước {@code V8}, {@code PrivacyTierService.tierFor()} trả {@code T1} cho Khách nhìn người còn
 * sống <b>thay vì từ chối</b>. Hệ thống an toàn chỉ vì mọi lối vào đều <i>nhớ</i> gọi
 * {@code canSee()} trước — một bất biến bảo mật không được kiểu dữ liệu bảo vệ, và loại bất biến
 * đó hỏng vào đúng ngày có người thêm lối vào thứ mười một.
 *
 * <p>Bây giờ không có đường nào dựng được {@code PersonVisibility} ngoài
 * {@link PrivacyTierService#visibility}, và phương thức ấy trả {@code Optional.empty()} khi người
 * gọi <b>không được biết bản ghi tồn tại</b>. Muốn lọc một hồ sơ thì buộc phải mở {@code Optional}
 * ra — trình biên dịch không cho quên. "Khách nhìn người còn sống" không còn là một trạng thái
 * biểu diễn được.</p>
 *
 * <h2>Ranh giới pháp lý nằm ngoài tay người dùng</h2>
 * {@link #allows} gộp ba luật mà chủ thể <b>không</b> điều khiển được, và chúng luôn thắng mức
 * chia sẻ do chủ thể chọn:
 * <ol>
 *   <li><b>Người đã khuất công khai</b> (BA v2 §10) — không áp mô hình đồng thuận lên người đã
 *       mất. <b>Trừ khối liên hệ:</b> số điện thoại ghi trong hồ sơ một cụ đã mất trên thực tế là
 *       số của người thân đang sống, nên nó vẫn bị giấu.</li>
 *   <li><b>Trẻ vị thành niên ẩn tối đa</b> — chặn trước cả đồng thuận, vì một đứa trẻ không tự
 *       quyết được việc công khai dữ liệu của chính mình.</li>
 *   <li><b>Chính chủ và Hội đồng Tộc biểu/Admin</b> luôn xem được, kể cả khi mọi nhóm ở
 *       {@code PRIVATE} — đó chính là định nghĩa của mức "Riêng tư".</li>
 * </ol>
 *
 * @param deceased      người đã khuất
 * @param self          hồ sơ của chính người đang đăng nhập
 * @param clanWide      vai phạm vi toàn dòng họ ({@code ADMIN} / {@code COUNCIL})
 * @param inBranchScope người gọi cùng chi/ngành với hồ sơ, hoặc được giao quản trị chi đó
 * @param minor         dưới 18 tuổi, suy từ <b>năm</b> sinh
 * @param consent       ý chí của chủ thể; không bao giờ {@code null}
 */
public record PersonVisibility(boolean deceased,
                               boolean self,
                               boolean clanWide,
                               boolean inBranchScope,
                               boolean minor,
                               PrivacyConsent consent) {

    public PersonVisibility {
        consent = consent == null ? PrivacyConsent.allPrivate() : consent;
    }

    /**
     * <b>Câu hỏi duy nhất</b> mà lớp lọc được phép đặt về một nhóm trường.
     *
     * @return {@code true} nếu người gọi được nhìn thấy nhóm trường này
     */
    public boolean allows(PrivacyFieldGroup group) {
        if (group == null) {
            return false;
        }
        if (self || clanWide) {
            return true;
        }
        if (deceased) {
            // Người đã khuất công khai, trừ khối liên hệ — xem javadoc của lớp.
            return group != PrivacyFieldGroup.CONTACT;
        }
        if (minor) {
            return false;
        }
        ShareScope scope = consent.scopeOf(group);
        return switch (scope) {
            case PRIVATE -> false;
            case BRANCH -> inBranchScope;
            case CLAN -> true;
        };
    }

    /**
     * Trường <b>không thuộc nhóm nào</b>: nguyên quán, <i>năm</i> sinh, các lớp tên phụ
     * (húy/tự/hiệu/thụy), tiểu sử và {@code attributes} mở rộng.
     *
     * <p>Người dùng không có công tắc cho chúng, nên luật phải là một hằng số. Hằng số đó là
     * <b>kín nhất có thể</b>: người đã khuất, chính chủ, Hội đồng Tộc biểu/Admin — hết. Thành viên
     * cùng chi <b>không</b> nằm trong danh sách.</p>
     *
     * <h2>Vì sao "cùng chi" bị loại, dù nghe rất hợp lý</h2>
     * Bản nháp đầu của V8 cho thành viên cùng chi đọc nguyên quán / năm sinh / tên phụ, vì đó là
     * chất liệu của chính cây phả hệ. {@code PrivacyConsentMigrationIT} bắt ngay: một người trước
     * V8 chọn {@code RESTRICTED} — tức đã chủ động nói "chỉ Tầng 1, kể cả với người cùng chi" —
     * sau di trú lại <b>lộ thêm</b> ba mảnh đó cho đúng những người họ vừa từ chối. Một cái công
     * tắc mà người dùng không chạm vào được thì không được phép rộng hơn lựa chọn kín nhất mà họ
     * từng thực hiện.
     *
     * <p>Hệ quả cần biết khi dựng giao diện: với người còn sống, thành viên thường <b>không</b>
     * thấy năm sinh trên node phả đồ. Muốn cho họ thấy ngày sinh thì chủ thể bật nhóm
     * {@code BIRTH_DETAIL_AND_PHOTO} — và khi đó là ngày đầy đủ, không phải riêng năm. Đổi lại,
     * không còn nhóm trường nào âm thầm kéo theo nhóm khác.</p>
     */
    public boolean ungroupedFieldsVisible() {
        return deceased || self || clanWide;
    }

    /**
     * Tóm tắt tầng cho {@code meta.visibleTier}. Suy ra từ <b>kết quả</b> của {@link #allows}, nên
     * nó luôn nói thật về thứ người gọi thực sự chạm tới được.
     */
    public VisibleTier tier() {
        if (deceased) {
            return VisibleTier.PUBLIC;
        }
        if (self || clanWide) {
            return VisibleTier.T3;
        }
        if (allows(PrivacyFieldGroup.RESIDENCE_FULL)
                || allows(PrivacyFieldGroup.CONTACT)
                || allows(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO)) {
            return VisibleTier.T3;
        }
        if (allows(PrivacyFieldGroup.OCCUPATION)
                || allows(PrivacyFieldGroup.RESIDENCE_PROVINCE)) {
            return VisibleTier.T2;
        }
        return VisibleTier.T1;
    }
}
