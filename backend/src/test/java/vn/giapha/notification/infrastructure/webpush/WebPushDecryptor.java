package vn.giapha.notification.infrastructure.webpush;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * <b>Phía nhận</b> của Web Push (RFC 8291) — chỉ dùng trong test.
 *
 * <h2>Vì sao phải viết lại một lần nữa thay vì gọi ngược {@link WebPushCipher}</h2>
 * Kiểm một bộ mã hoá bằng chính nó ("mã hoá rồi giải mã, thấy khớp") <b>luôn xanh</b> kể cả khi cả
 * hai chiều cùng lệch chuẩn — và triệu chứng thật của lệch chuẩn ở Web Push là trình duyệt <i>nhận
 * được gói tin nhưng sự kiện {@code push} không bao giờ nổ ra</i>, không một dòng lỗi nào ở bất kỳ
 * đâu. Vì vậy lớp này được viết độc lập, chỉ dựa vào JCE của JDK, và bản thân nó
 * <b>được neo vào bộ số mẫu RFC 8291 §5</b> trong
 * {@code WebPushCipherRfc8291Test#giaiMaDuocChinhGoiTinMauCuaRfc()}: giải đúng gói tin mẫu của RFC
 * ra đúng câu "When I grow up, I want to be a watermelon". Chỉ sau phép neo ấy nó mới đủ tư cách làm
 * trọng tài cho các gói tin do mã sản xuất sinh ra bằng khoá ngẫu nhiên.
 *
 * <p>Lớp này đóng vai <b>trình duyệt của người trong họ</b>: nó giữ cặp khoá client
 * ({@code p256dh} + {@code auth}) đúng như {@code PushManager.subscribe()} sinh ra, và mở được
 * đúng những gói tin mà máy chủ đẩy chuyển tiếp tới.</p>
 */
public final class WebPushDecryptor {

    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final byte[] KEY_INFO_PREFIX = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);

    /** Dấu hiệu "bản ghi cuối" của RFC 8188 — thiếu nó thì trình duyệt vứt bản ghi trong im lặng. */
    static final byte LAST_RECORD_DELIMITER = 0x02;

    private final ECPrivateKey privateKey;
    private final byte[] publicKey;
    private final byte[] authSecret;

    private WebPushDecryptor(ECPrivateKey privateKey, byte[] publicKey, byte[] authSecret) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.authSecret = authSecret;
    }

    /** Một "thiết bị" mới: cặp khoá P-256 ngẫu nhiên + bí mật xác thực 16 byte, như trình duyệt. */
    public static WebPushDecryptor thietBiMoi() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        return new WebPushDecryptor((ECPrivateKey) pair.getPrivate(),
                uncompressed((ECPublicKey) pair.getPublic()), auth);
    }

    /** Thiết bị dựng từ khoá cho sẵn — dùng để dựng lại đúng bên nhận của bộ số mẫu RFC 8291. */
    public static WebPushDecryptor tuKhoa(String privateScalarB64, String publicPointB64, String authB64)
            throws GeneralSecurityException {
        ECPrivateKey key = (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(
                new ECPrivateKeySpec(new BigInteger(1, DECODER.decode(privateScalarB64)), curve()));
        return new WebPushDecryptor(key, DECODER.decode(publicPointB64), DECODER.decode(authB64));
    }

    /** Giá trị {@code keys.p256dh} mà trình duyệt gửi lên khi đăng ký. */
    public String p256dh() {
        return ENCODER.encodeToString(publicKey);
    }

    /** Giá trị {@code keys.auth} mà trình duyệt gửi lên khi đăng ký. */
    public String auth() {
        return ENCODER.encodeToString(authSecret);
    }

    /**
     * Giải mã một thân bản tin {@code aes128gcm} và trả về bản rõ <b>đã bỏ</b> byte đệm cuối.
     *
     * @throws GeneralSecurityException khi thẻ GCM sai (khoá lệch), hoặc thiếu byte {@code 0x02} —
     *                                  đúng hai kiểu hỏng mà trình duyệt thật sẽ nuốt trong im lặng
     */
    public byte[] giaiMa(byte[] body) throws GeneralSecurityException {
        if (body.length < 16 + 4 + 1) {
            throw new GeneralSecurityException("Than ban tin ngan hon ca header RFC 8188");
        }
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        int recordSize = buffer.getInt();
        int keyIdLength = Byte.toUnsignedInt(buffer.get());
        byte[] senderPublic = new byte[keyIdLength];
        buffer.get(senderPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        if (ciphertext.length > recordSize) {
            throw new GeneralSecurityException("Ban ghi " + ciphertext.length
                    + " byte vuot qua rs=" + recordSize + " da khai bao");
        }

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(decodePoint(senderPublic), true);
        byte[] shared = agreement.generateSecret();

        byte[] prkKey = hmac(authSecret, shared);
        byte[] ikm = hkdfExpand(prkKey, concat(KEY_INFO_PREFIX, publicKey, senderPublic), 32);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = hkdfExpand(prk, CEK_INFO, 16);
        byte[] nonce = hkdfExpand(prk, NONCE_INFO, 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"),
                new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);

        int end = padded.length - 1;
        while (end >= 0 && padded[end] == 0) {
            end--;
        }
        if (end < 0 || padded[end] != LAST_RECORD_DELIMITER) {
            throw new GeneralSecurityException("Thieu byte 0x02 danh dau ban ghi cuoi (RFC 8188)."
                    + " Trinh duyet se vut ban ghi nay ma khong bao loi.");
        }
        byte[] plaintext = new byte[end];
        System.arraycopy(padded, 0, plaintext, 0, end);
        return plaintext;
    }

    public String giaiMaThanhChuoi(byte[] body) throws GeneralSecurityException {
        return new String(giaiMa(body), StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------------------------------
    // JCE thuần — cố ý KHÔNG gọi P256 để phép kiểm không tự chứng minh chính nó
    // ---------------------------------------------------------------------------------------

    private static ECParameterSpec curve() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private static ECPublicKey decodePoint(byte[] point) throws GeneralSecurityException {
        if (point.length != 65 || point[0] != 0x04) {
            throw new GeneralSecurityException("Khoa cong khai cua ben gui khong phai diem 65 byte");
        }
        byte[] x = new byte[32];
        byte[] y = new byte[32];
        System.arraycopy(point, 1, x, 0, 32);
        System.arraycopy(point, 33, y, 0, 32);
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(
                new ECPublicKeySpec(new ECPoint(new BigInteger(1, x), new BigInteger(1, y)), curve()));
    }

    private static byte[] uncompressed(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        put(key.getW().getAffineX(), out, 1);
        put(key.getW().getAffineY(), out, 33);
        return out;
    }

    private static void put(BigInteger coordinate, byte[] target, int offset) {
        byte[] raw = coordinate.toByteArray();
        int length = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - length, target, offset + 32 - length, length);
    }

    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length)
            throws GeneralSecurityException {
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
