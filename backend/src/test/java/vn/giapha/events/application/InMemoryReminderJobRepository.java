package vn.giapha.events.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import vn.giapha.events.domain.ReminderJob;
import vn.giapha.events.domain.ReminderStatus;

/**
 * Bảng {@code reminder_job} trong bộ nhớ.
 *
 * <h2>Vì sao phải mô phỏng chỉ mục duy nhất chứ không chỉ lưu một danh sách</h2>
 * Chống trùng lịch nhắc <b>không</b> nằm trong mã Java — nó nằm ở
 * {@code ux_reminder_job_occurrence (event_id, occurrence_year, offset_days)} cộng
 * {@code INSERT ... ON CONFLICT DO NOTHING}. Một fake chỉ biết {@code add()} sẽ khiến bài test
 * "chạy hai lần không sinh trùng" luôn xanh bất kể tầng ứng dụng viết thế nào, tức là kiểm thử
 * đúng cái không cần kiểm. Bộ ba khoá ở đây được chép nguyên từ migration V4.
 */
final class InMemoryReminderJobRepository implements vn.giapha.events.domain.port.ReminderJobRepository {

    private final Map<UUID, ReminderJob> byId = new LinkedHashMap<>();
    private final Set<String> uniqueKeys = new HashSet<>();

    /** Nhật ký {@code markStatus} để bài test đọc lại được lý do huỷ/lỗi. */
    final List<StatusChange> statusChanges = new ArrayList<>();

    @Override
    public boolean insertIfAbsent(ReminderJob job) {
        if (!uniqueKeys.add(key(job))) {
            return false;
        }
        byId.put(job.id(), job);
        return true;
    }

    @Override
    public List<ReminderJob> claimDue(Instant now, int limit) {
        // Giành và đánh dấu QUEUED trong cùng một bước, đúng như câu UPDATE ... RETURNING thật:
        // gọi hai lần liên tiếp không được trả lại cùng một job.
        List<ReminderJob> claimed = byId.values().stream()
                .filter(job -> job.status() == ReminderStatus.PENDING)
                .filter(job -> !job.fireAt().isAfter(now))
                .sorted(Comparator.comparing(ReminderJob::fireAt))
                .limit(limit)
                .toList();
        List<ReminderJob> result = new ArrayList<>(claimed.size());
        for (ReminderJob job : claimed) {
            ReminderJob queued = withStatus(job, ReminderStatus.QUEUED, job.lastError());
            byId.put(job.id(), queued);
            result.add(queued);
        }
        return List.copyOf(result);
    }

    @Override
    public void markStatus(UUID jobId, ReminderStatus status, String error) {
        statusChanges.add(new StatusChange(jobId, status, error));
        ReminderJob job = byId.get(jobId);
        if (job != null) {
            byId.put(jobId, withStatus(job, status, error));
        }
    }

    List<ReminderJob> all() {
        return List.copyOf(byId.values());
    }

    int size() {
        return byId.size();
    }

    ReminderStatus statusOf(UUID jobId) {
        ReminderJob job = byId.get(jobId);
        return job == null ? null : job.status();
    }

    private static ReminderJob withStatus(ReminderJob job, ReminderStatus status, String error) {
        return new ReminderJob(job.id(), job.eventId(), job.occurrenceYear(), job.dueSolarDate(),
                job.offsetDays(), job.fireAt(), status, job.attemptCount() + 1, error, job.dispatchedAt());
    }

    private static String key(ReminderJob job) {
        return job.eventId() + "|" + job.occurrenceYear() + "|" + job.offsetDays();
    }

    record StatusChange(UUID jobId, ReminderStatus status, String error) {
    }
}
