package vn.giapha.notification.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Đơn vị công việc mà một {@code NotificationProvider} nhận: <b>một thông báo, một người nhận,
 * một kênh</b>.
 *
 * <p>Cố ý không gộp nhiều người nhận vào một tin. Gộp thì một người lỗi sẽ kéo cả lô vào DLQ, và
 * khi retry thì những người đã nhận rồi sẽ nhận lại. Một tin một người là điều kiện để cả retry lẫn
 * khoá chống trùng {@code reminder_job_id + recipient} hoạt động đúng.</p>
 *
 * <p><b>Nội dung không được chứa dữ liệu Tầng 3</b> (số điện thoại, email, địa chỉ, ngày sinh đầy
 * đủ). Thông báo hiện trên màn hình khoá của thiết bị và đi qua hạ tầng của bên thứ ba.</p>
 *
 * @param reminderJobId  {@code null} với thông báo không sinh từ lịch nhắc (ví dụ thông báo hệ
 *                       thống). Khi {@code null} thì <b>không có</b> khoá chống trùng — xem
 *                       {@code ux_notification_log_idempotency} chỉ áp khi cột này khác NULL.
 * @param deepLink       đường dẫn tương đối trong ứng dụng, dùng chung cho in-app và Web Push
 */
public record NotificationMessage(UUID reminderJobId,
                                  UUID eventId,
                                  UUID subjectPersonId,
                                  Recipient recipient,
                                  Channel channel,
                                  NotificationCategory category,
                                  String title,
                                  String body,
                                  String deepLink,
                                  Integer offsetDays,
                                  LocalDate dueSolarDate) {

    public NotificationMessage {
        Objects.requireNonNull(recipient, "NotificationMessage.recipient khong duoc null");
        Objects.requireNonNull(channel, "NotificationMessage.channel khong duoc null");
        Objects.requireNonNull(title, "NotificationMessage.title khong duoc null");
        category = category == null ? NotificationCategory.REMINDER : category;
    }

    /** Có khoá chống trùng hay không — quyết định consumer có thể bỏ qua bản sao hay không. */
    public boolean hasIdempotencyKey() {
        return reminderJobId != null;
    }
}
