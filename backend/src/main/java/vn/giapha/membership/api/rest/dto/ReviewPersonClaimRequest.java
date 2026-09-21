package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Trưởng chi (hoặc vai cao hơn) xử một đơn tự nhận. */
@Schema(description = "Duyệt hoặc từ chối một đơn tự nhận")
public record ReviewPersonClaimRequest(

        @NotNull
        @Schema(description = "true = duyệt. Với đơn NEW_PERSON thì duyệt **tạo nhân khẩu mới**,"
                + " nối vào người thân được chỉ ra, rồi gắn tài khoản — tất cả trong một"
                + " transaction.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean approve,

        @Size(max = 1000)
        @Schema(description = "Lý do. **Bắt buộc khi từ chối** — người gửi có quyền biết vì sao để"
                + " khai lại cho đúng, và không nói lý do là cách chắc chắn nhất để họ gửi lại đúng"
                + " cái đơn ấy. Lưu ý số lần bị từ chối có giới hạn.")
        String note) {
}
