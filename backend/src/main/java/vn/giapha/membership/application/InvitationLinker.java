package vn.giapha.membership.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.Invitation;
import vn.giapha.membership.domain.InvitationUsability;
import vn.giapha.membership.domain.port.AppUserRepository;
import vn.giapha.membership.domain.port.InvitationRepository;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * <b>Bước duy nhất không đảo ngược được của luồng nhận lời mời</b>, gói gọn trong một transaction
 * Postgres: ghép {@code app_user} với nhân khẩu <i>và</i> đốt mã mời, cùng lúc, hoặc không làm gì cả.
 *
 * <h2>Vì sao tách khỏi {@code InvitationService}</h2>
 * Việc nhận lời mời chạm vào <b>hai hệ thống</b>: Keycloak và Postgres. Không có transaction chung
 * cho cả hai, nên {@code InvitationService.accept} phải gọi Keycloak <i>ngoài</i> transaction —
 * giữ một connection Postgres mở trong lúc chờ mạng là cách chắc chắn để cạn pool vào ngày giỗ họ.
 * Mà một phương thức {@code @Transactional} thì không có chỗ nào "ở ngoài": ranh giới là cả phương
 * thức. Vì vậy phần CSDL được tách sang một bean riêng, và ranh giới transaction trở thành đúng cái
 * người đọc nhìn thấy.
 *
 * <h2>Thứ tự bên trong: ghép tài khoản TRƯỚC, đốt mã SAU</h2>
 * {@link Invitation#accept} được gọi <b>sau cùng</b>, khi mọi phép kiểm đã qua và
 * {@code app_user} đã ghi xong. Đảo lại thì ở môi trường có rollback thật vẫn đúng, nhưng lập luận
 * "mã chỉ chết khi mọi thứ đã xong" sẽ phụ thuộc hoàn toàn vào rollback thay vì đọc được từ thứ tự
 * dòng lệnh — và đó là loại bất biến người ta vô tình phá khi thêm một lời gọi ở giữa.
 *
 * <h2>Lời mời được ĐỌC LẠI bên trong transaction</h2>
 * {@code InvitationService} đã tra mã một lần ở ngoài, trước khi gọi Keycloak. Giữa hai thời điểm
 * ấy có vài lượt HTTP, và trong khoảng đó Trưởng chi có thể vừa thu hồi mã, hoặc người kia vừa bấm
 * "Không phải tôi". Đọc lại ở đây là phép kiểm <b>cuối cùng</b> và là phép kiểm duy nhất chạy cùng
 * transaction với lần ghi.
 */
@Service
public class InvitationLinker {

    private static final Logger log = LoggerFactory.getLogger(InvitationLinker.class);

    private static final String ENTITY = "Invitation";
    private static final String ENTITY_APP_USER = "AppUser";

    private final InvitationRepository invitations;
    private final AppUserRepository appUsers;
    private final AppUserProvisioningService provisioning;
    private final AuditTrailService audit;

    public InvitationLinker(InvitationRepository invitations, AppUserRepository appUsers,
                            AppUserProvisioningService provisioning, AuditTrailService audit) {
        this.invitations = invitations;
        this.appUsers = appUsers;
        this.provisioning = provisioning;
        this.audit = audit;
    }

    /**
     * Ghép tài khoản của {@code keycloakSub} vào nhân khẩu của lời mời và đánh dấu mã đã dùng.
     *
     * @param invitationId lời mời đã được xác nhận còn dùng được ở ngoài transaction này
     * @param keycloakSub  {@code sub} của tài khoản đăng nhập — từ token, hoặc từ tài khoản vừa lập
     * @param email        email lấy từ token / từ màn nhận lời mời, dùng để làm tươi hồ sơ hiển thị
     * @param displayName  tên hiển thị, có thể {@code null}
     * @return tài khoản đã {@code ACTIVE} và đã có {@code personId}
     */
    @Transactional
    public AppUser link(UUID invitationId, String keycloakSub, String email, String displayName) {
        Invitation invitation = invitations.byId(invitationId)
                .orElseThrow(() -> new NotFoundException(MembershipProblemCodes.NOT_FOUND,
                        "Khong tim thay loi moi khop ma nay"));
        InvitationUsability usability = invitation.usabilityAt(Instant.now());
        if (!usability.isUsable()) {
            throw new InvitationNotUsableException(usability);
        }

        AppUser account = provisioning.ensureFor(keycloakSub, email, displayName);

        if (account.personId() != null && !account.personId().equals(invitation.personId())) {
            throw new DomainException(MembershipProblemCodes.ACCOUNT_ALREADY_LINKED,
                    "Tai khoan nay da gan voi mot nhan khau khac trong pha");
        }
        Optional<AppUser> occupied = appUsers.byPersonId(invitation.personId());
        if (occupied.isPresent() && !occupied.get().id().equals(account.id())) {
            // Duong dua: mot tai khoan khac vua gan xong trong luc loi moi nam cho.
            throw new DomainException(MembershipProblemCodes.PERSON_ALREADY_LINKED,
                    "Nhan khau nay da duoc ghep voi mot tai khoan khac");
        }

        account.linkPerson(invitation.personId());
        AppUser linked = appUsers.save(account);

        // DOT MA — dong cuoi cung, sau khi moi thu da xong. Xem javadoc lop.
        invitation.accept(linked.id(), Instant.now());
        invitations.save(invitation);

        audit.record(ENTITY, invitation.id().toString(), AuditAction.UPDATE, null,
                Map.of("status", invitation.status().name(),
                        "acceptedBy", String.valueOf(linked.id())),
                List.of("status", "acceptedBy"), "Nhan loi moi");
        audit.record(ENTITY_APP_USER, linked.id().toString(), AuditAction.UPDATE, null,
                Map.of("personId", String.valueOf(linked.personId()),
                        "status", linked.status().name()),
                List.of("personId", "status"), "Ghep tai khoan qua loi moi (khong qua cho duyet)");

        log.info("App_user {} nhan loi moi {} va duoc ghep voi person {}",
                linked.id(), invitation.id(), linked.personId());
        return linked;
    }
}
