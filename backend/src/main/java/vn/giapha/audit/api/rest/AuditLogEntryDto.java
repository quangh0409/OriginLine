package vn.giapha.audit.api.rest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.giapha.audit.domain.port.AuditLogRepository;

/**
 * Một dòng nhật ký trả ra API.
 *
 * <p><b>Không có {@code before}/{@code after}.</b> Hai cột đó là JSONB tự do; trả nguyên si ra API
 * là mở một lối vòng qua toàn bộ bộ lọc phân tầng hiển thị. Kiểm toán viên cần biết
 * <i>trường nào</i> đã đổi — {@code changedFields} đủ cho việc đó — chứ không cần đọc lại giá trị
 * cũ của số điện thoại một người còn sống.</p>
 */
public record AuditLogEntryDto(long id, String entityType, String entityId, String action,
                               UUID actorUserId, UUID actorPersonId, Instant at,
                               List<String> changedFields, String requestId, String note) {

    public static AuditLogEntryDto from(AuditLogRepository.AuditLogRow row) {
        return new AuditLogEntryDto(row.id(), row.entityType(), row.entityId(), row.action(),
                row.actorUserId(), row.actorPersonId(), row.at(), row.changedFields(),
                row.requestId(), row.note());
    }
}
