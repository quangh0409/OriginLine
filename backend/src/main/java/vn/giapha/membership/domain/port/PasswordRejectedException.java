package vn.giapha.membership.domain.port;

/**
 * Nhà cung cấp danh tính từ chối mật khẩu vì <b>chính sách mật khẩu</b> của nó (quá ngắn, quá phổ
 * biến, trùng mật khẩu cũ...).
 *
 * <p>Đây là lỗi <i>của người dùng</i>, không phải lỗi hạ tầng, nên tầng {@code api} ánh xạ nó thành
 * {@code 422 VALIDATION_FAILED} chứ không phải {@code 503}. Luật độ mạnh mật khẩu <b>không</b> được
 * chép lại ở backend: chép là có hai nguồn chân lý, và nguồn ở đây sẽ lệch khỏi realm ngay lần đầu
 * ai đó siết chính sách trong giao diện quản trị Keycloak.</p>
 *
 * <p>Nằm ở gói {@code domain.port} chứ không ở gói adapter, dù chỉ adapter mới ném nó: tầng
 * {@code api} phải bắt được nó, và một lớp {@code api} biết tới {@code infrastructure} là đi ngược
 * chiều phụ thuộc mà cả dự án giữ.</p>
 */
public class PasswordRejectedException extends IdentityProviderException {

    public PasswordRejectedException(String message) {
        super(message);
    }
}
