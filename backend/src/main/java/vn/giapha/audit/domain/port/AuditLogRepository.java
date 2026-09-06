package vn.giapha.audit.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.giapha.audit.domain.AuditActor;
import vn.giapha.audit.domain.AuditEntry;
import vn.giapha.audit.domain.RequestFingerprint;

/**
 * Ghi thêm và tra cứu {@code audit_log}.
 *
 * <p><b>Chỉ ghi thêm.</b> Không có {@code update} và sẽ không bao giờ có: bảng có trigger
 * {@code tg_audit_log_immutable} chặn UPDATE ở phía CSDL, và một cổng cho phép sửa lịch sử là mâu
 * thuẫn với chính lý do lịch sử tồn tại.</p>
 */
public interface AuditLogRepository {

    /** Ghi một dòng. Ngoại lệ được để lan lên: mutation không có vết thì mutation phải hỏng theo. */
    void append(AuditEntry entry, AuditActor actor, RequestFingerprint fingerprint);

    /** Lịch sử của một thực thể, mới nhất trước. */
    List<AuditLogRow> byEntity(String entityType, String entityId, int limit, int offset);

    /** Lịch sử thao tác của một tài khoản, mới nhất trước. */
    List<AuditLogRow> byActor(UUID appUserId, int limit, int offset);

    /**
     * Một dòng đã đọc lên. Cố ý <b>không</b> trả {@code before}/{@code after} ở đây: chúng là JSONB
     * tự do và chỉ nên lộ ra qua endpoint quản trị có kiểm quyền riêng.
     */
    record AuditLogRow(long id, String entityType, String entityId, String action,
                       UUID actorUserId, UUID actorPersonId, Instant at,
                       List<String> changedFields, String requestId, String note) {
    }
}
