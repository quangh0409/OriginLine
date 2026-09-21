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
 *
 * <h2>{@link #invitee()} — ba trường cứu một bài toán N+1</h2>
 * Không có khối này thì màn "lời mời đã phát" là một cột UUID, và giao diện buộc phải gọi
 * {@code GET /persons/&#123;id&#125;} một lượt cho <b>mỗi dòng</b>. Tầng application vốn đã tra sẵn
 * ({@code InvitationService#list} truyền {@code Invitee} vào {@link InvitationView}); chỉ tầng này
 * bỏ rơi nó, nên mỗi dòng hiện "Không rõ nhân khẩu".
 *
 * <p><b>Vì sao đưa ra ở đây là an toàn:</b> {@code POST /invitations/lookup} vốn đã hiện đúng cái
 * tên ấy cho <i>bất kỳ ai cầm mã</i>, kể cả người chưa đăng nhập. Người đọc danh sách này thì chặt
 * hơn nhiều: đã đăng nhập, đã qua {@code BranchScopeGuard}, và thường chính là người vừa phát lời
 * mời. {@code null} khi không tra được nhân khẩu.</p>
 */
@Schema(description = "Lời mời đã phát, nhìn từ phía người mời")
public record InvitationDto(
        UUID id,
        UUID personId,

        @Schema(description = "Tóm tắt nhân khẩu được mời — đúng đủ để nhận ra ai trong danh sách")
        InviteeSummaryDto invitee,

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
        return new InvitationDto(view.id(), view.personId(), InviteeSummaryDto.from(view.invitee()),
                view.branchId(), view.invitedBy(),
                view.status(), view.usability().name(), view.expiresAt(), view.acceptedBy(),
                view.acceptedAt(), view.revokedAt(), view.revokedReason(), view.note(),
                view.createdAt());
    }

    /**
     * Nhân khẩu được mời, ở mức <b>đúng đủ để nhận ra ai</b> trong một danh sách.
     *
     * <p>Không năm sinh, không nghề nghiệp, không nơi ở, không điện thoại, không ảnh — cùng ranh
     * giới mà màn nhận lời mời đã đặt, vì đây là cùng một dữ liệu đi ra ngoài. Thêm một trường vào
     * đây là mở rộng đúng thứ mà một danh sách bị chụp màn hình sẽ tiết lộ.</p>
     *
     * <p><b>Không phải</b> khối {@code Invitee} của {@code POST /invitations/lookup}: khối ấy chở
     * cả một {@code Branch} đầy đủ (id, path, vùng miền) vì màn nhận lời mời cần chúng. Ở đây chỉ
     * có {@code branchName} — một chuỗi để đọc. Hai schema giống nhau đến mức dễ gộp nhầm, và gộp
     * thì danh sách này bắt đầu chở {@code ltree} của chi ra ngoài.</p>
     */
    @Schema(description = "Nhân khẩu được mời (tóm tắt)")
    public record InviteeSummaryDto(String displayName, Integer generation, String branchName) {

        static InviteeSummaryDto from(InvitationView.InviteeSummary summary) {
            return summary == null ? null
                    : new InviteeSummaryDto(summary.displayName(), summary.generation(),
                            summary.branchName());
        }
    }
}
