package vn.giapha.notification.infrastructure.webpush;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ký JWT VAPID (RFC 8292) bằng ES256 và dựng header {@code Authorization}.
 *
 * <pre>
 *   Authorization: vapid t=&lt;JWT&gt;, k=&lt;khoa cong khai base64url&gt;
 * </pre>
 *
 * <p>Payload gồm ba claim, thiếu bất kỳ claim nào đều bị từ chối:</p>
 * <ul>
 *   <li>{@code aud} — <b>gốc của endpoint</b> ({@code scheme://host}), không phải cả URL. Sai claim
 *       này là nguyên nhân số một của lỗi 401 khi dựng Web Push lần đầu, và thông điệp lỗi của push
 *       service không nói gì về nó.</li>
 *   <li>{@code exp} — hạn dùng, tối đa 24 giờ theo chuẩn; ở đây mặc định 12 giờ để chịu được lệch
 *       đồng hồ giữa máy chủ và push service.</li>
 *   <li>{@code sub} — {@code mailto:} hoặc URL liên hệ của người vận hành.</li>
 * </ul>
 *
 * <p>JWT được ký <b>mới cho mỗi lần gửi</b>. Ký một lần rồi lưu lại thì phải quản lý vòng đời và
 * xử lý ca hết hạn giữa chừng; một phép ký ECDSA tốn cỡ chục micro-giây, rẻ hơn nhiều so với đoạn
 * mã quản lý bộ nhớ đệm ấy.</p>
 */
@Component
public class VapidSigner {

    private static final Logger log = LoggerFactory.getLogger(VapidSigner.class);

    /**
     * {@code inP1363Format} là phần bắt buộc: JOSE cần chữ ký {@code R||S} thô 64 byte, còn
     * {@code SHA256withECDSA} thường trả DER và push service sẽ trả 401.
     */
    private static final String ES256 = "SHA256withECDSAinP1363Format";

    private static final String JWT_HEADER_JSON = "{\"typ\":\"JWT\",\"alg\":\"ES256\"}";

    /**
     * @return {@code null} nếu chưa cấu hình khoá — bên gọi phải hiểu là "kênh Web Push đang tắt"
     */
    public String authorizationHeader(VapidKeyMaterial keys, String audience, String subject,
                                      Duration validity) {
        if (keys == null) {
            return null;
        }
        try {
            String token = sign(keys.privateKey(), audience, subject, validity);
            return "vapid t=" + token + ", k=" + keys.publicKeyBase64Url();
        } catch (GeneralSecurityException ex) {
            // Khoá sai định dạng là lỗi cấu hình, không phải lỗi mạng — retry vô ích.
            log.error("Khong ky duoc JWT VAPID (kiem tra lai GIAPHA_WEBPUSH_PRIVATE_KEY): {}",
                    ex.getMessage());
            return null;
        }
    }

    private String sign(ECPrivateKey privateKey, String audience, String subject, Duration validity)
            throws GeneralSecurityException {
        long expiresAt = Instant.now().plus(validity).getEpochSecond();
        String payloadJson = "{\"aud\":\"" + escape(audience)
                + "\",\"exp\":" + expiresAt
                + ",\"sub\":\"" + escape(subject) + "\"}";

        String signingInput = P256.encodeBase64Url(JWT_HEADER_JSON.getBytes(StandardCharsets.UTF_8))
                + "." + P256.encodeBase64Url(payloadJson.getBytes(StandardCharsets.UTF_8));

        Signature signature = Signature.getInstance(ES256);
        signature.initSign(privateKey);
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + P256.encodeBase64Url(signature.sign());
    }

    /**
     * Thoát chuỗi cho JSON thủ công. Ba claim ở đây đều là giá trị do người vận hành đặt hoặc suy
     * từ URL của endpoint, nên rủi ro thấp — nhưng một dấu nháy kép lọt vào cấu hình sẽ tạo ra một
     * JWT hỏng mà thông điệp lỗi 401 không giải thích được.
     */
    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
