package vn.giapha.membership.support;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.support.InMemoryAuditLog;
import vn.giapha.audit.support.SimpleObjectProvider;
import vn.giapha.membership.application.BranchScopeGuard;
import vn.giapha.membership.application.MemberScopeService;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.BranchAssignment;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.membership.infrastructure.audit.MembershipAuditActorAdapter;
import vn.giapha.shared.vo.BranchPath;

/**
 * Một dòng họ giả, đủ hình dạng để kiểm luật phân quyền theo phạm vi chi/ngành.
 *
 * <pre>
 * goc                              gốc — cả dòng họ
 * ├── goc.chi_giap                 Chi Giáp
 * │   └── goc.chi_giap.nganh_truong  Ngành Trưởng (cành con của Chi Giáp)
 * └── goc.chi_at                   Chi Ất
 * </pre>
 *
 * <p>Hình dạng này cố ý có <b>ba</b> quan hệ khác nhau giữa hai chi: Chi Giáp và Chi Ất là hai
 * nhánh <i>rời nhau</i> (ca "Trưởng Chi A đụng người Chi B"), Ngành Trưởng nằm <i>dưới</i> Chi Giáp
 * (ca "phạm vi phủ cả hậu duệ"), và gốc phủ tất (ca "toàn dòng họ"). Thiếu một trong ba thì luật
 * {@code ltree} không được kiểm hết.</p>
 *
 * <p>Toàn bộ dây nối là thật — {@code MemberScopeService}, {@code BranchScopeGuard},
 * {@code AuditTrailService} đều là lớp production; chỉ có kho dữ liệu và {@code SecurityContext} là
 * giả. Nhờ vậy test bắt được lỗi ở chính luật phân quyền, không phải ở bản mô phỏng luật.</p>
 */
public final class ClanFixture {

    public static final BranchPath GOC = BranchPath.of("goc");
    public static final BranchPath CHI_GIAP = BranchPath.of("goc.chi_giap");
    public static final BranchPath NGANH_TRUONG = BranchPath.of("goc.chi_giap.nganh_truong");
    public static final BranchPath CHI_AT = BranchPath.of("goc.chi_at");

    public final UUID gocId = UUID.randomUUID();
    public final UUID chiGiapId = UUID.randomUUID();
    public final UUID nganhTruongId = UUID.randomUUID();
    public final UUID chiAtId = UUID.randomUUID();

    public final StubBranchLookup branches = new StubBranchLookup();
    public final InMemoryAppUserRepository appUsers = new InMemoryAppUserRepository();
    public final InMemoryBranchAssignmentRepository assignments =
            new InMemoryBranchAssignmentRepository();
    public final InMemoryChangeRequestRepository requests =
            new InMemoryChangeRequestRepository(branches);
    public final InMemoryAuditLog auditLog = new InMemoryAuditLog();

    public final AuditTrailService auditTrail;
    public final MemberScopeService scopes;
    public final BranchScopeGuard guard = new BranchScopeGuard();

    /** Sự kiện miền đã phát — {@code ChangeRequestApproved/Rejected}. */
    public final List<Object> publishedEvents = new ArrayList<>();
    public final ApplicationEventPublisher events = publishedEvents::add;

    public ClanFixture() {
        branches.branch(gocId, GOC.value());
        branches.branch(chiGiapId, CHI_GIAP.value());
        branches.branch(nganhTruongId, NGANH_TRUONG.value());
        branches.branch(chiAtId, CHI_AT.value());
        this.auditTrail = new AuditTrailService(auditLog,
                SimpleObjectProvider.of(new MembershipAuditActorAdapter(appUsers)));
        this.scopes = new MemberScopeService(appUsers, assignments, branches);
    }

    // -------------------------------------------------------------------------------------
    // Nhân khẩu & tài khoản
    // -------------------------------------------------------------------------------------

    /** Một nhân khẩu thuộc một chi — chưa có tài khoản nào. */
    public UUID personIn(UUID branchId) {
        return branches.person(UUID.randomUUID(), branchId);
    }

    /** Nhân khẩu chưa được gắn chi nào — bản ghi thiếu dữ liệu mà phả hệ nào cũng có. */
    public UUID personWithoutBranch() {
        return UUID.randomUUID();
    }

    /** Tài khoản đã ghép với một nhân khẩu thuộc {@code branchId}. */
    public AppUser account(String keycloakSub, UUID branchId) {
        UUID personId = personIn(branchId);
        AppUser user = new AppUser(UUID.randomUUID(), keycloakSub, personId,
                keycloakSub + "@example.test", keycloakSub,
                vn.giapha.membership.domain.AppUserStatus.ACTIVE, "vi", null, 0L);
        return appUsers.seed(user);
    }

    /** Tài khoản chưa được Hội đồng ghép vào cây — {@code personId} còn trống. */
    public AppUser unlinkedAccount(String keycloakSub) {
        return appUsers.seed(AppUser.register(UUID.randomUUID(), keycloakSub,
                keycloakSub + "@example.test", keycloakSub));
    }

    // -------------------------------------------------------------------------------------
    // Phân công vai trò kèm phạm vi
    // -------------------------------------------------------------------------------------

    /** Trưởng Chi/Ngành — phạm vi <b>chỉ</b> chi được giao và hậu duệ của nó. */
    public BranchAssignment branchHeadOf(AppUser user, UUID branchId) {
        return assignments.seed(new BranchAssignment(UUID.randomUUID(), user.id(),
                RoleCode.BRANCH_HEAD, branchId, branches.pathOfBranch(branchId).orElseThrow(),
                null, null, null, "Truong chi"));
    }

    /** Hội đồng Tộc biểu — phân công không gắn chi, nghĩa là toàn dòng họ. */
    public BranchAssignment councilWide(AppUser user) {
        return assignments.seed(new BranchAssignment(UUID.randomUUID(), user.id(),
                RoleCode.COUNCIL, null, null, null, null, null, "Hoi dong Toc bieu"));
    }

    /** Quản trị hệ thống — vai kỹ thuật, phân công toàn cục. */
    public BranchAssignment adminWide(AppUser user) {
        return assignments.seed(new BranchAssignment(UUID.randomUUID(), user.id(),
                RoleCode.ADMIN, null, null, null, null, null, "Quan tri he thong"));
    }

    /** Một nhiệm kỳ Trưởng chi <b>đã hết hạn</b> — phải không còn sinh ra phạm vi nào. */
    public BranchAssignment expiredBranchHeadOf(AppUser user, UUID branchId) {
        LocalDate today = LocalDate.now();
        return assignments.seed(new BranchAssignment(UUID.randomUUID(), user.id(),
                RoleCode.BRANCH_HEAD, branchId, branches.pathOfBranch(branchId).orElseThrow(),
                today.minusYears(3), today.minusDays(1), null, "Nhiem ky da man"));
    }
}
