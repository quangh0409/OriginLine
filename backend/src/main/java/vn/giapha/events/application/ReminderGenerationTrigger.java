package vn.giapha.events.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.giapha.shared.security.CurrentUserProvider;

/**
 * Chạy tay job sinh lịch nhắc giỗ — use case của {@code POST /api/v1/admin/reminders/generate}.
 *
 * <h2>Vì sao endpoint này tồn tại</h2>
 * {@link GenerateRemindersService#generateNightly()} chỉ chạy 01:30 giờ Việt Nam. Trên môi trường
 * demo và staging — vốn hay được dựng lên rồi tắt trong ngày — bảng {@code reminder_job} vì thế
 * <b>rỗng</b>, và cả đường ống nhắc giỗ không thể trình diễn được đầu-cuối. Bấm nút vẫn tốt hơn
 * là sửa cron thành mỗi phút rồi quên trả lại.
 *
 * <h2>Ngày tham chiếu: vì sao đổi luôn cả "bây giờ"</h2>
 * Truyền {@code referenceDate} không chỉ đổi cái mốc "hôm nay" mà còn đổi mốc <b>thời điểm hiện
 * tại</b> thành 00:00 của chính ngày đó. Nếu giữ {@code Instant.now()} thật thì
 * {@link vn.giapha.events.domain.ReminderPlan#planFor} sẽ loại sạch mọi mốc nhắc nằm trước hiện
 * tại, và một ngày tham chiếu trong quá khứ sẽ sinh ra đúng 0 job — tức là tham số coi như vô dụng
 * đúng ở trường hợp người ta cần nó nhất: dựng lại cả ba mốc 7 / 3 / 1 để xem chúng chạy thật.
 *
 * <p><b>Hệ quả phải biết trước:</b> ngày tham chiếu trong quá khứ sinh ra job có {@code fire_at} đã
 * qua, và lượt quét gửi kế tiếp (5 phút một lần) sẽ <b>gửi thật</b>. Đó chính là điều làm cho bản
 * demo chạy được đầu-cuối, nhưng cũng là lý do endpoint này chỉ dành cho System Admin.</p>
 *
 * <h2>Chặn ngày tham chiếu quá xa</h2>
 * Giới hạn ±{@value #MAX_DRIFT_DAYS} ngày. Gõ nhầm năm ("2026" thành "2062") mà không có chốt này
 * thì job sẽ quét toàn bộ sự kiện của dòng họ để sinh lịch nhắc cho một mốc chẳng ai dùng, và
 * {@code reminder_job} bẩn dữ liệu ma. Lệch một năm là đủ cho mọi nhu cầu thử nghiệm thật.
 */
@Service
public class ReminderGenerationTrigger {

    private static final Logger log = LoggerFactory.getLogger(ReminderGenerationTrigger.class);

    /** Biên độ cho phép của {@code referenceDate} quanh ngày hôm nay, tính bằng ngày. */
    public static final int MAX_DRIFT_DAYS = 366;

    private final GenerateRemindersService generator;
    private final ReminderProperties properties;

    /**
     * Chốt trong tiến trình, {@code tryLock} <b>không chờ</b>: quản trị viên bấm lại lần hai nhận
     * ngay 409 thay vì treo một luồng servlet cho tới khi lượt trước xong.
     */
    private final ReentrantLock runLock = new ReentrantLock();

    public ReminderGenerationTrigger(GenerateRemindersService generator, ReminderProperties properties) {
        this.generator = generator;
        this.properties = properties;
    }

    /**
     * @param referenceDate ngày coi là "hôm nay"; {@code null} = hôm nay thật theo GMT+7
     * @throws IllegalArgumentException          ngày tham chiếu lệch quá {@value #MAX_DRIFT_DAYS} ngày
     * @throws ReminderGenerationBusyException   đã có lượt chạy khác trên instance này
     */
    public ReminderGenerationResult runNow(LocalDate referenceDate) {
        ZoneId zone = properties.zone();
        LocalDate today = LocalDate.now(zone);
        LocalDate reference = referenceDate == null ? today : referenceDate;
        requireWithinDrift(reference, today);

        // Ngày tham chiếu tự nhập -> "bây giờ" là 00:00 của chính ngày ấy (xem javadoc lớp).
        Instant now = referenceDate == null ? Instant.now() : reference.atStartOfDay(zone).toInstant();

        if (!runLock.tryLock()) {
            throw new ReminderGenerationBusyException(
                    "Dang co mot luot sinh lich nhac chay do dang, thu lai sau khi luot do ket thuc.");
        }
        try {
            log.info("Sinh lich nhac CHAY TAY: nguoi goi={} ngay tham chieu={}{}",
                    CurrentUserProvider.currentSubjectOrAnonymous(), reference,
                    reference.equals(today) ? "" : " (khac hom nay " + today + ")");
            ReminderGenerationResult result = generator.generateFor(reference, now);
            log.info("Sinh lich nhac chay tay xong: quet {} su kien, tao moi {} job, het {} ms",
                    result.scannedEvents(), result.createdJobs(), result.duration().toMillis());
            return result;
        } finally {
            runLock.unlock();
        }
    }

    private static void requireWithinDrift(LocalDate reference, LocalDate today) {
        long drift = Math.abs(ChronoUnit.DAYS.between(today, reference));
        if (drift > MAX_DRIFT_DAYS) {
            throw new IllegalArgumentException("referenceDate=" + reference + " lech " + drift
                    + " ngay so voi hom nay (" + today + "); chi cho phep toi da " + MAX_DRIFT_DAYS
                    + " ngay ve moi phia.");
        }
    }
}
