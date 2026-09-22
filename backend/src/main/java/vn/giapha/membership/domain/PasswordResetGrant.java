package vn.giapha.membership.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lượt <b>Trưởng chi / Hội đồng cấp lại liên kết đặt mật khẩu</b> cho một thành viên.
 *
 * <h2>Vì sao bản ghi này tồn tại, trong khi luồng mời không cần bản ghi nào tương tự</h2>
 * Liên kết đặt mật khẩu <i>lần đầu</i> ({@code SetPasswordLink}) không có bảng nào lưu, và đó là
 * một lựa chọn đúng ở đó: tính "một lần" của nó suy ra được từ trạng thái thật của Keycloak — tài
 * khoản đã có credential mật khẩu hay chưa. Đặt xong là điều kiện ấy sai vĩnh viễn.
 *
 * <p>Ở đây nguồn chân lý ấy <b>không còn</b>: người cần cấp lại gần như luôn đã có mật khẩu (họ
 * quên nó, chứ không phải chưa từng đặt), nên "tài khoản chưa có mật khẩu" là sai ngay từ giây đầu
 * tiên. Phải có một cái thay thế, và đó là {@link #usedAt()} — một cột, được đặt bằng một lệnh
 * {@code UPDATE ... WHERE used_at IS NULL} có điều kiện. Đó là lý do <i>duy nhất</i> bảng
 * {@code password_reset_grant} tồn tại.</p>
 *
 * <h2>Không mang token, không mang băm của token</h2>
 * Token là chuỗi ký HMAC mang đúng {@link #id()} và một hạn; chữ ký đã chứng minh nó do máy chủ này
 * phát. Lưu thêm băm vào đây không mua được gì mà lại đẻ ra một bí mật dẫn xuất thứ hai.
 *
 * @param targetAppUserId tài khoản được cấp lại. Cố ý <b>không</b> kèm {@code keycloak_sub}: sub
 *                        đọc từ {@code app_user} lúc đổi mật khẩu nên chỉ có một nguồn chân lý
 * @param branchId        ảnh chụp chi lúc cấp, <b>chỉ để tra soát</b>; phép kiểm phạm vi đọc
 *                        {@code ltree} hiện hành của {@code person}
 * @param usedAt          đã đổi mật khẩu xong; {@code null} là chưa
 * @param revokedAt       bị thu hồi (thường vì chính người ấy được cấp một liên kết mới)
 */
public record PasswordResetGrant(UUID id, UUID targetAppUserId, UUID targetPersonId, UUID branchId,
                                 UUID issuedBy, Instant issuedAt, Instant expiresAt,
                                 Instant usedAt, Instant revokedAt, String revokedReason,
                                 String note) {

    /** Lượt cấp mới, chưa dùng, chưa thu hồi. */
    public static PasswordResetGrant issue(UUID id, UUID targetAppUserId, UUID targetPersonId,
                                           UUID branchId, UUID issuedBy, Instant issuedAt,
                                           Instant expiresAt, String note) {
        return new PasswordResetGrant(id, targetAppUserId, targetPersonId, branchId, issuedBy,
                issuedAt, expiresAt, null, null, null, note);
    }

    /**
     * Còn dùng được tại thời điểm {@code now} hay không.
     *
     * <p><b>Đây là phép kiểm rẻ tiền chạy trước</b>, không phải phép canh "một lần". Giữa lúc gọi
     * hàm này và lúc ghi mật khẩu có một khe hở, và khe hở ấy chỉ đóng được bằng lệnh
     * {@code UPDATE ... WHERE used_at IS NULL} ở tầng lưu trữ. Ai đọc hàm này rồi kết luận "vậy là
     * an toàn rồi" là đang dựng lại đúng lỗi kiểm-rồi-mới-ghi.</p>
     */
    public boolean usableAt(Instant now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }
}
