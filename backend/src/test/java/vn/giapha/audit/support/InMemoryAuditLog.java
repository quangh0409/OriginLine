package vn.giapha.audit.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import vn.giapha.audit.domain.AuditActor;
import vn.giapha.audit.domain.AuditEntry;
import vn.giapha.audit.domain.RequestFingerprint;
import vn.giapha.audit.domain.port.AuditLogRepository;

/**
 * {@code audit_log} trong bộ nhớ — giữ đúng tính chất <b>chỉ ghi thêm</b> của bảng thật.
 *
 * <p>Cố ý không có phương thức sửa hay xoá dòng đã ghi: bảng thật có trigger
 * {@code tg_audit_log_immutable}, và một bản giả cho phép sửa lịch sử sẽ khiến test xanh trên một
 * hành vi mà production không có.</p>
 */
public final class InMemoryAuditLog implements AuditLogRepository {

    /** Một lần ghi, giữ nguyên cả ba mảnh mà cổng nhận vào. */
    public record Written(AuditEntry entry, AuditActor actor, RequestFingerprint fingerprint,
                          Instant at) {
    }

    private final List<Written> rows = new ArrayList<>();

    @Override
    public void append(AuditEntry entry, AuditActor actor, RequestFingerprint fingerprint) {
        rows.add(new Written(entry, actor, fingerprint, Instant.now()));
    }

    @Override
    public List<AuditLogRow> byEntity(String entityType, String entityId, int limit, int offset) {
        List<AuditLogRow> result = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            Written written = rows.get(i);
            if (written.entry().entityType().equals(entityType)
                    && written.entry().entityId().equals(entityId)) {
                result.add(toRow(i, written));
            }
        }
        return page(result, limit, offset);
    }

    @Override
    public List<AuditLogRow> byActor(UUID appUserId, int limit, int offset) {
        List<AuditLogRow> result = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            Written written = rows.get(i);
            if (appUserId != null && appUserId.equals(written.actor().appUserId())) {
                result.add(toRow(i, written));
            }
        }
        return page(result, limit, offset);
    }

    public List<Written> all() {
        return List.copyOf(rows);
    }

    public Written last() {
        if (rows.isEmpty()) {
            throw new IllegalStateException("Chua co dong audit nao duoc ghi");
        }
        return rows.get(rows.size() - 1);
    }

    public int size() {
        return rows.size();
    }

    public void clear() {
        rows.clear();
    }

    private static AuditLogRow toRow(int index, Written written) {
        return new AuditLogRow(index, written.entry().entityType(), written.entry().entityId(),
                written.entry().action().name(), written.actor().appUserId(),
                written.actor().personId(), written.at(), written.entry().changedFields(),
                written.fingerprint().requestId(), written.entry().note());
    }

    private static List<AuditLogRow> page(List<AuditLogRow> rows, int limit, int offset) {
        if (offset >= rows.size()) {
            return List.of();
        }
        return List.copyOf(rows.subList(offset, Math.min(rows.size(), offset + limit)));
    }
}
