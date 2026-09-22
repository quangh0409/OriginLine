package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Thân yêu cầu cấp lại liên kết đặt mật khẩu.
 *
 * <p>Một trường bắt buộc, và nó là <b>nhân khẩu</b> chứ không phải tài khoản: Trưởng chi làm việc
 * trên phả đồ, và phép kiểm phạm vi chi đọc {@code ltree} của nhân khẩu — xem
 * {@code IssuePasswordResetCommand}.</p>
 *
 * <p><b>Không có trường hạn.</b> Hạn là {@code giapha.keycloak.admin.link-ttl} (mặc định 30 phút)
 * và người cấp không chỉnh được: một liên kết đặt lại mật khẩu sống 7 ngày là một liên kết sống lâu
 * hơn cuộc gọi điện đã sinh ra nó, và người cấp không phải người chịu hậu quả nếu nó lọt.</p>
 */
@Schema(description = "Cấp lại liên kết đặt mật khẩu cho một thành viên đã quên mật khẩu")
public record IssuePasswordResetRequest(

        @NotNull
        @Schema(description = "Nhân khẩu của người quên mật khẩu",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID personId,

        @Size(max = 500)
        @Schema(description = "Ghi chú của người cấp, ví dụ \"cụ gọi điện sáng 12/3\"."
                + " KHÔNG ghi số điện thoại hay địa chỉ vào đây — nó vào audit_log vĩnh viễn.")
        String note) {
}
