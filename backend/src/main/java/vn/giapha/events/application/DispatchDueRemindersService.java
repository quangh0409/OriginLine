package vn.giapha.events.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.giapha.config.SchedulerConfig;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventSubject;
import vn.giapha.events.domain.ReminderJob;
import vn.giapha.events.domain.ReminderStatus;
import vn.giapha.events.domain.port.EventRepository;
import vn.giapha.events.domain.port.EventSubjectPort;
import vn.giapha.events.domain.port.ReminderJobRepository;
import vn.giapha.notification.application.NotificationDispatchService;
import vn.giapha.notification.application.ReminderDispatch;

/**
 * Đẩy các {@link ReminderJob} đã tới giờ lên hàng đợi thông báo.
 *
 * <h2>Vì sao tách khỏi {@link GenerateRemindersService}</h2>
 * Sinh lịch và bắn lịch có nhịp hoàn toàn khác nhau: sinh một lần mỗi đêm, bắn thì phải quét vài
 * phút một lần để mốc 07:00 không lệch cả tiếng. Gộp chung thì hoặc job đêm phải chạy suốt ngày,
 * hoặc thông báo tới trễ.
 *
 * <h2>Job quá hạn thì huỷ, không bắn</h2>
 * Hệ thống ngừng chạy ba ngày rồi bật lại: mọi job trong ba ngày ấy đều "đã tới giờ". Bắn hết thì
 * người trong họ nhận một loạt tin nhắc những cái giỗ đã qua — vô nghĩa và gây hoang mang. Job có
 * ngày giỗ đã qua được chuyển {@link ReminderStatus#CANCELLED} (không phải {@code FAILED}: đây
 * không phải lỗi gửi). Job có ngày giỗ còn ở phía trước vẫn bắn, dù trễ giờ — muộn còn hơn không.
 *
 * <h2>Không chặn luồng web</h2>
 * Toàn bộ việc gửi là bất đồng bộ: ở đây chỉ đẩy tin vào RabbitMQ rồi đánh dấu {@code QUEUED}.
 * Gateway Zalo/Web Push chết cũng không ảnh hưởng tới bất kỳ request HTTP nào.
 */
@Service
public class DispatchDueRemindersService {

    private static final Logger log = LoggerFactory.getLogger(DispatchDueRemindersService.class);

    private final ReminderJobRepository jobs;
    private final EventRepository events;
    private final EventSubjectPort subjects;
    private final ReminderMessageFactory messages;
    private final NotificationDispatchService notifications;
    private final ReminderProperties properties;

    public DispatchDueRemindersService(ReminderJobRepository jobs, EventRepository events,
                                       EventSubjectPort subjects, ReminderMessageFactory messages,
                                       NotificationDispatchService notifications,
                                       ReminderProperties properties) {
        this.jobs = jobs;
        this.events = events;
        this.subjects = subjects;
        this.messages = messages;
        this.notifications = notifications;
        this.properties = properties;
    }

    /** Năm phút một lần — đủ mịn để mốc 07:00 không lệch đáng kể, đủ thưa để không quấy cơ sở dữ liệu. */
    @Scheduled(cron = "${giapha.reminders.dispatch-cron:0 */5 * * * *}", zone = SchedulerConfig.BUSINESS_TIMEZONE)
    public void dispatchDue() {
        if (!properties.isSchedulerEnabled()) {
            return;
        }
        dispatchDueAt(Instant.now(), LocalDate.now(properties.zone()));
    }

    /**
     * Thân thật của job, tách khỏi {@code @Scheduled} để kiểm thử được với thời điểm giả lập.
     *
     * @return số job đã đẩy đi
     */
    public int dispatchDueAt(Instant now, LocalDate today) {
        // claimDue vừa lấy vừa đánh dấu QUEUED trong một câu lệnh: instance khác không lấy lại được
        // chính những job này. Xem javadoc của ReminderJobRepository.
        List<ReminderJob> due = jobs.claimDue(now, properties.getDispatchBatchSize());
        if (due.isEmpty()) {
            return 0;
        }
        int dispatched = 0;
        int cancelled = 0;
        for (ReminderJob job : due) {
            try {
                if (job.isStale(today)) {
                    jobs.markStatus(job.id(), ReminderStatus.CANCELLED,
                            "Ngay gio " + job.dueSolarDate() + " da qua truoc khi kip gui");
                    cancelled++;
                    continue;
                }
                if (dispatchOne(job)) {
                    dispatched++;
                }
            } catch (RuntimeException ex) {
                // Một job hỏng không được chặn hàng đợi. Để nguyên PENDING và ghi lỗi: lượt quét
                // sau sẽ thử lại, và nếu ngày giỗ trôi qua thì nó tự chuyển CANCELLED.
                log.error("Khong day duoc lich nhac {}: {}", job.id(), ex.getMessage(), ex);
                jobs.markStatus(job.id(), ReminderStatus.PENDING, ex.getMessage());
            }
        }
        log.info("Quet lich nhac den han: {} job, day di {}, huy {} (qua han)",
                due.size(), dispatched, cancelled);
        return dispatched;
    }

    private boolean dispatchOne(ReminderJob job) {
        Optional<Event> found = events.findById(job.eventId());
        if (found.isEmpty()) {
            jobs.markStatus(job.id(), ReminderStatus.CANCELLED, "Su kien khong con ton tai");
            return false;
        }
        Event event = found.get();
        if (event.isDeleted()) {
            // Xoá mềm là để giữ liên kết dữ liệu, không phải để tiếp tục làm phiền cả họ.
            jobs.markStatus(job.id(), ReminderStatus.CANCELLED, "Su kien da bi xoa mem");
            return false;
        }

        EventSubject subject = event.personId() == null
                ? null : subjects.findPerson(event.personId()).orElse(null);

        int recipients = notifications.dispatchReminder(new ReminderDispatch(
                job.id(),
                event.id(),
                event.personId(),
                event.targetBranchId(),
                event.isClanLevel(),
                job.offsetDays(),
                job.dueSolarDate(),
                messages.title(event, subject, job),
                messages.body(event, subject, job),
                messages.deepLink(event)));
        jobs.markStatus(job.id(), ReminderStatus.SENT, null);
        log.debug("Da day lich nhac {} (su kien {}, D-{}) toi {} nguoi",
                job.id(), event.id(), job.offsetDays(), recipients);
        return true;
    }
}
