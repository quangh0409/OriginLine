package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.giapha.membership.application.ClanInviteView;

/**
 * Một mã mời dòng họ, nhìn từ màn quản trị của Hội đồng.
 *
 * <p><b>Không chứa mã thô và cũng không chứa băm.</b> Băm không mở được cửa nào, nhưng một trường
 * không màn hình nào dùng thì không có lý do để đi qua dây.</p>
 */
@Schema(description = "Mã mời dòng họ đã phát, nhìn từ màn quản trị của Hội đồng")
public record ClanInviteDto(
        UUID id,

        @Schema(description = "Nhãn nội bộ của mã", example = "Nhóm Zalo họ Nguyễn 2026")
        String label,

        UUID issuedBy,

        @Schema(description = "Trạng thái lưu trữ", allowableValues = {"ACTIVE", "REVOKED"})
        String status,

        @Schema(description = "Trạng thái đã xét đồng hồ VÀ bộ đếm — dùng cái này để hiển thị",
                allowableValues = {"USABLE", "EXPIRED", "REVOKED", "EXHAUSTED"})
        String usability,

        Instant expiresAt,

        @Schema(description = "Số lượt đã dùng. Đây là con số làm cả màn hình này có ích: thấy 400"
                + " lượt trên một dòng họ 600 người thì *biết* mà thu hồi. Không có bộ đếm thì mã"
                + " rò ra và mọi thứ trông vẫn bình thường.",
                example = "37")
        int useCount,

        @Schema(description = "Trần lượt dùng; vắng nghĩa là không đặt trần")
        Integer maxUses,

        @Schema(description = "Số lượt còn lại; vắng cùng lúc với maxUses")
        Integer remainingUses,

        Instant revokedAt,
        String revokedReason,
        String note,
        Instant createdAt) {

    public static ClanInviteDto from(ClanInviteView view) {
        return new ClanInviteDto(view.id(), view.label(), view.issuedBy(), view.status(),
                view.usability().name(), view.expiresAt(), view.useCount(), view.maxUses(),
                view.remainingUses(), view.revokedAt(), view.revokedReason(), view.note(),
                view.createdAt());
    }
}
