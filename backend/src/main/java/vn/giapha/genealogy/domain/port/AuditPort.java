package vn.giapha.genealogy.domain.port;

import java.util.List;
import java.util.Map;

/**
 * Ghi nhật ký thay đổi vào {@code audit_log} — <b>bắt buộc với mọi mutation phả hệ</b>
 * (ai / khi nào / trước / sau).
 *
 * <p><b>Không bao giờ</b> đưa giá trị Tầng 3 (số điện thoại, email, địa chỉ đầy đủ, ảnh) vào
 * {@code before}/{@code after}: nhật ký thay đổi mà chép nguyên dữ liệu nhạy cảm thì mọi công sức
 * phân tầng ở tầng trên trở thành vô nghĩa. {@code Person.auditSnapshot()} đã loại sẵn.</p>
 */
public interface AuditPort {

    /**
     * @param entityType tên thực thể, ví dụ {@code Person}
     * @param entityId   định danh dạng chuỗi
     * @param action     một trong các giá trị của {@code ck_audit_log_action} ở V5
     * @param before     ảnh chụp trước; {@code null} khi là thao tác tạo mới
     * @param after      ảnh chụp sau; {@code null} khi là thao tác xoá
     * @param changedFields danh sách tên trường đã đổi
     * @param note       ghi chú, ví dụ lý do xoá hoặc lý do ghi đè cảnh báo kỵ húy
     */
    void record(String entityType, String entityId, String action,
                Map<String, Object> before, Map<String, Object> after,
                List<String> changedFields, String note);

    /** Hành động chuẩn, khớp ràng buộc CHECK của bảng {@code audit_log}. */
    final class Action {

        public static final String CREATE = "CREATE";
        public static final String UPDATE = "UPDATE";
        public static final String SOFT_DELETE = "SOFT_DELETE";
        public static final String RESTORE = "RESTORE";
        public static final String ANONYMIZE = "ANONYMIZE";
        public static final String LINK_RELATIONSHIP = "LINK_RELATIONSHIP";
        public static final String UNLINK_RELATIONSHIP = "UNLINK_RELATIONSHIP";
        public static final String MOVE_BRANCH = "MOVE_BRANCH";

        private Action() {
        }
    }
}
