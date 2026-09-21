package vn.giapha.events.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventOccurrence;
import vn.giapha.events.domain.EventSubject;
import vn.giapha.events.domain.ReminderJob;
import vn.giapha.events.domain.ReminderPlan;
import vn.giapha.events.domain.port.EventSubjectPort;
import vn.giapha.events.domain.port.ReminderJobRepository;

/**
 * Sinh lịch nhắc cho <b>một lô</b> sự kiện — đây là ranh giới {@code @Transactional} của việc sinh
 * lịch nhắc.
 *
 * <p>Tách khỏi {@link GenerateRemindersService} vì hai lý do, và lý do thứ hai mới là lý do thật:</p>
 * <ol>
 *   <li>trách nhiệm khác nhau — bên kia điều phối vòng quét, bên này ghi dữ liệu;</li>
 *   <li>{@code @Transactional} <b>không có tác dụng khi gọi phương thức của chính bean mình</b>
 *       (proxy bị bỏ qua). Đặt cùng lớp thì annotation trông thì có mà chạy thì không, và lỗi kiểu
 *       này chỉ lộ ra khi đã có dữ liệu ghi dở dang trong sản xuất.</li>
 * </ol>
 */
@Service
public class ReminderBatchGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReminderBatchGenerator.class);

    private final EventSubjectPort subjects;
    private final ReminderJobRepository jobs;
    private final OccurrenceResolver occurrences;

    public ReminderBatchGenerator(EventSubjectPort subjects, ReminderJobRepository jobs,
                                  OccurrenceResolver occurrences) {
        this.subjects = subjects;
        this.jobs = jobs;
        this.occurrences = occurrences;
    }

    /** @return số job thực sự sinh mới trong lô */
    @Transactional
    public int generate(List<Event> batch, ReminderPlan plan, List<Integer> lunarYears,
                        List<Integer> solarYears, LocalDate today, Instant now) {
        Map<UUID, EventSubject> subjectsById = subjects.findPersons(personIdsOf(batch));
        int created = 0;
        for (Event event : batch) {
            try {
                // Bẫy: Map.of() và Map.copyOf() là map bất biến của JDK, và get(null) trên chúng ném
                // NullPointerException chứ không trả null. Sự kiện cấp dòng họ không gắn nhân khẩu
                // nào — đúng trường hợp phổ biến nhất — nên đây là NPE chắc chắn xảy ra.
                created += generateForEvent(event, subjectOf(subjectsById, event), plan,
                        lunarYears, solarYears, today, now);
            } catch (RuntimeException ex) {
                // Một sự kiện hỏng không được làm câm lịch nhắc của cả họ. Ghi lại đủ để tìm ra rồi
                // đi tiếp — job này chỉ ghi thêm, không sửa gì, nên bỏ dở một dòng là an toàn.
                log.error("Bo qua su kien {} khi sinh lich nhac: {}", event.id(), ex.getMessage(), ex);
            }
        }
        return created;
    }

    private int generateForEvent(Event event, EventSubject subject, ReminderPlan plan,
                                 List<Integer> lunarYears, List<Integer> solarYears,
                                 LocalDate today, Instant now) {
        if (!event.generatesReminders()) {
            return 0;
        }
        List<EventOccurrence> candidates = occurrences.resolveAll(event,
                EffectiveLunarDate.of(event, subject), lunarYears, solarYears);

        int created = 0;
        Set<Integer> handledOccurrenceYears = new HashSet<>();
        for (EventOccurrence occurrence : candidates) {
            if (occurrence.dueSolarDate().isBefore(today)) {
                continue;
            }
            // Hai năm âm liên tiếp không bao giờ rơi vào cùng một năm dương: khoảng cách tối thiểu
            // giữa hai lần giỗ là 354 ngày, còn biên độ ngày dương của một ngày âm cố định chỉ
            // khoảng 30 ngày. Chốt chặn này chỉ để dữ liệu hỏng không đâm vào chỉ mục duy nhất rồi
            // sinh log nhiễu.
            if (!handledOccurrenceYears.add(occurrence.occurrenceYear())) {
                log.warn("Su kien {}: hai lan xay ra cung roi vao nam duong {} - bo qua lan sau",
                        event.id(), occurrence.occurrenceYear());
                continue;
            }
            for (ReminderPlan.PlannedReminder planned : plan.planFor(occurrence, now)) {
                ReminderJob job = ReminderJob.pending(UUID.randomUUID(), event.id(), occurrence,
                        planned.offsetDays(), planned.fireAt());
                if (jobs.insertIfAbsent(job)) {
                    created++;
                    log.debug("Tao lich nhac {} cho su kien {} (D-{}, gio ngay {})",
                            job.id(), event.id(), planned.offsetDays(), occurrence.dueSolarDate());
                }
            }
        }
        return created;
    }

    /** Xem ghi chú về {@code Map.of().get(null)} ở {@link #generate}. */
    private static EventSubject subjectOf(Map<UUID, EventSubject> subjectsById, Event event) {
        return event.personId() == null ? null : subjectsById.get(event.personId());
    }

    private static Collection<UUID> personIdsOf(List<Event> batch) {
        Set<UUID> ids = new HashSet<>();
        for (Event event : batch) {
            if (event.personId() != null) {
                ids.add(event.personId());
            }
        }
        return ids;
    }
}
