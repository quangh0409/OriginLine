package vn.giapha.notification.infrastructure.webpush;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;

/**
 * Cặp khoá VAPID đã giải mã sẵn.
 *
 * <p>Giải mã <b>một lần lúc khởi động</b> thay vì mỗi lần gửi: giải mã khoá EC là thao tác đắt, và
 * mùa giỗ có thể gửi vài nghìn tin trong ít phút.</p>
 *
 * <p>{@code publicKeyBase64Url} giữ nguyên đúng chuỗi trong cấu hình (chỉ chuẩn hoá bỏ padding) vì
 * nó xuất hiện trong header {@code Authorization: vapid ... k=} và trong
 * {@code GET /api/v1/push/public-key} — hai nơi này <b>phải khớp từng ký tự</b> với khoá mà service
 * worker truyền vào {@code applicationServerKey}, nếu không push service sẽ từ chối.</p>
 *
 * <p>Bản ghi này cố ý <b>không có</b> {@code toString} sinh tự động lộ khoá riêng: nó chỉ giữ đối
 * tượng {@link ECPrivateKey}, và {@code toString} của khoá EC trong JDK không in vô hướng {@code d}.
 * Dù vậy, đừng bao giờ ghi log đối tượng này.</p>
 */
public record VapidKeyMaterial(ECPrivateKey privateKey, String publicKeyBase64Url) {

    /** Cùng thuật toán mà {@link VapidSigner} dùng để ký JWT — phép tự kiểm phải trùng đường thật. */
    private static final String THUAT_TOAN_KY = "SHA256withECDSAinP1363Format";

    /**
     * @return {@code null} nếu chưa cấu hình đủ khoá — kênh Web Push tự tắt, ứng dụng vẫn chạy
     * @throws GeneralSecurityException khi khoá có nhưng <b>sai định dạng</b>, hoặc hai khoá
     *                                  <b>không cùng một cặp</b> (xem
     *                                  {@link #kiemTraCungMotCap}): cả hai đều là lỗi cấu hình phải
     *                                  nổ ra lúc khởi động, không phải lúc đang gửi giữa mùa giỗ
     */
    public static VapidKeyMaterial from(WebPushProperties properties) throws GeneralSecurityException {
        if (!properties.isConfigured()) {
            return null;
        }
        byte[] privateScalar = P256.decodeBase64Url(properties.getPrivateKey());
        if (privateScalar.length != P256.COORDINATE_LENGTH) {
            throw new GeneralSecurityException("Khoa rieng VAPID phai la 32 byte base64url, nhan duoc "
                    + privateScalar.length + " byte");
        }
        byte[] publicPoint = P256.decodeBase64Url(properties.getPublicKey());
        if (publicPoint.length != P256.UNCOMPRESSED_POINT_LENGTH) {
            throw new GeneralSecurityException("Khoa cong khai VAPID phai la 65 byte base64url,"
                    + " nhan duoc " + publicPoint.length + " byte");
        }
        ECPrivateKey khoaRieng = P256.decodePrivateKey(privateScalar);
        kiemTraCungMotCap(khoaRieng, publicPoint);
        return new VapidKeyMaterial(khoaRieng, P256.encodeBase64Url(publicPoint));
    }

    /**
     * Hai khoá phải là <b>cùng một cặp</b>.
     *
     * <h2>Vì sao phải kiểm, và vì sao phải kiểm ở đây</h2>
     * Sinh khoá hai lần rồi dán nhầm (khoá công khai của lần này, khoá riêng của lần kia) là lỗi
     * cấu hình dễ mắc nhất và <b>khó thấy nhất</b>: cả hai chuỗi đều đúng độ dài, đúng base64url,
     * ứng dụng khởi động sạch, {@code GET /api/v1/push/public-key} trả 200 và giao diện hiện công
     * tắc bật được. Chỉ tới lượt gửi thật mới vỡ — push service so khoá trong header
     * {@code Authorization: vapid ... k=} với chữ ký JWT, thấy lệch, và trả 401 cho <i>mọi</i>
     * thiết bị. Tức là kênh đẩy đã chết từ lúc khởi động nhưng chỉ lộ ra vào ngày giỗ.
     *
     * <p>Phép kiểm chỉ là ký một chuỗi cố định rồi tự xác minh bằng khoá công khai đã cấu hình:
     * chữ ký ECDSA chỉ xác minh được bằng đúng khoá công khai sinh cùng cặp. Tốn cỡ vài trăm
     * micro-giây, một lần, lúc khởi động — đổi lấy việc lỗi cấu hình <b>kêu to ngay</b> thay vì
     * biến thành một loạt 401 im lặng.</p>
     *
     * @throws GeneralSecurityException khi hai khoá không cùng cặp — ứng dụng dừng khởi động
     */
    private static void kiemTraCungMotCap(ECPrivateKey khoaRieng, byte[] publicPoint)
            throws GeneralSecurityException {
        byte[] mauThu = "giapha-vapid-selftest".getBytes(StandardCharsets.US_ASCII);

        Signature ky = Signature.getInstance(THUAT_TOAN_KY);
        ky.initSign(khoaRieng);
        ky.update(mauThu);
        byte[] chuKy = ky.sign();

        Signature xacMinh = Signature.getInstance(THUAT_TOAN_KY);
        xacMinh.initVerify(P256.decodePublicKey(publicPoint));
        xacMinh.update(mauThu);
        if (!xacMinh.verify(chuKy)) {
            throw new GeneralSecurityException(
                    "GIAPHA_WEBPUSH_PUBLIC_KEY va GIAPHA_WEBPUSH_PRIVATE_KEY KHONG cung mot cap khoa."
                            + " Push service se tra 401 cho moi thiet bi. Sinh lai ca hai bang:"
                            + " java -cp target/classes"
                            + " vn.giapha.notification.infrastructure.webpush.VapidKeyGenerator");
        }
    }
}
