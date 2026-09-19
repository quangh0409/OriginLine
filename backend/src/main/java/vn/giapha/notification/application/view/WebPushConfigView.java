package vn.giapha.notification.application.view;

import java.util.List;

/**
 * Kết quả chẩn đoán cấu hình Web Push cho người vận hành.
 *
 * @param ready         đã sẵn sàng gửi hay chưa — câu trả lời duy nhất mà quản trị viên cần trước
 * @param enabled       công tắc {@code giapha.webpush.enabled}
 * @param publicKeySet  đã đặt {@code GIAPHA_WEBPUSH_PUBLIC_KEY}
 * @param privateKeySet đã đặt {@code GIAPHA_WEBPUSH_PRIVATE_KEY} (chỉ cờ; giá trị không bao giờ trả)
 * @param publicKey     khoá công khai đang phục vụ, {@code null} khi chưa sẵn sàng
 * @param subject       claim {@code sub} của JWT VAPID
 * @param ttlSeconds    thời gian push service giữ tin khi thiết bị offline
 * @param summary       một câu tiếng Việt nói rõ tình trạng
 * @param remediation   các bước cần làm; rỗng khi đã sẵn sàng
 */
public record WebPushConfigView(boolean ready,
                                boolean enabled,
                                boolean publicKeySet,
                                boolean privateKeySet,
                                String publicKey,
                                String subject,
                                int ttlSeconds,
                                String summary,
                                List<String> remediation) {

    public WebPushConfigView {
        remediation = remediation == null ? List.of() : List.copyOf(remediation);
    }
}
