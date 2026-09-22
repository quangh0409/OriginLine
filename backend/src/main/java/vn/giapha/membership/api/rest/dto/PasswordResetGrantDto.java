package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.giapha.membership.application.PasswordResetGrantView;

/**
 * Một lượt cấp lại liên kết đặt mật khẩu, ở dạng đọc.
 *
 * <p>Không có liên kết và không có mảnh nào của token. Liên kết chỉ nằm ở
 * {@link IssuedPasswordResetDto}, và chỉ trong phản hồi của lệnh cấp.</p>
 */
@Schema(description = "Bản ghi một lượt cấp lại liên kết đặt mật khẩu. Không chứa liên kết.")
public record PasswordResetGrantDto(

        UUID id,

        @Schema(description = "Nhân khẩu được cấp lại")
        UUID targetPersonId,

        @Schema(description = "Tên/đời/chi của người được cấp lại — đúng đủ để nhận ra ai")
        TargetDto target,

        UUID branchId,

        @Schema(description = "Tài khoản của người cấp")
        UUID issuedBy,

        Instant issuedAt,

        @Schema(description = "Liên kết hết hiệu lực lúc này")
        Instant expiresAt,

        @Schema(description = "Null nghĩa là người ấy CHƯA đặt lại mật khẩu — tức liên kết"
                + " có thể chưa tới được tay họ")
        Instant usedAt,

        Instant revokedAt,

        String note) {

    /** Người được cấp lại, ở mức đúng đủ để nhận ra ai trong một danh sách. */
    @Schema(description = "Tóm tắt nhân khẩu: tên, đời, tên chi. Không năm sinh, không liên hệ.")
    public record TargetDto(String displayName, Integer generation, String branchName) {

        static TargetDto from(PasswordResetGrantView.TargetSummary summary) {
            return summary == null ? null : new TargetDto(summary.displayName(),
                    summary.generation(), summary.branchName());
        }
    }

    public static PasswordResetGrantDto from(PasswordResetGrantView view) {
        return new PasswordResetGrantDto(view.id(), view.targetPersonId(),
                TargetDto.from(view.target()), view.branchId(), view.issuedBy(), view.issuedAt(),
                view.expiresAt(), view.usedAt(), view.revokedAt(), view.note());
    }
}
