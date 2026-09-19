package vn.giapha.membership.domain.port;

import java.time.Instant;

/**
 * Liên kết <b>một lần</b> để người được mời tự đặt mật khẩu.
 *
 * <p>"Một lần" ở đây không được canh bằng một cờ trong một bảng nào của hệ thống này, mà bằng
 * <b>chính trạng thái thật</b> ở Keycloak: liên kết chỉ dùng được khi tài khoản đích còn
 * <i>chưa có credential mật khẩu nào</i>. Đặt xong là điều kiện ấy sai vĩnh viễn, nên lần bấm thứ
 * hai tự thất bại mà không cần ai đi lật cờ — cùng lập luận với việc {@code InvitationStatus}
 * không có giá trị {@code EXPIRED}.</p>
 *
 * @param url       địa chỉ trao cho người dùng; mang một token ký HMAC, hạn ngắn
 * @param expiresAt hạn của token trong {@link #url}
 */
public record SetPasswordLink(String url, Instant expiresAt) {
}
