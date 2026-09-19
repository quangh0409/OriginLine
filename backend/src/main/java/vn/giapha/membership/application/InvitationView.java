package vn.giapha.membership.application;

import java.time.Instant;
import java.util.UUID;
import vn.giapha.membership.domain.Invitation;
import vn.giapha.membership.domain.InvitationUsability;

/**
 * Lời mời ở dạng đọc, cho <b>màn quản trị của người mời</b>.
 *
 * <p>Không có mã, không có băm của mã. Băm cũng không: nó là bí mật dẫn xuất, và một băm lọt ra
 * ngoài cho phép người nhận kiểm chứng offline xem một mã đoán được có đúng không — tức là bẻ mất
 * lớp giới hạn tần suất.</p>
 *
 * @param usability trạng thái <i>đã xét đồng hồ</i>; giao diện dùng cái này chứ không dùng
 *                  {@code status}, vì {@code PENDING} của một mã quá hạn không nói lên điều gì
 */
public record InvitationView(UUID id, UUID personId, UUID branchId, UUID invitedBy,
                             String status, InvitationUsability usability, Instant expiresAt,
                             UUID acceptedBy, Instant acceptedAt, Instant revokedAt,
                             String revokedReason, String note, Instant createdAt, long version) {

    public static InvitationView from(Invitation invitation, Instant now) {
        return new InvitationView(invitation.id(), invitation.personId(), invitation.branchId(),
                invitation.invitedBy(), invitation.status().name(), invitation.usabilityAt(now),
                invitation.expiresAt(), invitation.acceptedBy(), invitation.acceptedAt(),
                invitation.revokedAt(), invitation.revokedReason(), invitation.note(),
                invitation.createdAt(), invitation.version());
    }
}
