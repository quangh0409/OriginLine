package vn.giapha.events.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Một mốc nhắc đã được lên lịch cho một lần xảy ra của sự kiện — bản chiếu của bảng
 * {@code reminder_job}.
 *
 * <p>Bộ ba {@code (eventId, occurrenceYear, offsetDays)} là <b>khoá chống trùng</b>
 * ({@code ux_reminder_job_occurrence}). Nhờ nó, scheduler chạy lại — hoặc chạy đồng thời trên hai
 * instance — cũng không nhân đôi lịch nhắc. Đó là chốt chặn duy nhất; đừng thay bằng kiểm tra
 * "SELECT rồi mới INSERT" ở tầng Java, vì hai instance sẽ cùng đọc thấy "chưa có".</p>
 */
public final class ReminderJob {

    private final UUID id;
    private final UUID eventId;
    private final int occurrenceYear;
    private final LocalDate dueSolarDate;
    private final int offsetDays;
    private final Instant fireAt;
    private final ReminderStatus status;
    private final int attemptCount;
    private final String lastError;
    private final Instant dispatchedAt;

    public ReminderJob(UUID id, UUID eventId, int occurrenceYear, LocalDate dueSolarDate, int offsetDays,
                       Instant fireAt, ReminderStatus status, int attemptCount, String lastError,
                       Instant dispatchedAt) {
        this.id = Objects.requireNonNull(id, "ReminderJob.id khong duoc null");
        this.eventId = Objects.requireNonNull(eventId, "ReminderJob.eventId khong duoc null");
        this.occurrenceYear = occurrenceYear;
        this.dueSolarDate = Objects.requireNonNull(dueSolarDate, "dueSolarDate khong duoc null");
        if (offsetDays < 0) {
            throw new IllegalArgumentException("offsetDays phai >= 0 (ck_reminder_job_offset): " + offsetDays);
        }
        this.offsetDays = offsetDays;
        this.fireAt = Objects.requireNonNull(fireAt, "fireAt khong duoc null");
        this.status = status == null ? ReminderStatus.PENDING : status;
        this.attemptCount = Math.max(0, attemptCount);
        this.lastError = lastError;
        this.dispatchedAt = dispatchedAt;
    }

    /** Job mới sinh từ scheduler — luôn ở trạng thái {@code PENDING}. */
    public static ReminderJob pending(UUID id, UUID eventId, EventOccurrence occurrence, int offsetDays,
                                      Instant fireAt) {
        return new ReminderJob(id, eventId, occurrence.occurrenceYear(), occurrence.dueSolarDate(),
                offsetDays, fireAt, ReminderStatus.PENDING, 0, null, null);
    }

    public UUID id() {
        return id;
    }

    public UUID eventId() {
        return eventId;
    }

    public int occurrenceYear() {
        return occurrenceYear;
    }

    public LocalDate dueSolarDate() {
        return dueSolarDate;
    }

    public int offsetDays() {
        return offsetDays;
    }

    public Instant fireAt() {
        return fireAt;
    }

    public ReminderStatus status() {
        return status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public String lastError() {
        return lastError;
    }

    public Instant dispatchedAt() {
        return dispatchedAt;
    }

    /**
     * Job đã tới giờ nhưng ngày giỗ thì <b>đã qua</b> — nhắc lúc này là vô nghĩa và gây hoang mang.
     *
     * <p>Xảy ra khi hệ thống ngừng chạy dài ngày. Bên gọi phải chuyển sang {@code CANCELLED}, không
     * phải {@code FAILED}: đây không phải lỗi gửi.</p>
     */
    public boolean isStale(LocalDate today) {
        return dueSolarDate.isBefore(today);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReminderJob job && id.equals(job.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ReminderJob[" + id + " event=" + eventId + " D-" + offsetDays + " due=" + dueSolarDate
                + " " + status + "]";
    }
}
