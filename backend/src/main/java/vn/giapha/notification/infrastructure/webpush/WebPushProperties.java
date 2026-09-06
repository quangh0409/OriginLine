package vn.giapha.notification.infrastructure.webpush;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình Web Push / VAPID, tiền tố {@code giapha.webpush}.
 *
 * <h2>Khoá bí mật chỉ đến từ biến môi trường</h2>
 * Hai trường {@code publicKey} và {@code privateKey} <b>không có giá trị mặc định</b> và
 * <b>không</b> được ghi vào bất kỳ tệp nào trong repo. Nhờ ràng buộc tên lỏng của Spring Boot,
 * chúng đọc thẳng từ {@code GIAPHA_WEBPUSH_PUBLIC_KEY} và {@code GIAPHA_WEBPUSH_PRIVATE_KEY} mà
 * không cần một dòng nào trong {@code application.yml} — đó là lý do chọn cách này thay vì
 * {@code @Value} có mặc định.
 *
 * <p>Sinh cặp khoá một lần cho mỗi môi trường (dev / staging / prod). Dùng chung một cặp khoá giữa
 * các môi trường nghĩa là bản dev có thể đẩy thông báo tới điện thoại thật của người trong họ.</p>
 *
 * <p><b>Khoá riêng không bao giờ được ghi log, không bao giờ xuất hiện trong phản hồi API, và
 * không bao giờ chạm tới cơ sở dữ liệu.</b> Endpoint {@code GET /api/v1/push/public-key} chỉ trả
 * khoá công khai.</p>
 *
 * <p>Thiếu cấu hình thì kênh Web Push <b>tự tắt</b> chứ không làm hỏng ứng dụng: in-app là nguồn
 * chân lý của thông báo, và một môi trường chưa cấu hình VAPID vẫn phải chạy được.</p>
 */
@Component
@ConfigurationProperties(prefix = "giapha.webpush")
public class WebPushProperties {

    /** Khoá công khai VAPID, base64url không padding (65 byte, điểm P-256 dạng không nén). */
    private String publicKey;

    /** Khoá riêng VAPID, base64url không padding (32 byte). CHỈ đọc từ biến môi trường. */
    private String privateKey;

    /**
     * Claim {@code sub} của JWT VAPID — {@code mailto:} hoặc URL. Push service dùng nó để liên hệ
     * khi máy chủ này gửi sai; để trống là lý do phổ biến khiến Firefox trả 400.
     */
    private String subject = "mailto:admin@giapha.vn";

    /** Thời gian push service giữ tin khi thiết bị offline. Một ngày là đủ cho nhắc giỗ. */
    private int ttlSeconds = 86_400;

    /** Hạn JWT VAPID. Chuẩn cho phép tối đa 24 giờ; 12 giờ là mức an toàn với lệch đồng hồ. */
    private Duration jwtValidity = Duration.ofHours(12);

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration requestTimeout = Duration.ofSeconds(10);

    /** Tắt tay kênh Web Push mà không phải xoá khoá. */
    private boolean enabled = true;

    /** Đã đủ khoá để gửi hay chưa. */
    public boolean isConfigured() {
        return enabled
                && publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public Duration getJwtValidity() {
        return jwtValidity;
    }

    public void setJwtValidity(Duration jwtValidity) {
        this.jwtValidity = jwtValidity;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** {@code toString} cố ý bỏ khoá riêng — Spring in cấu hình ra log ở chế độ debug. */
    @Override
    public String toString() {
        return "WebPushProperties[enabled=" + enabled + ", subject=" + subject
                + ", publicKey=" + (publicKey == null ? "chua dat" : "da dat")
                + ", privateKey=" + (privateKey == null ? "chua dat" : "da dat (an)") + "]";
    }
}
