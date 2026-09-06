package vn.giapha.audit.domain;

import java.util.UUID;

/**
 * Người thực hiện hành động, đã phân giải xong chuỗi {@code keycloak_sub → app_user → person}.
 *
 * <p>Cả hai trường đều có thể {@code null}: một job nền không có tài khoản nào, và một tài khoản
 * vừa đăng ký thì chưa được ghép vào cây phả hệ. Cột {@code actor_user_id}/{@code actor_person_id}
 * của {@code audit_log} cũng nullable đúng vì lẽ đó.</p>
 *
 * @param appUserId {@code app_user.id}
 * @param personId  nhân khẩu tương ứng, {@code null} khi tài khoản chưa nối vào cây
 */
public record AuditActor(UUID appUserId, UUID personId) {

    private static final AuditActor SYSTEM = new AuditActor(null, null);

    /** Không có người dùng nào — job nền, scheduler, migration. */
    public static AuditActor system() {
        return SYSTEM;
    }

    public boolean isSystem() {
        return appUserId == null && personId == null;
    }
}
