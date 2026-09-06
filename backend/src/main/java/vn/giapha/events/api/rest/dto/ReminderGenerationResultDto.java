package vn.giapha.events.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import vn.giapha.events.application.ReminderGenerationResult;

/**
 * Kết quả một lượt chạy tay job sinh lịch nhắc giỗ.
 *
 * <p>{@code createdJobs = 0} ở lần gọi thứ hai <b>không phải lỗi</b> — đó là bằng chứng chống trùng
 * đang hoạt động. {@code scannedEvents} vẫn giữ nguyên để phân biệt với ca "không có sự kiện nào
 * để quét".</p>
 */
@Schema(name = "ReminderGenerationResult", description = "Ket qua sinh lich nhac gio chay tay")
public record ReminderGenerationResultDto(
        @Schema(description = "Ngay duoc coi la 'hom nay' cua luot chay", example = "2026-09-05")
        LocalDate referenceDate,
        @Schema(description = "Cac nam am lich da quy doi", example = "[2026, 2027]")
        List<Integer> lunarYears,
        @Schema(description = "Cac nam duong lich da quy doi", example = "[2026, 2027]")
        List<Integer> solarYears,
        @Schema(description = "So su kien lap lai da quet")
        int scannedEvents,
        @Schema(description = "So reminder_job THUC SU ghi moi; 0 nghia la da co san tu luot truoc")
        int createdJobs,
        Instant startedAt,
        @Schema(description = "Thoi gian chay, mili giay")
        long durationMs) {

    public static ReminderGenerationResultDto from(ReminderGenerationResult result) {
        return new ReminderGenerationResultDto(
                result.referenceDate(),
                result.lunarYears(),
                result.solarYears(),
                result.scannedEvents(),
                result.createdJobs(),
                result.startedAt(),
                result.duration().toMillis());
    }
}
