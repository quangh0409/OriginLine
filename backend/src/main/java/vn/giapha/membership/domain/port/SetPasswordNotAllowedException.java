package vn.giapha.membership.domain.port;

/**
 * Liên kết đặt mật khẩu không dùng được: sai chữ ký, quá hạn, hoặc — ca quan trọng nhất —
 * <b>tài khoản đã có mật khẩu rồi</b>.
 *
 * <p>Ca cuối chính là cơ chế "một lần" của liên kết. Nó cũng là hàng rào chặn leo thang: nếu liên
 * kết vẫn dùng được trên một tài khoản <i>đã</i> có mật khẩu thì bất kỳ ai cầm mã mời và đoán đúng
 * email của một thành viên cũ sẽ đổi được mật khẩu của người ta.</p>
 */
public class SetPasswordNotAllowedException extends RuntimeException {

    public SetPasswordNotAllowedException(String message) {
        super(message);
    }
}
