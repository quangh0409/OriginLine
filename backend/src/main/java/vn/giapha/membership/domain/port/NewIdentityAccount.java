package vn.giapha.membership.domain.port;

/**
 * Yêu cầu lập một tài khoản đăng nhập mới.
 *
 * @param username    <b>định danh bắt buộc</b> — email đã chuẩn hoá, hoặc số điện thoại đã chuẩn
 *                    hoá. Đây là thứ người dùng sẽ gõ ở ô đăng nhập
 * @param email       thuộc tính {@code email} của realm. <b>{@code null} là hợp lệ</b>, và đó là ca
 *                    của người lập tài khoản bằng số điện thoại: realm áp bộ kiểm email lên thuộc
 *                    tính này và sẽ từ chối một số máy, còn nếu lọt thì mọi lối gửi thư về sau gửi
 *                    vào hư không
 * @param displayName tên hiển thị tự khai; không phải tên trong phả
 */
public record NewIdentityAccount(String username, String email, String displayName) {
}
