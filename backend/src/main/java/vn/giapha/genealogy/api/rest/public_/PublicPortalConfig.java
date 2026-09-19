package vn.giapha.genealogy.api.rest.public_;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Nối {@link PublicRateLimitFilter} vào chuỗi servlet, <b>chỉ</b> cho tiền tố công khai.
 *
 * <h2>Vì sao không đánh {@code @Component} lên chính bộ lọc</h2>
 * Một {@code Filter} là bean sẽ được Spring Boot đăng ký cho <b>mọi</b> đường dẫn, kể cả
 * {@code /actuator/health} mà load balancer gọi liên tục — chính nó sẽ tự đâm vào hạn mức. Khai
 * báo {@code urlPatterns} tường minh giữ phạm vi đúng bằng phạm vi mà luật {@code permitAll} trong
 * {@code SecurityConfig} mở ra.
 *
 * <p>Thứ tự đặt trước chuỗi lọc của Spring Security ({@code -100}) để hạn mức được tính <b>trước</b>
 * mọi công việc nặng — kể cả việc giải mã JWT của một token vô nghĩa gửi kèm.</p>
 */
@Configuration
public class PublicPortalConfig {

    @Bean
    public FilterRegistrationBean<PublicRateLimitFilter> publicRateLimitFilterRegistration(
            PublicPortalProperties properties) {
        FilterRegistrationBean<PublicRateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new PublicRateLimitFilter(properties));
        registration.addUrlPatterns(PublicRateLimitFilter.PUBLIC_PREFIX + "/*");
        registration.setName("publicPortalRateLimitFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50);
        return registration;
    }
}
