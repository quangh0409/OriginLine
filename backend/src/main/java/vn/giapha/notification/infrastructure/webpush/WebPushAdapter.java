package vn.giapha.notification.infrastructure.webpush;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.DeliveryResult;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.PushSubscription;
import vn.giapha.notification.domain.port.NotificationProvider;
import vn.giapha.notification.domain.port.PushSubscriptionRepository;
import vn.giapha.notification.domain.port.VapidKeyProvider;

/**
 * Kênh <b>Web Push</b> (VAPID / RFC 8291) — đẩy thông báo tới mọi thiết bị đã đăng ký của người nhận.
 *
 * <h2>Subscription bị thu hồi thì XOÁ, không retry</h2>
 * Push service trả {@code 404} hoặc {@code 410} khi người dùng gỡ ứng dụng, xoá dữ liệu trình duyệt
 * hoặc thu hồi quyền. Đó là trạng thái <b>vĩnh viễn</b>: mọi lần gửi sau đều nhận đúng mã ấy. Bản
 * ghi bị xoá ngay khỏi {@code push_subscription} — giữ lại chỉ khiến mỗi mùa giỗ tốn thêm một lượt
 * gọi HTTP cho mỗi thiết bị chết, và làm nhiễu số liệu "gửi thất bại".
 *
 * <h2>Nhiều thiết bị, kết quả tổng hợp</h2>
 * Một người có thể có điện thoại, máy tính bảng và máy tính để bàn. Kết quả trả về là kết quả
 * <b>tốt nhất</b> trong các thiết bị: chỉ cần một máy nhận được thì lượt gửi coi như thành công.
 * Đánh dấu thất bại vì cái máy tính cũ ở nhà đã tắt sẽ khiến người dùng bị gửi lại vô ích.
 *
 * <h2>Chưa cấu hình khoá thì tự tắt</h2>
 * Không có VAPID thì trả {@code SKIPPED} chứ không lỗi. In-app là nguồn chân lý; một môi trường
 * chưa cấu hình VAPID vẫn phải chạy và vẫn phải nhắc được giỗ.
 *
 * <p><b>Khoá riêng VAPID chỉ nằm trong biến môi trường</b> ({@link WebPushProperties}) — không ở
 * repo, không ở cơ sở dữ liệu, không ở log.</p>
 */
@Component
public class WebPushAdapter implements NotificationProvider, VapidKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(WebPushAdapter.class);

    /** Mã HTTP nghĩa là "subscription không còn tồn tại" — xoá, không thử lại. */
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_GONE = 410;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final WebPushProperties properties;
    private final VapidSigner signer;
    private final WebPushCipher cipher;
    private final PushSubscriptionRepository subscriptions;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final VapidKeyMaterial keys;

    public WebPushAdapter(WebPushProperties properties, VapidSigner signer, WebPushCipher cipher,
                          PushSubscriptionRepository subscriptions, ObjectMapper objectMapper)
            throws GeneralSecurityException {
        this.properties = properties;
        this.signer = signer;
        this.cipher = cipher;
        this.subscriptions = subscriptions;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        // Khoá sai định dạng phải nổ ra lúc khởi động, không phải lúc 7 giờ sáng ngày giỗ.
        this.keys = VapidKeyMaterial.from(properties);
        if (keys == null) {
            log.warn("Web Push chua duoc cau hinh (thieu GIAPHA_WEBPUSH_PUBLIC_KEY/PRIVATE_KEY)."
                    + " Kenh nay tam tat; thong bao in-app khong bi anh huong.");
        } else {
            log.info("Web Push san sang (VAPID subject={})", properties.getSubject());
        }
    }

    @Override
    public Optional<String> publicKeyBase64Url() {
        return keys == null ? Optional.empty() : Optional.of(keys.publicKeyBase64Url());
    }

    @Override
    public Channel channel() {
        return Channel.WEBPUSH;
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        if (keys == null) {
            return DeliveryResult.skipped("Web Push chua cau hinh khoa VAPID");
        }
        if (message.recipient().appUserId() == null) {
            return DeliveryResult.skipped("Nguoi nhan chua co tai khoan nen khong co thiet bi nao");
        }
        List<PushSubscription> devices = subscriptions.activeByAppUser(message.recipient().appUserId());
        if (devices.isEmpty()) {
            return DeliveryResult.skipped("Nguoi nhan chua dang ky thiet bi nao");
        }

        byte[] payload = buildPayload(message);
        boolean anySent = false;
        boolean anyRetryable = false;
        String lastDetail = null;

        for (PushSubscription device : devices) {
            Outcome outcome = sendTo(device, payload);
            anySent |= outcome.sent();
            anyRetryable |= outcome.retryable();
            if (outcome.detail() != null) {
                lastDetail = outcome.detail();
            }
        }

        if (anySent) {
            return DeliveryResult.sent();
        }
        if (anyRetryable) {
            return DeliveryResult.retryable(lastDetail);
        }
        // Tất cả thiết bị đều đã bị thu hồi và vừa bị xoá: không còn gì để gửi, và cũng không có gì
        // để sửa. SKIPPED chứ không FAILED — đây là trạng thái bình thường của một người đã gỡ app.
        return DeliveryResult.skipped(lastDetail == null ? "Khong con thiet bi nao hoat dong" : lastDetail);
    }

    private Outcome sendTo(PushSubscription device, byte[] payload) {
        String authorization;
        byte[] body;
        try {
            authorization = signer.authorizationHeader(keys, device.audience(), properties.getSubject(),
                    properties.getJwtValidity());
            if (authorization == null) {
                return new Outcome(false, false, "Khong ky duoc JWT VAPID");
            }
            body = cipher.encrypt(payload, device.p256dh(), device.auth());
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            // Khoá của client hỏng: thiết bị này không bao giờ nhận được. Xoá luôn cho gọn.
            log.warn("Khoa client hong o dang ky {} - xoa: {}", device.id(), ex.getMessage());
            subscriptions.deleteByEndpoint(device.endpoint());
            return new Outcome(false, false, "Khoa client khong hop le");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(device.endpoint()))
                .timeout(properties.getRequestTimeout())
                .header("Authorization", authorization)
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("TTL", String.valueOf(properties.getTtlSeconds()))
                .header("Urgency", "normal")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return interpret(device, response.statusCode(), response.body());
        } catch (java.io.IOException ex) {
            subscriptions.recordFailure(device.id());
            return new Outcome(false, true, "Loi mang toi push service: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Outcome(false, true, "Bi ngat khi goi push service");
        }
    }

    private Outcome interpret(PushSubscription device, int status, String responseBody) {
        if (status >= 200 && status < 300) {
            subscriptions.touchLastUsed(device.id(), Instant.now());
            return new Outcome(true, false, null);
        }
        if (status == HTTP_NOT_FOUND || status == HTTP_GONE) {
            subscriptions.deleteByEndpoint(device.endpoint());
            log.info("Dang ky {} bi thu hoi (HTTP {}) - da xoa, khong retry", device.id(), status);
            return new Outcome(false, false, "Subscription bi thu hoi (HTTP " + status + ")");
        }
        if (status == HTTP_TOO_MANY_REQUESTS || status >= 500) {
            subscriptions.recordFailure(device.id());
            return new Outcome(false, true, "Push service tra HTTP " + status);
        }
        // 400/401/403: gần như luôn là VAPID sai (claim `aud`, khoá lệch giữa server và client, hoặc
        // thiếu `sub`). Retry không sửa được cấu hình — ghi ERROR để có người nhìn.
        log.error("Push service tu choi (HTTP {}) o dang ky {}: {}", status, device.id(),
                abbreviate(responseBody));
        subscriptions.recordFailure(device.id());
        return new Outcome(false, false, "Push service tu choi (HTTP " + status + ")");
    }

    /**
     * Payload gửi tới service worker.
     *
     * <p><b>Không chứa dữ liệu Tầng 3.</b> Thông báo đẩy hiện trên màn hình khoá của thiết bị, và đi
     * qua hạ tầng của Google/Mozilla/Apple — dù đã mã hoá đầu-cuối thì nó vẫn hiện công khai trên
     * một màn hình mà người khác nhìn thấy được.</p>
     */
    private byte[] buildPayload(NotificationMessage message) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("title", message.title());
        json.put("body", message.body() == null ? "" : message.body());
        json.put("url", message.deepLink() == null ? "/" : message.deepLink());
        // `tag` để hệ điều hành gộp các thông báo của cùng một lịch nhắc thay vì chồng đống.
        if (message.reminderJobId() != null) {
            json.put("tag", "gio-" + message.reminderJobId());
        }
        if (message.eventId() != null) {
            json.put("eventId", message.eventId().toString());
        }
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String flat = value.replaceAll("\\s+", " ").trim();
        return flat.length() <= 200 ? flat : flat.substring(0, 200);
    }

    /** Kết quả gửi tới <b>một</b> thiết bị. */
    private record Outcome(boolean sent, boolean retryable, String detail) {
    }
}
