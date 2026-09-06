package vn.giapha.config;

import java.util.TimeZone;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Bật job định kỳ. Người dùng chính của cấu hình này là {@code GenerateRemindersService} — mỗi đêm
 * quét sự kiện, quy đổi ngày âm sang dương của năm nay và sinh {@code ReminderJob} cho mốc
 * 7 / 3 / 1 ngày.
 *
 * <p><b>Múi giờ là chi tiết nghiệp vụ, không phải chi tiết kỹ thuật.</b> Âm lịch Việt Nam được tính
 * ở GMT+7; scheduler chạy theo UTC sẽ khiến job "hằng đêm" rơi vào 7 giờ sáng giờ Việt Nam và ngày
 * âm bị lệch một ngày ở vùng biên. Vì vậy scheduler cố định {@code Asia/Ho_Chi_Minh}.</p>
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulerConfig {

    /** Múi giờ nghiệp vụ của toàn hệ thống: âm lịch Việt Nam tính tại GMT+7. */
    public static final String BUSINESS_TIMEZONE = "Asia/Ho_Chi_Minh";

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("giapha-sched-");
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setErrorHandler(throwable ->
                org.slf4j.LoggerFactory.getLogger(SchedulerConfig.class)
                        .error("Job dinh ky that bai", throwable));
        return scheduler;
    }

    /** Múi giờ để đặt trong {@code @Scheduled(cron = "...", zone = SchedulerConfig.BUSINESS_TIMEZONE)}. */
    @Bean
    public TimeZone businessTimeZone() {
        return TimeZone.getTimeZone(BUSINESS_TIMEZONE);
    }
}
