package vn.giapha.notification.infrastructure.webpush;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/**
 * Sinh một cặp khoá VAPID (P-256, base64url không đệm) cho <b>một môi trường</b>.
 *
 * <h2>Vì sao là một lớp có {@code main} chứ không phải một endpoint</h2>
 * Sinh khoá là việc của <b>người vận hành</b>, làm một lần cho mỗi môi trường, trước khi ứng dụng
 * khởi động. Đặt nó sau một endpoint HTTP thì máy chủ phải có chỗ ghi khoá riêng xuống — và chỗ đó
 * (cơ sở dữ liệu, tệp cấu hình, bản sao lưu) chính là thứ mà cả thiết kế này đang tránh. Ở đây khoá
 * riêng chỉ đi từ bộ nhớ ra màn hình của người vận hành, rồi vào kho bí mật của họ.
 *
 * <h2>In ra {@code System.out}, cố ý, không dùng SLF4J</h2>
 * Quy ước của dự án là dùng SLF4J thay cho {@code System.out} — chỗ này là ngoại lệ có chủ đích:
 * mọi thứ đi qua logger đều chảy vào tệp log và từ đó lên Loki. Một khoá riêng Web Push nằm trong
 * kho log tập trung là đã lộ, và không rút lại được. Công cụ dòng lệnh thì đầu ra <i>là</i> giao
 * diện của nó; khoá dừng lại ở terminal của người chạy.
 *
 * <h2>Cách chạy</h2>
 * <pre>
 *   cd backend
 *   ./mvnw -o -q compile
 *   java -cp target/classes vn.giapha.notification.infrastructure.webpush.VapidKeyGenerator
 * </pre>
 *
 * <p>Cặp khoá sinh ra ở máy lập trình viên là <b>khoá của máy phát triển</b>. Mỗi môi trường
 * (dev / staging / production) phải có cặp khoá riêng: dùng chung nghĩa là một bản dev chạy trên
 * laptop có thể đẩy thông báo tới điện thoại thật của người trong dòng họ.</p>
 *
 * <p><b>Đổi khoá là mất toàn bộ đăng ký hiện có.</b> Trình duyệt gắn subscription với đúng khoá công
 * khai đã dùng lúc {@code subscribe()}; khoá mới thì mọi bản ghi cũ trong {@code push_subscription}
 * sẽ bị push service trả 403 và phải đăng ký lại. Sinh một lần rồi giữ, đừng sinh lại cho vui.</p>
 */
public final class VapidKeyGenerator {

    private VapidKeyGenerator() {
    }

    /**
     * Cặp khoá đã mã hoá base64url, sẵn sàng gán vào biến môi trường.
     *
     * @param publicKey  65 byte điểm không nén (~87 ký tự) — công khai, được trả qua
     *                   {@code GET /api/v1/push/public-key}
     * @param privateKey 32 byte vô hướng {@code d} (~43 ký tự) — <b>bí mật</b>
     */
    public record CapKhoaVapid(String publicKey, String privateKey) {
    }

    /** Sinh cặp khoá mới bằng đúng {@link P256} mà đường gửi thật đang dùng. */
    public static CapKhoaVapid sinhCapKhoa() throws GeneralSecurityException {
        KeyPair pair = P256.generateEphemeralKeyPair();
        String publicKey = P256.encodeBase64Url(P256.encodePublicKey((ECPublicKey) pair.getPublic()));
        String privateKey = P256.encodeBase64Url(voHuong32Byte((ECPrivateKey) pair.getPrivate()));
        return new CapKhoaVapid(publicKey, privateKey);
    }

    /**
     * Vô hướng {@code d} đệm đủ 32 byte.
     *
     * <p>Không đệm thì {@code BigInteger.toByteArray()} trả 31 byte (số nhỏ) hoặc 33 byte (byte dấu
     * {@code 0x00}), và khoá sinh ra sẽ bị {@code VapidKeyMaterial} từ chối ngay lúc khởi động — đúng
     * như thiết kế, nhưng người vận hành sẽ tưởng công cụ hỏng.</p>
     */
    private static byte[] voHuong32Byte(ECPrivateKey key) {
        byte[] raw = key.getS().toByteArray();
        byte[] scalar = new byte[P256.COORDINATE_LENGTH];
        int length = Math.min(raw.length, P256.COORDINATE_LENGTH);
        System.arraycopy(raw, raw.length - length, scalar, P256.COORDINATE_LENGTH - length, length);
        return scalar;
    }

    @SuppressWarnings("java:S106") // System.out là chủ đích: khoá riêng không được chạm tới logger.
    public static void main(String[] args) throws GeneralSecurityException {
        CapKhoaVapid cap = sinhCapKhoa();
        System.out.println("# ===================================================================");
        System.out.println("# Cap khoa VAPID moi - KHOA CUA MAY PHAT TRIEN, khong phai khoa that.");
        System.out.println("# Dan hai dong duoi vao bien moi truong cua tien trinh backend.");
        System.out.println("# KHONG commit, KHONG ghi vao application.yml, KHONG dan vao chat.");
        System.out.println("# ===================================================================");
        System.out.println("GIAPHA_WEBPUSH_PUBLIC_KEY=" + cap.publicKey());
        System.out.println("GIAPHA_WEBPUSH_PRIVATE_KEY=" + cap.privateKey());
    }
}
