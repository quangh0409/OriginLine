package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.giapha.membership.application.InvitationView;

/**
 * Lời mời trong <b>danh sách quản trị của người mời</b>.
 *
 * <p>Không có mã, và cũng không có băm của mã: băm là bí mật dẫn xuất, một bản băm lọt ra ngoài cho
 * phép kiểm chứng offline xem một mã đoán được có đúng không — tức bẻ mất lớp giới hạn tần suất.</p>
 *
 * <p>{@link #usability()} là thứ giao diện nên hiển thị, <b>không</b> phải {@link #status()}:
 * {@code PENDING} của một mã đã quá hạn không nói lên điều gì cho người đang nhìn danh sách.</p>
 */
@Schema(description = "Lời mời đã phát, nhìn từ phía người mời")
public record InvitationDto(
        UUID id,
        UUID personId,
        UUID branchId,
        UUID invitedBy,

        @Schema(description = "Trạng thái lưu trữ", allowableValues = {"PENDING", "ACCEPTED", "REVOKED"})
        String status,

        @Schema(description = "Trạng thái đã xét đồng hồ — dùng cái này để hiển thị",
                allowableValues = {"USABLE", "EXPIRED", "ALREADY_USED", "REVOKED"})
        String usability,

        Instant expiresAt,
        UUID acceptedBy,
        Instant acceptedAt,
        Instant revokedAt,
        String revokedReason,
        String note,
        Instant createdAt) {

    public static InvitationDto from(InvitationView view) {
        return new InvitationDto(view.id(), view.personId(), view.branchId(), view.invitedBy(),
                view.status(), view.usability().name(), view.expiresAt(), view.acceptedBy(),
                view.acceptedAt(), view.revokedAt(), view.revokedReason(), view.note(),
                view.createdAt());
    }
}
