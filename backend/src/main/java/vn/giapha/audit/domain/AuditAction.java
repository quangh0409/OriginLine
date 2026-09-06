package vn.giapha.audit.domain;

import java.util.Locale;

/**
 * Hành động được ghi vào {@code audit_log}.
 *
 * <p>Danh sách này phải khớp <b>từng ký tự</b> với ràng buộc {@code ck_audit_log_action} của
 * {@code V5__membership_audit.sql}. Đưa nó thành enum thay vì chuỗi tự do để một hành động sai
 * chính tả lộ ra lúc biên dịch, chứ không lộ ra dưới dạng một transaction chết giữa chừng: Postgres
 * huỷ mọi lệnh còn lại sau khi một {@code CHECK} bị vi phạm, nên câu INSERT audit hỏng sẽ kéo theo
 * cả nghiệp vụ mà nó đang ghi vết.</p>
 */
public enum AuditAction {

    CREATE,
    UPDATE,
    SOFT_DELETE,
    RESTORE,
    ANONYMIZE,
    LINK_RELATIONSHIP,
    UNLINK_RELATIONSHIP,
    MOVE_BRANCH,

    /** Duyệt yêu cầu đính chính. */
    APPROVE,

    /** Từ chối yêu cầu đính chính. */
    REJECT,

    /** Cấp vai trò kèm phạm vi chi/ngành. */
    GRANT_ROLE,

    /** Thu hồi vai trò. */
    REVOKE_ROLE,

    LOGIN,

    /** Đọc dữ liệu Tầng 3 — ghi lại <b>việc đọc</b>, tuyệt đối không ghi giá trị đã đọc. */
    READ_SENSITIVE,

    EXPORT;

    /** Phân giải từ chuỗi; ném {@link IllegalArgumentException} với tên hành động rõ ràng. */
    public static AuditAction of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Hanh dong audit khong duoc rong");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Hanh dong audit khong nam trong ck_audit_log_action: " + raw, ex);
        }
    }
}
