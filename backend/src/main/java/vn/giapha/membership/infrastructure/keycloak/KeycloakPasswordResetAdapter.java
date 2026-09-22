package vn.giapha.membership.infrastructure.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.giapha.membership.domain.port.IdentityAccount;
import vn.giapha.membership.domain.port.IdentityProviderException;
import vn.giapha.membership.domain.port.PasswordResetPort;
import vn.giapha.membership.domain.port.SetPasswordLink;

/**
 * Hiện thực {@code PasswordResetPort} bằng Keycloak Admin REST API.
 *
 * <h2>Vì sao là một adapter RIÊNG, không phải vài phương thức nữa trong
 * {@link KeycloakIdentityProviderAdapter}</h2>
 * Hai adapter nói chuyện với đúng một máy chủ, dùng đúng một tài khoản dịch vụ, và
 * {@code manage-users} đã phủ cả hai — nên tách ra <b>không</b> siết thêm được một quyền nào ở phía
 * Keycloak. Cái nó siết là phía Java: ghi đè mật khẩu của một tài khoản đã có chủ là năng lực nguy
 * hiểm nhất của cả context, và tách cổng khiến nó chỉ tiêm được vào hai lớp đã cân nhắc thay vì có
 * mặt sẵn trong tay mọi lớp đang giữ {@code IdentityProviderPort}. Xem javadoc
 * {@code PasswordResetPort}.
 *
 * <h2>Cái giá đã biết: một {@link KeycloakAdminApi} thứ hai</h2>
 * Mỗi adapter tự dựng client của mình, nên có hai bộ nhớ đệm token của tài khoản dịch vụ và, ở
 * trường hợp xấu nhất, hai lượt lấy token mỗi chu kỳ. Với lưu lượng của luồng này (vài lượt một
 * tuần cho cả dòng họ) đó là một cái giá không đo được. Đổi lại là không phải đụng vào wiring của
 * adapter đang chạy thật.
 *
 * <p>Chưa cấu hình bí mật thì adapter <b>tự tắt</b>: {@link #isConfigured()} trả {@code false} và
 * ứng dụng vẫn khởi động bình thường — cùng khuôn mẫu với adapter kia.</p>
 */
@Component
public class KeycloakPasswordResetAdapter implements PasswordResetPort {

    private static final Logger log = LoggerFactory.getLogger(KeycloakPasswordResetAdapter.class);

    /** Chỗ dành sẵn trong khuôn địa chỉ liên kết đặt mật khẩu. */
    private static final String TOKEN_PLACEHOLDER = "{token}";

    private final KeycloakAdminProperties properties;
    private final KeycloakAdminApi api;
    private final SetPasswordTokenCodec codec;
    private final Clock clock;

    // @Autowired la BAT BUOC: lop nay co hai constructor va Spring khong tu chon duoc giua chung —
    // no roi ve constructor khong tham so, khong thay, roi CHET LUC KHOI DONG. Cung cai bay da
    // duoc ghi o KeycloakIdentityProviderAdapter.
    @Autowired
    public KeycloakPasswordResetAdapter(KeycloakAdminProperties properties,
                                        ObjectMapper objectMapper) {
        this(properties,
                properties.isConfigured() ? new KeycloakAdminApi(properties, objectMapper) : null,
                properties.isConfigured()
                        ? SetPasswordTokenCodec.forPasswordReset(properties.getClientSecret())
                        : null,
                Clock.systemUTC());
        if (!properties.isConfigured()) {
            log.warn("Cong cap lai mat khau chua duoc cau hinh (thieu"
                    + " GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET). Truong chi se khong cap lai duoc"
                    + " lien ket dat mat khau cho thanh vien nao.");
        }
    }

    /** Lối dựng cho test: nối thẳng vào một {@link KeycloakAdminApi} giả và một đồng hồ đứng yên. */
    KeycloakPasswordResetAdapter(KeycloakAdminProperties properties, KeycloakAdminApi api,
                                 SetPasswordTokenCodec codec, Clock clock) {
        this.properties = properties;
        this.api = api;
        this.codec = codec;
        this.clock = clock;
    }

    @Override
    public boolean isConfigured() {
        return api != null && codec != null && properties.isConfigured();
    }

    @Override
    public Optional<IdentityAccount> findBySubject(String keycloakSub) {
        requireConfigured();
        if (keycloakSub == null || keycloakSub.isBlank()) {
            return Optional.empty();
        }
        return api.findUserById(keycloakSub.trim()).map(this::toAccount);
    }

    @Override
    public SetPasswordLink issueResetLink(UUID grantId, Instant expiresAt) {
        requireConfigured();
        Instant now = clock.instant();
        if (!expiresAt.isAfter(now)) {
            throw new IdentityProviderException("Han cua lien ket dat lai mat khau da qua");
        }
        SetPasswordTokenCodec.Minted minted =
                codec.mint(grantId.toString(), Duration.between(now, expiresAt), now);
        String url = properties.getSetPasswordUrl().replace(TOKEN_PLACEHOLDER,
                URLEncoder.encode(minted.token(), StandardCharsets.UTF_8));
        // KHONG log url, KHONG log token. Ca hai la bi mat mot lan — xem IssuedPasswordReset.
        return new SetPasswordLink(url, minted.expiresAt());
    }

    @Override
    public Optional<UUID> readGrantRef(String token) {
        if (!isConfigured()) {
            // Chua cau hinh thi khong the co token nao cua luong nay: tra rong de nguoi goi chuyen
            // sang luong dat mat khau lan dau, thay vi nem 503 cho mot nguoi dang cam token hop le
            // cua luong kia.
            return Optional.empty();
        }
        return codec.verify(token, clock.instant()).flatMap(KeycloakPasswordResetAdapter::toUuid);
    }

    @Override
    public void overwritePassword(String keycloakSub, String rawPassword) {
        requireConfigured();
        if (api.findUserById(keycloakSub).isEmpty()) {
            throw new IdentityProviderException(
                    "Tai khoan khong con ton tai trong realm: " + keycloakSub);
        }
        api.setPassword(keycloakSub, rawPassword);
        // Go UPDATE_PASSWORD neu con treo: ca "loi moi het han, chua kip dat mat khau lan nao" di
        // qua day, va bo quen se de nguoi dung ket o man doi mat khau cua Keycloak ngay sau khi
        // vua dat mat khau xong.
        api.clearUpdatePasswordAction(keycloakSub);
        log.info("Tai khoan Keycloak {} vua doi mat khau qua lien ket cap lai", keycloakSub);
    }

    /**
     * {@code UserRepresentation} → {@link IdentityAccount}.
     *
     * <p>{@code justCreated} luôn {@code false}: một lượt <b>tra</b> không bao giờ là một lượt
     * <b>tạo</b>. Cổng này không tạo tài khoản nào cả.</p>
     */
    private IdentityAccount toAccount(ObjectNode node) {
        String id = node.path("id").asText();
        return new IdentityAccount(id, node.path("username").asText(null),
                node.path("email").asText(null), api.hasPasswordCredential(id), false,
                coTreoDoiMatKhau(node), createdAtOf(node));
    }

    private static boolean coTreoDoiMatKhau(ObjectNode node) {
        for (JsonNode action : node.path("requiredActions")) {
            if (KeycloakRequiredActions.UPDATE_PASSWORD.equals(action.asText())) {
                return true;
            }
        }
        return false;
    }

    private static Instant createdAtOf(ObjectNode node) {
        JsonNode created = node.path("createdTimestamp");
        return created.isNumber() ? Instant.ofEpochMilli(created.asLong()) : null;
    }

    /** Chữ ký đúng nhưng nội dung không phải UUID = token do một phiên bản khác đúc. Coi như hỏng. */
    private static Optional<UUID> toUuid(String raw) {
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IdentityProviderException(
                    "Cong danh tinh chua duoc cau hinh: thieu GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET");
        }
    }
}
