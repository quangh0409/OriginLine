package vn.giapha.audit.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.audit.application.AuditQueryService;

/**
 * Tra cứu nhật ký thay đổi — {@code /api/v1/audit-logs}.
 *
 * <h2>Chỉ hai vai có phạm vi toàn dòng họ</h2>
 * Nhật ký là dữ liệu <b>siêu nhạy cảm</b>: nó cho biết ai đã đụng vào hồ sơ của ai, kể cả những hồ
 * sơ mà người xem không được phép nhìn thấy. Cho Trưởng chi xem theo phạm vi nghe có vẻ hợp lý,
 * nhưng phạm vi ở đây phải suy từ {@code entity_id} sang chi của nhân khẩu đó — một phép nối mà
 * {@code audit_log} cố ý không có (cột {@code entity_id} là {@code VARCHAR} dùng chung cho mọi
 * bảng). Chưa làm được đúng thì đóng lại, chứ không mở hé.
 *
 * <p>Endpoint này chỉ đọc và <b>không</b> tự ghi thêm dòng {@code READ_SENSITIVE}: nó không trả về
 * giá trị nhạy cảm nào (xem {@link AuditLogEntryDto}).</p>
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit", description = "Nhật ký thay đổi (chỉ Quản trị hệ thống / Hội đồng Tộc biểu)")
public class AuditLogController {

    private final AuditQueryService auditQuery;

    public AuditLogController(AuditQueryService auditQuery) {
        this.auditQuery = auditQuery;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','COUNCIL')")
    @Operation(summary = "Lịch sử thay đổi của một thực thể")
    public List<AuditLogEntryDto> byEntity(@RequestParam String entityType,
                                           @RequestParam String entityId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return auditQuery.byEntity(entityType, entityId, page, size).stream()
                .map(AuditLogEntryDto::from)
                .toList();
    }

    @GetMapping("/by-actor/{appUserId}")
    @PreAuthorize("hasAnyRole('ADMIN','COUNCIL')")
    @Operation(summary = "Lịch sử thao tác của một tài khoản")
    public List<AuditLogEntryDto> byActor(@PathVariable UUID appUserId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return auditQuery.byActor(appUserId, page, size).stream()
                .map(AuditLogEntryDto::from)
                .toList();
    }
}
