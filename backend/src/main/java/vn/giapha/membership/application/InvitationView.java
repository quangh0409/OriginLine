package vn.giapha.membership.application;

import java.time.Instant;
import java.util.UUID;
import vn.giapha.membership.domain.Invitation;
import vn.giapha.membership.domain.InvitationUsability;
import vn.giapha.membership.domain.Invitee;

/**
 * Lời mời ở dạng đọc, cho <b>màn quản trị của người mời</b>.
 *
 * <p>Không có mã, không có băm của mã. Băm cũng không: nó là bí mật dẫn xuất, và một băm lọt ra
 * ngoài cho phép người nhận kiểm chứng offline xem một mã đoán được có đúng không — tức là bẻ mất
 * lớp giới hạn tần suất.</p>
 *
 * @param usability trạng thái <i>đã xét đồng hồ</i>; giao diện dùng cái này chứ không dùng
 *                  {@code status}, vì {@code PENDING} của một mã quá hạn không nói lên điều gì
 * @param invitee   khối tóm tắt nhân khẩu được mời — tên, đời, tên chi. {@code null} khi không tra
 *                  được.
 *                  <p><b>Vì sao đưa ra ở đây là an toàn:</b> {@code POST /invitations/lookup} vốn
 *                  đã hiện đúng cái tên ấy cho <i>bất kỳ ai cầm mã</i>, kể cả người chưa đăng nhập
 *                  — đó là ngoại lệ có chủ ý của BA v2 §10, đã cân nhắc và đã trả giá bằng ba lớp
 *                  chống đỡ. Người đọc danh sách này thì chặt hơn nhiều: đã đăng nhập, đã qua
 *                  {@code BranchScopeGuard}, và thường chính là người vừa phát lời mời ấy.</p>
 *                  <p>Không có khối này thì màn "lời mời đã phát" là một cột UUID, và giao diện
 *                  buộc phải gọi {@code GET /persons/&#123;id&#125;} một lượt cho mỗi dòng — một
 *                  bài toán N+1 sinh ra chỉ vì thiếu ba trường.</p>
 */
public record InvitationView(UUID id, UUID personId, InviteeSummary invitee, UUID branchId,
                             UUID invitedBy, String status, InvitationUsability usability,
                             Instant expiresAt, UUID acceptedBy, Instant acceptedAt,
                             Instant revokedAt, String revokedReason, String note,
                             Instant createdAt, long version) {

    /**
     * Nhân khẩu được mời, ở mức <b>đúng đủ để nhận ra ai</b> trong một danh sách.
     *
     * <p>Không năm sinh, không nghề nghiệp, không nơi ở, không điện thoại, không ảnh — cùng ranh
     * giới mà {@link Invitee} đã đặt cho màn nhận lời mời, vì đây là cùng một dữ liệu đi ra ngoài.
     * Thêm một trường vào đây là mở rộng đúng thứ mà một danh sách bị chụp màn hình sẽ tiết lộ.</p>
     */
    public record InviteeSummary(String displayName, Integer generation, String branchName) {

        public static InviteeSummary from(Invitee invitee) {
            return invitee == null ? null
                    : new InviteeSummary(invitee.displayName(), invitee.generation(),
                            invitee.branchName());
        }
    }

    /** Không kèm khối nhân khẩu — dùng cho phản hồi của lệnh phát và lệnh thu hồi. */
    public static InvitationView from(Invitation invitation, Instant now) {
        return from(invitation, now, null);
    }

    public static InvitationView from(Invitation invitation, Instant now, Invitee invitee) {
        return new InvitationView(invitation.id(), invitation.personId(),
                InviteeSummary.from(invitee), invitation.branchId(), invitation.invitedBy(),
                invitation.status().name(), invitation.usabilityAt(now), invitation.expiresAt(),
                invitation.acceptedBy(), invitation.acceptedAt(), invitation.revokedAt(),
                invitation.revokedReason(), invitation.note(), invitation.createdAt(),
                invitation.version());
    }
}
