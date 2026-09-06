package vn.giapha.events.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.LunarDate;

/** Bất biến của một mốc nhắc đã lên lịch — khớp các ràng buộc CHECK của {@code reminder_job}. */
class ReminderJobTest {

    private static final EventOccurrence GIO = new EventOccurrence(
            LocalDate.of(2026, 10, 20), 2026, LunarDate.of(2026, 9, 10), OccurrenceAdjustment.EXACT);

    @Test
    @DisplayName("Job moi sinh luon o trang thai PENDING, chua thu lan nao")
    void jobMoiLaPending() {
        ReminderJob job = ReminderJob.pending(UUID.randomUUID(), UUID.randomUUID(), GIO, 3,
                Instant.parse("2026-10-17T00:00:00Z"));

        assertThat(job.status()).isEqualTo(ReminderStatus.PENDING);
        assertThat(job.attemptCount()).isZero();
        assertThat(job.dispatchedAt()).isNull();
        assertThat(job.occurrenceYear()).isEqualTo(2026);
        assertThat(job.dueSolarDate()).isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    @DisplayName("offsetDays am bi tu choi ngay tu constructor (ck_reminder_job_offset)")
    void offsetAmBiTuChoi() {
        assertThatThrownBy(() -> new ReminderJob(UUID.randomUUID(), UUID.randomUUID(), 2026,
                LocalDate.of(2026, 10, 20), -1, Instant.now(), ReminderStatus.PENDING, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offsetDays");
    }

    @Test
    @DisplayName("Job co ngay gio da qua la job qua han - phai huy chu khong ban")
    void jobQuaHan() {
        ReminderJob job = ReminderJob.pending(UUID.randomUUID(), UUID.randomUUID(), GIO, 1,
                Instant.parse("2026-10-19T00:00:00Z"));

        assertThat(job.isStale(LocalDate.of(2026, 10, 21))).isTrue();
        assertThat(job.isStale(LocalDate.of(2026, 10, 20))).isFalse();
        assertThat(job.isStale(LocalDate.of(2026, 10, 19))).isFalse();
    }

    @Test
    @DisplayName("CANCELLED va SENT la trang thai ket thuc; FAILED thi khong")
    void trangThaiKetThuc() {
        assertThat(ReminderStatus.SENT.isTerminal()).isTrue();
        assertThat(ReminderStatus.CANCELLED.isTerminal()).isTrue();
        // FAILED không phải trạng thái kết thúc: lượt quét sau còn thử lại được.
        assertThat(ReminderStatus.FAILED.isTerminal()).isFalse();
        assertThat(ReminderStatus.QUEUED.isTerminal()).isFalse();
    }
}
