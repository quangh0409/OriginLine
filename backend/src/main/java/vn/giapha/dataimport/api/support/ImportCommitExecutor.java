package vn.giapha.dataimport.api.support;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Luồng nền của bước ghi vào phả.
 *
 * <h2>Vì sao một pool riêng, nhỏ, có hàng đợi có trần</h2>
 * Ghi một lô là một giao dịch dài (400 người, vài giây tới vài chục giây) và nó <b>giữ khoá tư vấn
 * trên chi</b>. Hai lô của cùng một chi không chạy song song được, và bốn chi thì cũng chỉ có bốn.
 * Một pool mặc định không giới hạn ở đây nghĩa là mỗi cú bấm sốt ruột sinh thêm một luồng nằm chờ
 * khoá — tốn luồng để đợi một việc vốn đã tuần tự.
 *
 * <p>Hàng đợi có trần và chính sách <b>{@code CallerRuns}</b>: khi quá tải, lượt ghi chạy trên
 * luồng web của người vừa bấm. Nghe ngược đời, nhưng đó là hành vi đúng — nó làm người bấm thứ
 * mười phải chờ, tức tự nó phanh lại, thay vì âm thầm <i>vứt</i> một lượt duyệt mà người dùng
 * tưởng đã nhận. Mất một lượt duyệt là mất cả buổi đối soát.
 *
 * <h2>Vì sao không dùng {@code applicationTaskExecutor}</h2>
 * Bean ấy không tồn tại trong cấu hình hiện tại của ứng dụng, và kể cả khi có thì nó là pool dùng
 * chung với {@code @Async} của các context khác — một lô 400 người sẽ chiếm chỗ của việc đẩy thông
 * báo giỗ.
 *
 * <h2>Ngữ cảnh bảo mật phải đi theo</h2>
 * {@link DelegatingSecurityContextRunnable} chuyển {@code SecurityContext} của request sang luồng
 * nền. Thiếu nó thì mọi dòng nhật ký kiểm toán do bước ghi sinh ra mang tên
 * <i>anonymous</i> — và một nhật ký kiểm toán không biết ai đã ghi 400 người vào phả thì không còn
 * là nhật ký kiểm toán.
 */
@Component
public class ImportCommitExecutor implements Executor {

    private static final Logger log = LoggerFactory.getLogger(ImportCommitExecutor.class);

    private final ThreadPoolTaskExecutor pool;

    public ImportCommitExecutor() {
        this.pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(16);
        pool.setThreadNamePrefix("import-commit-");
        pool.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // Cho lo dang ghi chay xong khi tat ung dung: cat ngang giua chung thi transaction cuon
        // lai — khong hong du lieu, nhung nguoi dung mat cong bam lai ma khong hieu vi sao.
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(60);
        pool.initialize();
    }

    @Override
    public void execute(Runnable task) {
        pool.execute(new DelegatingSecurityContextRunnable(task,
                SecurityContextHolder.getContext()));
    }

    @PreDestroy
    void dong() {
        log.info("Dong pool ghi nhap lieu, cho toi da 60 giay cho lo dang chay");
        pool.shutdown();
    }
}
