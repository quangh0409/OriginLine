package vn.giapha.membership.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.membership.application.command.GrantRoleCommand;
import vn.giapha.membership.domain.BranchAssignment;
import vn.giapha.membership.domain.MemberScope;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.membership.domain.port.AppUserRepository;
import vn.giapha.membership.domain.port.BranchAssignmentRepository;
import vn.giapha.membership.domain.port.BranchLookupPort;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.BranchPath;

/**
 * Cấp và thu hồi vai trò kèm phạm vi chi/ngành.
 *
 * <h2>Ba luật không được nới</h2>
 * <ol>
 *   <li><b>{@code BRANCH_HEAD} bắt buộc kèm chi.</b> Một Trưởng chi không giới hạn phạm vi là một
 *       Hội đồng Tộc biểu đội tên khác — và là loại quyền mà không ai rà lại được từ bảng phân
 *       công.</li>
 *   <li><b>Chỉ vai kỹ thuật {@code ADMIN} mới cấp được {@code ADMIN}.</b> Hội đồng Tộc biểu là
 *       thẩm quyền <i>nội dung</i> của dòng họ; để nó tự nâng quyền kỹ thuật là biến một tranh chấp
 *       nội bộ thành một sự cố an ninh.</li>
 *   <li><b>Trưởng chi không cấp quyền cho ai cả.</b> Nếu cấp được, người quản một nhánh sẽ dựng
 *       được cả một tầng quyền song song bên dưới mình mà Hội đồng không thấy.</li>
 * </ol>
 *
 * <h2>Thu hồi là xoá dòng, không phải cờ</h2>
 * Ngoại lệ có chủ ý của quy tắc "chỉ xoá mềm": quy tắc ấy bảo vệ node phả hệ khỏi đứt liên kết, còn
 * một dòng cấp quyền thì càng biến mất sớm càng tốt. Vết nằm ở {@code audit_log} với hành động
 * {@code REVOKE_ROLE} — không nằm ở một cột cờ mà một câu truy vấn nào đó có thể quên lọc.
 */
@Service
public class RoleAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(RoleAssignmentService.class);

    private static final String ENTITY = "BranchAssignment";

    private final BranchAssignmentRepository assignments;
    private final AppUserRepository appUsers;
    private final BranchLookupPort branches;
    private final MemberScopeService scopes;
    private final BranchScopeGuard guard;
    private final AuditTrailService audit;

    public RoleAssignmentService(BranchAssignmentRepository assignments, AppUserRepository appUsers,
                                 BranchLookupPort branches, MemberScopeService scopes,
                                 BranchScopeGuard guard, AuditTrailService audit) {
        this.assignments = assignments;
        this.appUsers = appUsers;
        this.branches = branches;
        this.scopes = scopes;
        this.guard = guard;
        this.audit = audit;
    }

    /** Cấp vai trò. Ném 403 nếu người gọi không đủ thẩm quyền, 422 nếu phân công vô nghĩa. */
    @Transactional
    public BranchAssignment grant(GrantRoleCommand command) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireClanWide(caller, "cap vai tro cho tai khoan");
        if (command.role() == RoleCode.ADMIN) {
            guard.requireSystemAdmin(caller, "cap vai tro Quan tri he thong");
        }
        validate(command);

        appUsers.byId(command.appUserId()).orElseThrow(() -> new NotFoundException(
                MembershipProblemCodes.NOT_FOUND,
                "Khong tim thay tai khoan " + command.appUserId()));

        BranchPath path = command.branchId() == null
                ? null
                : branches.pathOfBranch(command.branchId()).orElseThrow(() -> new NotFoundException(
                        MembershipProblemCodes.NOT_FOUND,
                        "Khong tim thay chi/nganh " + command.branchId()));

        BranchAssignment assignment = new BranchAssignment(UUID.randomUUID(), command.appUserId(),
                command.role(), command.branchId(), path, command.validFrom(), command.validTo(),
                caller.appUserId(), command.note());
        BranchAssignment saved = assignments.save(assignment);

        audit.record(ENTITY, saved.id().toString(), AuditAction.GRANT_ROLE,
                null, snapshot(saved), List.of("role", "branchId", "validFrom", "validTo"),
                command.note());
        log.info("Cap vai {} pham vi {} cho app_user {} boi {}",
                saved.role(), path == null ? "TOAN DONG HO" : path, saved.appUserId(),
                caller.appUserId());
        return saved;
    }

    /** Thu hồi một phân công. */
    @Transactional
    public void revoke(UUID assignmentId, String reason) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireClanWide(caller, "thu hoi vai tro");

        BranchAssignment assignment = assignments.byId(assignmentId)
                .orElseThrow(() -> new NotFoundException(MembershipProblemCodes.NOT_FOUND,
                        "Khong tim thay phan cong " + assignmentId));
        if (assignment.role() == RoleCode.ADMIN) {
            guard.requireSystemAdmin(caller, "thu hoi vai tro Quan tri he thong");
        }

        assignments.delete(assignmentId);
        audit.record(ENTITY, assignmentId.toString(), AuditAction.REVOKE_ROLE,
                snapshot(assignment), null, List.of("role", "branchId"), reason);
        log.info("Thu hoi phan cong {} ({} tren {}) boi app_user {}", assignmentId,
                assignment.role(), assignment.branchPath(), caller.appUserId());
    }

    /** Toàn bộ phân công của một tài khoản — màn hình quản trị. */
    @Transactional(readOnly = true)
    public List<BranchAssignment> assignmentsOf(UUID appUserId) {
        MemberScope caller = scopes.currentMemberScope();
        if (!caller.isClanWide() && !appUserId.equals(caller.appUserId())) {
            guard.requireClanWide(caller, "xem phan cong vai tro cua tai khoan khac");
        }
        return assignments.allFor(appUserId);
    }

    /** Ai đang là Trưởng chi của một chi cụ thể. */
    @Transactional(readOnly = true)
    public List<BranchAssignment> headsOfBranch(UUID branchId) {
        return assignments.byRoleAndBranch(RoleCode.BRANCH_HEAD, branchId, LocalDate.now());
    }

    private void validate(GrantRoleCommand command) {
        if (command.role() == null) {
            throw new DomainException(MembershipProblemCodes.INVALID_ROLE_ASSIGNMENT,
                    "Phai chi ro vai tro can cap");
        }
        if (command.role() == RoleCode.BRANCH_HEAD && command.branchId() == null) {
            throw new DomainException(MembershipProblemCodes.INVALID_ROLE_ASSIGNMENT,
                    "Vai Truong Chi/Nganh bat buoc phai kem pham vi chi/nganh");
        }
        if (command.role() == RoleCode.GUEST) {
            throw new DomainException(MembershipProblemCodes.INVALID_ROLE_ASSIGNMENT,
                    "Vai Khach la mac dinh cho nguoi chua dang nhap, khong cap duoc");
        }
        if (command.role().isClanWide() && command.branchId() != null) {
            throw new DomainException(MembershipProblemCodes.INVALID_ROLE_ASSIGNMENT,
                    "Vai pham vi toan dong ho khong duoc gioi han vao mot chi");
        }
        if (command.validFrom() != null && command.validTo() != null
                && command.validTo().isBefore(command.validFrom())) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Ngay het hieu luc phai khong som hon ngay bat dau");
        }
    }

    private static Map<String, Object> snapshot(BranchAssignment assignment) {
        return Map.of(
                "id", assignment.id().toString(),
                "appUserId", assignment.appUserId().toString(),
                "role", assignment.role().name(),
                "branchId", String.valueOf(assignment.branchId()),
                "branchPath", String.valueOf(assignment.branchPath()),
                "validFrom", String.valueOf(assignment.validFrom()),
                "validTo", String.valueOf(assignment.validTo()));
    }
}
