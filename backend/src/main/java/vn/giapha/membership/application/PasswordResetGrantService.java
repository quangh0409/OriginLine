package vn.giapha.membership.application;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.giapha.membership.application.command.IssuePasswordResetCommand;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.AppUserStatus;
import vn.giapha.membership.domain.Invitee;
import vn.giapha.membership.domain.MemberScope;
import vn.giapha.membership.domain.PasswordResetGrant;
import vn.giapha.membership.domain.port.AppUserRepository;
import vn.giapha.membership.domain.port.BranchLookupPort;
import vn.giapha.membership.domain.port.IdentityAccount;
import vn.giapha.membership.domain.port.IdentityProviderException;
import vn.giapha.membership.domain.port.InviteeLookupPort;
import vn.giapha.membership.domain.port.PasswordResetGrantRepository;
import vn.giapha.membership.domain.port.PasswordResetPort;
import vn.giapha.membership.domain.port.SetPasswordLink;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * <b>Trưởng chi / Hội đồng cấp lại liên kết đặt mật khẩu</b> cho một thành viên đã quên mật khẩu.
 *
 * <h2>Vấn đề có thật, và nó không phải vấn đề kỹ thuật</h2>
 * Realm bật {@code resetPasswordAllowed: true}, nhưng đường phục hồi duy nhất Keycloak biết làm là
 * <b>gửi thư</b>. Sản phẩm lại cho lập tài khoản bằng email <i>hoặc</i> số điện thoại, và lối số
 * điện thoại sinh ra chính vì <b>các cụ không có email</b>. Hệ quả trước lớp này: một cụ đăng ký
 * bằng số máy rồi quên mật khẩu thì <b>không có đường nào tự lấy lại</b> — {@code messages/vi.json}
 * phải bảo họ "gọi Trưởng chi". Đó là câu trả lời đúng, nhưng nó biến mỗi lần quên mật khẩu thành
 * một cuộc điện thoại, và với 1.500 người thì đó là một gánh nặng thật lên Trưởng chi.
 *
 * <h2>KHÔNG dựng cơ chế thứ hai</h2>
 * Liên kết ở đây là <b>cùng một thứ</b> với liên kết của luồng mời: cùng {@code SetPasswordTokenCodec}
 * (khác chuỗi phân tách miền), cùng khuôn địa chỉ {@code giapha.keycloak.admin.set-password-url},
 * cùng màn {@code /dat-mat-khau}, cùng endpoint {@code POST /api/v1/invitations/set-password}. Việc
 * của lớp này là <b>một luật phân quyền</b>, không phải một cơ chế mới.
 *
 * <h2>Ai được cấp cho ai — bốn phép kiểm, không phép nào bỏ được</h2>
 * <ol>
 *   <li><b>Phạm vi {@code ltree}.</b> {@code BranchScopeGuard.requireWriteAccess} trên chi của
 *       <i>nhân khẩu đích</i>: Trưởng chi Ất không cấp được cho người Chi Bính; Hội đồng thì cả
 *       họ.</li>
 *   <li><b>Không ai tự cấp cho chính mình.</b> "Quên mật khẩu của chính mình" đi đường khác. Nếu tự
 *       cấp được thì một phiên bị chiếm sẽ tự gia hạn thành quyền kiểm soát vĩnh viễn tài khoản.
 *       Chặn lần thứ hai ở CSDL ({@code ck_password_reset_grant_not_self}).</li>
 *   <li><b>Tài khoản đích phải KHÔNG có địa chỉ thư.</b> Xem dưới — đây là phép kiểm đáng bàn
 *       nhất.</li>
 *   <li><b>Tài khoản đích phải còn {@code ACTIVE}</b> và còn tồn tại trong realm.</li>
 * </ol>
 *
 * <h2>Vì sao "không có email" là điều kiện, chứ không phải một tiện ích</h2>
 * Cấp liên kết đặt mật khẩu cho người khác <b>nghĩa là</b> đặt lại được mật khẩu của người ấy: liên
 * kết hiện trên màn hình người cấp (họ phải đọc nó để đưa tận tay). Đó là một năng lực chiếm tài
 * khoản, và cách duy nhất để nó chính đáng là <b>người kia không còn đường nào khác</b>. Ai đã có
 * email thì Keycloak tự lo được bằng "quên mật khẩu", nên mở thêm đường này cho họ chỉ thêm một lối
 * tấn công mà không giải quyết vấn đề nào.
 *
 * <p>Phép kiểm ấy còn <b>tự động loại mọi tài khoản đăng nhập bằng Google/Zalo</b>: một danh tính
 * xã hội luôn kèm địa chỉ thư. Không có nó thì Trưởng chi đặt được mật khẩu cục bộ lên một tài
 * khoản mà chủ nhân vẫn đang đăng nhập bằng Google, và chủ nhân ấy sẽ không bao giờ nhận ra. Đây
 * đúng là loại lỗ hổng mà {@code IdentityReclaimPolicy} đã phải đóng một lần ở luồng mời.</p>
 *
 * <p><b>Ca còn hở, đã biết và cố ý không chặn:</b> một tài khoản lập bằng số điện thoại rồi
 * <i>về sau</i> tự liên kết với Google (Keycloak giữ liên kết liên thông mà không nhất thiết điền
 * thuộc tính {@code email}) vẫn cấp lại được. Chặn nó cần thêm một lượt gọi
 * {@code GET /users/&#123;id&#125;/federated-identity} cho mỗi lần cấp. Không chặn, vì: người ấy
 * <i>vẫn</i> là thành viên nằm trong phạm vi của Trưởng chi, việc cấp vẫn để lại ba dấu vết đầy đủ,
 * và cái giá của phép chặn là một lượt gọi mạng nữa trên một đường vốn đã chạm Keycloak hai lần.
 * Ghi ra đây để lần sau không ai phải đoán rằng nó bị bỏ sót.</p>
 *
 * <h2>Vì sao KHÔNG dùng lại {@link InviteThrottle}</h2>
 * {@code InviteThrottle} trả lời câu hỏi "<i>người gọi ẩn danh này đã gõ sai mã bao nhiêu lần</i>",
 * khoá theo <b>băm địa chỉ IP</b> và chỉ đếm lần <b>thất bại</b>. Ở đây cả ba vế đều lệch: người
 * gọi <i>đã đăng nhập</i> (khoá đúng là {@code app_user}, không phải IP), và thứ cần đếm là những
 * lần <b>thành công</b> — một Trưởng chi bị chiếm tài khoản sẽ cấp hàng loạt liên kết <i>hợp lệ</i>,
 * không sai lần nào.
 *
 * <p>Tệ hơn nếu chép chung: bộ đếm của {@code InviteThrottle} là <b>một bộ đếm chung</b> cho cả hai
 * luồng mã mời, có chủ ý (xem javadoc của nó). Đổ thêm lượt cấp lại vào đó nghĩa là Trưởng chi ngồi
 * ở văn phòng dòng họ cấp vài liên kết sẽ <i>tiêu mất</i> hạn mức mà người đang gõ mã mời từ cùng
 * địa chỉ IP cần tới — hai tính năng không liên quan chặn lẫn nhau, và không ai nhìn ra khi đọc
 * riêng từng bảng.</p>
 *
 * <p>Bộ đếm ở đây vì thế là một câu {@code count(*)} trên chính {@code password_reset_grant} theo
 * {@code issued_by} — không bảng mới, không bộ đếm thứ hai, và dữ liệu thì <i>đã</i> phải ghi.</p>
 */
@Service
public class PasswordResetGrantService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetGrantService.class);

    private final AppUserRepository appUsers;
    private final InviteeLookupPort invitees;
    private final BranchLookupPort branches;
    private final MemberScopeService scopes;
    private final BranchScopeGuard guard;
    private final PasswordResetGrantRepository grants;
    private final PasswordResetGrantWriter writer;
    private final PasswordResetPort resets;
    private final Duration linkTtl;
    private final int maxPerHour;

    @SuppressWarnings("java:S107")
    public PasswordResetGrantService(AppUserRepository appUsers, InviteeLookupPort invitees,
                                     BranchLookupPort branches, MemberScopeService scopes,
                                     BranchScopeGuard guard, PasswordResetGrantRepository grants,
                                     PasswordResetGrantWriter writer, PasswordResetPort resets,
                                     @Value("${giapha.keycloak.admin.link-ttl:30m}") Duration linkTtl,
                                     @Value("${giapha.membership.password-reset.max-per-hour:10}")
                                     int maxPerHour) {
        this.appUsers = appUsers;
        this.invitees = invitees;
        this.branches = branches;
        this.scopes = scopes;
        this.guard = guard;
        this.grants = grants;
        this.writer = writer;
        this.resets = resets;
        this.linkTtl = linkTtl;
        this.maxPerHour = maxPerHour;
    }

    /**
     * Cấp lại liên kết đặt mật khẩu cho nhân khẩu {@code command.personId()}.
     *
     * <h2>THỨ TỰ THAO TÁC</h2>
     * <ol>
     *   <li>kiểm quyền và kiểm phạm vi chi — <b>trước mọi thứ khác</b>, để một lượt gọi sai quyền
     *       không hề chạm tới Keycloak và không để lại một tín hiệu nào về việc tài khoản kia có
     *       tồn tại hay không;</li>
     *   <li>kiểm tài khoản đích ở Keycloak (chỉ đọc, ngoài transaction);</li>
     *   <li>kiểm giới hạn tần suất;</li>
     *   <li>ghi CSDL + nhật ký + thông báo cho chính chủ, <b>một transaction</b>
     *       ({@link PasswordResetGrantWriter});</li>
     *   <li><b>rồi mới</b> đúc liên kết — thuần tính toán, không để lại vết ở đâu.</li>
     * </ol>
     *
     * <p><b>Vì sao đúc liên kết ở bước cuối.</b> Đúc trước rồi ghi sau thì mỗi lần ghi hỏng vẫn đẻ
     * ra một token hợp lệ về mặt chữ ký; nó không mở được gì (không có dòng {@code grant} để đốt),
     * nhưng nó đã tồn tại ở đâu đó trong bộ nhớ và trong một stack trace. Đúc sau cùng thì một lượt
     * gọi hỏng <b>không sinh ra token nào</b>, và đó là bất biến dễ canh trong test hơn hẳn.</p>
     */
    public IssuedPasswordReset issue(IssuePasswordResetCommand command) {
        if (command == null || command.personId() == null) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Phải chỉ định nhân khẩu cần cấp lại liên kết đặt mật khẩu");
        }
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);

        Invitee target = invitees.byId(command.personId())
                .orElseThrow(() -> new NotFoundException(MembershipProblemCodes.NOT_FOUND,
                        "Không tìm thấy nhân khẩu " + command.personId()));

        // (1) PHEP KIEM PHAM VI CHI — doc ltree hien hanh cua nhan khau dich, khong doc anh chup
        // nao. Dat truoc moi thu khac: xem javadoc.
        guard.requireWriteAccess(caller, target.branchPath());

        AppUser targetUser = appUsers.byPersonId(command.personId())
                .orElseThrow(() -> new DomainException(
                        MembershipProblemCodes.PASSWORD_RESET_NOT_ALLOWED,
                        "Nhân khẩu này chưa có tài khoản — hãy phát lời mời thay vì cấp lại mật khẩu"));

        // (2) KHONG AI TU CAP CHO CHINH MINH.
        if (targetUser.id().equals(caller.appUserId()) || caller.isSelf(command.personId())) {
            throw new ForbiddenException(MembershipProblemCodes.SELF_PASSWORD_RESET_FORBIDDEN,
                    "Không tự cấp liên kết đặt mật khẩu cho chính mình được; đây là luồng"
                            + " quên mật khẩu, hãy dùng đường khác");
        }

        if (targetUser.status() != AppUserStatus.ACTIVE) {
            throw new DomainException(MembershipProblemCodes.PASSWORD_RESET_NOT_ALLOWED,
                    "Tài khoản của người này đang ở trạng thái " + targetUser.status()
                            + ", không cấp lại liên kết đặt mật khẩu được");
        }

        kiemTaiKhoanDich(targetUser);
        kiemTanSuat(caller.appUserId());

        Instant now = Instant.now();
        PasswordResetGrant grant = PasswordResetGrant.issue(UUID.randomUUID(), targetUser.id(),
                command.personId(),
                branches.branchIdOfPerson(command.personId()).orElse(null),
                caller.appUserId(), now, now.plus(linkTtl), command.note());

        PasswordResetGrant saved = writer.write(grant, targetUser,
                appUsers.byId(caller.appUserId()).orElse(null));

        SetPasswordLink link = resets.issueResetLink(saved.id(), saved.expiresAt());
        // KHONG log link.url(). Mot bi mat lot vao tep log la lot vinh vien.
        return new IssuedPasswordReset(link.url(), link.expiresAt(),
                PasswordResetGrantView.from(saved, target));
    }

    /**
     * Tài khoản đích ở Keycloak có đủ điều kiện nhận một liên kết cấp lại không.
     *
     * <p>Đọc kỹ javadoc lớp về vế "không có địa chỉ thư" trước khi nới điều kiện này.</p>
     */
    private void kiemTaiKhoanDich(AppUser targetUser) {
        if (!resets.isConfigured()) {
            throw new IdentityProviderException(
                    "He thong chua duoc cau hinh de cap lai lien ket dat mat khau");
        }
        IdentityAccount account = resets.findBySubject(targetUser.keycloakSub())
                .orElseThrow(() -> new DomainException(
                        MembershipProblemCodes.PASSWORD_RESET_NOT_ALLOWED,
                        "Tài khoản đăng nhập của người này không còn tồn tại, hãy báo Quản trị hệ thống"));
        if (account.email() != null && !account.email().isBlank()) {
            throw new DomainException(MembershipProblemCodes.PASSWORD_RESET_NOT_ALLOWED,
                    "Tài khoản này có địa chỉ thư nên tự lấy lại mật khẩu được:"
                            + " bấm 'Quên mật khẩu' ở màn đăng nhập để nhận thư");
        }
    }

    private void kiemTanSuat(UUID issuerAppUserId) {
        Instant since = Instant.now().minus(Duration.ofHours(1));
        int issued = grants.countIssuedBySince(issuerAppUserId, since);
        if (issued >= maxPerHour) {
            log.warn("Chan cap lai mat khau: app_user {} da cap {} lan trong mot gio", issuerAppUserId,
                    issued);
            throw new TooManyAttemptsException(
                    "Đã cấp quá nhiều liên kết đặt mật khẩu trong một giờ, hãy đợi rồi làm tiếp",
                    Duration.ofHours(1).toSeconds());
        }
    }
}
