package vn.giapha.membership.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.BranchAssignment;
import vn.giapha.membership.domain.MemberScope;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.membership.domain.port.AppUserRepository;
import vn.giapha.membership.domain.port.BranchAssignmentRepository;
import vn.giapha.membership.domain.port.BranchLookupPort;
import vn.giapha.shared.security.CurrentUser;
import vn.giapha.shared.security.CurrentUserProvider;
import vn.giapha.shared.vo.BranchPath;

/**
 * <b>Mặt tiền phân quyền của hệ thống.</b> Trả lời đúng một câu hỏi, cho mọi context: người đang gọi
 * là ai, ứng với nhân khẩu nào, và được đụng vào những chi nào.
 *
 * <h2>Vì sao service này tồn tại</h2>
 * Vai trò trong Keycloak chỉ là <b>một nửa</b> phân quyền. Nửa còn lại — phạm vi chi/ngành theo
 * {@code ltree} — nằm ở {@code branch_assignment}, backend tự tra, và <b>không suy ra được từ
 * token</b>. Nếu mỗi context tự đi tra bảng ấy thì sớm muộn sẽ có nơi tra thiếu điều kiện hiệu lực,
 * hoặc diễn giải "danh sách rỗng" thành "toàn quyền". Gom về một chỗ để luật chỉ có một bản.
 *
 * <h2>Đây là chỗ mà {@code CallerIdentityJdbcAdapter} bên genealogy phải gọi tới</h2>
 * Javadoc của {@code CallerIdentityPort} ghi rõ khoản nợ: W2 đọc thẳng {@code app_user} và
 * {@code branch_assignment} bằng SQL để không phải chờ W6. Ba phương thức của cổng đó khớp một–một
 * với {@link #currentScope()}: {@code currentPersonId()} → {@link MemberScopeView#personId()},
 * {@code managedBranches()} → {@link MemberScopeView#managedBranches()}, {@code homeBranch()} →
 * {@link MemberScopeView#homeBranch()}.
 *
 * <h2>Vì sao không cache</h2>
 * Vai trò và phạm vi chi có thể bị thu hồi giữa chừng; cache lại nghĩa là một Trưởng chi vừa bị bãi
 * nhiệm vẫn duyệt được dữ liệu cho tới khi cache hết hạn. Ba câu SELECT theo khoá chính rẻ hơn
 * nhiều so với một lỗ hổng phân quyền. Nếu đo đạc sau này cho thấy đây là điểm nghẽn thì phạm vi
 * cache đúng đắn là <b>một request</b>, không phải một khoảng thời gian.
 */
@Service
public class MemberScopeService {

    private static final Logger log = LoggerFactory.getLogger(MemberScopeService.class);

    private final AppUserRepository appUsers;
    private final BranchAssignmentRepository assignments;
    private final BranchLookupPort branches;

    public MemberScopeService(AppUserRepository appUsers, BranchAssignmentRepository assignments,
                              BranchLookupPort branches) {
        this.appUsers = appUsers;
        this.assignments = assignments;
        this.branches = branches;
    }

    /**
     * Phạm vi của người gọi hiện tại, dựng từ {@code SecurityContext}.
     *
     * <p>Không token → {@link MemberScopeView#guest()}. Có token nhưng chưa có {@code app_user} →
     * vai lấy từ token, <b>không</b> phạm vi nào: token nói được người đó là ai, nhưng chỉ dữ liệu
     * của dòng họ mới nói được người đó quản nhánh nào.</p>
     */
    @Transactional(readOnly = true)
    public MemberScopeView currentScope() {
        return MemberScopeView.from(currentMemberScope());
    }

    /** Bản domain của {@link #currentScope()} — dùng nội bộ context, giữ được hành vi kiểm quyền. */
    @Transactional(readOnly = true)
    public MemberScope currentMemberScope() {
        Optional<CurrentUser> user = CurrentUserProvider.current();
        if (user.isEmpty()) {
            return MemberScope.guest();
        }
        CurrentUser caller = user.get();
        RoleCode tokenRole = RoleCode.broadest(caller.roles());
        if (tokenRole == RoleCode.GUEST) {
            // Token hop le nhung khong mang vai nao: van la thanh vien da dang nhap.
            tokenRole = RoleCode.MEMBER;
        }
        Optional<AppUser> account = appUsers.byKeycloakSub(caller.keycloakSub());
        if (account.isEmpty()) {
            log.debug("Chua co app_user cho keycloak_sub {}, tra pham vi rong", caller.keycloakSub());
            return MemberScope.unlinked(tokenRole);
        }
        return scopeOf(account.get(), tokenRole);
    }

    /** Phạm vi của một tài khoản bất kỳ — dùng cho màn hình quản trị và cho kiểm toán. */
    @Transactional(readOnly = true)
    public Optional<MemberScopeView> scopeOfUser(UUID appUserId) {
        return appUsers.byId(appUserId)
                .map(user -> MemberScopeView.from(scopeOf(user, highestAssignedRole(user))));
    }

    /** Nhân khẩu ứng với một tài khoản Keycloak ({@code keycloak_sub → app_user → person}). */
    @Transactional(readOnly = true)
    public Optional<UUID> personIdOf(String keycloakSub) {
        return appUsers.byKeycloakSub(keycloakSub).map(AppUser::personId);
    }

    /** Tài khoản ứng với một nhân khẩu — chiều ngược, dùng khi gửi thông báo cho một người. */
    @Transactional(readOnly = true)
    public Optional<UUID> appUserIdOfPerson(UUID personId) {
        return appUsers.byPersonId(personId).map(AppUser::id);
    }

    /**
     * Vai trò được <b>giao thật</b> cho tài khoản, không lấy từ token.
     *
     * <p>Dùng khi không có request nào (job nền, kiểm toán): lúc ấy không có JWT để đọc, và điều
     * duy nhất còn đáng tin là bảng phân công.</p>
     */
    @Transactional(readOnly = true)
    public RoleCode highestAssignedRole(AppUser user) {
        RoleCode highest = RoleCode.MEMBER;
        for (BranchAssignment assignment : assignments.activeFor(user.id(), LocalDate.now())) {
            if (assignment.role().rank() > highest.rank()) {
                highest = assignment.role();
            }
        }
        return highest;
    }

    private MemberScope scopeOf(AppUser user, RoleCode tokenRole) {
        List<BranchAssignment> active = assignments.activeFor(user.id(), LocalDate.now());
        BranchPath home = user.personId() == null
                ? null
                : branches.branchOfPerson(user.personId()).orElse(null);
        return MemberScope.from(user.id(), user.personId(), tokenRole, active, home);
    }
}
