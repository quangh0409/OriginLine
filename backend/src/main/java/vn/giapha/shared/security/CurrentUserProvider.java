package vn.giapha.shared.security;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Đọc người dùng hiện tại từ {@code SecurityContext}.
 *
 * <p>Đặt ở shared kernel để tầng audit, bộ lọc phân tầng hiển thị và mọi context đều dùng chung
 * một cách lấy danh tính, thay vì mỗi nơi tự bới {@code SecurityContextHolder} một kiểu.</p>
 *
 * <p>Trả về {@link Optional#empty()} với khách vãng lai — và theo Nghị định 13/2023, khách
 * <b>không được</b> thấy bất kỳ người còn sống nào.</p>
 */
public final class CurrentUserProvider {

    public static final String ROLE_PREFIX = "ROLE_";

    private CurrentUserProvider() {
    }

    public static Optional<CurrentUser> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            return Optional.of(new CurrentUser(
                    jwt.getSubject(),
                    jwt.getClaimAsString("preferred_username"),
                    jwt.getClaimAsString("email"),
                    normalizedRoles(jwtToken)));
        }
        if ("anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.empty();
        }
        return Optional.of(new CurrentUser(
                authentication.getName(), authentication.getName(), null, normalizedRoles(authentication)));
    }

    /** Định danh dùng cho cột {@code created_by} / {@code updated_by}; khách là {@code anonymous}. */
    public static String currentSubjectOrAnonymous() {
        return current().map(CurrentUser::keycloakSub).orElse("anonymous");
    }

    private static Set<String> normalizedRoles(Authentication authentication) {
        Set<String> roles = new LinkedHashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            roles.add(value.startsWith(ROLE_PREFIX) ? value.substring(ROLE_PREFIX.length()) : value);
        }
        return Set.copyOf(roles);
    }
}
