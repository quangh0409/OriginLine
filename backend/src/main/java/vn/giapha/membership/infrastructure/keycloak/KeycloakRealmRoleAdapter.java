package vn.giapha.membership.infrastructure.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.membership.domain.port.IdentityProviderException;
import vn.giapha.membership.domain.port.RealmRolePort;

/**
 * Hiện thực {@link RealmRolePort} bằng Keycloak Admin REST API.
 *
 * <p>Adapter riêng, cùng lý do và cùng cái giá với {@link KeycloakPasswordResetAdapter}: năng lực
 * gán vai chỉ tiêm được vào lớp đã cân nhắc, đổi lại một {@link KeycloakAdminApi} (và một bộ nhớ
 * đệm token) thứ ba. Với tần suất "vài lần trong đời một dòng họ" thì cái giá ấy không đo được.</p>
 *
 * <p>Không cần thêm quyền nào cho tài khoản dịch vụ: {@code manage-users} đủ để đọc và gắn vai
 * realm không phải vai quản trị realm — xem {@link KeycloakAdminApi#addRealmRole}.</p>
 *
 * <p>Chưa cấu hình bí mật thì adapter <b>tự tắt</b>, ứng dụng vẫn khởi động.</p>
 */
@Component
public class KeycloakRealmRoleAdapter implements RealmRolePort {

    private static final Logger log = LoggerFactory.getLogger(KeycloakRealmRoleAdapter.class);

    private final KeycloakAdminProperties properties;
    private final KeycloakAdminApi api;

    // @Autowired la BAT BUOC: hai constructor, Spring khong tu chon — cung cai bay da ghi o
    // KeycloakIdentityProviderAdapter.
    @Autowired
    public KeycloakRealmRoleAdapter(KeycloakAdminProperties properties, ObjectMapper objectMapper) {
        this(properties,
                properties.isConfigured() ? new KeycloakAdminApi(properties, objectMapper) : null);
    }

    /** Lối dựng cho test: nối thẳng vào một {@link KeycloakAdminApi} giả. */
    KeycloakRealmRoleAdapter(KeycloakAdminProperties properties, KeycloakAdminApi api) {
        this.properties = properties;
        this.api = api;
    }

    @Override
    public boolean isConfigured() {
        return api != null && properties.isConfigured();
    }

    @Override
    public void grantRealmRole(String subject, RoleCode role) {
        if (!isConfigured()) {
            throw new IdentityProviderException(
                    "He thong chua duoc cau hinh de gan vai tro cho tai khoan");
        }
        api.addRealmRole(subject, role.name());
        log.info("Gan vai realm {} cho tai khoan Keycloak {}", role, subject);
    }
}
