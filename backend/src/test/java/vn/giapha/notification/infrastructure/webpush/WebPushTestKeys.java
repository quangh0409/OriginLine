package vn.giapha.notification.infrastructure.webpush;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/**
 * Sinh cặp khoá VAPID <b>thật</b> cho test.
 *
 * <p>Cố ý sinh mới mỗi lần chạy thay vì gài một cặp khoá cố định vào mã nguồn: một khoá riêng Web
 * Push cho phép đẩy thông báo tới điện thoại thật của người trong họ, và mọi thứ lọt vào lịch sử
 * Git là lọt vĩnh viễn. {@code WebPushPropertiesTest} đang canh đúng điều này.</p>
 *
 * <p>Lớp này {@code public} vì {@link P256} chỉ mở trong phạm vi package, còn test tích hợp nằm ở
 * {@code vn.giapha.integration}.</p>
 */
public final class WebPushTestKeys {

    private WebPushTestKeys() {
    }

    /** Cấu hình Web Push đã bật, khoá sinh ngẫu nhiên, chủ thể {@code mailto:} hợp lệ. */
    public static WebPushProperties cauHinhVapidMoi() throws GeneralSecurityException {
        KeyPair pair = P256.generateEphemeralKeyPair();
        WebPushProperties properties = new WebPushProperties();
        properties.setPublicKey(P256.encodeBase64Url(
                P256.encodePublicKey((ECPublicKey) pair.getPublic())));
        properties.setPrivateKey(P256.encodeBase64Url(
                voHuong32Byte((ECPrivateKey) pair.getPrivate())));
        properties.setSubject("mailto:toc-truong@giapha.vn");
        return properties;
    }

    /** Vô hướng {@code d}, đệm đủ 32 byte — đúng định dạng khoá riêng VAPID của trình duyệt. */
    private static byte[] voHuong32Byte(ECPrivateKey key) {
        byte[] raw = key.getS().toByteArray();
        byte[] out = new byte[32];
        int length = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - length, out, 32 - length, length);
        return out;
    }
}
