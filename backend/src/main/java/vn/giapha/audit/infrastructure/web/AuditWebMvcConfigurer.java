package vn.giapha.audit.infrastructure.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Gắn {@link AuditInterceptor} vào mọi request.
 *
 * <h2>Vì sao đăng ký ở đây chứ không ở {@code config.WebConfig}</h2>
 * {@code config} được khai báo là <i>shared module</i> trong {@code GiaPhaApplication}: mọi context
 * phụ thuộc vào nó, còn nó <b>không được</b> phụ thuộc ngược vào bất kỳ context nào. Nếu
 * {@code WebConfig} import {@code AuditInterceptor} thì {@code config → audit}, mà {@code audit}
 * lại ngầm phụ thuộc {@code config} vì nó là shared module — {@code ModularityTests} sẽ đỏ vì vòng
 * lặp. Spring MVC gom <i>mọi</i> bean {@link WebMvcConfigurer} nên context tự đăng ký phần của mình
 * là cách sạch nhất.
 */
@Configuration
public class AuditWebMvcConfigurer implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuditInterceptor())
                .addPathPatterns("/**")
                .order(0);
    }
}
