package vn.giapha.genealogy.api.rest.public_.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import vn.giapha.genealogy.api.rest.dto.BranchRefDto;
import vn.giapha.genealogy.api.rest.dto.DateDualDto;
import vn.giapha.genealogy.api.rest.dto.PersonAccessMetaDto;
import vn.giapha.genealogy.api.rest.dto.PersonNameDto;
import vn.giapha.shared.vo.Gender;

/**
 * Hồ sơ một <b>người đã khuất</b> như Khách vãng lai nhìn thấy.
 *
 * <h2>Bản này hẹp hơn {@code PersonDto}, và sự hẹp đó là cơ chế bảo vệ chứ không phải tiện tay</h2>
 * DTO này <b>không có trường nào</b> để chứa dữ liệu Tầng 2/Tầng 3: không {@code contact}, không
 * {@code currentPlaceFull}, không {@code occupation}, không {@code attributes} (JSONB tự do — nội
 * dung do người nhập quyết định, không ai kiểm duyệt được ở tầng này), không {@code privacyLevel},
 * không {@code version}, không {@code createdAt}/{@code updatedAt}.
 * Một lỗi lập trình về sau vì thế <b>không thể</b> làm rò rỉ chúng qua bề mặt công khai — trình
 * biên dịch chặn trước.
 *
 * <h2>{@code meta} — vì sao bề mặt công khai vẫn cần nó</h2>
 * Khách xem được phả đồ công khai; bấm vào một node thì ngăn hồ sơ phải nối được vào
 * {@code /api/v1/public/persons/{id}}. Thiếu {@code meta}, giao diện không có cách nào biết Khách
 * <i>không</i> sửa được hồ sơ này, và lựa chọn duy nhất còn lại là <b>tự dựng {@code meta} ở trình
 * duyệt</b> — tức bịa ra siêu dữ liệu phân quyền do máy chủ sở hữu. Đó mới là thứ nguy hiểm, không
 * phải bản thân trường này.
 *
 * <p>{@code meta} nói về <b>quyền của người gọi</b>, không nói trường nào đang bị giấu — nó không
 * mở thêm một mẩu dữ liệu nhân khẩu nào. Với mọi lượt gọi ở đây nó luôn là quyền của Khách
 * ({@code callerRole = GUEST}, ba cờ {@code can*} đều {@code false}), vì
 * {@code PublicGuestScope.asGuest} đã hạ ngữ cảnh trước khi {@code PrivacyTierService} tính nó —
 * nên {@code Cache-Control: public} vẫn đúng. Giá trị được <b>chép nguyên</b> từ
 * {@code PersonView.access()}; không có bản sao luật riêng tư thứ hai ở đây, và
 * {@code PublicVisibilityGuard} từ chối phục vụ nếu nhận được một {@code meta} không phải của
 * Khách.</p>
 *
 * <p>{@code meta.visibleTier} với người đã khuất luôn là {@code PUBLIC}. <b>Đừng rẽ nhánh theo
 * nó</b> để đoán trường nào có mặt: {@code PUBLIC} không hàm ý Tầng 3 mở (đó đúng là cái bẫy
 * {@code VisibleTier.atLeast()} cũ), và trên bề mặt này thì không trường Tầng 2/3 nào tồn tại cả.</p>
 *
 * <p>{@code isAlive} luôn là {@code false}. Trường này được giữ lại có chủ ý: nó là mệnh đề mà
 * {@code PublicVisibilityGuard} và bộ test ghim vào, chứ không phải thông tin cho giao diện.</p>
 *
 * <p>{@code avatarKey} là <b>khoá đối tượng trên MinIO</b>, không phải URL đã ký và không phải
 * blob. Giai đoạn 1 chưa mở endpoint phát media công khai, nên khoá này tự nó không mở được tệp
 * nào; nếu về sau có endpoint đó thì nó phải tự chịu trách nhiệm phân quyền, đừng coi việc khoá
 * xuất hiện ở đây là đã cấp phép.</p>
 *
 * @param names     mọi lớp tên của người đã khuất (húy / tự / hiệu / thụy / thường gọi / pháp danh)
 *                  — với người đã khuất, tên húy đã hết vai trò kiêng kỵ với người ngoài và trở
 *                  thành dữ liệu phả hệ công khai
 * @param relations quan hệ trực tiếp một bậc, <b>chỉ những cạnh mà đầu kia cũng đã khuất</b>
 * @param meta      quyền của <b>Khách</b> trên hồ sơ này, chép nguyên từ {@code PersonView.access()}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicPersonDto(UUID id,
                              List<PersonNameDto> names,
                              String displayName,
                              Gender gender,
                              Integer generation,
                              @JsonProperty("isAlive") boolean isAlive,
                              DateDualDto birth,
                              DateDualDto death,
                              String nativePlace,
                              String biography,
                              String avatarKey,
                              BranchRefDto primaryBranch,
                              List<PublicRelationDto> relations,
                              PersonAccessMetaDto meta) {
}
