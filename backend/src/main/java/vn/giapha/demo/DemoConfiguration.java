package vn.giapha.demo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Bật module sinh dữ liệu giả. Toàn bộ bean của module chỉ tồn tại dưới profile {@code demo}.
 *
 * <p>Không đặt ở {@code vn.giapha.config}: đó là cấu hình cross-cutting của hệ thống thật, còn đây
 * là công cụ dựng môi trường. Để lẫn vào nhau là bước đầu tiên trên con đường dữ liệu giả xuất hiện
 * ở nơi không nên.</p>
 */
@Configuration(proxyBeanMethods = false)
@Profile("demo")
@EnableConfigurationProperties(DemoDataProperties.class)
class DemoConfiguration {
}
