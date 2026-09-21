package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** "Tôi là người này trong phả" — nhận một nhân khẩu đã có. */
@Schema(description = "Đơn tự nhận một nhân khẩu đã có trong phả")
public record SubmitPersonClaimRequest(

        @NotNull
        @Schema(description = "Ô mình vừa chọn trên phả đồ",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID personId,

        @NotBlank
        @Size(max = 32)
        @Schema(description = "Số điện thoại người khai. Bắt buộc: đây là thứ Trưởng chi dùng để"
                + " gọi kiểm chứng. Khi đơn được duyệt, số này thuộc nhóm `contact` của mô hình"
                + " riêng tư — **mặc định kín**, chính chủ tự mở nếu muốn.",
                example = "0912345678", requiredMode = Schema.RequiredMode.REQUIRED)
        String phone,

        @Size(max = 2000)
        @Schema(description = "Vài dòng tự giới thiệu — *con ông nào, bà nào, quê quán*. Đây mới là"
                + " thứ Trưởng chi thật sự dùng để đối chiếu; số điện thoại chỉ giúp gọi kiểm chứng.",
                example = "Cháu là con thứ hai của ông Nguyễn Văn Bốn, quê Đại Lan.")
        String introduction) {
}
