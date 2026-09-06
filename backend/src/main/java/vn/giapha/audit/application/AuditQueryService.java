package vn.giapha.audit.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.domain.port.AuditLogRepository;

/**
 * Tra cứu nhật ký. Chỉ đọc, phân trang, và <b>không</b> trả {@code before}/{@code after}.
 *
 * <p>Lý do bỏ hai cột ảnh chụp: chúng là JSONB tự do, có thể chứa bất cứ thứ gì mà bên gọi đã đưa
 * vào. Trả nguyên si ra API là mở một lối vòng qua toàn bộ bộ lọc phân tầng hiển thị. Khi nào cần
 * xem diff thật thì làm một endpoint riêng, có kiểm quyền riêng, và ghi lại chính việc xem đó bằng
 * hành động {@code READ_SENSITIVE}.</p>
 */
@Service
public class AuditQueryService {

    /** Trần cứng để một tham số {@code size} lớn không kéo cả bảng nhật ký lên bộ nhớ. */
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository auditLog;

    public AuditQueryService(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public List<AuditLogRepository.AuditLogRow> byEntity(String entityType, String entityId,
                                                         int page, int size) {
        int limit = clamp(size);
        return auditLog.byEntity(entityType, entityId, limit, Math.max(page, 0) * limit);
    }

    @Transactional(readOnly = true)
    public List<AuditLogRepository.AuditLogRow> byActor(UUID appUserId, int page, int size) {
        int limit = clamp(size);
        return auditLog.byActor(appUserId, limit, Math.max(page, 0) * limit);
    }

    private static int clamp(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
