package vn.giapha.membership.infrastructure.keycloak;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Đúc và kiểm token của liên kết đặt mật khẩu — <b>ký HMAC-SHA256, không lưu ở đâu cả</b>.
 *
 * <h2>Vì sao token KHÔNG có bảng lưu trữ</h2>
 * Cách quen thuộc là sinh một chuỗi ngẫu nhiên, lưu băm vào một bảng rồi xoá khi dùng — đúng như
 * {@code invitation.code_hash}. Ở đây cố ý <b>không</b> làm thế, vì tính "một lần" của liên kết đặt
 * mật khẩu đã có sẵn một nguồn chân lý tốt hơn: <b>tài khoản đã có mật khẩu hay chưa</b>, hỏi thẳng
 * Keycloak. Một bảng cờ song song chỉ thêm một thứ có thể lệch khỏi sự thật — và lệch theo hướng
 * nguy hiểm: cờ "chưa dùng" còn sót trên một tài khoản đã có mật khẩu là một lối đổi mật khẩu của
 * người khác.
 *
 * <p>Hệ quả kèm theo là không cần migration nào, và liên kết vẫn kiểm được sau khi backend khởi
 * động lại.</p>
 *
 * <h2>Khoá ký dẫn xuất từ bí mật của client dịch vụ</h2>
 * {@code HMAC(clientSecret, "giapha:set-password-link:v1")} — phân tách miền để khoá ký không bao
 * giờ trùng với chính bí mật gốc, nhưng vẫn chỉ có <b>một</b> biến môi trường phải quản lý. Xem
 * javadoc {@link KeycloakAdminProperties} về lựa chọn này.
 *
 * <p>Token mang đúng hai thứ: {@code subject} của tài khoản Keycloak và hạn. Không mang email,
 * không mang khoá nhân khẩu, không mang vai trò — nó đi qua thanh địa chỉ trình duyệt.</p>
 */
public final class SetPasswordTokenCodec {

    /** Chuỗi phân tách miền; đổi nó là làm mọi liên kết đang lưu hành hết hiệu lực. */
    private static final String DOMAIN_SEPARATION = "giapha:set-password-link:v1";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] signingKey;

    public SetPasswordTokenCodec(String clientSecret) {
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException(
                    "Khong dan xuat duoc khoa ky khi chua co bi mat client dich vu");
        }
        this.signingKey = hmac(clientSecret.getBytes(StandardCharsets.UTF_8),
                DOMAIN_SEPARATION.getBytes(StandardCharsets.UTF_8));
    }

    /** Token cho một {@code subject}, hết hạn sau {@code ttl}. */
    public Minted mint(String subject, Duration ttl, Instant now) {
        Instant expiresAt = now.plus(ttl);
        String payload = subject + ":" + expiresAt.getEpochSecond();
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String token = ENCODER.encodeToString(payloadBytes) + "."
                + ENCODER.encodeToString(hmac(signingKey, payloadBytes));
        return new Minted(token, expiresAt);
    }

    /**
     * Trả {@code subject} nếu token đúng chữ ký và còn hạn, ngược lại {@link Optional#empty()}.
     *
     * <p>Mọi lý do hỏng — sai định dạng, sai chữ ký, quá hạn — trả về <b>cùng một kết quả rỗng</b>.
     * Phân biệt chúng cho người gọi là nói cho kẻ dò biết mình đang sai ở đâu.</p>
     */
    public Optional<String> verify(String token, Instant now) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        byte[] payloadBytes;
        byte[] signature;
        try {
            payloadBytes = DECODER.decode(token.substring(0, dot));
            signature = DECODER.decode(token.substring(dot + 1));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        // So sanh HANG SO THOI GIAN: so sanh thuong roi ro ra do dai tien to dung qua thoi gian dap.
        if (!MessageDigest.isEqual(signature, hmac(signingKey, payloadBytes))) {
            return Optional.empty();
        }
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        int separator = payload.lastIndexOf(':');
        if (separator <= 0) {
            return Optional.empty();
        }
        long expiresAtEpochSecond;
        try {
            expiresAtEpochSecond = Long.parseLong(payload.substring(separator + 1));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        if (now.getEpochSecond() >= expiresAtEpochSecond) {
            return Optional.empty();
        }
        return Optional.of(payload.substring(0, separator));
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (java.security.GeneralSecurityException ex) {
            // HmacSHA256 la thuat toan bat buoc cua moi ban JRE — khong bao gio toi day.
            throw new IllegalStateException("JRE khong co HmacSHA256", ex);
        }
    }

    /**
     * Token vừa đúc kèm hạn của chính nó.
     *
     * @param token     chuỗi đặt vào địa chỉ liên kết
     * @param expiresAt hạn, để giao diện nói được "liên kết có hiệu lực tới ..."
     */
    public record Minted(String token, Instant expiresAt) {
    }
}
