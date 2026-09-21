package vn.giapha.media.infrastructure.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.giapha.config.SchedulerConfig;
import vn.giapha.media.application.MediaGcService;
import vn.giapha.media.application.view.GcResult;

/**
 * Chạy {@link MediaGcService#sweep()} hằng đêm.
 *
 * <h2>03:15 GMT+7 — giờ này được chọn, không phải gõ bừa</h2>
 * 01:30 đã là giờ sinh nhắc giỗ ({@code GenerateRemindersService}). Hai công việc đêm đặt cùng giờ
 * sẽ tranh nhau cùng một pool kết nối rồi cùng chậm, và người vận hành sáng hôm sau thấy hai thứ
 * hỏng mà không biết cái nào kéo cái nào. 03:15 để lại gần hai tiếng — thừa cho một lượt sinh nhắc
 * của cả dòng họ — và vẫn nằm trong khung giờ không ai dùng hệ thống.
 *
 * <h2>Lớp này KHÔNG chứa logic, và đó là chủ ý</h2>
 * Toàn bộ phần làm việc nằm ở {@code MediaGcService}, nên nó kiểm thử được bằng một lời gọi thẳng
 * và lái được qua {@code POST /api/v1/admin/media/gc}. Cùng khuôn mà {@code events} đã lập khi
 * tách thân job khỏi {@code @Scheduled}: một đường ống chỉ chạy được lúc 3 giờ sáng là một đường
 * ống không ai kiểm được.
 *
 * <p>Ngoại lệ bị nuốt ở đây (ghi {@code ERROR} rồi đi tiếp). Bộ lập lịch của Spring <b>dừng hẳn
 * lịch của một phương thức</b> nếu nó ném ra ngoài — nên một đêm kho MinIO hỏng sẽ tắt luôn việc
 * dọn của mọi đêm sau, im lặng.</p>
 */
@Component
public class MediaGcScheduler {

    private static final Logger log = LoggerFactory.getLogger(MediaGcScheduler.class);

    private final MediaGcService gc;

    public MediaGcScheduler(MediaGcService gc) {
        this.gc = gc;
    }

    @Scheduled(cron = "${giapha.media.gc-cron:0 15 3 * * *}", zone = SchedulerConfig.BUSINESS_TIMEZONE)
    public void nightlySweep() {
        try {
            GcResult result = gc.sweep();
            if (result.total() > 0 || result.storageFailures() > 0) {
                log.info("Don tep dem: {} phieu qua han, {} tep mo coi, {} loi kho",
                        result.expiredTickets(), result.orphanAssets(), result.storageFailures());
            }
        } catch (RuntimeException ex) {
            log.error("Luot don tep dem that bai. Lich van chay dem sau — ngoai le bi nuot o day"
                    + " vi Spring se DUNG HAN lich cua mot phuong thuc neu no nem ra ngoai.", ex);
        }
    }
}
