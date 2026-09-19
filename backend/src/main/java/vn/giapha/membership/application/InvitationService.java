package vn.giapha.membership.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.membership.application.command.AcceptInvitationCommand;
import vn.giapha.membership.application.command.IssueInvitationCommand;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.ClanTitle;
import vn.giapha.membership.domain.Invitation;
import vn.giapha.membership.domain.InvitationCode;
import vn.giapha.membership.domain.InvitationUsability;
import vn.giapha.membership.domain.Invitee;
import vn.giapha.membership.domain.MemberScope;
import vn.giapha.membership.domain.port.AppUserRepository;
import vn.giapha.membership.domain.port.BranchLookupPort;
import vn.giapha.membership.domain.port.IdentityAccount;
import vn.giapha.membership.domain.port.IdentityProviderException;
import vn.giapha.membership.domain.port.IdentityProviderPort;
import vn.giapha.membership.domain.port.InvitationRepository;
import vn.giapha.membership.domain.port.InviteeLookupPort;
import vn.giapha.membership.domain.port.NewIdentityAccount;
import vn.giapha.membership.domain.port.SetPasswordLink;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.security.CurrentUser;
import vn.giapha.shared.security.CurrentUserProvider;
import vn.giapha.shared.vo.BranchPath;

/**
 * <b>Cửa duy nhất để một người thứ tư vào được hệ thống.</b> Phát lời mời · xem lời mời · nhận lời
 * mời · từ chối · thu hồi.
 *
 * <h2>Dòng họ mời người vào — đây là điểm phân biệt căn bản với một mạng xã hội</h2>
 * Không có "đăng ký" trong sản phẩm này; realm Keycloak đặt {@code registrationAllowed: false} và
 * đó là mặc định đúng, đề xuất giữ vĩnh viễn (design 06 §5). Người ta không tự ghi danh rồi vào xem
 * phả nhà người khác. Quyết định ấy định hình toàn bộ lớp này.
 *
 * <h2>Lời mời MANG SẴN việc nối tài khoản với nhân khẩu</h2>
 * Đây là điểm mấu chốt. Trưởng chi đã tự tay chọn đích danh người mình mời trong phả khi phát mã,
 * nên người được mời <b>không rơi vào trạng thái "chờ duyệt"</b> — thứ mà tài liệu thiết kế phiên
 * đầu tiên gọi đích danh là điểm yếu nhất. Bắt họ chờ duyệt lần nữa là bắt hệ thống hỏi lại một
 * câu đã có đáp án. Hệ quả kỹ thuật: {@link #accept} ghép tài khoản qua {@link InvitationLinker}
 * và tài khoản chuyển {@code PENDING → ACTIVE} ngay trong cùng một transaction, không sinh
 * {@code ChangeRequest} nào.
 *
 * <h2>Lời mời cũng LẬP tài khoản đăng nhập, không chỉ ghép nhân khẩu</h2>
 * Bản dựng đầu của luồng này đòi người nhận phải <i>đã có</i> tài khoản Keycloak trước khi bấm
 * "Đúng là tôi" — mà realm đặt {@code registrationAllowed: false}, nên không ai lập được tài khoản
 * ấy cho họ. Nay {@link #accept} gọi {@code IdentityProviderPort} để lập tài khoản và trả về một
 * liên kết một lần để chính họ đặt mật khẩu; <b>lệnh nhận lời mời không còn đòi token</b>. Đọc kỹ
 * phần "THỨ TỰ THAO TÁC" trong javadoc của {@link #accept} trước khi đổi bất cứ dòng nào ở đó.
 *
 * <h2>PHẠM VI CHI — giả định của chủ dự án, KHÔNG phải phán quyết của Hội đồng Tộc biểu</h2>
 * {@link #issue} dùng {@link BranchScopeGuard#requireWriteAccess} chứ không phải
 * {@code requireClanWide}: <b>Trưởng chi phát được lời mời trong phạm vi chi mình; Hội đồng phát
 * được toàn họ.</b> Đó là một trong hai đường mà design 06 §5.6 nêu ra và <i>để ngỏ</i>, và nó được
 * chọn ở đây để luồng dùng được thật — giữ nguyên quyền toàn dòng họ thì mọi lời mời phải qua Hội
 * đồng, và với 1.500 người thì đó là một hàng đợi thật, không phải một chi tiết.
 *
 * <p><b>Cái giá, nói thẳng:</b> một Trưởng chi bị chiếm tài khoản mời được cả chi mình vào, tức mở
 * được Tầng 2 của cả chi ấy. Mọi lần phát đều vào {@code audit_log}, nhưng nhật ký là công cụ điều
 * tra chứ không phải công cụ ngăn chặn.</p>
 *
 * <p><b>Đảo lại rẻ, và đây là chỗ đảo:</b> đổi đúng một lời gọi trong {@link #issue} từ
 * {@code guard.requireWriteAccess(caller, path)} sang
 * {@code guard.requireClanWide(caller, "phat loi moi vao he thong")}. Không có chỗ thứ hai.
 * Nếu Hội đồng chọn đường "Trưởng chi đề nghị, Hội đồng duyệt rồi mã mới sinh" thì ngoài dòng đó
 * còn phải thêm một trạng thái chờ duyệt cho chính lời mời — và lúc ấy nên cân nhắc dùng lại
 * {@code ChangeRequest} thay vì dựng một hàng đợi thứ hai.</p>
 *
 * <h2>{@code AppUserProvisioningService.linkToPerson} vẫn đòi quyền toàn dòng họ, và đó là chủ ý</h2>
 * Hai lối ghép tài khoản vào nhân khẩu có mức rủi ro khác nhau nên có mức quyền khác nhau. Ghép
 * thẳng từ bảng quản trị là thao tác <b>một phía</b>: không ai hỏi người bị ghép. Ghép qua lời mời
 * thì người ở đầu kia phải bấm "Đúng là tôi" — có một con người xác nhận, và có một nút "Không phải
 * tôi" để nói ngược lại. Trưởng chi vì thế được nới quyền ở lối thứ hai chứ không phải lối thứ nhất.
 *
 * <h2>Ba việc chỉ làm ở lúc PHÁT, không để dành tới lúc nhận</h2>
 * Nhân khẩu có tồn tại không · đã có tài khoản chưa · người phát có quyền trên chi đó không. Cả ba
 * đều phát hiện được ngay lúc bấm, và Trưởng chi cần biết ngay — chứ không phải sau khi đã in
 * phiếu, gửi tin nhắn và chờ cụ bà gọi lại bảo không vào được.
 */
@Service
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

    private static final String ENTITY = "Invitation";
    private static final String ENTITY_APP_USER = "AppUser";

    /** Chặn trên của hạn hiệu lực; xem {@link #ttlOf}. */
    private static final int MAX_TTL_DAYS = 30;

    private final InvitationRepository invitations;
    private final AppUserRepository appUsers;
    private final InviteeLookupPort invitees;
    private final BranchLookupPort branches;
    private final MemberScopeService scopes;
    private final BranchScopeGuard guard;
    private final InvitationLinker linker;
    private final IdentityProviderPort identityProvider;
    private final InviteThrottle throttle;
    private final AuditTrailService audit;
    private final int defaultTtlDays;

    @SuppressWarnings("java:S107")
    public InvitationService(InvitationRepository invitations, AppUserRepository appUsers,
                             InviteeLookupPort invitees, BranchLookupPort branches,
                             MemberScopeService scopes, BranchScopeGuard guard,
                             InvitationLinker linker, IdentityProviderPort identityProvider,
                             InviteThrottle throttle, AuditTrailService audit,
                             @Value("${giapha.membership.invitation.ttl-days:7}") int defaultTtlDays) {
        this.invitations = invitations;
        this.appUsers = appUsers;
        this.invitees = invitees;
        this.branches = branches;
        this.scopes = scopes;
        this.guard = guard;
        this.linker = linker;
        this.identityProvider = identityProvider;
        this.throttle = throttle;
        this.audit = audit;
        this.defaultTtlDays = defaultTtlDays;
    }

    // -------------------------------------------------------------------------------------
    // Phát lời mời
    // -------------------------------------------------------------------------------------

    /**
     * Phát một lời mời cho một nhân khẩu. Trả về mã thô <b>đúng một lần</b>.
     *
     * <p>Phát lại cho cùng một người sẽ <b>thu hồi mã cũ</b> trước. Đó không phải tiện lợi mà là
     * điều kiện để chữ "thu hồi" có nghĩa: hai mã cùng mở được một hồ sơ thì thu hồi một mã không
     * đóng được cửa nào. {@code ux_invitation_open_person} canh điều này ở CSDL.</p>
     */
    @Transactional
    public IssuedInvitation issue(IssueInvitationCommand command) {
        if (command == null || command.personId() == null) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Phai chi dinh nhan khau duoc moi");
        }
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);

        Invitee invitee = invitees.byId(command.personId())
                .orElseThrow(() -> new NotFoundException(MembershipProblemCodes.NOT_FOUND,
                        "Khong tim thay nhan khau " + command.personId()));

        // PHEP KIEM PHAM VI CHI. Doc javadoc cua lop truoc khi doi dong nay.
        guard.requireWriteAccess(caller, invitee.branchPath());

        if (invitee.deleted()) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Nhan khau nay da bi xoa khoi pha, khong moi duoc");
        }
        if (!invitee.alive()) {
            // Khong phai mot phep kiem hinh thuc: ho so nguoi da khuat la du lieu cong khai, va
            // khong ai can mot tai khoan de "la" mot nguoi da khuat.
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Khong the moi mot nguoi da khuat");
        }

        // MOT NHAN KHAU CHI GAN MOT TAI KHOAN — tu choi o day, khong de toi luc nhan.
        if (appUsers.byPersonId(command.personId()).isPresent()) {
            throw new DomainException(MembershipProblemCodes.PERSON_ALREADY_LINKED,
                    "Nhan khau nay da co tai khoan, khong can moi lai");
        }

        Instant now = Instant.now();
        invitations.openForPerson(command.personId()).ifPresent(previous -> {
            previous.revoke("Phat lai loi moi moi", now);
            invitations.save(previous);
            audit.record(ENTITY, previous.id().toString(), AuditAction.UPDATE,
                    null, Map.of("status", previous.status().name()), List.of("status"),
                    "Thu hoi tu dong vi phat lai loi moi");
            log.info("Thu hoi loi moi {} vi phat lai cho person {}", previous.id(),
                    command.personId());
        });

        String code = InvitationCode.generate();
        UUID branchId = branches.branchIdOfPerson(command.personId()).orElse(null);
        Invitation invitation = Invitation.issue(UUID.randomUUID(), InvitationCode.hash(code),
                command.personId(), branchId, caller.appUserId(),
                now.plus(ttlOf(command)), command.note());
        Invitation saved = invitations.save(invitation);

        // KHONG ghi ma tho vao audit: audit_log la bang chi ghi them, mot bi mat lot vao do la lot
        // vinh vien. Ca bam cung khong — xem javadoc InvitationView.
        audit.record(ENTITY, saved.id().toString(), AuditAction.CREATE, null,
                Map.of("personId", String.valueOf(saved.personId()),
                        "branchId", String.valueOf(saved.branchId()),
                        "expiresAt", String.valueOf(saved.expiresAt())),
                List.of("personId", "expiresAt"), "Phat loi moi vao he thong");
        log.info("Phat loi moi {} cho person {} boi app_user {}, han {}",
                saved.id(), saved.personId(), caller.appUserId(), saved.expiresAt());

        return new IssuedInvitation(code, InvitationView.from(saved, now));
    }

    // -------------------------------------------------------------------------------------
    // Nhận lời mời
    // -------------------------------------------------------------------------------------

    /**
     * Nội dung màn "Lời mời này dành cho ai" — <b>không đòi đăng nhập</b>.
     *
     * <p>Người nhận chưa có tài khoản; bắt họ đăng nhập trước khi biết lời mời dành cho ai là bắt
     * họ đặt mật khẩu cho một thứ họ chưa xác nhận là của mình.</p>
     *
     * <p>Đổi lại, đây là <b>bề mặt rò rỉ duy nhất</b> của luồng và nó trả về tên một người đang
     * sống. Xem {@link InvitationPreview} và {@link InviteThrottle} để biết ba lớp chống đỡ đi kèm.</p>
     */
    @Transactional(readOnly = true)
    public InvitationPreview preview(String rawCode, String clientId) {
        throttle.guard(clientId);
        Invitation invitation = resolveUsable(rawCode, clientId);
        Invitee invitee = invitees.byId(invitation.personId())
                .orElseThrow(() -> new NotFoundException(MembershipProblemCodes.NOT_FOUND,
                        "Khong tim thay loi moi khop ma nay"));
        throttle.recordSuccess(clientId, false);
        return new InvitationPreview(invitee.clanName(), inviterOf(invitation),
                new InvitationPreview.Invitee(invitee.displayName(), invitee.generation(),
                        branchBlockOf(invitee)),
                invitation.expiresAt());
    }

    /**
     * Nhận lời mời: tài khoản được gắn vào nhân khẩu của lời mời, <b>ngay lập tức</b>, và — với
     * người chưa từng có tài khoản — tài khoản Keycloak được lập ngay tại đây.
     *
     * <h2>Đây là chỗ vòng luẩn quẩn bị phá</h2>
     * Realm đặt {@code registrationAllowed: false} và đó là mặc định đúng, nên trước khi có lối
     * này thì <b>người được mời phải đã có tài khoản mới nhận được lời mời</b> — tức là luồng mời
     * không giải quyết được đúng vấn đề nó sinh ra để giải quyết. Nay lệnh này nhận thêm một email
     * người nhận tự khai, lập tài khoản qua {@code IdentityProviderPort}, và trả về một liên kết
     * một lần để chính họ đặt mật khẩu. Hệ quả: <b>{@code accept} không còn đòi token</b>.
     *
     * <h2>Không đi qua trạng thái chờ duyệt — đây là cả điểm của thiết kế</h2>
     * Không có {@code ChangeRequest} nào được tạo, không ai phải bấm Duyệt. Tài khoản ra khỏi
     * phương thức này ở trạng thái {@code ACTIVE} và đã có {@code person_id}.
     *
     * <h2>Ai cho phép thao tác này</h2>
     * <b>Việc sở hữu mã</b>, không phải vai trò của người gọi. Người nhận thường là một tài khoản
     * mới tinh, không vai, không phạm vi — nên không có phép kiểm {@code BranchScopeGuard} nào ở
     * đây, và điều đó là đúng: quyền đã được kiểm một lần rồi, lúc phát.
     *
     * <h2>THỨ TỰ THAO TÁC — phần khó nhất của phương thức này</h2>
     * Keycloak và Postgres là <b>hai hệ thống</b>, không có transaction chung. Thứ tự được chọn:
     * <ol>
     *   <li>tra mã (chỉ đọc);</li>
     *   <li>tìm-hoặc-tạo tài khoản Keycloak — <b>lặp lại được</b>, gọi hai lần ra cùng một tài
     *       khoản;</li>
     *   <li>đúc liên kết đặt mật khẩu — thuần tính toán, không để lại vết ở đâu;</li>
     *   <li><b>rồi mới</b> ghi CSDL: ghép {@code app_user} với nhân khẩu <i>và</i> đốt mã, trong
     *       cùng một transaction ({@link InvitationLinker}).</li>
     * </ol>
     *
     * <p><b>Vì sao không phải thứ tự ngược lại.</b> Ghi CSDL trước rồi mới tạo tài khoản Keycloak
     * nghe có vẻ "an toàn hơn" vì CSDL rollback được — nhưng nó tạo ra đúng cái kẹt không ai gỡ
     * được: mã đã cháy, nhân khẩu đã bị chiếm bởi một {@code app_user} trỏ tới một {@code sub}
     * không tồn tại, và người dùng thì không có tài khoản để đăng nhập mà sửa. Thứ tự ở trên thì
     * mọi hỏng hóc đều rơi về cùng một trạng thái lành: <b>mã mời chưa bị đánh dấu đã dùng</b>, nên
     * người dùng bấm lại được, và lần bấm lại tìm thấy tài khoản Keycloak của lần trước thay vì tạo
     * bản sao.</p>
     *
     * <p><b>Tài khoản Keycloak mồ côi thì KHÔNG xoá đi.</b> Nếu bước 4 hỏng, tài khoản của bước 2 ở
     * lại: nó không ghép với nhân khẩu nào nên không thấy được gì ngoài dữ liệu công khai — đúng
     * trạng thái của mọi tài khoản vừa đăng nhập Google lần đầu. Xoá nó đi lại <i>tự tạo</i> một
     * lỗi mới: hai người bấm gần nhau thì luồng A tạo, luồng B tìm thấy và dùng, rồi luồng A hỏng
     * và xoá mất tài khoản mà luồng B vừa ghép xong.</p>
     */
    public AcceptedInvitation accept(AcceptInvitationCommand command) {
        if (command == null || command.code() == null) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Phai nhap ma moi");
        }
        String clientId = command.clientId();
        throttle.guard(clientId);

        // (1) Tra ma. Chi doc — ma chua chet o buoc nay.
        Invitation invitation = resolveUsable(command.code(), clientId);

        Optional<CurrentUser> caller = CurrentUserProvider.current();
        AcceptedInvitation accepted = caller.isPresent()
                ? acceptWithToken(invitation, caller.get())
                : acceptAsNewAccount(invitation, command);

        throttle.recordSuccess(clientId, true);
        return accepted;
    }

    /**
     * Người gọi đã có token: danh tính lấy từ token, không hỏi email, không tạo gì ở Keycloak.
     *
     * <p>Không phát liên kết đặt mật khẩu — họ vừa đăng nhập được thì hiển nhiên đã có cách đăng
     * nhập. Đây cũng là lối của người đã vào bằng Google/Zalo nhưng chưa được ghép vào cây.</p>
     */
    private AcceptedInvitation acceptWithToken(Invitation invitation, CurrentUser caller) {
        AppUser linked = linker.link(invitation.id(), caller.keycloakSub(), caller.email(),
                caller.username());
        return AcceptedInvitation.withoutLink(linked);
    }

    /** Người chưa có tài khoản: lập tài khoản Keycloak rồi mới ghi CSDL. Xem javadoc {@link #accept}. */
    private AcceptedInvitation acceptAsNewAccount(Invitation invitation,
                                                  AcceptInvitationCommand command) {
        String email = command.email() == null ? null : command.email().trim();
        if (email == null || email.isBlank()) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Phai cho biet dia chi thu dien tu de lap tai khoan");
        }
        if (!identityProvider.isConfigured()) {
            // Noi thang la chua cau hinh, khong gia vo la loi cua nguoi dung: ho khong sua duoc.
            throw new IdentityProviderException(
                    "He thong chua duoc cau hinh de lap tai khoan moi");
        }

        // (2) TIM TRUOC, TAO SAU. Nguoi ay co the da co tai khoan tu mot email cu, hoac vua bam
        // hai lan, hoac dang thu lai sau mot lan ghep hong.
        IdentityAccount account = identityProvider.findByEmail(email)
                .orElseGet(() -> identityProvider.createAccount(
                        new NewIdentityAccount(email, email, command.displayName())));

        // (3) Duc lien ket. Rong khi tai khoan DA co mat khau — xem AcceptedInvitation.
        Optional<SetPasswordLink> link = identityProvider.issueSetPasswordLink(account);

        // (4) Buoc duy nhat khong dao nguoc duoc.
        AppUser linked = linker.link(invitation.id(), account.subject(), email,
                command.displayName() == null ? email : command.displayName());

        log.info("Loi moi {} duoc nhan boi tai khoan Keycloak {} (vua tao: {}), phat lien ket dat"
                        + " mat khau: {}", invitation.id(), account.subject(),
                account.justCreated(), link.isPresent());
        return new AcceptedInvitation(linked, link);
    }

    /**
     * "Không phải tôi" — huỷ mã, <b>không đòi đăng nhập</b>.
     *
     * <p>Đây là lớp chống đỡ mà design 06 §5.2 đặt ngang hàng với "dùng một lần" và "hết hạn":
     * người gửi nhầm số không có cách nào khác để biết mình gửi nhầm. Bắt đăng nhập trước khi bấm
     * được nút này là vô hiệu hoá nó — người bấm chính là người <i>không</i> có tài khoản.</p>
     *
     * <p>Người mời được báo qua {@code audit_log}; gửi thông báo chủ động cho Trưởng chi là việc
     * của context {@code notification} và chưa nằm trong phạm vi này.</p>
     */
    @Transactional
    public void decline(String rawCode, String clientId) {
        throttle.guard(clientId);
        Invitation invitation = resolveUsable(rawCode, clientId);
        invitation.revoke("Nguoi nhan bam: Khong phai toi", Instant.now());
        invitations.save(invitation);
        audit.record(ENTITY, invitation.id().toString(), AuditAction.UPDATE, null,
                Map.of("status", invitation.status().name()), List.of("status"),
                "Nguoi nhan tu choi loi moi");
        throttle.recordSuccess(clientId, false);
        log.info("Loi moi {} bi nguoi nhan tu choi, ma da chet", invitation.id());
    }

    // -------------------------------------------------------------------------------------
    // Quản trị
    // -------------------------------------------------------------------------------------

    /** Thu hồi một lời mời còn mở — đòi đúng quyền ghi trên chi đích, như lúc phát. */
    @Transactional
    public InvitationView revoke(UUID invitationId, String reason) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);
        Invitation invitation = invitations.byId(invitationId)
                .orElseThrow(() -> new NotFoundException(MembershipProblemCodes.NOT_FOUND,
                        "Khong tim thay loi moi " + invitationId));
        guard.requireWriteAccess(caller, branchPathOf(invitation));

        try {
            invitation.revoke(reason, Instant.now());
        } catch (IllegalStateException ex) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED, ex.getMessage(), ex);
        }
        Invitation saved = invitations.save(invitation);
        audit.record(ENTITY, saved.id().toString(), AuditAction.UPDATE, null,
                Map.of("status", saved.status().name()), List.of("status"),
                reason == null ? "Thu hoi loi moi" : reason);
        return InvitationView.from(saved, Instant.now());
    }

    /** Lời mời trong phạm vi người gọi. Trưởng chi thấy chi mình, Hội đồng thấy toàn họ. */
    @Transactional(readOnly = true)
    public List<InvitationView> inScope(int page, int size) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);
        int limit = Math.min(Math.max(size, 1), 200);
        int offset = Math.max(page, 0) * limit;
        Instant now = Instant.now();
        return invitations.inScope(caller.managedBranches(), caller.isClanWide(), limit, offset)
                .stream()
                .map(invitation -> InvitationView.from(invitation, now))
                .toList();
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ
    // -------------------------------------------------------------------------------------

    /**
     * Tra mã và khẳng định nó còn dùng được, đồng thời đếm lần thất bại.
     *
     * <p><b>Mã không tồn tại và mã hết hạn dùng chung một mã lỗi</b> ({@code INVITE_NOT_USABLE});
     * khác nhau ở <i>thông điệp</i>, và thông điệp chỉ nói ra lý do khi mã thật sự khớp một lời
     * mời. Phân biệt bằng <i>mã lỗi</i> sẽ biến endpoint này thành máy xác nhận mã tồn tại.</p>
     */
    private Invitation resolveUsable(String rawCode, String clientId) {
        String hash;
        try {
            hash = InvitationCode.hash(rawCode);
        } catch (IllegalArgumentException ex) {
            // Ma sai dinh dang van la mot lan thu: khong dem thi bo dem tro thanh tu chon.
            // Va no tra cung mot cau tra loi voi ma dung dinh dang nhung khong ton tai — neu khac
            // nhau thi ke do doc duoc do dai va bang chu cua ma ma khong can doan trung lan nao.
            throttle.recordFailure(clientId);
            throw khongKhopLoiMoiNao();
        }
        Optional<Invitation> found = invitations.byCodeHash(hash);
        if (found.isEmpty()) {
            throttle.recordFailure(clientId);
            throw khongKhopLoiMoiNao();
        }
        Invitation invitation = found.get();
        InvitationUsability usability = invitation.usabilityAt(Instant.now());
        if (!usability.isUsable()) {
            throttle.recordFailure(clientId);
            throw new InvitationNotUsableException(usability);
        }
        return invitation;
    }

    /** Hạn hiệu lực: mặc định 7 ngày, rút ngắn được, nhưng không kéo dài quá 30 ngày. */
    private Duration ttlOf(IssueInvitationCommand command) {
        int days = command.ttlDays() == null ? defaultTtlDays : command.ttlDays();
        if (days < 1 || days > MAX_TTL_DAYS) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Han loi moi phai tu 1 den " + MAX_TTL_DAYS + " ngay");
        }
        return Duration.ofDays(days);
    }

    private BranchPath branchPathOf(Invitation invitation) {
        return invitees.byId(invitation.personId())
                .map(Invitee::branchPath)
                .orElseGet(() -> branches.pathOfBranch(invitation.branchId()).orElse(null));
    }

    /**
     * Mã không khớp lời mời nào — <b>404 với mã {@code NOT_FOUND} sẵn có</b>, không phải một mã thứ
     * tư.
     *
     * <p>{@code ProblemCode} là danh sách đóng trong contract, và ca này không mang thêm nghĩa nào
     * so với "không tìm thấy". Ba ca còn lại (hết hạn · đã dùng · đã thu hồi) mới có mã riêng, vì
     * mỗi ca dẫn tới một màn hình và một lối đi tiếp khác nhau.</p>
     */
    private static NotFoundException khongKhopLoiMoiNao() {
        return new NotFoundException(MembershipProblemCodes.NOT_FOUND,
                "Khong tim thay loi moi khop ma nay");
    }

    /** Khối chi/ngành cho màn nhận lời mời; {@code null} với nhân khẩu chưa gắn chi. */
    private static InvitationPreview.Branch branchBlockOf(Invitee invitee) {
        if (invitee.branchId() == null) {
            return null;
        }
        return new InvitationPreview.Branch(invitee.branchId(), invitee.branchName(),
                invitee.branchPath(), invitee.branchRegion());
    }

    /**
     * Người mời để in lên màn nhận lời mời.
     *
     * <p>Tên: ưu tiên tên trong phả (người mời cũng là một nhân khẩu), lùi về
     * {@code display_name} của tài khoản. <b>Không bao giờ email</b> — đó là dữ liệu Tầng 3, còn
     * người đọc màn này chưa đăng nhập. Và không kính ngữ: xem
     * {@link InvitationPreview.Inviter}.</p>
     *
     * <p>Chức danh: lấy từ {@code branch.head_person_id}, tức chức danh <b>dòng tộc</b>, không phải
     * vai kỹ thuật trong {@code branch_assignment}. Trống là câu trả lời đúng với phần lớn người
     * trong họ.</p>
     */
    private InvitationPreview.Inviter inviterOf(Invitation invitation) {
        Optional<AppUser> found = appUsers.byId(invitation.invitedBy());
        if (found.isEmpty()) {
            return new InvitationPreview.Inviter(null, null);
        }
        AppUser account = found.get();
        UUID personId = account.personId();
        String name = account.displayName();
        String clanTitle = null;
        if (personId != null) {
            name = invitees.byId(personId).map(Invitee::displayName).orElse(name);
            clanTitle = ClanTitle.of(invitees.clanOfficeOf(personId).orElse(null));
        }
        return new InvitationPreview.Inviter(name, clanTitle);
    }
}
