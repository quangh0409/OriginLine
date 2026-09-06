package vn.giapha.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Cấu hình web dùng chung: CORS cho frontend Next.js và i18n song ngữ VI–EN.
 *
 * <p><b>CORS:</b> danh sách origin lấy từ cấu hình ({@code giapha.cors.allowed-origins}), không
 * hard-code và không dùng {@code *} — vì {@code allowCredentials=true} không đi cùng wildcard, và
 * vì cổng thông tin dòng họ có dữ liệu cá nhân thì không nên mở cho mọi origin.</p>
 *
 * <p><b>i18n:</b> ngôn ngữ xác định theo header {@code Accept-Language}, mặc định tiếng Việt. Thông
 * điệp nằm ở {@code classpath:i18n/messages*.properties} và được dùng cho cả thông báo lỗi
 * bean-validation, nhờ {@link #getValidator()}. Đây là lý do FE nên hiển thị theo {@code code}
 * trong Problem Details khi cần thông điệp phong phú hơn.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public WebConfig(@Value("${giapha.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language",
                "X-Requested-With", "If-Match"));
        config.setExposedHeaders(List.of("Location", "ETag", "Content-Language"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/graphql", config);
        source.registerCorsConfiguration("/api/v1/graphql", config);
        return source;
    }

    /**
     * Mặc định tiếng Việt; chỉ chấp nhận vi và en. Cố tình dùng {@code AcceptHeaderLocaleResolver}
     * chứ không dùng locale theo session: API stateless, client PWA tự biết ngôn ngữ của mình.
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.of("vi"));
        resolver.setSupportedLocales(List.of(Locale.of("vi"), Locale.ENGLISH));
        return resolver;
    }

    /**
     * Nguồn thông điệp song ngữ dùng chung cho Problem Details và bean-validation.
     *
     * <h2>Vì sao {@code useCodeAsDefaultMessage} phải là {@code false}</h2>
     * Với {@code spring.mvc.problemdetails.enabled=true}, Spring tra thông điệp cho <i>mọi</i> ngoại
     * lệ MVC sẵn có theo mã {@code problemDetail.title.<TênNgoạiLệ>}. Bật cờ này thì
     * {@code AbstractMessageSource#getDefaultMessage} trả về <b>chính cái mã</b> khi không tìm thấy
     * khoá, và nó đi thẳng ra thân phản hồi:
     *
     * <pre>
     * {"title":"problemDetail.title.org.springframework.web.servlet.NoHandlerFoundException",
     *  "detail":"problemDetail.org.springframework.web.servlet.NoHandlerFoundException"}
     * </pre>
     *
     * Hai cái sai cùng lúc: client nhận một chuỗi vô nghĩa thay vì thông báo lỗi, và <b>tên class
     * nội bộ của Spring bị lộ ra API công khai</b>. Tắt cờ đi thì {@code getMessage(code, args,
     * null, locale)} trả {@code null}, và {@code ErrorResponse#updateAndGetBody} giữ nguyên thông
     * điệp mặc định của Spring — mất tiếng Việt nhưng đúng nghĩa và không rò rỉ gì.
     *
     * <h2>Tác dụng phụ lên bean-validation là tác dụng tốt</h2>
     * {@link #getValidator()} bơm chính nguồn này vào Hibernate Validator qua
     * {@code MessageSourceResourceBundleLocator}. Với cờ bật, mọi khoá — kể cả
     * {@code jakarta.validation.constraints.NotNull.message} — đều "tồn tại", nên HV tưởng bundle
     * của ta có khoá và dùng luôn cái mã làm thông điệp, che mất bundle mặc định của chính nó. Tắt
     * cờ khôi phục lại thông điệp chuẩn cho mọi ràng buộc mà ta chưa dịch.
     *
     * <p><b>Việc còn lại (ngoài phạm vi tệp này):</b> bổ sung khoá {@code problemDetail.title.*} và
     * {@code problemDetail.*} cho các ngoại lệ hay gặp (400/401/403/404/405/409/412/415/500) vào
     * {@code i18n/messages_vi.properties} và {@code messages_en.properties}. Khi có khoá, chúng sẽ
     * được dùng ngay — cấu hình ở đây không cần đổi thêm lần nữa.</p>
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);
        return messageSource;
    }

    /** Cho phép thông điệp bean-validation lấy từ MessageSource để dịch được VI/EN. */
    @Bean
    public LocalValidatorFactoryBean getValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource());
        return validator;
    }
}
