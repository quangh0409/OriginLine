package vn.giapha.membership.support;

import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Dựng {@code SecurityContext} giả cho unit test.
 *
 * <p>{@code CurrentUserProvider} đọc thẳng {@code SecurityContextHolder}, nên muốn kiểm luật phân
 * quyền mà không khởi động cả Spring Security thì phải nạp một {@link JwtAuthenticationToken}
 * bằng tay. Vai trò được gắn kèm tiền tố {@code ROLE_} <b>đúng như Keycloak</b>, để test cũng đi
 * qua bước chuẩn hoá tiền tố của {@code CurrentUserProvider} chứ không bỏ qua nó.</p>
 */
public final class TestSecurity {

    private TestSecurity() {
    }

    /**
     * Đăng nhập với một {@code sub} và tập vai trò trong token.
     *
     * <p>Nhắc lại một lần nữa vì đây là nguồn của mọi hiểu lầm trong W6: vai trong token chỉ là
     * <b>một nửa</b> phân quyền. Nửa còn lại — phạm vi chi/ngành — nằm ở {@code branch_assignment}
     * và phải được nạp riêng qua {@link InMemoryBranchAssignmentRepository}.</p>
     */
    public static void loginAs(String keycloakSub, String... roles) {
        loginAsWithProfile(keycloakSub, keycloakSub, keycloakSub + "@example.test", roles);
    }

    /** Đăng nhập kèm hồ sơ hiển thị lấy từ token (tên, email). */
    public static void loginAsWithProfile(String keycloakSub, String username, String email,
                                          String... roles) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(keycloakSub)
                .claim("preferred_username", username)
                .claim("email", email)
                .build();
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }

    /**
     * Khách vãng lai đã đi qua bộ lọc ẩn danh của Spring Security.
     *
     * <p>Khác với {@link #logout()}: ở đây {@code Authentication} tồn tại và
     * {@code isAuthenticated()} trả {@code true}, chỉ principal là {@code anonymousUser}. Đây là
     * hình dạng thật của một request không token, và là ca dễ lọt nhất nếu chỉ kiểm
     * {@code authentication == null}.</p>
     */
    public static void loginAsAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    }

    /** Không có {@code Authentication} nào — job nền, scheduler, consumer RabbitMQ. */
    public static void logout() {
        SecurityContextHolder.clearContext();
    }
}
