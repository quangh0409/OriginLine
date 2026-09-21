package vn.giapha.media.domain;

import java.util.Locale;

/** Ba kết cục của một đơn báo gỡ. {@code OPEN} là trạng thái duy nhất còn nằm trong hàng đợi. */
public enum ReportStatus {

    OPEN,

    /** Đã gỡ: liên kết bị cắt, byte bị xoá khỏi kho ngay, không ân hạn. */
    ACTIONED,

    /** Người duyệt xem rồi và giữ nguyên tệp — vẫn phải ký tên và nêu lý do. */
    DISMISSED;

    public static ReportStatus of(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
