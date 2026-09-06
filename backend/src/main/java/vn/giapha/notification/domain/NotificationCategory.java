package vn.giapha.notification.domain;

import java.util.Locale;

/**
 * Loại thông báo trong hộp thư — khớp {@code ck_notification_inbox_category} của
 * {@code V4__events.sql}.
 *
 * <p><b>Cảnh báo lệch hợp đồng:</b> {@code contracts/openapi.yaml} dùng một tập mã khác
 * ({@code GIO_REMINDER}, {@code EVENT}, {@code CHANGE_REQUEST}, {@code SYSTEM}). Migration thuộc sở
 * hữu của W1 nên enum này giữ mã của cơ sở dữ liệu; quy đổi sang mã hợp đồng nằm ở
 * {@code api/rest/NotificationApiMapper}.</p>
 */
public enum NotificationCategory {

    /** Nhắc giỗ/lễ theo mốc 7/3/1 ngày. */
    REMINDER,

    /** Yêu cầu đính chính chờ duyệt (Giai đoạn 2). */
    APPROVAL,

    /** Thông báo hệ thống. */
    SYSTEM,

    /** Bản tin, tin dòng họ. */
    NEWS;

    public static NotificationCategory fromDbValue(String raw) {
        return raw == null ? REMINDER : valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
