package vn.giapha.genealogy.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import vn.giapha.genealogy.domain.ShareScope;

/**
 * <b>Bản đồng thuận riêng tư</b> — sáu nhóm trường, mỗi nhóm một mức độc lập
 * (Nghị định 13/2023, BA v2 §10). Khớp schema {@code PrivacySettings} của
 * {@code contracts/openapi.yaml}.
 *
 * <h2>Ai đọc được khối này</h2>
 * Chỉ <b>chính chủ</b> và {@code ADMIN}. Biết người khác đang siết quyền riêng tư cũng là một dạng
 * rò rỉ, nên với mọi người gọi khác khối này <b>vắng mặt hoàn toàn</b> khỏi JSON — không phải
 * {@code null}, không phải object rỗng.
 *
 * <h2>Ngữ nghĩa khi ghi</h2>
 * <ul>
 *   <li><b>{@code POST /persons}</b> — nhóm vắng mặt ⇒ {@code PRIVATE}. Mặc định là KÍN: hệ thống
 *       không tự mở hộ ai bao giờ.</li>
 *   <li><b>{@code PATCH /persons/{id}}</b> — nhóm vắng mặt ⇒ <b>giữ nguyên</b> mức hiện có, đúng
 *       ngữ nghĩa PATCH của phần còn lại trong contract. Muốn đóng hết thì đưa {@code "privacy"}
 *       vào {@code clearFields}, hoặc gửi tường minh {@code PRIVATE} cho từng nhóm.</li>
 * </ul>
 *
 * <p><b>Đây là ý chí của chủ thể, không phải kết quả cuối.</b> Khách vãng lai vẫn không thấy bất
 * kỳ người còn sống nào dù chọn {@code CLAN}; trẻ vị thành niên vẫn ẩn tối đa; người đã khuất vẫn
 * công khai. Xem {@code PrivacyTierService}.</p>
 *
 * @param occupation          nghề nghiệp &amp; nơi làm việc
 * @param residenceProvince   nơi ở cấp tỉnh
 * @param residenceFull       địa chỉ đầy đủ
 * @param contact             điện thoại · email · Zalo — <b>một khối, không tách lẻ</b>
 * @param birthDetailAndPhoto ngày sinh đầy đủ &amp; ảnh chân dung
 * @param honour              vinh danh (V17) — đỗ đạt · chức tước · thành tích · khen thưởng.
 *        Thêm sau nên <b>client cũ không gửi khoá này</b>, và đó là ca mặc-định-kín chạy thật:
 *        vắng mặt ở {@code POST} ⇒ {@code PRIVATE}, vắng mặt ở {@code PATCH} ⇒ giữ nguyên. Không
 *        có cửa sổ lộ nào trong lúc giao diện chưa dựng xong công tắc thứ sáu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PrivacySettingsDto(ShareScope occupation,
                                 ShareScope residenceProvince,
                                 ShareScope residenceFull,
                                 ShareScope contact,
                                 ShareScope birthDetailAndPhoto,
                                 ShareScope honour) {
}
