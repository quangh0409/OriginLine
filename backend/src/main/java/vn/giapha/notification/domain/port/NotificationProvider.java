package vn.giapha.notification.domain.port;

import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.DeliveryResult;
import vn.giapha.notification.domain.NotificationMessage;

/**
 * Cổng gửi thông báo qua <b>một kênh</b>.
 *
 * <p>Đây là điểm mở rộng đã hứa với Giai đoạn 2: thêm Zalo ZNS = thêm một {@code @Component} hiện
 * thực giao diện này với {@code channel() == Channel.ZALO}, cộng một queue trong
 * {@code RabbitConfig} và một {@code @RabbitListener}. <b>Không phải sửa</b> luồng gửi,
 * {@code NotificationDeliveryService}, schema hay tầng API.</p>
 *
 * <h2>Hợp đồng</h2>
 * <ul>
 *   <li><b>Không được ném ngoại lệ</b> cho lỗi gửi thông thường — trả {@link DeliveryResult} và để
 *       bên gọi quyết định retry. Ném ngoại lệ thì tầng trên mất thông tin "tạm thời hay vĩnh viễn"
 *       và buộc phải đoán; đoán sai thì hoặc mất thông báo, hoặc đốt hạn ngạch gateway.</li>
 *   <li>Phải vô hại khi bị gọi lại ở mức có thể: bên gọi đã chặn trùng bằng
 *       {@code reminder_job_id + recipient}, nhưng gateway vẫn có thể nhận hai lần khi mạng đứt
 *       giữa chừng.</li>
 * </ul>
 */
public interface NotificationProvider {

    Channel channel();

    DeliveryResult send(NotificationMessage message);
}
