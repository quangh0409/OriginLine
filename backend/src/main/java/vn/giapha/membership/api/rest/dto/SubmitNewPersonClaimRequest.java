package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import vn.giapha.membership.domain.RelativeKind;
import vn.giapha.shared.vo.Gender;

/**
 * "Tôi chưa có trong phả" — đơn xin được <b>thêm vào</b> gia phả.
 *
 * <p>Đây là lối <i>ghi vào phả</i>, không phải một biểu mẫu liên hệ: nó cho một người chưa được
 * duyệt khởi tạo việc thêm người vào gia phả. Đơn <b>không tạo nhân khẩu nào</b> lúc gửi — nhân
 * khẩu chỉ ra đời khi Trưởng chi duyệt.</p>
 */
@Schema(description = "Đơn xin được thêm vào phả — con dâu mới về, cháu mới sinh, nhánh ở xa")
public record SubmitNewPersonClaimRequest(

        @NotBlank
        @Size(max = 160)
        @Schema(description = "Họ tên", example = "Trần Thị Mai",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,

        @Min(1800)
        @Max(2200)
        @Schema(description = "Năm sinh. Không bắt buộc, nhưng nó là tín hiệu tốt của bộ dò trùng"
                + " — và bộ dò là thứ giữ cho phả không sinh ra hai bản ghi của cùng một người.",
                example = "1998")
        Integer birthYear,

        @Schema(description = "Giới tính. Cần để dựng được node, và nó quyết định vế nào của cạnh"
                + " hôn phối khi người thân được chỉ ra là vợ/chồng.")
        Gender gender,

        @NotNull
        @Schema(description = "**Người thân đã có trong phả.** Bắt buộc: không có nó thì nhân khẩu"
                + " mới thành node mồ côi — không gắn vào cây, không tính được đời, không tra được"
                + " danh xưng. Người thân ấy cũng là thứ quyết định **ai duyệt**, vì người mới chưa"
                + " thuộc chi nào.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID relativePersonId,

        @NotNull
        @Schema(description = "Quan hệ với người thân ấy. Con cái cố ý không có mặt: \"tôi là cha"
                + " của người đã có trong phả\" chèn một đời lên trên và đánh số lại cả một nhánh"
                + " — việc Trưởng chi phải làm bằng tay, không phải hệ quả phụ của một đơn.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        RelativeKind relativeKind,

        @NotBlank
        @Size(max = 32)
        @Schema(description = "Số điện thoại người khai, để Trưởng chi gọi kiểm chứng",
                example = "0912345678", requiredMode = Schema.RequiredMode.REQUIRED)
        String phone,

        @Size(max = 2000)
        @Schema(description = "Vài dòng tự giới thiệu — thứ Trưởng chi thật sự dùng để đối chiếu",
                example = "Cháu là con dâu mới của ông Nguyễn Văn Bốn, cưới tháng 3 năm nay.")
        String introduction) {
}
