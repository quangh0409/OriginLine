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
            return "Web Push da san sang: cap khoa VAPID hop le va dung mot cap.";
        }
        if (!status.enabled()) {
            return "Web Push dang bi TAT bang tay (giapha.webpush.enabled=false).";
        }
        if (!status.publicKeySet() && !status.privateKeySet()) {
            return "Chua cau hinh khoa VAPID nen kenh Web Push dang tat."
                    + " Thong bao trong ung dung khong bi anh huong.";
        }
        return "Cau hinh VAPID thieu mot nua: "
                + (status.publicKeySet() ? "co khoa cong khai, thieu khoa rieng"
                                         : "co khoa rieng, thieu khoa cong khai")
                + ". Thieu mot nua thi khong ky duoc JWT VAPID va kenh van tat.";
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
            buoc.add("Bo giapha.webpush.enabled=false (hoac dat lai thanh true) roi khoi dong lai backend.");
            return List.copyOf(buoc);
        }
        buoc.add("Sinh cap khoa VAPID bang chinh cong cu cua du an: " + LENH_SINH_KHOA);
        buoc.add("Dat hai bien moi truong GIAPHA_WEBPUSH_PUBLIC_KEY va GIAPHA_WEBPUSH_PRIVATE_KEY"
                + " cho tien trinh backend. KHONG ghi vao application.yml, KHONG commit:"
                + " khoa rieng lot vao lich su Git la lot vinh vien.");
        buoc.add("Khoi dong lai backend - khoa chi duoc doc va tu kiem mot lan luc khoi dong.");
        buoc.add("Goi lai GET /api/v1/admin/notifications/webpush de xac nhan ready=true,"
                + " roi bat lai cong tac trong muc Cai dat.");
        return List.copyOf(buoc);
    }
}
