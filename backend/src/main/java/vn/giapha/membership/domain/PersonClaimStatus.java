package vn.giapha.membership.domain;

import java.util.Locale;

/**
 * Máy trạng thái của một đơn tự nhận — cố ý giống hệt {@link ChangeRequestStatus}.
 *
 * <p>Giống là đúng: luồng duyệt đính chính đã chạy thật, và người dùng lẫn người đọc mã đều đã
 * quen bốn trạng thái ấy. Đẻ ra một tập trạng thái thứ hai cho cùng một hình dạng nghiệp vụ chỉ
 * tạo thêm chỗ để lệch.</p>
 */
public enum PersonClaimStatus {

    PENDING,
    APPROVED,

    /**
     * Bị từ chối. <b>Gửi lại được, nhưng có giới hạn số lần</b> (design 07 §1.4) — không giới hạn
     * thì màn này thành cách dò đúng người bằng cách thử lần lượt.
     */
    REJECTED,

    /** Người gửi tự rút lại. */
    CANCELLED;

    public boolean isOpen() {
        return this == PENDING;
    }

    public boolean isFinal() {
        return this != PENDING;
    }

    public static PersonClaimStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            return PENDING;
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
