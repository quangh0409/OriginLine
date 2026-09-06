package vn.giapha.events.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Kết quả một lượt chạy tay việc đẩy lịch nhắc đã tới giờ lên RabbitMQ.
 *
 * <p>Con số này là <b>số job đã đẩy đi</b>, không phải số người đã nhận được: việc gửi thật diễn ra
 * bất đồng bộ ở consumer, và luồng HTTP không bao giờ chờ nó.</p>
 */
@Schema(name = "ReminderDispatchResult", description = "Ket qua day lich nhac den han len hang doi")
public record ReminderDispatchResultDto(
        @Schema(description = "So reminder_job da day len RabbitMQ (chua phai so nguoi da nhan)")
        int dispatchedJobs,
        Instant dispatchedAt) {
}
