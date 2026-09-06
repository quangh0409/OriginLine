package vn.giapha.events.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Kết quả một lượt sinh lịch nhắc giỗ.
 *
 * <h2>Vì sao không trả về mỗi con số {@code created}</h2>
 * Lượt chạy tay (do quản trị viên bấm) phải <b>tự chứng minh được tính idempotent</b>: gọi lần hai
 * trên cùng một ngày tham chiếu vẫn quét đúng ngần ấy sự kiện nhưng {@code createdJobs = 0}. Chỉ
 * trả một con số thì người bấm không phân biệt được "không có gì để sinh" với "job đã có sẵn từ
 * lượt trước" — mà đó chính là câu hỏi họ đang cần trả lời.
 *
 * <p>{@code lunarYears} có mặt vì cùng lý do: giỗ tháng Chạp rơi sang năm dương kế tiếp, và khi
 * người vận hành thắc mắc "sao cụ X chưa có lịch nhắc" thì hai danh sách năm này là chỗ nhìn đầu
 * tiên.</p>
 *
 * @param referenceDate  ngày được coi là "hôm nay" của lượt chạy
 * @param lunarYears     các năm <b>âm lịch</b> đã quy đổi
 * @param solarYears     các năm <b>dương lịch</b> đã quy đổi (cho sự kiện theo dương lịch)
 * @param scannedEvents  số sự kiện lặp lại đã quét
 * @param createdJobs    số {@code reminder_job} <b>thực sự ghi mới</b>; job đã có sẵn không tính
 * @param startedAt      thời điểm bắt đầu
 * @param duration       thời gian chạy
 */
public record ReminderGenerationResult(LocalDate referenceDate,
                                       List<Integer> lunarYears,
                                       List<Integer> solarYears,
                                       int scannedEvents,
                                       int createdJobs,
                                       Instant startedAt,
                                       Duration duration) {

    public ReminderGenerationResult {
        lunarYears = lunarYears == null ? List.of() : List.copyOf(lunarYears);
        solarYears = solarYears == null ? List.of() : List.copyOf(solarYears);
    }

    /** Lượt chạy không ghi thêm job nào — trạng thái bình thường của lần bấm thứ hai. */
    public boolean createdNothing() {
        return createdJobs == 0;
    }
}
