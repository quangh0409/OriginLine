package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Thân yêu cầu phát lời mời.
 *
 * <p>Chỉ một trường bắt buộc, và đó là chủ ý: Trưởng chi đã chọn đích danh người mình mời trong
 * phả, hệ thống không nên bắt họ nói lại điều đó bằng tên, chi và số điện thoại.</p>
 *
 * <p><b>Không có trường vai trò.</b> Nhận lời mời cho ra một tài khoản Thành viên đã gắn nhân khẩu;
 * cấp vai {@code BRANCH_HEAD}/{@code COUNCIL} vẫn đi qua {@code /api/v1/branch-assignments}. Nếu
 * lời mời chở theo vai trò thì một Trưởng chi phát được lời mời mang vai Hội đồng, và luật "chỉ
 * ADMIN mới cấp được ADMIN" bị đi vòng qua một cửa sau.</p>
 */
@Schema(description = "Phát một lời mời đích danh cho một nhân khẩu trong phả")
public record IssueInvitationRequest(

        @NotNull
        @Schema(description = "Nhân khẩu sẽ được ghép khi lời mời được nhận", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID personId,

        @Min(1)
        @Max(30)
        @Schema(description = "Số ngày hiệu lực; bỏ trống dùng mặc định 7 ngày", example = "7")
        Integer ttlDays,

        @Size(max = 500)
        @Schema(description = "Ghi chú của người mời, chỉ hiện trong danh sách quản trị")
        String note) {
}
