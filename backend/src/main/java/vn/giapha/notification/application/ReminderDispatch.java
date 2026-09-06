package vn.giapha.notification.application;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Yêu cầu gửi một lượt nhắc giỗ/lễ — <b>hợp đồng công khai</b> giữa {@code events} và
 * {@code notification}.
 *
 * <p>Bên gửi ({@code events}) chỉ nói <i>nhắc cái gì, cho phạm vi nào</i>. Việc <i>ai</i> nhận,
 * <i>qua kênh nào</i>, retry ra sao là chuyện của {@code notification}. Nhờ ranh giới này,
 * {@code events} không biết RabbitMQ hay Web Push tồn tại, và Giai đoạn 2 thêm Zalo ZNS mà không
 * chạm vào scheduler.</p>
 *
 * @param reminderJobId khoá chống trùng phía consumer, ghép với người nhận thành
 *                      {@code reminder_job_id + recipient}
 * @param targetBranchId chi/ngành đích; {@code null} khi {@code clanLevel}
 * @param clanLevel     {@code true} = cả dòng họ nhận, bỏ qua {@code targetBranchId}
 * @param offsetDays    mốc nhắc đã bắn (7 / 3 / 1) — FE hiển thị lại được và log đối soát được
 * @param dueSolarDate  ngày dương của lần giỗ/lễ này
 * @param deepLink      đường dẫn tương đối trong ứng dụng, dùng chung cho in-app và Web Push
 */
public record ReminderDispatch(UUID reminderJobId,
                               UUID eventId,
                               UUID subjectPersonId,
                               UUID targetBranchId,
                               boolean clanLevel,
                               int offsetDays,
                               LocalDate dueSolarDate,
                               LocalizedText title,
                               LocalizedText body,
                               String deepLink) {

    public ReminderDispatch {
        Objects.requireNonNull(reminderJobId, "ReminderDispatch.reminderJobId khong duoc null");
        Objects.requireNonNull(eventId, "ReminderDispatch.eventId khong duoc null");
        Objects.requireNonNull(title, "ReminderDispatch.title khong duoc null");
    }
}
