package vn.giapha.events.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.giapha.events.domain.ReminderJob;
import vn.giapha.events.domain.ReminderStatus;

/**
 * Cổng ghi/đọc {@code reminder_job}.
 *
 * <p>Hai phép ở đây đều cố ý <b>không</b> phải là "đọc rồi ghi": chống trùng và giành việc phải do
 * cơ sở dữ liệu quyết định, không phải do kỷ luật của tầng Java. Mẫu "SELECT xem có chưa rồi mới
 * INSERT" và mẫu "lấy job rồi mới đánh dấu đang xử lý" đều hỏng ngay khi có hai instance — và hệ
 * thống này được thiết kế để chạy nhiều instance.</p>
 */
public interface ReminderJobRepository {

    /**
     * Ghi job nếu chưa có bộ ba {@code (eventId, occurrenceYear, offsetDays)}.
     *
     * @return {@code true} nếu thực sự ghi mới; {@code false} nếu đã tồn tại (không phải lỗi)
     */
    boolean insertIfAbsent(ReminderJob job);

    /**
     * <b>Giành</b> các job đã tới giờ: chuyển sang {@code QUEUED}, tăng {@code attempt_count} và trả
     * về — tất cả trong <b>một câu lệnh</b>.
     *
     * <p>Tách thành "lấy" rồi "đánh dấu" thì hai instance sẽ cùng lấy một job và cả họ nhận hai tin
     * nhắc cho cùng một cái giỗ. Consumer tuy idempotent nên vẫn không nhân đôi thông báo, nhưng
     * dựa vào lớp phòng thủ cuối cùng để che một lỗi ở lớp đầu tiên là cách tích luỹ sự cố.</p>
     *
     * @return các job vừa giành được, cũ nhất trước
     */
    List<ReminderJob> claimDue(Instant now, int limit);

    /** Đặt trạng thái kết thúc: {@code SENT} / {@code FAILED} / {@code CANCELLED} / {@code PENDING}. */
    void markStatus(UUID jobId, ReminderStatus status, String error);
}
