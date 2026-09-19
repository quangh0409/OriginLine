package vn.giapha.membership.infrastructure.keycloak;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình tài khoản dịch vụ dùng Keycloak Admin REST API, tiền tố {@code giapha.keycloak.admin}.
 *
 * <h2>Bí mật của client dịch vụ CHỈ đến từ biến môi trường</h2>
 * {@link #clientSecret} <b>không có giá trị mặc định</b> và <b>không</b> được ghi vào bất kỳ tệp
 * nào trong kho mã. Nhờ ràng buộc tên lỏng của Spring Boot, nó đọc thẳng từ
 * {@code GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET} mà không cần một dòng nào trong
 * {@code application.yml} — đúng khuôn mẫu đã dùng cho khoá riêng VAPID
 * ({@code GIAPHA_WEBPUSH_PRIVATE_KEY}), và là lý do chọn {@code @ConfigurationProperties} thay vì
 * {@code @Value} có mặc định.
 *
 * <p>Bí mật này mở quyền <b>tạo người dùng trong realm</b>. Một chuỗi như thế lọt vào lịch sử Git
 * là lọt vĩnh viễn: xoá ở commit sau không xoá được ở commit trước, và mọi bản clone đều đã có.
 * {@code KeycloakAdminPropertiesTest} canh điều này và sẽ làm đổ bộ test nếu
 * {@code application.yml} xuất hiện khoá bí mật.</p>
 *
 * <h2>Khoá ký liên kết đặt mật khẩu được DẪN XUẤT, không phải một bí mật thứ hai</h2>
 * Token trong {@code setPasswordUrl} được ký HMAC-SHA256 bằng một khoá dẫn xuất từ chính
 * {@link #clientSecret} với một chuỗi phân tách miền (xem {@link SetPasswordTokenCodec}). Cố ý
 * <b>không</b> thêm một biến môi trường thứ hai: mỗi bí mật phải xoay vòng thêm là một bí mật nữa
 * có thể bị quên, và ở đây hai thứ có cùng vòng đời — mất quyền gọi Admin API thì liên kết đặt mật
 * khẩu cũng vô nghĩa. Đổi bí mật làm mọi liên kết đang lưu hành hết hiệu lực; với hạn
 * {@link #linkTtl} thì đó là chuyện của nửa giờ.
 *
 * <p>Thiếu cấu hình thì cổng danh tính <b>tự tắt</b> chứ không làm hỏng ứng dụng: lối nhận lời mời
 * bằng token sẵn có vẫn chạy, chỉ lối "người chưa có tài khoản" là trả 503.</p>
 */
@Component
@ConfigurationProperties(prefix = "giapha.keycloak.admin")
public class KeycloakAdminProperties {

    /** Tắt tay cổng danh tính mà không phải xoá bí mật. */
    private boolean enabled = true;

    /** Gốc của máy chủ Keycloak, không kèm {@code /realms/...}. */
    private String serverUrl = "http://localhost:8081";

    /** Realm chứa người trong dòng họ. */
    private String realm = "giapha";

    /**
     * Client dịch vụ. <b>KHÔNG</b> dùng lại {@code giapha-backend}: nó là audience của mọi token
     * người dùng, và gắn thêm quyền quản trị realm vào đó là gộp hai mức rủi ro vào một bí mật.
     */
    private String clientId = "giapha-provisioner";

    /** CHỈ đọc từ {@code GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET}. Không mặc định, không ghi vào repo. */
    private String clientSecret;

    /**
     * Khuôn địa chỉ của liên kết đặt mật khẩu; {@code {token}} là chỗ token được chèn vào.
     *
     * <p>Trỏ về <b>frontend</b> chứ không về API: thứ người dùng bấm phải là một trang có ô nhập
     * mật khẩu, và trang ấy mới gọi {@code POST /api/v1/invitations/set-password}.</p>
     */
    private String setPasswordUrl = "http://localhost:3000/vi/dat-mat-khau?token={token}";

    /**
     * Hạn của liên kết đặt mật khẩu.
     *
     * <p>Ngắn có chủ ý. Liên kết này được trả ngay trong phản hồi của lệnh nhận lời mời nên người
     * dùng đang ngồi trước màn hình; nửa giờ là rộng rãi, còn một liên kết sống nhiều ngày là một
     * bí mật nằm trong lịch sử trình duyệt suốt nhiều ngày.</p>
     */
    private Duration linkTtl = Duration.ofMinutes(30);

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration requestTimeout = Duration.ofSeconds(10);

    /** Đủ bí mật để gọi Admin API hay chưa. */
    public boolean isConfigured() {
        return enabled
                && serverUrl != null && !serverUrl.isBlank()
                && realm != null && !realm.isBlank()
                && clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    /** Gốc của Admin REST API cho realm đang dùng. */
    public String adminRealmUri() {
        return trimmedServerUrl() + "/admin/realms/" + realm;
    }

    /** Endpoint phát token cho luồng {@code client_credentials}. */
    public String tokenUri() {
        return trimmedServerUrl() + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    private String trimmedServerUrl() {
        String value = serverUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getSetPasswordUrl() {
        return setPasswordUrl;
    }

    public void setSetPasswordUrl(String setPasswordUrl) {
        this.setPasswordUrl = setPasswordUrl;
    }

    public Duration getLinkTtl() {
        return linkTtl;
    }

    public void setLinkTtl(Duration linkTtl) {
        this.linkTtl = linkTtl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    /** {@code toString} cố ý bỏ bí mật — Spring in cấu hình ra log ở chế độ debug. */
    @Override
    public String toString() {
        return "KeycloakAdminProperties[enabled=" + enabled + ", serverUrl=" + serverUrl
                + ", realm=" + realm + ", clientId=" + clientId
                + ", clientSecret=" + (clientSecret == null ? "chua dat" : "da dat (an)") + "]";
    }
}
