package vn.giapha.membership.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.AppUserStatus;
import vn.giapha.membership.domain.BranchAssignment;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.membership.domain.port.AppUserRepository;
import vn.giapha.membership.domain.port.BranchAssignmentRepository;
import vn.giapha.shared.exception.DomainException;

/**
 * Nửa <b>CSDL</b> của việc lập tài khoản Hội đồng: {@code app_user} + phân công {@code COUNCIL}
 * toàn dòng họ + hai dòng {@code audit_log} — một transaction.
 *
 * <p>Tách khỏi {@link CouncilAccountService} cùng lý do {@code PasswordResetGrantWriter} tách: lời
 * gọi Keycloak <b>không được</b> nằm trong transaction Postgres, và gọi phương thức
 * {@code @Transactional} của chính mình thì proxy không chạy — ranh giới phải là một bean khác.</p>
 */
@Service
public class CouncilAccountWriter {

    private static final Logger log = LoggerFactory.getLogger(CouncilAccountWriter.class);

    private final AppUserRepository appUsers;
    private final BranchAssignmentRepository assignments;
    private final AuditTrailService audit;

    public CouncilAccountWriter(AppUserRepository appUsers, BranchAssignmentRepository assignments,
                                AuditTrailService audit) {
        this.appUsers = appUsers;
        this.assignments = assignments;
        this.audit = audit;
    }

    /**
     * @param grantedBy tài khoản của quản trị đang thao tác; {@code null} khi quản trị chưa có dòng
     *                  {@code app_user} (lần đầu vào hệ thống) — cột {@code granted_by} cho phép
     */
    @Transactional
    public AppUser write(String keycloakSub, String email, String displayName, UUID personId,
                         UUID grantedBy, String note) {
        // Kiem LAI trong transaction: phep kiem o service chay truoc chuyen di Keycloak, va trong
        // luc ay mot loi moi ca nhan co the da gan nhan khau nay cho nguoi khac. ux_app_user_person
        // van chan o CSDL, nhung o day loi hien ra bang thong diep nghiep vu.
        if (personId != null && appUsers.byPersonId(personId).isPresent()) {
            throw new DomainException(MembershipProblemCodes.PERSON_ALREADY_LINKED,
                    "Nhân khẩu này đã có tài khoản; không gắn thêm tài khoản Hội đồng vào được");
        }
        if (appUsers.byKeycloakSub(keycloakSub).isPresent()) {
            throw new DomainException(MembershipProblemCodes.IDENTITY_ALREADY_REGISTERED,
                    "Định danh này đã có tài khoản trong hệ thống");
        }
        // ACTIVE ngay: nguoi nay do Quan tri he thong chi dich danh, khong qua hang cho duyet.
        AppUser user = appUsers.save(new AppUser(UUID.randomUUID(), keycloakSub, personId, email,
                displayName, AppUserStatus.ACTIVE, "vi", null, 0L));
        // branchId = NULL = pham vi toan dong ho — dung hinh dang ma MemberScope.from doc la
        // clanWide cho vai COUNCIL.
        BranchAssignment assignment = assignments.save(new BranchAssignment(UUID.randomUUID(),
                user.id(), RoleCode.COUNCIL, null, null, null, null, grantedBy, note));

        // KHONG ghi email, KHONG ghi keycloak_sub, KHONG ghi lien ket vao audit (Tang 3 / bi mat).
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("displayName", displayName);
        after.put("personId", personId == null ? null : personId.toString());
        after.put("status", AppUserStatus.ACTIVE.name());
        audit.record("AppUser", user.id().toString(), AuditAction.CREATE, null, after,
                List.of("displayName", "personId", "status"),
                "Lap tai khoan Hoi dong Toc bieu" + (note == null ? "" : ": " + note));
        Map<String, Object> grant = new LinkedHashMap<>();
        grant.put("appUserId", user.id().toString());
        grant.put("role", RoleCode.COUNCIL.name());
        grant.put("branchId", null);
        audit.record("BranchAssignment", assignment.id().toString(), AuditAction.GRANT_ROLE, null,
                grant, List.of("role", "branchId"), note);

        log.info("Lap tai khoan Hoi dong {} (nhan khau {}) boi app_user {}", user.id(), personId,
                grantedBy);
        return user;
    }
}
