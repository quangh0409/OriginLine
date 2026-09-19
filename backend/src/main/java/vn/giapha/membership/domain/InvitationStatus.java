package vn.giapha.membership.domain;

import java.util.Locale;

/**
 * Trạng thái lưu trữ của một lời mời, khớp {@code ck_invitation_status} của V15.
 *
 * <p><b>Cố ý chỉ có ba giá trị.</b> "Hết hạn" <i>không</i> nằm ở đây: nó là một phép so sánh giữa
 * {@code expires_at} và đồng hồ, không phải một dòng dữ liệu. Nếu EXPIRED là trạng thái lưu trữ thì
 * phải có một job đi lật cờ, và cho tới khi job ấy chạy thì mọi lời mời quá hạn vẫn dùng được — một
 * lỗ hổng có lịch chạy. Xem {@link InvitationUsability}.</p>
 */
public enum InvitationStatus {

    /** Đã phát, chưa ai nhận, chưa bị thu hồi. Còn dùng được hay không thì hỏi đồng hồ. */
    PENDING,

    /** Đã có người nhận. Mã chết từ đây — một lần là một lần. */
    ACCEPTED,

    /**
     * Bị thu hồi. Hai lối đi tới đây: Trưởng chi tự thu, hoặc người nhận bấm "Không phải tôi".
     *
     * <p>Lối thứ hai quan trọng hơn lối thứ nhất: nó là cách duy nhất một người gửi nhầm số biết
     * được mình gửi nhầm, và là lớp chống đỡ mà design 06 §5.2 đặt cạnh "dùng một lần" và
     * "hết hạn 7 ngày".</p>
     */
    REVOKED;

    public static InvitationStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            return PENDING;
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
