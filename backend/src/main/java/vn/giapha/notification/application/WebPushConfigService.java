package vn.giapha.notification.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import vn.giapha.notification.application.view.WebPushConfigView;
import vn.giapha.notification.domain.WebPushConfigStatus;
import vn.giapha.notification.domain.port.VapidKeyProvider;

/**
 * Chẩn đoán cấu hình Web Push — phục vụ {@code GET /api/v1/admin/notifications/webpush}.
 *
 * <h2>Vì sao tồn tại</h2>
 * Giao diện nói với thành viên: "máy chủ chưa cấu hình khoá thông báo đẩy, xin báo quản trị viên".
 * Câu đó chỉ có nghĩa nếu quản trị viên <b>có chỗ để nhìn và có việc cụ thể để làm</b>. Trước khi có
 * lớp này thì không: dấu vết duy nhất là một dòng {@code WARN} trong log lúc khởi động, đã trôi qua
 * từ lâu, và {@code GET /api/v1/push/public-key} trả 422 với đúng một mã lỗi mà không nói thiếu
 * cái gì. Người quản trị nhận được lời phàn nàn nhưng không có cách nào xác nhận hay sửa.
 *
 * <p><b>Chỉ đọc, không có đường ghi.</b> Không có endpoint nào nhận khoá riêng VAPID: nhận khoá qua
 * HTTP thì máy chủ phải lưu nó xuống một chỗ nào đó, và chỗ đó sẽ nằm trong bản sao lưu. Khoá vào hệ
 * thống <b>chỉ</b> bằng biến môi trường, nghĩa là bằng tay người vận hành, một lần cho mỗi môi
 * trường.</p>
 */
@Service
public class WebPushConfigService {

    /** Lệnh sinh khoá — trỏ thẳng vào công cụ của chính dự án, không phải một trang web lạ. */
    private static final String LENH_SINH_KHOA =
            "cd backend && ./mvnw -o -q compile"
                    + " && java -cp target/classes"
                    + " vn.giapha.notification.infrastructure.webpush.VapidKeyGenerator";

    private final VapidKeyProvider vapidKeys;

    public WebPushConfigService(VapidKeyProvider vapidKeys) {
        this.vapidKeys = vapidKeys;
    }

    /** Tình trạng hiện tại kèm các bước sửa, nếu còn thiếu. */
    public WebPushConfigView status() {
        WebPushConfigStatus status = vapidKeys.configStatus();
        return new WebPushConfigView(
                status.ready(),
                status.enabled(),
                status.publicKeySet(),
                status.privateKeySet(),
                status.publicKey(),
                status.subject(),
                status.ttlSeconds(),
                tomTat(status),
                cacBuocCanLam(status));
    }

    private static String tomTat(WebPushConfigStatus status) {
        if (status.ready()) {
            return "Web Push đã sẵn sàng: cặp khoá VAPID hợp lệ và đúng một cặp.";
        }
        if (!status.enabled()) {
            return "Web Push đang bị TẮT bằng tay (giapha.webpush.enabled=false).";
        }
        if (!status.publicKeySet() && !status.privateKeySet()) {
            return "Chưa cấu hình khoá VAPID nên kênh Web Push đang tắt."
                    + " Thông báo trong ứng dụng không bị ảnh hưởng.";
        }
        return "Cấu hình VAPID thiếu một nửa: "
                + (status.publicKeySet() ? "có khoá công khai, thiếu khoá riêng"
                                         : "có khoá riêng, thiếu khoá công khai")
                + ". Thiếu một nửa thì không ký được JWT VAPID và kênh vẫn tắt.";
    }

    /**
     * Các bước sửa, viết cho người sẽ thực sự gõ lệnh.
     *
     * <p>Luôn nhắc <b>khởi động lại</b>: khoá được giải mã một lần lúc khởi động (và đi kèm phép tự
     * kiểm cùng cặp), nên đặt biến môi trường cho tiến trình đang chạy không có tác dụng gì. Thiếu
     * dòng này là người vận hành sẽ đặt biến, gọi lại endpoint, thấy vẫn "chưa sẵn sàng", và kết
     * luận nhầm rằng khoá sai.</p>
     */
    private static List<String> cacBuocCanLam(WebPushConfigStatus status) {
        if (status.ready()) {
            return List.of();
        }
        List<String> buoc = new ArrayList<>(4);
        if (!status.enabled()) {
            buoc.add("Bỏ giapha.webpush.enabled=false (hoặc đặt lại thành true) rồi khởi động lại backend.");
            return List.copyOf(buoc);
        }
        buoc.add("Sinh cặp khoá VAPID bằng chính công cụ của dự án: " + LENH_SINH_KHOA);
        buoc.add("Đặt hai biến môi trường GIAPHA_WEBPUSH_PUBLIC_KEY và GIAPHA_WEBPUSH_PRIVATE_KEY"
                + " cho tiến trình backend. KHÔNG ghi vào application.yml, KHÔNG commit:"
                + " khoá riêng lọt vào lịch sử Git là lọt vĩnh viễn.");
        buoc.add("Khởi động lại backend - khoá chỉ được đọc và tự kiểm một lần lúc khởi động.");
        buoc.add("Goi lai GET /api/v1/admin/notifications/webpush de xac nhan ready=true,"
                + " roi bat lai cong tac trong muc Cai dat.");
        return List.copyOf(buoc);
    }
}
