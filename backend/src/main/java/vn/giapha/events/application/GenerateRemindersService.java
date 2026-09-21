package vn.giapha.events.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.config.SchedulerConfig;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.ReminderJob;
import vn.giapha.events.domain.ReminderPlan;
import vn.giapha.events.domain.port.EventRepository;
import vn.giapha.events.domain.port.ReminderJobRepository;

/**
 * Job chạy <b>hằng đêm</b>: quét toàn bộ sự kiện lặp lại, quy đổi ngày âm sang ngày dương của năm
 * nay (và năm tới), rồi sinh {@link ReminderJob} cho các mốc 7 / 3 / 1 ngày (FR-2.2).
 *
 * <h2>Vì sao sinh trước hai năm âm lịch</h2>
 * Giỗ vào tháng Chạp rơi vào tháng 1–2 <i>dương lịch của năm sau</i>. Nếu chỉ sinh cho năm âm hiện
 * tại thì mọi ngày giỗ cuối năm chỉ có lịch nhắc sau khi đã sang năm âm mới — tức là sau khi giỗ đã
 * qua. Sinh dư một năm rẻ hơn nhiều so với một cái giỗ bị bỏ quên.
 *
 * <h2>Vì sao chạy lại được nhiều lần trong đêm mà không nhân đôi</h2>
 * Chống trùng nằm ở chỉ mục {@code ux_reminder_job_occurrence (event_id, occurrence_year,
 * offset_days)} và {@code INSERT ... ON CONFLICT DO NOTHING}, <b>không</b> ở tầng Java. Hai instance
 * cùng chạy sẽ cùng đọc thấy "chưa có" nếu kiểm tra bằng {@code SELECT} — cơ sở dữ liệu mới là
 * trọng tài duy nhất.
 *
 * <h2>Múi giờ</h2>
 * Cron gắn {@link SchedulerConfig#BUSINESS_TIMEZONE}. Chạy theo UTC thì "hằng đêm" rơi vào 7 giờ
 * sáng giờ Việt Nam và ngày âm bị lệch một ngày ở vùng biên.
 *
 * <p>Vòng quét cố ý <b>không</b> nằm trong một transaction: một sự kiện có ngày âm hỏng không được
 * phép cuốn theo lịch nhắc của cả dòng họ. Ranh giới transaction là <i>một lô</i>, đặt ở
 * {@link ReminderBatchGenerator}.</p>
 */
@Service
public class GenerateRemindersService {

    private static final Logger log = LoggerFactory.getLogger(GenerateRemindersService.class);

    private final EventRepository events;
    private final ReminderJobRepository jobs;
    private final ReminderBatchGenerator batchGenerator;
    private final OccurrenceResolver occurrences;
    private final ReminderProperties properties;

    public GenerateRemindersService(EventRepository events, ReminderJobRepository jobs,
                                    ReminderBatchGenerator batchGenerator,
                                    OccurrenceResolver occurrences, ReminderProperties properties) {
        this.events = events;
        this.jobs = jobs;
        this.batchGenerator = batchGenerator;
        this.occurrences = occurrences;
        this.properties = properties;
    }

    /**
     * Dựng lại lịch nhắc cho <b>đúng một</b> sự kiện vừa được tạo, vừa đổi ngày/phạm vi, hoặc vừa
     * bị xoá mềm.
     *
     * <h2>Vì sao không chờ tới 01:30</h2>
     * Một buổi họp họ tạo lúc 9 giờ sáng cho Chủ nhật tuần này có mốc D-3 rơi vào <i>ngày mai</i>.
     * Chờ job đêm nghĩa là mốc ấy vẫn kịp — nhưng một sự kiện tạo cho <i>ngày kia</i> thì mốc D-1
     * đã trôi mất. Dựng ngay tại lượt ghi, trong <b>cùng transaction</b>, nên sự kiện và lịch nhắc
     * của nó hoặc cùng có hoặc cùng không.
     *
     * <h2>Xoá trước, dựng sau — và xoá chứ không huỷ</h2>
     * Xem {@link ReminderJobRepository#deleteUnsentByEvent}: một dòng {@code CANCELLED} vẫn chiếm
     * khoá của chỉ mục chống trùng, nên "huỷ rồi sinh lại" sẽ không sinh được gì. Job đã
     * {@code QUEUED}/{@code SENT} không bị đụng tới — lời nhắc đã phát đi là chuyện đã rồi.
     *
     * @return số job sinh mới
     */
    @Transactional
    public int regenerateFor(Event event) {
        int removed = jobs.deleteUnsentByEvent(event.id());
        if (!event.generatesReminders()) {
            log.info("Su kien {} khong con sinh lich nhac (da xoa mem) - da don {} job chua ban",
                    event.id(), removed);
            return 0;
        }
        LocalDate today = LocalDate.now(properties.zone());
        int created = batchGenerator.generate(List.of(event), properties.toPlan(),
                targetLunarYears(today), targetSolarYears(today), today, Instant.now());
        log.info("Dung lai lich nhac cho su kien {}: don {} job chua ban, tao moi {}",
                event.id(), removed, created);
        return created;
    }

    /**
     * 01:30 giờ Việt Nam mỗi đêm — sau nửa đêm nên "hôm nay" đã sang ngày mới, và trước 07:00 nên
     * lịch nhắc của chính ngày hôm đó vẫn kịp được sinh trước giờ bắn.
     */
    @Scheduled(cron = "${giapha.reminders.generate-cron:0 30 1 * * *}", zone = SchedulerConfig.BUSINESS_TIMEZONE)
    public void generateNightly() {
        if (!properties.isSchedulerEnabled()) {
            log.debug("Bo qua sinh lich nhac: giapha.reminders.scheduler-enabled=false");
            return;
        }
        ZoneId zone = properties.zone();
        generateFor(LocalDate.now(zone), Instant.now());
    }

    /**
     * Thân thật của job, tách khỏi {@code @Scheduled} để kiểm thử được với ngày giả lập — và để
     * quản trị viên gọi tay được qua {@link ReminderGenerationTrigger} thay vì chờ tới 01:30.
     *
     * @param today ngày được coi là "hôm nay"; mọi lần xảy ra trước ngày này bị bỏ qua
     * @param now   thời điểm hiện tại; mốc nhắc đã trôi qua trước thời điểm này không được sinh
     * @return kết quả lượt chạy, xem {@link ReminderGenerationResult}
     */
    public ReminderGenerationResult generateFor(LocalDate today, Instant now) {
        Instant startedAt = Instant.now();
        ReminderPlan plan = properties.toPlan();
        List<Integer> lunarYears = targetLunarYears(today);
        List<Integer> solarYears = targetSolarYears(today);
        log.info("Bat dau sinh lich nhac: hom nay={} nam am={} nam duong={} moc={}",
                today, lunarYears, solarYears, plan.offsetDays());

        int created = 0;
        int scanned = 0;
        int batchSize = properties.getBatchSize();
        for (int page = 0; ; page++) {
            List<Event> batch = events.findActivePage(page, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            scanned += batch.size();
            created += batchGenerator.generate(batch, plan, lunarYears, solarYears, today, now);
            if (batch.size() < batchSize) {
                break;
            }
        }
        log.info("Sinh lich nhac xong: quet {} su kien, tao moi {} job", scanned, created);
        return new ReminderGenerationResult(today, lunarYears, solarYears, scanned, created,
                startedAt, Duration.between(startedAt, Instant.now()));
    }

    /** Năm âm lịch cần sinh: năm âm của hôm nay và {@code horizonYears - 1} năm kế tiếp. */
    private List<Integer> targetLunarYears(LocalDate today) {
        Integer currentLunarYear = occurrences.lunarYearOf(today);
        if (currentLunarYear == null) {
            // Không quy đổi được hôm nay gần như không thể xảy ra (dải hỗ trợ của LunarConverter phủ
            // rộng), nhưng nếu xảy ra thì lấy năm dương làm xấp xỉ còn hơn là không nhắc ai cả.
            log.warn("Khong quy doi duoc {} sang am lich - tam dung nam duong {} lam nam am",
                    today, today.getYear());
            currentLunarYear = today.getYear();
        }
        return yearsFrom(currentLunarYear);
    }

    private List<Integer> targetSolarYears(LocalDate today) {
        return yearsFrom(today.getYear());
    }

    private List<Integer> yearsFrom(int first) {
        List<Integer> years = new ArrayList<>(properties.getHorizonYears());
        for (int i = 0; i < properties.getHorizonYears(); i++) {
            years.add(first + i);
        }
        return years;
    }
}
