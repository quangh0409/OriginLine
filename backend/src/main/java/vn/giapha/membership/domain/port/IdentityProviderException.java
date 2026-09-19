package vn.giapha.membership.domain.port;

/**
 * Nhà cung cấp danh tính không làm được việc được giao — mạng hỏng, Keycloak chết, cấu hình sai,
 * hoặc realm từ chối.
 *
 * <p>Đây là ngoại lệ <b>hạ tầng</b>, cố ý tách khỏi mọi ngoại lệ nghiệp vụ: nó nghĩa là "chưa biết,
 * thử lại sau", không phải "người dùng làm sai". Tầng {@code api} ánh xạ nó thành
 * {@code 503 IDENTITY_PROVIDER_UNAVAILABLE} — và quan trọng nhất, khi nó bay ra thì <b>mã mời chưa
 * bị đánh dấu đã dùng</b>, nên người dùng bấm lại được.</p>
 */
public class IdentityProviderException extends RuntimeException {

    public IdentityProviderException(String message) {
        super(message);
    }

    public IdentityProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
