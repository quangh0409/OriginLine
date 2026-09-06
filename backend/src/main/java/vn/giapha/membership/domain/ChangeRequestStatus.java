package vn.giapha.membership.domain;

import java.util.Locale;

/**
 * Trạng thái một yêu cầu đính chính, khớp {@code ck_change_request_status} của V5.
 *
 * <p>Chỉ {@link #PENDING} là trạng thái mở. Ba trạng thái còn lại là <b>cuối</b>: đã có người
 * quyết thì không quay lại được. Muốn đổi ý thì gửi yêu cầu mới — như vậy lịch sử duyệt vẫn đọc
 * được, còn cho phép mở lại thì một yêu cầu bị từ chối có thể lặng lẽ trở thành đã duyệt mà
 * {@code audit_log} chỉ thấy một dòng.</p>
 */
public enum ChangeRequestStatus {

    PENDING,
    APPROVED,
    REJECTED,

    /** Người gửi tự rút lại. */
    CANCELLED;

    public boolean isOpen() {
        return this == PENDING;
    }

    public boolean isFinal() {
        return this != PENDING;
    }

    public static ChangeRequestStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            return PENDING;
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
