package vn.giapha.membership.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.AppUserStatus;
import vn.giapha.membership.domain.MemberScope;
import vn.giapha.membership.domain.port.AppUserRepository;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.security.CurrentUser;
import vn.giapha.shared.security.CurrentUserProvider;

/**
 * Tạo và duy trì dòng {@code app_user} ứng với một tài khoản Keycloak.
 *
 * <h2>Ánh xạ {@code keycloak_sub → app_user → person}</h2>
 * Backend không quản lý mật khẩu; nó chỉ giữ mắt xích mà Keycloak không biết: nhân khẩu tương ứng
 * trong cây phả hệ. Khoá nối là claim {@code sub} — <b>không phải email</b>. Email đổi được, và một
 * người có thể đăng nhập bằng Google rồi sau đó bằng Zalo với cùng địa chỉ; chỉ {@code sub} là bất
 * biến trong một realm.
 *
 * <h2>Tự khởi tạo ở lần gọi đầu (just-in-time provisioning)</h2>
 * Không có bước "đăng ký" riêng: ai đăng nhập được qua Keycloak thì lần gọi API đầu tiên sẽ tạo ra
 * dòng {@code app_user} ở trạng thái {@link AppUserStatus#PENDING}. Trạng thái đó <b>chưa</b> gắn
 * với nhân khẩu nào nên chưa thấy được gì ngoài dữ liệu công khai; việc ghép vào cây là quyết định
 * nghiệp vụ của Hội đồng Tộc biểu, không phải hệ quả của một lần đăng nhập.
 *
 * <h2>Đua tranh ở lần đăng nhập đầu</h2>
 * PWA hay bắn nhiều request song song ngay khi mở app; hai luồng cùng thấy "chưa có tài khoản" rồi
 * cùng INSERT. {@code ux_app_user_keycloak_sub} chặn ở CSDL, và ở đây bắt lại
 * {@link DataIntegrityViolationException} rồi đọc lại — chứ không để người dùng nhận một lỗi 500 ở
 * đúng giây đầu tiên họ dùng hệ thống.
 */
@Service
public class AppUserProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(AppUserProvisioningService.class);

    private static final String ENTITY = "AppUser";

    private final AppUserRepository appUsers;
    private final BranchScopeGuard guard;
    private final MemberScopeService scopes;
    private final AuditTrailService audit;

    public AppUserProvisioningService(AppUserRepository appUsers, BranchScopeGuard guard,
                                      MemberScopeService scopes, AuditTrailService audit) {
        this.appUsers = appUsers;
        this.guard = guard;
        this.scopes = scopes;
        this.audit = audit;
    }

    /**
     * Tài khoản của người đang gọi, tạo mới nếu chưa có.
     *
     * <p>Trả {@link Optional#empty()} với khách vãng lai — không token thì không có gì để tạo.</p>
     */
    @Transactional
    public Optional<AppUser> ensureCurrentUser() {
        Optional<CurrentUser> caller = CurrentUserProvider.current();
        if (caller.isEmpty()) {
            return Optional.empty();
        }
        CurrentUser user = caller.get();
        return Optional.of(ensureFor(user.keycloakSub(), user.email(), user.username()));
    }

    /**
     * Tài khoản ứng với một {@code keycloak_sub} <b>bất kỳ</b>, tạo mới nếu chưa có.
     *
     * <h2>Vì sao lối này tồn tại bên cạnh {@link #ensureCurrentUser()}</h2>
     * Luồng mời lập tài khoản Keycloak cho người <i>chưa đăng nhập bao giờ</i>. Lúc ấy không có
     * {@code SecurityContext} nào để đọc, nhưng {@code sub} thì đã có — nó vừa được Keycloak trả về
     * ở header {@code Location}. Không có phương thức này thì {@code InvitationLinker} buộc phải
     * chép lại toàn bộ luật tự khởi tạo (kể cả cách xử lý đua tranh), và hai bản chép sẽ lệch nhau.
     *
     * <p><b>Đây KHÔNG phải một phép kiểm quyền.</b> Nó chỉ dựng dòng {@code app_user} ở trạng thái
     * {@link AppUserStatus#PENDING} — chưa gắn nhân khẩu nên chưa thấy được gì ngoài dữ liệu công
     * khai. Việc quyết định tài khoản này được ghép vào ai là của nơi gọi, và nơi gọi phải có bằng
     * chứng riêng (một mã mời còn hiệu lực, hoặc quyền toàn dòng họ).</p>
     */
    @Transactional
    public AppUser ensureFor(String keycloakSub, String email, String displayName) {
        Optional<AppUser> existing = appUsers.byKeycloakSub(keycloakSub);
        if (existing.isPresent()) {
            AppUser account = existing.get();
            account.refreshProfile(email, displayName, Instant.now());
            return appUsers.save(account);
        }
        return create(keycloakSub, email, displayName);
    }

    /**
     * Như {@link #ensureFor}, nhưng cho người gọi <b>chưa chứng minh được mình là ai</b>: tạo mới
     * thì được, chạm vào một dòng đã có thì không.
     *
     * <h2>Vì sao cần một lối thứ hai thay vì một cờ trên lối cũ</h2>
     * {@link #ensureFor} làm tươi {@code display_name}/{@code email}/{@code last_login_at} từ
     * token ở mỗi lần đăng nhập — đúng, vì ở đó Keycloak đã chứng nhận danh tính. Lối đăng ký bằng
     * mã dòng họ thì <b>không</b> có token: định danh là chuỗi người gọi tự gõ. Dùng lại
     * {@link #ensureFor} ở đó nghĩa là một người chưa đăng nhập sửa được hàng {@code app_user} của
     * thành viên khác — và {@code display_name} ấy chính là thứ Trưởng chi đọc trong hàng chờ
     * duyệt đơn tự nhận.
     *
     * <p>Hai lối tách rời thì <b>nơi gọi phải chọn</b>, và lựa chọn ấy hiện ra ở chỗ gọi. Một cờ
     * {@code boolean} với giá trị mặc định thì lối mới nào quên truyền sẽ lặng lẽ rơi vào nhánh
     * tin tưởng.</p>
     *
     * @throws DomainException {@code IDENTITY_ALREADY_REGISTERED} khi {@code keycloak_sub} đã có
     *                         một dòng {@code app_user} — dòng ấy có chủ, và người gọi chưa chứng
     *                         minh được mình là chủ
     */
    @Transactional
    public AppUser ensureForUnverified(String keycloakSub, String email, String displayName) {
        Optional<AppUser> existing = appUsers.byKeycloakSub(keycloakSub);
        if (existing.isPresent()) {
            log.warn("Tu choi dung lai app_user {} cho mot nguoi goi khong trinh token",
                    existing.get().id());
            throw new DomainException(MembershipProblemCodes.IDENTITY_ALREADY_REGISTERED,
                    "Dinh danh nay da co tai khoan trong he thong. Hay dang nhap roi thu lai.");
        }
        return create(keycloakSub, email, displayName);
    }

    /** Trạng thái tài khoản hiện tại; ném 403 nếu chưa khởi tạo được. */
    @Transactional(readOnly = true)
    public AppUser requireCurrentUser() {
        return CurrentUserProvider.current()
                .flatMap(user -> appUsers.byKeycloakSub(user.keycloakSub()))
                .orElseThrow(() -> new NotFoundException(
                        MembershipProblemCodes.ACCOUNT_NOT_PROVISIONED,
                        "Tai khoan chua duoc khoi tao trong he thong"));
    }

    /**
     * Ghép tài khoản với một nhân khẩu — <b>chỉ Hội đồng Tộc biểu hoặc Quản trị hệ thống</b>.
     *
     * <p>Đây là thao tác nhạy cảm nhất của context: ghép sai là trao cho một người quyền xem Tầng 3
     * của người khác dưới danh nghĩa "hồ sơ của mình". Vì vậy nó không bao giờ được suy ra tự động
     * từ trùng tên hay trùng ngày sinh.</p>
     *
     * <h2>Vì sao đường này VẪN đòi quyền toàn dòng họ, trong khi luồng mời thì không</h2>
     * {@code InvitationService.issue} cho Trưởng chi phát lời mời trong phạm vi chi mình. Hai chỗ
     * <b>cố ý</b> khác nhau vì mức rủi ro khác nhau: ghép thẳng ở đây là thao tác <b>một phía</b> —
     * không ai hỏi người bị ghép, không ai xác nhận. Ghép qua lời mời thì người ở đầu kia phải bấm
     * "Đúng là tôi", và có một nút "Không phải tôi" để nói ngược lại. Trưởng chi được nới quyền ở
     * lối có con người xác nhận, không phải ở lối im lặng.
     *
     * <p>Trước khi nới {@code requireClanWide} ở đây thành {@code requireWriteAccess}: hãy hỏi vì
     * sao Trưởng chi cần ghép mà không mời. Nếu câu trả lời là "người ấy không dùng được điện
     * thoại" thì vấn đề nằm ở kênh chuyển mã, không ở phép kiểm quyền.</p>
     */
    @Transactional
    public AppUser linkToPerson(UUID appUserId, UUID personId) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireClanWide(caller, "ghep tai khoan voi nhan khau trong cay pha he");

        AppUser account = appUsers.byId(appUserId).orElseThrow(() -> new NotFoundException(
                MembershipProblemCodes.NOT_FOUND, "Khong tim thay tai khoan " + appUserId));

        Optional<AppUser> occupied = appUsers.byPersonId(personId);
        if (occupied.isPresent() && !occupied.get().id().equals(appUserId)) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Nhan khau nay da duoc ghep voi mot tai khoan khac");
        }

        try {
            account.linkPerson(personId);
        } catch (IllegalStateException ex) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED, ex.getMessage(), ex);
        }
        AppUser saved = appUsers.save(account);

        audit.record(ENTITY, saved.id().toString(), AuditAction.UPDATE,
                null, snapshot(saved), List.of("personId", "status"),
                "Ghep tai khoan voi nhan khau");
        log.info("Ghep app_user {} voi person {}", appUserId, personId);
        return saved;
    }

    /** Đổi trạng thái tài khoản (khoá / mở khoá) — vai kỹ thuật {@code ADMIN}. */
    @Transactional
    public AppUser changeStatus(UUID appUserId, AppUserStatus status, String reason) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireSystemAdmin(caller, "thay doi trang thai tai khoan");

        AppUser account = appUsers.byId(appUserId).orElseThrow(() -> new NotFoundException(
                MembershipProblemCodes.NOT_FOUND, "Khong tim thay tai khoan " + appUserId));
        Map<String, Object> before = snapshot(account);
        account.changeStatus(status);
        AppUser saved = appUsers.save(account);

        audit.record(ENTITY, saved.id().toString(), AuditAction.UPDATE,
                before, snapshot(saved), List.of("status"), reason);
        return saved;
    }

    private AppUser create(String keycloakSub, String email, String displayName) {
        AppUser fresh = AppUser.register(UUID.randomUUID(), keycloakSub, email, displayName);
        fresh.refreshProfile(email, displayName, Instant.now());
        try {
            AppUser saved = appUsers.save(fresh);
            audit.record(ENTITY, saved.id().toString(), AuditAction.CREATE,
                    null, snapshot(saved), List.of("keycloakSub", "status"),
                    "Tu khoi tao o lan dang nhap dau tien");
            log.info("Khoi tao app_user {} cho keycloak_sub {}", saved.id(), keycloakSub);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            // Hai request song song cung tao mot tai khoan: doc lai ban ma luong kia vua ghi.
            log.debug("Dua tranh khi khoi tao app_user cho sub {}, doc lai", keycloakSub);
            return appUsers.byKeycloakSub(keycloakSub).orElseThrow(() -> ex);
        }
    }

    /**
     * Ảnh chụp cho audit.
     *
     * <p><b>Không có email.</b> {@code app_user.email} được V5 chú thích rõ là dữ liệu Tầng 3;
     * {@code audit_log} là bảng chỉ ghi thêm nên một địa chỉ lọt vào đó là lọt vĩnh viễn.</p>
     */
    private static Map<String, Object> snapshot(AppUser user) {
        return Map.of(
                "id", user.id().toString(),
                "keycloakSub", user.keycloakSub(),
                "personId", String.valueOf(user.personId()),
                "status", user.status().name(),
                "locale", user.locale());
    }
}
