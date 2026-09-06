package vn.giapha.notification.infrastructure.webpush;

import java.security.GeneralSecurityException;
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

    /**
     * @return {@code null} nếu chưa cấu hình đủ khoá — kênh Web Push tự tắt, ứng dụng vẫn chạy
     * @throws GeneralSecurityException khi khoá có nhưng sai định dạng: đó là lỗi cấu hình phải nổ
     *                                  ra lúc khởi động, không phải lúc đang gửi giữa mùa giỗ
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
        return new VapidKeyMaterial(P256.decodePrivateKey(privateScalar),
                P256.encodeBase64Url(publicPoint));
    }
}
