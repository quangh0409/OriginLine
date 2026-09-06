package vn.giapha.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Backend là <b>resource server</b> thuần: chỉ xác thực JWT do Keycloak (realm {@code giapha}) cấp,
 * không tự quản lý mật khẩu, không tự phát hành token. Đăng nhập và social login (Google, Zalo)
 * hoàn toàn thuộc về Keycloak.
 *
 * <h2>W6 đã siết {@code /actuator} (khoản nợ của W0)</h2>
 * W0 mở {@code /actuator/**} cho mọi người để dựng hạ tầng. Nay chỉ còn
 * {@code /actuator/health} (kèm hai probe {@code liveness}/{@code readiness}) và
 * {@code /actuator/info} là công khai; <b>mọi endpoint quản trị còn lại đòi vai
 * {@code ADMIN}</b> — {@code metrics}, {@code prometheus}, và bất cứ thứ gì được phơi thêm về sau.
 *
 * <p>Vì sao không nới cho {@code COUNCIL}: Hội đồng Tộc biểu là thẩm quyền <i>nội dung</i> của dòng
 * họ, {@code /actuator} là bề mặt <i>vận hành</i>. Hai thứ tách bạch, đúng như chức danh dòng tộc
 * tách khỏi vai kỹ thuật.</p>
 *
 * <p><b>Hệ quả cần biết:</b> Prometheus scraper nay phải mang token {@code ADMIN}. Cách chuẩn hơn
 * là tách {@code management.server.port} sang một cổng riêng chỉ mạng nội bộ chạm tới — đó là thay
 * đổi ở {@code application.yml}, nằm ngoài phạm vi tệp này.</p>
 *
 * <p>{@code /v3/api-docs} và Swagger UI vẫn mở: chúng chỉ mô tả <i>hình dạng</i> API chứ không trả
 * dữ liệu nhân khẩu nào, và frontend cùng bộ kiểm thử hợp đồng đang dựa vào. Nếu về sau muốn đóng
 * trên môi trường thật thì đóng bằng cấu hình {@code springdoc.api-docs.enabled}, đừng đóng bằng
 * một luật ở đây rồi quên mất trên môi trường dev.</p>
 *
 * <p><b>Bẫy khởi động:</b> nếu chỉ khai báo {@code issuer-uri}, Spring Boot sẽ gọi endpoint
 * discovery của Keycloak <i>ngay lúc tạo bean</i> — Keycloak chưa chạy là backend chết ngay khi
 * khởi động. Vì vậy {@code application.yml} khai báo cả {@code jwk-set-uri} (khởi tạo lười) lẫn
 * {@code issuer-uri} (để vẫn kiểm tra claim {@code iss}).</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Đường dẫn thăm dò sức khoẻ được để công khai.
     *
     * <p>Liệt kê tường minh chứ không dùng {@code /actuator/health/**}: mẫu bao trùm ấy cũng mở
     * luôn mọi health-indicator con được thêm về sau, và một indicator do thư viện bên thứ ba đăng
     * ký có thể phơi cấu hình kết nối. {@code show-details: when_authorized} trong
     * {@code application.yml} là lớp phòng thủ thứ hai cho cùng vấn đề.</p>
     */
    private static final String[] HEALTH_ENDPOINTS = {
            "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness",
            "/actuator/info"};

    /** Không có tiền tố {@code ROLE_} — {@code hasRole} tự thêm. */
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_COUNCIL = "COUNCIL";

    /** Client id trong realm Keycloak, dùng để đọc vai trò cấp client trong token. */
    private final String clientId;

    public SecurityConfig(@Value("${giapha.security.client-id:giapha-backend}") String clientId) {
        this.clientId = clientId;
    }

    /**
     * <p><b>Vi sao phai co @Qualifier:</b> tu Spring 6, {@code mvcHandlerMappingIntrospector} cua
     * Spring MVC cung hien thuc {@link CorsConfigurationSource}. Trong ngu canh nay co dung 2 bean
     * cung kieu, va ten tham so {@code corsSource} khong trung ten bean nao nen Spring khong the
     * phan giai theo ten. Hau qua la ung dung <b>chet ngay luc khoi dong</b>:
     *
     * <pre>
     * Parameter 1 of method apiSecurityFilterChain required a single bean, but 2 were found:
     *   - corsConfigurationSource (WebConfig)
     *   - mvcHandlerMappingIntrospector (WebMvcAutoConfiguration)
     * </pre>
     *
     * Loi nay khong the phat hien bang compile hay bang unit test — chi lo ra khi that su nang
     * Spring context len, nen no da song sot qua toan bo W0.
     */
    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource)
            throws Exception {
        http
                // API stateless dùng Bearer token -> CSRF token không có tác dụng bảo vệ gì thêm.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Thăm dò sức khoẻ: công khai để load balancer và docker healthcheck gọi được.
                        .requestMatchers(HEALTH_ENDPOINTS).permitAll()
                        // Phần còn lại của actuator là bề mặt vận hành -> chỉ vai KỸ THUẬT ADMIN.
                        .requestMatchers("/actuator/**").hasRole(ROLE_ADMIN)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/graphiql/**").permitAll()
                        // Cổng thông tin công khai: CHỈ dữ liệu người đã khuất mới được lộ ở đây.
                        // Bộ lọc phân tầng hiển thị vẫn phải chạy trên mọi phản hồi.
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                        // Quản trị tài khoản & phân quyền: chặn thô ở đây, nhưng phép kiểm THẬT
                        // (vai x phạm vi ltree) nằm ở BranchScopeGuard của context membership.
                        .requestMatchers("/api/v1/branch-assignments/**").hasAnyRole(ROLE_ADMIN, ROLE_COUNCIL)
                        .requestMatchers("/api/v1/audit-logs/**").hasAnyRole(ROLE_ADMIN, ROLE_COUNCIL)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter(clientId));
        // Chủ thể của token là claim "sub" của Keycloak - chinh la khoa noi sang bang app_user.
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
