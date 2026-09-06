package vn.giapha.audit.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Một dòng nhật ký chờ ghi: ai đổi gì, trên thực thể nào, trước ra sao và sau ra sao.
 *
 * <p><b>Không bao giờ</b> đặt giá trị Tầng 3 (số điện thoại, email, địa chỉ đầy đủ, ngày sinh đầy
 * đủ, ảnh) vào {@code before}/{@code after}. {@link SensitiveFieldRedactor} là lưới an toàn cuối
 * cùng chứ không phải giấy phép: {@code audit_log} là bảng chỉ ghi thêm, có trigger chặn UPDATE —
 * dữ liệu nhạy cảm lọt vào đó thì không sửa lại được, chỉ còn cách xoá dòng.</p>
 *
 * @param entityType    tên thực thể, ví dụ {@code ChangeRequest} — tối đa 48 ký tự
 * @param entityId      định danh dạng chuỗi — tối đa 64 ký tự
 * @param action        hành động, khớp {@code ck_audit_log_action}
 * @param before        ảnh chụp trước; {@code null} khi tạo mới
 * @param after         ảnh chụp sau; {@code null} khi xoá
 * @param changedFields tên các trường đã đổi — chỉ tên, không kèm giá trị
 * @param note          ghi chú nghiệp vụ, ví dụ lý do từ chối một yêu cầu đính chính
 */
public record AuditEntry(String entityType, String entityId, AuditAction action,
                         Map<String, Object> before, Map<String, Object> after,
                         List<String> changedFields, String note) {

    public static final int MAX_ENTITY_TYPE = 48;
    public static final int MAX_ENTITY_ID = 64;

    public AuditEntry {
        entityType = require(entityType, "entityType", MAX_ENTITY_TYPE);
        entityId = require(entityId, "entityId", MAX_ENTITY_ID);
        Objects.requireNonNull(action, "AuditEntry.action khong duoc null");
        before = copySnapshot(before);
        after = copySnapshot(after);
        changedFields = changedFields == null || changedFields.isEmpty()
                ? List.of()
                : changedFields.stream().filter(Objects::nonNull).toList();
    }

    /** Ghi vết một hành động không có ảnh chụp trước/sau — duyệt, từ chối, cấp vai trò, đăng nhập. */
    public static AuditEntry of(String entityType, String entityId, AuditAction action, String note) {
        return new AuditEntry(entityType, entityId, action, null, null, List.of(), note);
    }

    /**
     * Sao chép bất biến một ảnh chụp, <b>chấp nhận giá trị {@code null}</b>.
     *
     * <p>Cố ý không dùng {@link Map#copyOf}: nó ném {@link NullPointerException} khi map chứa bất
     * kỳ giá trị {@code null} nào. Mà một ô trống là chuyện thường trong ảnh chụp nghiệp vụ —
     * {@code ChangeRequest.auditSnapshot()} luôn có {@code reviewerId = null} với một yêu cầu vừa
     * gửi, và một hồ sơ vừa ẩn danh hoá thì toàn bộ trường Tầng 3 đều là {@code null}. Với
     * {@code Map.copyOf} thì chính hai luồng ấy vỡ ngay lúc ghi vết, và vì audit chạy trong cùng
     * transaction với nghiệp vụ nên nó kéo cả thao tác gửi/duyệt yêu cầu đính chính rollback theo.
     * Vết audit không bao giờ được phép phá nghiệp vụ mà nó đang ghi vết.</p>
     *
     * <p>Ngữ nghĩa cũng khác nhau và cả hai đều cần: {@code null} <i>toàn bộ ảnh chụp</i> nghĩa là
     * "không có trạng thái này" (tạo mới / xoá), còn {@code null} <i>một giá trị</i> nghĩa là
     * "trường này rỗng" — thông tin thật, phải ghi lại được.</p>
     */
    private static Map<String, Object> copySnapshot(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return null;
        }
        return java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(snapshot));
    }

    private static String require(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Truong audit '" + field + "' khong duoc rong");
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
