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

    /**
     * Xoá các job <b>chưa tới tay ai</b> ({@code PENDING} <i>và</i> {@code CANCELLED}) của một sự
     * kiện.
     *
     * <p>Gọi khi sự kiện đổi ngày, đổi phạm vi, hoặc bị xoá mềm. Không dọn thì cả chi vẫn được nhắc
     * theo ngày cũ — không lỗi, không log, người ta chỉ đến nhầm ngày.</p>
     *
     * <h2>Vì sao XOÁ chứ không chuyển {@code CANCELLED}, và vì sao {@code QUEUED}/{@code SENT}
     * thì ở lại</h2>
     * Chống trùng nằm ở chỉ mục duy nhất {@code ux_reminder_job_occurrence (event_id,
     * occurrence_year, offset_days)}. Một dòng {@code CANCELLED} vẫn chiếm khoá ấy, nên
     * {@link #insertIfAbsent} của lượt sinh kế tiếp sẽ lặng lẽ không ghi gì và sự kiện vừa sửa
     * <b>vĩnh viễn không có lịch nhắc nào</b>.
     *
     * <p>{@code QUEUED} đã nằm trong RabbitMQ và {@code SENT} đã tới tay người trong họ — cả hai là
     * chuyện đã rồi, và {@code notification_log} phải còn chỗ trỏ về. Lời nhắc đã phát không bị xoá
     * mềm của sự kiện làm biến mất.</p>
     *
     * <p>{@code CANCELLED} thì ngược hẳn: <b>không ai từng nhận được gì</b>, mà dòng ấy vẫn giữ
     * khoá {@code ux_reminder_job_occurrence}. Bỏ sót nó nghĩa là {@link #insertIfAbsent} cho đúng
     * lần xảy ra đó lặng lẽ không làm gì <b>mãi mãi</b> — sự kiện vừa sửa vĩnh viễn không có lịch
     * nhắc nào. Trạng thái ấy không phải chuyện lý thuyết: {@code DispatchDueRemindersService} đặt
     * {@code CANCELLED} lên những job nó vừa {@code claimDue} (đã {@code QUEUED}) khi sự kiện vừa
     * bị xoá mềm hoặc ngày đã trôi qua, nên một lượt sửa rơi vào giữa hai bước ấy là đủ.
     *
     * @return số job đã xoá
     */
    int deleteUnsentByEvent(UUID eventId);
}
