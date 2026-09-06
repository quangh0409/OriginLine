package vn.giapha.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import vn.giapha.shared.security.CurrentUserProvider;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;

/**
 * Cấu hình JPA cho phần <b>quan hệ</b> của kho dữ liệu.
 *
 * <p>Chú ý phân vai: đồ thị phả hệ (Apache AGE) <b>không</b> đi qua JPA — kiểu {@code agtype} của
 * AGE Hibernate không map được. Truy vấn Cypher dùng {@code JdbcTemplate} trong adapter ở
 * {@code genealogy.infrastructure}, nhưng chạy trên <i>cùng một</i> {@code DataSource} nên ghi
 * bảng {@code relationship} và ghi cạnh đồ thị nằm gọn trong một transaction.</p>
 *
 * <p>Repository Spring Data chỉ được nằm trong package {@code infrastructure} của từng context —
 * quét từ gốc {@code vn.giapha} là đủ, ranh giới do Spring Modulith canh.</p>
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "vn.giapha")
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    /**
     * Ai đang sửa dữ liệu — đổ vào {@code created_by} / {@code updated_by} và là đầu vào của
     * nhật ký thay đổi (context {@code audit}). Lấy {@code sub} của Keycloak vì username có thể
     * đổi, còn {@code sub} thì không.
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of(CurrentUserProvider.currentSubjectOrAnonymous());
    }

    /**
     * Mốc thời gian audit dùng {@code OffsetDateTime} theo UTC. Hiển thị theo GMT+7 là việc của
     * tầng trình bày — lưu lệch múi giờ là nguồn bug âm thầm khi so ngày giỗ.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of((TemporalAccessor) OffsetDateTime.now(clock));
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
