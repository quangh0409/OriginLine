package vn.giapha.membership.application;

import java.time.Instant;
import java.util.UUID;
import vn.giapha.membership.domain.Invitee;
import vn.giapha.membership.domain.PasswordResetGrant;

/**
 * Một lượt cấp lại liên kết đặt mật khẩu, ở dạng đọc — cho màn hình của <b>người cấp</b>.
 *
 * <p>Không có liên kết, không có token, không có mảnh nào của token. Liên kết rời máy chủ đúng một
 * lần, trong {@link IssuedPasswordReset}; bản ghi thì đọc lại được mãi mãi, và hai thứ ấy không
 * được trộn vào một kiểu.</p>
 *
 * @param target  tên và đời của người được cấp lại — đúng đủ để người cấp nhận ra mình vừa làm cho
 *                ai, không hơn. Không năm sinh, không nơi ở, không điện thoại: cùng ranh giới mà
 *                {@code InvitationView.InviteeSummary} đã đặt
 * @param usedAt  {@code null} nghĩa là người ấy <b>chưa</b> đặt lại mật khẩu. Đây là con số đáng
 *                nhìn nhất trên màn hình: một lượt cấp nằm mãi ở trạng thái chưa dùng nghĩa là
 *                liên kết không tới được tay người nhận
 */
public record PasswordResetGrantView(UUID id, UUID targetPersonId, TargetSummary target,
                                     UUID branchId, UUID issuedBy, Instant issuedAt,
                                     Instant expiresAt, Instant usedAt, Instant revokedAt,
                                     String note) {

    /** Người được cấp lại, ở mức đúng đủ để nhận ra ai. */
    public record TargetSummary(String displayName, Integer generation, String branchName) {

        public static TargetSummary from(Invitee invitee) {
            return invitee == null ? null
                    : new TargetSummary(invitee.displayName(), invitee.generation(),
                            invitee.branchName());
        }
    }

    public static PasswordResetGrantView from(PasswordResetGrant grant, Invitee target) {
        return new PasswordResetGrantView(grant.id(), grant.targetPersonId(),
                TargetSummary.from(target), grant.branchId(), grant.issuedBy(), grant.issuedAt(),
                grant.expiresAt(), grant.usedAt(), grant.revokedAt(), grant.note());
    }
}
