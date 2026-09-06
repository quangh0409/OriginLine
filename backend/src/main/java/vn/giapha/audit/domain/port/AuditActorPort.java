package vn.giapha.audit.domain.port;

import java.util.Optional;
import vn.giapha.audit.domain.AuditActor;

/**
 * Phân giải {@code keycloak_sub} thành {@code app_user.id} + {@code person.id} để điền hai cột
 * {@code actor_user_id} / {@code actor_person_id} của {@code audit_log}.
 *
 * <h2>Vì sao cổng này nằm ở audit chứ không ở membership</h2>
 * Tài khoản là dữ liệu của context {@code membership}, nhưng nếu {@code audit} gọi thẳng service
 * của {@code membership} thì có <b>vòng phụ thuộc</b>: {@code membership} cũng phải ghi audit khi
 * duyệt yêu cầu đính chính và khi cấp/thu hồi vai trò. Đảo chiều bằng một cổng: {@code audit} khai
 * báo, {@code membership} hiện thực. Phụ thuộc chỉ còn một chiều {@code membership → audit}, và
 * {@code audit} không cần biết bảng {@code app_user} tồn tại.
 *
 * <p>Không có adapter nào (ví dụ trong test lát mỏng) thì {@code AuditTrailService} vẫn ghi được,
 * chỉ là hai cột actor để trống — mất một chút thông tin còn hơn mất cả dòng nhật ký.</p>
 */
public interface AuditActorPort {

    /** Rỗng khi chưa có tài khoản nào ứng với {@code sub} đó, hoặc khi {@code sub} rỗng. */
    Optional<AuditActor> resolveByKeycloakSub(String keycloakSub);
}
