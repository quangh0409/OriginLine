package vn.giapha.membership.infrastructure.audit;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.domain.AuditActor;
import vn.giapha.audit.domain.port.AuditActorPort;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.port.AppUserRepository;

/**
 * {@code membership} trả lời câu hỏi của {@code audit}: {@code keycloak_sub} này là tài khoản nào,
 * ứng với nhân khẩu nào.
 *
 * <h2>Vì sao adapter nằm ở đây chứ không ở audit</h2>
 * Bảng {@code app_user} thuộc về context này. Nếu {@code audit} tự đọc bảng ấy thì đã có hai
 * context cùng biết cấu trúc của nó; nếu {@code audit} gọi service của {@code membership} thì có
 * <b>vòng phụ thuộc</b>, vì {@code membership} cũng phải ghi audit khi duyệt đính chính và khi
 * cấp/thu hồi vai trò. Đảo chiều: {@code audit} khai báo cổng, {@code membership} hiện thực. Phụ
 * thuộc chỉ còn một chiều {@code membership → audit}.
 *
 * <p>Đây cũng là lý do {@code AuditTrailService} nhận cổng qua {@code ObjectProvider}: thiếu
 * adapter thì nhật ký vẫn ghi được, chỉ là hai cột actor để trống. Mất một chút thông tin còn hơn
 * mất cả dòng nhật ký.</p>
 */
@Component
public class MembershipAuditActorAdapter implements AuditActorPort {

    private final AppUserRepository appUsers;

    public MembershipAuditActorAdapter(AppUserRepository appUsers) {
        this.appUsers = appUsers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuditActor> resolveByKeycloakSub(String keycloakSub) {
        return appUsers.byKeycloakSub(keycloakSub)
                .map(MembershipAuditActorAdapter::toActor);
    }

    private static AuditActor toActor(AppUser user) {
        return new AuditActor(user.id(), user.personId());
    }
}
