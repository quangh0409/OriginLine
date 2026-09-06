package vn.giapha.genealogy.infrastructure.audit;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.genealogy.domain.port.AuditPort;

/**
 * Hiện thực {@link AuditPort} bằng cách chuyển tiếp sang {@link AuditTrailService} — mặt tiền ghi
 * nhật ký của context {@code audit}.
 *
 * <h2>Khoản nợ W2 đã được trả</h2>
 * Bản Giai đoạn 1 tự viết câu {@code INSERT INTO audit_log}, tự phân giải người thực hiện qua
 * {@code AppUserDirectory} (nay đã xoá), tự bới MDC lấy {@code request_id} và tự che dữ liệu Tầng 3.
 * Bốn việc ấy nay có <b>một</b> bản duy nhất ở {@code audit}, nên:
 * <ul>
 *   <li>{@code ip_address} và {@code user_agent} — hai cột W2 đành để trống — được điền, vì
 *       {@code AuditInterceptor} đã nạp chúng vào {@code AuditRequestContext};</li>
 *   <li>bộ khoá Tầng 3 chỉ còn một danh sách ({@code SensitiveFieldRedactor}), không phải hai bản
 *       lệch nhau mà bên nào sót khoá nào thì không ai biết;</li>
 *   <li>người thực hiện đi qua {@code AuditActorPort} do {@code membership} hiện thực — không còn
 *       context nào ngoài {@code membership} biết bảng {@code app_user} có những cột gì.</li>
 * </ul>
 *
 * <h2>Vì sao cổng này vẫn tồn tại thay vì để genealogy gọi thẳng AuditTrailService</h2>
 * {@code AuditPort} do <b>domain</b> của {@code genealogy} khai báo, và domain là POJO thuần —
 * không được import {@code audit}. Adapter mỏng ở đây chính là chỗ dịch giữa hai từ vựng: chuỗi
 * {@code action} tự do của cổng thành {@link AuditAction} có kiểu.
 *
 * <h2>Ghi audit hỏng thì mutation phải hỏng theo</h2>
 * Ngoại lệ được để nguyên cho lan lên, không nuốt. Một hành động sai chính tả lộ ra ngay tại
 * {@link AuditAction#of(String)} dưới dạng {@link IllegalArgumentException} có tên hành động rõ
 * ràng — chứ không lộ ra dưới dạng một {@code CHECK} bị vi phạm giết cả transaction đang chạy
 * (Postgres huỷ mọi lệnh sau lỗi cho tới ROLLBACK).
 *
 * <p>Tên lớp còn chữ "Jdbc" là <b>di sản</b>: nó không còn chạm JDBC nữa. Đổi tên là việc của bên
 * sở hữu package {@code genealogy.infrastructure}, không gộp vào lần đấu nối này.</p>
 */
@Component
public class AuditJdbcAdapter implements AuditPort {

    private final AuditTrailService auditTrail;

    public AuditJdbcAdapter(AuditTrailService auditTrail) {
        this.auditTrail = auditTrail;
    }

    @Override
    public void record(String entityType, String entityId, String action,
                       Map<String, Object> before, Map<String, Object> after,
                       List<String> changedFields, String note) {
        auditTrail.record(entityType, entityId, AuditAction.of(action),
                before, after, changedFields, note);
    }
}
