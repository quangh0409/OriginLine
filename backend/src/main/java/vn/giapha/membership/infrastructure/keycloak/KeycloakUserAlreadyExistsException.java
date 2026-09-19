package vn.giapha.membership.infrastructure.keycloak;

import vn.giapha.membership.domain.port.IdentityProviderException;

/**
 * Realm trả {@code 409} khi tạo người dùng — đã có tài khoản trùng email hoặc tên đăng nhập.
 *
 * <p>Không phải lỗi, mà là <b>cuộc đua</b>: hai lần bấm nút gần nhau, hoặc một lần thử lại sau khi
 * bước ghép nhân khẩu hỏng. Lớp trên bắt ngoại lệ này rồi <i>đọc lại</i> tài khoản vừa bị chặn —
 * đúng khuôn mẫu {@code AppUserProvisioningService} đã dùng cho
 * {@code ux_app_user_keycloak_sub}.</p>
 */
public class KeycloakUserAlreadyExistsException extends IdentityProviderException {

    public KeycloakUserAlreadyExistsException(String message) {
        super(message);
    }
}
