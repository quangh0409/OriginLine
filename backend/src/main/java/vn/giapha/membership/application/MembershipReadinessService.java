package vn.giapha.membership.application;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.membership.domain.BranchAssignment;
import vn.giapha.membership.domain.ClanInviteUsability;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.membership.domain.port.BranchAssignmentRepository;
import vn.giapha.membership.domain.port.ClanInviteCodeRepository;

/**
 * Hai con số của {@code membership} cho bảng "dòng họ còn thiếu bước nào" (context {@code clan}).
 *
 * <p>Chỉ đếm, không trả danh sách: màn khởi tạo cần biết "đã có Hội đồng chưa", không cần biết
 * <i>ai</i>. Trả danh sách ở đây là mở một lối đọc tài khoản thứ hai, cạnh màn phân quyền vốn đã có
 * luật riêng. Không kiểm quyền: hai con số này không lộ gì về một người cụ thể, và bên gọi duy nhất
 * ({@code ClanReadinessService}) đã đòi vai {@code ADMIN}.</p>
 */
@Service
public class MembershipReadinessService {

    /** Trần quét mã mời. Một dòng họ phát vài chục mã trong cả đời; quá trần thì đếm thiếu, không sai hướng nguy hiểm. */
    private static final int INVITE_SCAN_LIMIT = 1000;

    private final BranchAssignmentRepository assignments;
    private final ClanInviteCodeRepository clanInvites;

    public MembershipReadinessService(BranchAssignmentRepository assignments,
                                      ClanInviteCodeRepository clanInvites) {
        this.assignments = assignments;
        this.clanInvites = clanInvites;
    }

    /** Số <b>tài khoản</b> (không phải số dòng phân công) đang giữ vai {@code COUNCIL}. */
    @Transactional(readOnly = true)
    public int countCouncilMembers() {
        return (int) assignments.byRoleAndBranch(RoleCode.COUNCIL, null, LocalDate.now()).stream()
                .map(BranchAssignment::appUserId)
                .distinct()
                .count();
    }

    /** Số mã mời dòng họ còn dùng được ngay lúc này (chưa hết hạn, chưa thu hồi, chưa hết lượt). */
    @Transactional(readOnly = true)
    public int countActiveClanInvites() {
        Instant now = Instant.now();
        return (int) clanInvites.all(INVITE_SCAN_LIMIT, 0).stream()
                .filter(code -> code.usabilityAt(now) == ClanInviteUsability.USABLE)
                .count();
    }
}
