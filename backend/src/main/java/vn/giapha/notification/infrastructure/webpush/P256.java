package vn.giapha.notification.infrastructure.webpush;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Base64;

/**
 * Tiện ích đường cong <b>P-256 (secp256r1)</b> cho Web Push — dựng bằng <b>JCE có sẵn trong JDK</b>,
 * không thêm thư viện.
 *
 * <h2>Vì sao tự viết thay vì kéo thư viện web-push</h2>
 * Toàn bộ thứ cần cho VAPID + RFC 8291 đều đã nằm trong JDK: {@code ECDH} để thoả thuận khoá,
 * {@code HmacSHA256} cho HKDF, {@code AES/GCM} cho mã hoá, và
 * {@code SHA256withECDSAinP1363Format} cho chữ ký ES256 <i>đúng định dạng JOSE</i>. Thư viện
 * {@code nl.martijndwars:web-push} kéo theo cả BouncyCastle và một client HTTP riêng — đó là một
 * phụ thuộc lớn cho khoảng hai trăm dòng mã, trong một kho Maven đang chạy ngoại tuyến.
 *
 * <h2>Hai cái bẫy của phần này</h2>
 * <ul>
 *   <li><b>Chữ ký ES256 phải là {@code R||S} thô 64 byte</b>, không phải DER. {@code SHA256withECDSA}
 *       mặc định của JDK trả DER và push service sẽ từ chối với 401 — một lỗi không có thông điệp
 *       nào chỉ đúng nguyên nhân. Hậu tố {@code inP1363Format} (JDK 9+) cho ra đúng định dạng.</li>
 *   <li><b>Toạ độ phải đệm đủ 32 byte.</b> {@code BigInteger.toByteArray()} có thể trả 31 byte (số
 *       nhỏ) hoặc 33 byte (thêm byte dấu 0x00). Nối thẳng vào là ra một điểm sai lệch vài byte, và
 *       phía nhận sẽ giải mã ra rác thay vì báo lỗi.</li>
 * </ul>
 */
final class P256 {

    static final String CURVE = "secp256r1";

    /** Độ dài toạ độ của P-256, tính bằng byte. */
    static final int COORDINATE_LENGTH = 32;

    /** Điểm dạng không nén: {@code 0x04 || X(32) || Y(32)}. */
    static final int UNCOMPRESSED_POINT_LENGTH = 1 + 2 * COORDINATE_LENGTH;

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private P256() {
    }

    static ECParameterSpec parameters() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(CURVE));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    static KeyPair generateEphemeralKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(CURVE));
        return generator.generateKeyPair();
    }

    /** Khoá công khai từ điểm không nén 65 byte (giá trị {@code p256dh} của trình duyệt). */
    static ECPublicKey decodePublicKey(byte[] uncompressedPoint) throws GeneralSecurityException {
        if (uncompressedPoint.length != UNCOMPRESSED_POINT_LENGTH || uncompressedPoint[0] != 0x04) {
            throw new GeneralSecurityException("Khoa cong khai P-256 phai la diem khong nen 65 byte"
                    + " bat dau bang 0x04, nhan duoc " + uncompressedPoint.length + " byte");
        }
        byte[] x = new byte[COORDINATE_LENGTH];
        byte[] y = new byte[COORDINATE_LENGTH];
        System.arraycopy(uncompressedPoint, 1, x, 0, COORDINATE_LENGTH);
        System.arraycopy(uncompressedPoint, 1 + COORDINATE_LENGTH, y, 0, COORDINATE_LENGTH);
        ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        return (ECPublicKey) KeyFactory.getInstance("EC")
                .generatePublic(new ECPublicKeySpec(point, parameters()));
    }

    /** Khoá riêng từ 32 byte vô hướng {@code d} — đúng định dạng khoá riêng VAPID. */
    static ECPrivateKey decodePrivateKey(byte[] scalar) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), parameters()));
    }

    /** Điểm không nén 65 byte của một khoá công khai. */
    static byte[] encodePublicKey(ECPublicKey key) {
        byte[] encoded = new byte[UNCOMPRESSED_POINT_LENGTH];
        encoded[0] = 0x04;
        copyCoordinate(key.getW().getAffineX(), encoded, 1);
        copyCoordinate(key.getW().getAffineY(), encoded, 1 + COORDINATE_LENGTH);
        return encoded;
    }

    /**
     * Chép một toạ độ vào đúng 32 byte, căn phải.
     *
     * <p>Đây là chỗ sửa cái bẫy đã nói ở javadoc lớp: {@code toByteArray()} có thể ngắn hơn (số nhỏ)
     * hoặc dài hơn một byte (byte dấu {@code 0x00} của số dương có bit cao bằng 1).</p>
     */
    private static void copyCoordinate(BigInteger coordinate, byte[] target, int offset) {
        byte[] raw = coordinate.toByteArray();
        int length = Math.min(raw.length, COORDINATE_LENGTH);
        int sourceOffset = raw.length - length;
        int targetOffset = offset + COORDINATE_LENGTH - length;
        System.arraycopy(raw, sourceOffset, target, targetOffset, length);
    }

    static byte[] decodeBase64Url(String value) {
        // Trình duyệt gửi base64url không padding; một số client vẫn kèm padding. Cả hai đều nhận.
        String normalized = value.trim().replace('+', '-').replace('/', '_');
        int padding = normalized.indexOf('=');
        if (padding >= 0) {
            normalized = normalized.substring(0, padding);
        }
        return URL_DECODER.decode(normalized);
    }

    static String encodeBase64Url(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }
}
