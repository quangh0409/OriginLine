package vn.giapha.audit.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.audit.domain.AuditActor;
import vn.giapha.audit.domain.AuditEntry;
import vn.giapha.audit.domain.RequestFingerprint;
import vn.giapha.audit.domain.SensitiveFieldRedactor;
import vn.giapha.audit.domain.port.AuditActorPort;
import vn.giapha.audit.domain.port.AuditLogRepository;
import vn.giapha.shared.security.CurrentUser;
import vn.giapha.shared.security.CurrentUserProvider;

/**
 * Mặt tiền ghi nhật ký của toàn hệ thống. Context khác gọi service này, không chạm
 * {@code audit_log}.
 *
 * <h2>Ghi audit hỏng thì mutation phải hỏng theo</h2>
 * {@link Propagation#MANDATORY} <b>không</b> được dùng ở đây (một số lối gọi hợp lệ nằm ngoài
 * transaction), nhưng ngoại lệ thì được để nguyên cho lan lên. Audit versioning là ràng buộc bắt
 * buộc của BA v2 và Nghị định 13/2023; một thay đổi phả hệ ghi thành công mà không để lại vết là
 * thứ không ai phát hiện ra cho tới đúng lúc cần tra lại.
 *
 * <h2>Ba cột mà W2 để trống, W6 điền</h2>
 * {@code ip_address}, {@code user_agent}, {@code request_id} lấy từ {@link AuditRequestContext} —
 * do {@code AuditInterceptor} nạp ở đầu mỗi request. Ngoài ngữ cảnh HTTP thì cả ba là {@code null},
 * đúng ý: một job nền không có IP.
 *
 * <h2>Tầng 3 không bao giờ lọt vào</h2>
 * {@link SensitiveFieldRedactor} chạy trên cả {@code before} lẫn {@code after} ngay trước khi ghi.
 * Bên gọi vẫn phải tự lọc; lớp này chỉ là lưới cuối.
 */
@Service
public class AuditTrailService {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailService.class);

    private final AuditLogRepository auditLog;
    private final ObjectProvider<AuditActorPort> actorPort;

    public AuditTrailService(AuditLogRepository auditLog, ObjectProvider<AuditActorPort> actorPort) {
        this.auditLog = auditLog;
        this.actorPort = actorPort;
    }

    /** Ghi một dòng, tự phân giải người thực hiện và dấu vết HTTP. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditEntry entry) {
        AuditActor actor = currentActor();
        RequestFingerprint fingerprint = AuditRequestContext.current();
        auditLog.append(scrub(entry), actor, fingerprint);
        log.debug("Audit {} {}#{} boi app_user {} (request {})", entry.action(), entry.entityType(),
                entry.entityId(), actor.appUserId(), fingerprint.requestId());
    }

    /** Dạng rút gọn cho hành động không có ảnh chụp: duyệt, từ chối, cấp vai trò. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String entityType, String entityId, AuditAction action, String note) {
        record(AuditEntry.of(entityType, entityId, action, note));
    }

    /** Dạng đầy đủ, tránh bắt bên gọi phải import {@code AuditEntry} chỉ để dựng một record. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String entityType, String entityId, AuditAction action,
                       Map<String, Object> before, Map<String, Object> after,
                       List<String> changedFields, String note) {
        record(new AuditEntry(entityType, entityId, action, before, after, changedFields, note));
    }

    /**
     * Người thực hiện hành động hiện tại. Không có token, hoặc có token mà chưa ghép được tài
     * khoản, thì trả {@link AuditActor#system()} — dòng nhật ký vẫn được ghi.
     */
    public AuditActor currentActor() {
        Optional<String> sub = CurrentUserProvider.current().map(CurrentUser::keycloakSub);
        if (sub.isEmpty()) {
            return AuditActor.system();
        }
        AuditActorPort port = actorPort.getIfAvailable();
        if (port == null) {
            log.debug("Chua co adapter AuditActorPort, bo trong actor_user_id");
            return AuditActor.system();
        }
        return port.resolveByKeycloakSub(sub.get()).orElseGet(AuditActor::system);
    }

    private static AuditEntry scrub(AuditEntry entry) {
        return new AuditEntry(entry.entityType(), entry.entityId(), entry.action(),
                SensitiveFieldRedactor.scrub(entry.before()),
                SensitiveFieldRedactor.scrub(entry.after()),
                entry.changedFields(), entry.note());
    }
}
