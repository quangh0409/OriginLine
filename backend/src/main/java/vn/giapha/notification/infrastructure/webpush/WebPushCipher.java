package vn.giapha.notification.infrastructure.webpush;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Mã hoá payload Web Push theo <b>RFC 8291</b> với mã hoá nội dung <b>{@code aes128gcm}</b>
 * (RFC 8188).
 *
 * <h2>Vì sao payload phải được mã hoá</h2>
 * Push service (Google, Mozilla, Apple) chỉ là bên chuyển tiếp và <b>không được phép đọc nội dung</b>.
 * Với hệ thống này, nội dung là tên các cụ trong họ và ngày giỗ — dữ liệu cá nhân theo Nghị định
 * 13/2023. Không mã hoá thì buộc phải gửi tin rỗng và bắt client gọi ngược về, tức là mất luôn khả
 * năng hiển thị thông báo khi ứng dụng chưa mở.
 *
 * <h2>Trình tự dẫn xuất khoá — sai một byte là phía nhận ra rác</h2>
 * <pre>
 *   ecdh_secret = ECDH(khoa_rieng_tam_thoi, p256dh_cua_client)
 *   PRK_key     = HMAC-SHA256(key = auth_secret, msg = ecdh_secret)
 *   key_info    = "WebPush: info" || 0x00 || p256dh_client || khoa_cong_khai_tam_thoi
 *   IKM         = HKDF-Expand(PRK_key, key_info, 32)
 *   PRK         = HMAC-SHA256(key = salt, msg = IKM)
 *   CEK         = HKDF-Expand(PRK, "Content-Encoding: aes128gcm" || 0x00, 16)
 *   NONCE       = HKDF-Expand(PRK, "Content-Encoding: nonce"     || 0x00, 12)
 * </pre>
 *
 * <p>Thân bản tin theo RFC 8188:
 * {@code salt(16) || rs(4) || idlen(1) || khoa_cong_khai_tam_thoi(65) || ciphertext}.</p>
 *
 * <p><b>Bẫy:</b> bản rõ phải được nối thêm một byte {@code 0x02} — dấu hiệu "bản ghi cuối" của
 * RFC 8188. Thiếu byte này thì trình duyệt giải mã thành công nhưng vứt bỏ bản ghi, và
 * {@code push} event không bao giờ nổ ra: một lỗi hoàn toàn im lặng ở cả hai đầu.</p>
 *
 * <p>Khoá tạm thời được sinh <b>mới cho mỗi tin</b> — đó là yêu cầu của RFC 8291, không phải tuỳ
 * chọn: dùng lại khoá tạm thời làm mất tính bí mật chuyển tiếp giữa các tin.</p>
 */
@Component
public class WebPushCipher {

    /** Kích thước bản ghi khai báo trong header; payload nhắc giỗ luôn nhỏ hơn nhiều. */
    private static final int RECORD_SIZE = 4096;

    private static final int SALT_LENGTH = 16;
    private static final int CEK_LENGTH = 16;
    private static final int NONCE_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private static final byte[] KEY_INFO_PREFIX = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);

    /** Dấu hiệu "bản ghi cuối" của RFC 8188. Xem cảnh báo ở javadoc lớp. */
    private static final byte LAST_RECORD_DELIMITER = 0x02;

    private final SecureRandom random = new SecureRandom();

    /**
     * @param payload    nội dung UTF-8 (JSON) gửi tới service worker
     * @param p256dh     khoá công khai của client, base64url
     * @param authSecret bí mật xác thực của client, base64url
     * @return thân bản tin đã mã hoá, gửi kèm {@code Content-Encoding: aes128gcm}
     */
    public byte[] encrypt(byte[] payload, String p256dh, String authSecret)
            throws GeneralSecurityException {
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return encrypt(payload, p256dh, authSecret, P256.generateEphemeralKeyPair(), salt);
    }

    /**
     * Biến thể <b>tất định</b>: khoá tạm thời và salt được truyền vào.
     *
     * <p>Tồn tại vì một lý do duy nhất, và là lý do đáng giá: chỉ với biến thể này mới đối chiếu
     * được kết quả với <b>bộ số mẫu ở RFC 8291 §5</b>. Không có nó thì phép mã hoá chỉ kiểm được
     * bằng vòng lặp tự mã hoá rồi tự giải mã — một phép thử luôn xanh kể cả khi cả hai chiều cùng
     * sai so với chuẩn, và triệu chứng thật sẽ là "trình duyệt nhận tin nhưng sự kiện push không bao
     * giờ nổ ra", không có lỗi ở bất kỳ đâu.</p>
     *
     * <p>Phạm vi package: không phải API để gọi trong sản xuất. Dùng lại khoá tạm thời giữa các tin
     * làm mất tính bí mật chuyển tiếp mà RFC 8291 yêu cầu.</p>
     */
    byte[] encrypt(byte[] payload, String p256dh, String authSecret, KeyPair ephemeral, byte[] salt)
            throws GeneralSecurityException {
        ECPublicKey clientKey = P256.decodePublicKey(P256.decodeBase64Url(p256dh));
        byte[] clientKeyBytes = P256.encodePublicKey(clientKey);
        byte[] auth = P256.decodeBase64Url(authSecret);

        byte[] ephemeralPublic = P256.encodePublicKey((ECPublicKey) ephemeral.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ephemeral.getPrivate());
        agreement.doPhase(clientKey, true);
        byte[] sharedSecret = agreement.generateSecret();

        byte[] prkKey = hmac(auth, sharedSecret);
        byte[] keyInfo = concat(KEY_INFO_PREFIX, clientKeyBytes, ephemeralPublic);
        byte[] ikm = hkdfExpand(prkKey, keyInfo, 32);
        byte[] prk = hmac(salt, ikm);
        byte[] contentEncryptionKey = hkdfExpand(prk, CEK_INFO, CEK_LENGTH);
        byte[] nonce = hkdfExpand(prk, NONCE_INFO, NONCE_LENGTH);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentEncryptionKey, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, nonce));
        byte[] ciphertext = cipher.doFinal(concat(payload, new byte[] {LAST_RECORD_DELIMITER}));

        ByteBuffer body = ByteBuffer.allocate(SALT_LENGTH + 4 + 1 + ephemeralPublic.length + ciphertext.length);
        body.put(salt);
        body.putInt(RECORD_SIZE);
        body.put((byte) ephemeralPublic.length);
        body.put(ephemeralPublic);
        body.put(ciphertext);
        return body.array();
    }

    /** HKDF-Expand rút gọn: mọi độ dài cần ở đây đều ≤ 32 byte nên chỉ cần đúng một vòng lặp. */
    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        byte[] block = hmac(prk, concat(info, new byte[] {0x01}));
        byte[] result = new byte[length];
        System.arraycopy(block, 0, result, 0, length);
        return result;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
