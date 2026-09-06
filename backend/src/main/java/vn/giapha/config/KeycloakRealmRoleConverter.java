package vn.giapha.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Chuyển vai trò trong JWT của Keycloak thành {@code GrantedAuthority} của Spring Security.
 *
 * <p>Keycloak nhét vai trò vào {@code realm_access.roles} (vai trò cấp realm) và
 * {@code resource_access.<client>.roles} (vai trò cấp client) — Spring Security mặc định chỉ đọc
 * {@code scope}/{@code scp}, nên không có converter này thì <b>mọi</b> kiểm tra role sẽ trượt dù
 * token hoàn toàn hợp lệ.</p>
 *
 * <p>Ánh xạ sang tiền tố {@code ROLE_} để {@code hasRole(...)} và {@code @PreAuthorize} dùng được.
 * Lưu ý: đây mới là nửa "role thô" của phân quyền. Nửa còn lại — phạm vi chi/ngành theo
 * {@code ltree} — là kiểm tra riêng ở W6, không thể suy ra từ token.</p>
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String RESOURCE_ACCESS = "resource_access";
    private static final String ROLES = "roles";

    private final String clientId;

    public KeycloakRealmRoleConverter(String clientId) {
        this.clientId = clientId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS);
        if (realmAccess != null && realmAccess.get(ROLES) instanceof Collection<?> realmRoles) {
            realmRoles.forEach(role -> authorities.add(toAuthority(role)));
        }

        Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS);
        if (clientId != null && resourceAccess != null
                && resourceAccess.get(clientId) instanceof Map<?, ?> client
                && client.get(ROLES) instanceof Collection<?> clientRoles) {
            clientRoles.forEach(role -> authorities.add(toAuthority(role)));
        }

        return authorities;
    }

    private GrantedAuthority toAuthority(Object role) {
        return new SimpleGrantedAuthority("ROLE_" + String.valueOf(role).toUpperCase().replace('-', '_'));
    }
}
