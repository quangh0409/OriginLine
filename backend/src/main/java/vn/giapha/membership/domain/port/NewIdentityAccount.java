package vn.giapha.membership.domain.port;

/**
 * Yêu cầu tạo một tài khoản đăng nhập mới.
 *
 * <p><b>Không có trường mật khẩu, và đó là điểm chính.</b> Hệ thống này không đặt mật khẩu hộ ai:
 * tài khoản sinh ra <i>không có credential nào</i>, kèm yêu cầu bắt buộc {@code UPDATE_PASSWORD},
 * rồi người được mời tự đặt qua một liên kết một lần. Thêm một trường mật khẩu vào đây là mở lại
 * đúng lối mà cả thiết kế này tránh.</p>
 *
 * @param username    tên đăng nhập; ở đây luôn bằng email để người được mời chỉ phải nhớ một thứ
 * @param email       địa chỉ thư người được mời tự khai ở màn "Đúng là tôi"
 * @param displayName tên hiển thị, có thể {@code null}
 */
public record NewIdentityAccount(String username, String email, String displayName) {
}
