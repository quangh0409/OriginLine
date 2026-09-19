package vn.giapha.membership.infrastructure.keycloak;

/**
 * Tên các "required action" của Keycloak mà luồng mời dùng tới.
 *
 * <p>Tách hằng số ra một chỗ vì đây là chuỗi <b>khớp theo tên</b> với realm: gõ sai một ký tự thì
 * Keycloak lặng lẽ bỏ qua, tài khoản sinh ra không bị bắt đặt mật khẩu, và không có lỗi nào in ra.</p>
 */
public final class KeycloakRequiredActions {

    /** Bắt người dùng đặt mật khẩu trước khi làm được bất cứ việc gì. */
    public static final String UPDATE_PASSWORD = "UPDATE_PASSWORD";

    private KeycloakRequiredActions() {
    }
}
