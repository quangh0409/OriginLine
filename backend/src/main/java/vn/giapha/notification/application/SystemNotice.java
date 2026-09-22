package vn.giapha.notification.application;

import java.util.Objects;
import java.util.UUID;

/**
 * Một thông báo <b>hệ thống gửi cho đúng một người</b>, không sinh ra từ lịch nhắc nào.
 *
 * <h2>Khác {@link ReminderDispatch} ở ba điểm, và cả ba đều có hệ quả</h2>
 * <ol>
 *   <li><b>Người nhận đã biết trước</b>, không phải phân giải từ chi/ngành. Bên gọi vốn đang cầm
 *       {@code app_user} của người ấy trong tay; bắt {@code RecipientDirectory} tra lại là thêm một
 *       lượt đọc để ra đúng dữ liệu vừa có.</li>
 *   <li><b>Không có khoá chống trùng</b> ({@code reminderJobId == null}), nên
 *       {@code ux_notification_inbox_idempotency} không áp. RabbitMQ giao <i>ít nhất một lần</i>,
 *       nên một tin có thể vào hộp thư hai lần. Với loại tin này thì hai bản <b>an toàn hơn</b>
 *       không bản nào: nội dung là một cảnh báo an ninh, và thừa một dòng thì người đọc chỉ thấy
 *       lặp, còn thiếu thì họ không biết chuyện đã xảy ra.</li>
 *   <li><b>Nội dung dựng sẵn ở bên gọi</b> chứ không suy từ sự kiện, nên nó cũng là nơi chịu trách
 *       nhiệm giữ đúng ranh giới Tầng 3: thông báo hiện trên màn hình khoá của thiết bị và đi qua
 *       hạ tầng của bên thứ ba. Không số điện thoại, không email, không địa chỉ, không ngày sinh
 *       đầy đủ — kể cả của chính người nhận.</li>
 * </ol>
 *
 * @param recipientPersonId khoá nhân khẩu — khoá của {@code notification_inbox}
 * @param recipientAppUserId khoá tài khoản — khoá của {@code push_subscription}
 * @param locale            {@code vi} / {@code en}, lấy từ {@code app_user.locale}
 * @param deepLink          đường dẫn tương đối trong ứng dụng; {@code null} nếu không có chỗ để tới
 */
public record SystemNotice(UUID recipientPersonId, UUID recipientAppUserId, String locale,
                           LocalizedText title, LocalizedText body, String deepLink) {

    public SystemNotice {
        Objects.requireNonNull(recipientPersonId, "SystemNotice.recipientPersonId khong duoc null");
        Objects.requireNonNull(title, "SystemNotice.title khong duoc null");
        // body NOT NULL o notification_inbox — chan o day de loi no ra ngay tai cho dung, thay vi
        // o consumer sau ba luot retry va mot chuyen di DLQ.
        Objects.requireNonNull(body, "SystemNotice.body khong duoc null");
    }
}
