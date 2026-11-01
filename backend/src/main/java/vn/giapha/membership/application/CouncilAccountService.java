package vn.giapha.membership.application;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.Invitee;
import vn.giapha.membership.domain.LoginIdentifier;
import vn.giapha.membership.domain.MemberScope;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.membership.domain.port.AppUserRepository;
import vn.giapha.membership.domain.port.IdentityAccount;
import vn.giapha.membership.domain.port.IdentityProviderPort;
import vn.giapha.membership.domain.port.InviteeLookupPort;
import vn.giapha.membership.domain.port.RealmRolePort;
import vn.giapha.membership.domain.port.SetPasswordLink;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * <b>Quản trị hệ thống lập tài khoản Hội đồng Tộc biểu</b> — bước 2 của "Khởi tạo dòng họ".
 *
 * <h2>Không có cơ chế mới nào ở đây</h2>
 * Mỗi mảnh là một mảnh đã có và đã được kiểm:
 * <ul>
 *   <li>đọc định danh (email <i>hoặc</i> số điện thoại) và tìm-hoặc-tạo tài khoản Keycloak —
 *       {@link IdentityEnroller}, đúng lối của {@code /clan-invites/register};</li>
 *   <li>"định danh đã có chủ thì dừng" — {@link IdentityReclaimPolicy}, cùng luật với lối đăng ký;</li>
 *   <li>liên kết đặt mật khẩu — {@link IdentityProviderPort#issueSetPasswordLink}, tức
 *       {@code SetPasswordTokenCodec} + màn {@code /dat-mat-khau} + {@link SetPasswordService}.</li>
 * </ul>
 * <b>Vì sao không dùng {@code PasswordResetGrantWriter}.</b> Lối ấy sinh ra cho người <i>đã có</i>
 * tài khoản và quên mật khẩu: nó đòi một nhân khẩu đích, cấm tự cấp, và cấm tài khoản có email.
 * Tài khoản Hội đồng ở đây vừa được lập, có thể chưa gắn nhân khẩu nào, và có thể dùng email —
 * ba điều kiện của lối cấp lại đều sai với nó. Liên kết cho một tài khoản <b>vừa lập</b> là đúng
 * việc của lối mời, nên dùng lối mời.
 *
 * <h2>THỨ TỰ THAO TÁC — cùng khuôn với {@code ClanInviteService.register}</h2>
 * <ol>
 *   <li>kiểm quyền ({@code ADMIN}) và kiểm dữ liệu — trước mọi lời gọi mạng;</li>
 *   <li>tìm-hoặc-tạo tài khoản Keycloak (lặp lại được);</li>
 *   <li>định danh đã có chủ ⇒ {@code 422 IDENTITY_ALREADY_REGISTERED}, không chạm gì thêm;</li>
 *   <li>gắn vai realm {@code COUNCIL} (lặp lại được);</li>
 *   <li>đúc liên kết đặt mật khẩu — thuần tính toán;</li>
 *   <li><b>rồi mới</b> ghi CSDL ({@link CouncilAccountWriter}, một transaction).</li>
 * </ol>
 * Hỏng ở bước 6 thì còn lại một tài khoản Keycloak mồ côi <i>đã mang vai</i> {@code COUNCIL}. Nó
 * không thấy được gì của dòng họ — {@code MemberScopeService} không có dòng {@code app_user} nào
 * để dựng phạm vi — và bấm lại trong cửa sổ mồ côi thì lượt sau nhận nó về. Ngoài cửa sổ, quản trị
 * xoá nó trong Keycloak. Ghi ra đây để không ai phải đoán.
 */
@Service
public class CouncilAccountService {

    private static final Logger log = LoggerFactory.getLogger(CouncilAccountService.class);

    private static final int DISPLAY_NAME_MAX = 120;

    private final MemberScopeService scopes;
    private final BranchScopeGuard guard;
    private final IdentityEnroller enroller;
    private final IdentityReclaimPolicy reclaim;
    private final IdentityProviderPort identityProvider;
    private final RealmRolePort realmRoles;
    private final InviteeLookupPort persons;
    private final AppUserRepository appUsers;
    private final CouncilAccountWriter writer;

    @SuppressWarnings("java:S107")
    public CouncilAccountService(MemberScopeService scopes, BranchScopeGuard guard,
                                 IdentityEnroller enroller, IdentityReclaimPolicy reclaim,
                                 IdentityProviderPort identityProvider, RealmRolePort realmRoles,
                                 InviteeLookupPort persons, AppUserRepository appUsers,
                                 CouncilAccountWriter writer) {
        this.scopes = scopes;
        this.guard = guard;
        this.enroller = enroller;
        this.reclaim = reclaim;
        this.identityProvider = identityProvider;
        this.realmRoles = realmRoles;
        this.persons = persons;
        this.appUsers = appUsers;
        this.writer = writer;
    }

    public CouncilAccountCreated create(CreateCouncilAccountCommand command) {
        MemberScope caller = scopes.currentMemberScope();
        // Chi vai KY THUAT: Hoi dong tu lap them Hoi dong la tu nhan ban tham quyen noi dung cua
        // chinh minh ma khong ai thu hai nhin thay — dung dieu RoleAssignmentService cam voi ADMIN.
        guard.requireSystemAdmin(caller, "lập tài khoản Hội đồng Tộc biểu");
        if (command == null) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Thiếu thông tin tài khoản Hội đồng");
        }
        String displayName = requireDisplayName(command.displayName());
        LoginIdentifier login = enroller.readIdentifier(command.loginId());
        if (command.personId() != null) {
            requireLinkablePerson(command);
        }

        IdentityAccount account = enroller.findOrCreate(login, displayName);
        if (!reclaim.mayClaim(account)) {
            // Tai khoan co that cua nguoi khac (hoac cua chinh nguoi ay tu truoc). Gan vai COUNCIL
            // vao do la nang quyen mot tai khoan ma quan tri khong chung minh duoc la cua ai.
            log.warn("Tu choi lap tai khoan Hoi dong: dinh danh kieu {} da thuoc tai khoan {}",
                    login.kind(), account.subject());
            throw new DomainException(MembershipProblemCodes.IDENTITY_ALREADY_REGISTERED,
                    "Định danh này đã có tài khoản trong hệ thống. Hãy dùng định danh khác, hoặc"
                            + " cấp vai Hội đồng cho tài khoản sẵn có ở màn Phân quyền.");
        }

        realmRoles.grantRealmRole(account.subject(), RoleCode.COUNCIL);

        SetPasswordLink link = identityProvider.issueSetPasswordLink(account)
                .orElseThrow(() -> new DomainException(
                        MembershipProblemCodes.IDENTITY_ALREADY_REGISTERED,
                        "Tài khoản này đã có mật khẩu; không phát liên kết đặt mật khẩu được"));

        AppUser user = writer.write(account.subject(), login.emailOrNull(), displayName,
                command.personId(), caller.appUserId(), command.note());
        // KHONG log link.url(): bi mat mot lan.
        return new CouncilAccountCreated(user.id(), account.subject(), RoleCode.COUNCIL.name(),
                link.url(), link.expiresAt());
    }

    private static String requireDisplayName(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Thiếu tên hiển thị của tài khoản Hội đồng");
        }
        if (value.length() > DISPLAY_NAME_MAX) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Tên hiển thị dài quá " + DISPLAY_NAME_MAX + " ký tự");
        }
        return value;
    }

    /**
     * Nhân khẩu được gắn phải có thật, chưa xoá mềm, <b>còn sống</b>, và chưa có tài khoản.
     * Kiểm trước khi chạm Keycloak để một lượt gọi sai không để lại tài khoản mồ côi.
     */
    private void requireLinkablePerson(CreateCouncilAccountCommand command) {
        Optional<Invitee> person = persons.byId(command.personId());
        Invitee target = person.orElseThrow(() -> new NotFoundException(
                MembershipProblemCodes.NOT_FOUND, "Không tìm thấy nhân khẩu " + command.personId()));
        if (target.deleted()) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Nhân khẩu này đã bị xoá khỏi phả; không gắn tài khoản vào được");
        }
        if (!target.alive()) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Nhân khẩu này đã khuất; tài khoản chỉ gắn được cho người còn sống");
        }
        if (appUsers.byPersonId(command.personId()).isPresent()) {
            throw new DomainException(MembershipProblemCodes.PERSON_ALREADY_LINKED,
                    "Nhân khẩu này đã có tài khoản; hãy cấp vai Hội đồng cho tài khoản ấy");
        }
    }
}
