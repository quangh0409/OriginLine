package vn.giapha.membership.infrastructure.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.giapha.membership.domain.port.IdentityAccount;
import vn.giapha.membership.domain.port.IdentityProviderException;
import vn.giapha.membership.domain.port.IdentityProviderPort;
import vn.giapha.membership.domain.port.NewIdentityAccount;
import vn.giapha.membership.domain.port.SetPasswordLink;
import vn.giapha.membership.domain.port.SetPasswordNotAllowedException;

/**
 * Hiện thực {@code IdentityProviderPort} bằng Keycloak Admin REST API.
 *
 * <h2>Quyền tối thiểu của tài khoản dịch vụ: đúng MỘT vai client</h2>
 * {@code realm-management:manage-users}. <b>Không</b> {@code realm-admin} — vai ấy gộp cả
 * {@code manage-realm}, {@code manage-clients}, {@code manage-identity-providers} và
 * {@code impersonation}, nên một bí mật rò ra sẽ thành quyền quản trị toàn realm: đổi được luồng
 * xác thực, tắt được kiểm mật khẩu, mạo danh được bất kỳ ai. Cũng <b>không</b> cần
 * {@code view-users} riêng: trong Keycloak {@code canView()} đã đúng khi người gọi có quyền quản
 * lý, nên {@code manage-users} tự phủ cả tìm kiếm lẫn đọc credential.
 *
 * <p>Hẹp hơn {@code manage-users} thì Keycloak bản thường không có — phân quyền quản trị chi tiết
 * (fine-grained admin permissions) là tính năng riêng và là hướng siết tiếp theo nếu cần. Ghi ra
 * đây để lần sau không ai phải đi tìm lại.</p>
 *
 * <h2>Mọi thao tác ghi đều LẶP LẠI ĐƯỢC</h2>
 * Tạo tài khoản đi qua "tìm trước, tạo sau", và ngay cả khi thua cuộc đua ({@code 409}) thì nó đọc
 * lại bản của luồng kia thay vì hỏng. Nhờ vậy {@code InvitationService.accept} được phép gọi cổng
 * này <i>trước</i> khi ghi CSDL và coi lần ghi CSDL là bước duy nhất không đảo ngược được.
 *
 * <p>Chưa cấu hình bí mật thì adapter <b>tự tắt</b>: {@link #isConfigured()} trả {@code false} và
 * ứng dụng vẫn khởi động bình thường.</p>
 */
@Component
public class KeycloakIdentityProviderAdapter implements IdentityProviderPort {

    private static final Logger log =
            LoggerFactory.getLogger(KeycloakIdentityProviderAdapter.class);

    /** Chỗ dành sẵn trong khuôn địa chỉ liên kết đặt mật khẩu. */
    private static final String TOKEN_PLACEHOLDER = "{token}";

    private final KeycloakAdminProperties properties;
    private final KeycloakAdminApi api;
    private final SetPasswordTokenCodec codec;
    private final Clock clock;

    // @Autowired la BAT BUOC o day, khong phai trang tri: lop nay co HAI constructor (loi thu hai
    // danh cho test), va Spring khong tu chon duoc giua hai constructor cong khai — no roi ve
    // constructor khong tham so, khong thay, roi CHET LUC KHOI DONG voi "No default constructor
    // found". Loi ay khong lo ra o unit test nao vi khong test nao nang Spring context len.
    @Autowired
    public KeycloakIdentityProviderAdapter(KeycloakAdminProperties properties,
                                           ObjectMapper objectMapper) {
        this(properties,
                properties.isConfigured() ? new KeycloakAdminApi(properties, objectMapper) : null,
                properties.isConfigured()
                        ? new SetPasswordTokenCodec(properties.getClientSecret()) : null,
                Clock.systemUTC());
        if (!properties.isConfigured()) {
            log.warn("Cong danh tinh chua duoc cau hinh (thieu"
                    + " GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET). Nguoi CHUA co tai khoan se khong"
                    + " nhan duoc loi moi; loi nhan bang token san co van chay.");
        }
    }

    /** Lối dựng cho test: nối thẳng vào một {@link KeycloakAdminApi} giả và một đồng hồ đứng yên. */
    KeycloakIdentityProviderAdapter(KeycloakAdminProperties properties, KeycloakAdminApi api,
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
    public Optional<IdentityAccount> findByEmail(String email) {
        requireConfigured();
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return api.findUserByEmail(email.trim().toLowerCase(java.util.Locale.ROOT))
                .map(node -> toAccount(node, false));
    }

    @Override
    public Optional<IdentityAccount> findByUsername(String username) {
        requireConfigured();
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return api.findUserByUsername(username.trim().toLowerCase(java.util.Locale.ROOT))
                .map(node -> toAccount(node, false));
    }

    @Override
    public IdentityAccount createAccount(NewIdentityAccount request) {
        requireConfigured();
        // email CO THE null: nguoi lap tai khoan bang so dien thoai khong co truong ay, va realm
        // ap bo kiem email len thuoc tinh do nen nhet so may vao se bi tu choi. username moi la
        // dinh danh bat buoc.
        String email = request.email() == null || request.email().isBlank()
                ? null : request.email().trim().toLowerCase(java.util.Locale.ROOT);
        String username = request.username() == null || request.username().isBlank()
                ? email : request.username().trim().toLowerCase(java.util.Locale.ROOT);
        if (username == null) {
            throw new IdentityProviderException(
                    "Khong lap duoc tai khoan: thieu ca ten dang nhap lan dia chi thu");
        }
        String tenDangNhap = username;
        try {
            String id = api.createUser(tenDangNhap, email, null, request.displayName());
            log.info("Tao tai khoan Keycloak {} cho nguoi duoc moi (co email: {})",
                    id, email != null);
            // Vua tao: chac chan chua co credential nao, va createUser luon dat UPDATE_PASSWORD.
            return new IdentityAccount(id, tenDangNhap, email, false, true, true,
                    clock.instant());
        } catch (KeycloakUserAlreadyExistsException ex) {
            // Thua cuoc dua (hai lan bam, hoac mot lan thu lai): doc lai ban cua luong kia.
            // Tra theo TEN DANG NHAP chu khong theo email — tai khoan dung so dien thoai khong co
            // email de ma tra, va tra rong o day se nem mot loi 503 cho mot lan bam lai vo hai.
            log.info("Realm da co tai khoan cho dinh danh nay, dung lai thay vi tao moi");
            return findByUsername(tenDangNhap).orElseThrow(() -> new IdentityProviderException(
                    "Realm bao trung tai khoan nhung khong tim lai duoc", ex));
        }
    }

    @Override
    public Optional<SetPasswordLink> issueSetPasswordLink(IdentityAccount account) {
        requireConfigured();
        if (account.hasPassword()) {
            // Da co mat khau thi dang nhap nhu binh thuong. Phat lien ket o day la mo mot loi doi
            // mat khau cho bat ky ai cam ma moi va doan dung email cua mot thanh vien cu.
            log.info("Khong phat lien ket dat mat khau: tai khoan {} da co mat khau",
                    account.subject());
            return Optional.empty();
        }
        SetPasswordTokenCodec.Minted minted =
                codec.mint(account.subject(), properties.getLinkTtl(), clock.instant());
        String url = properties.getSetPasswordUrl().replace(TOKEN_PLACEHOLDER,
                URLEncoder.encode(minted.token(), StandardCharsets.UTF_8));
        return Optional.of(new SetPasswordLink(url, minted.expiresAt()));
    }

    @Override
    public void completeSetPassword(String token, String rawPassword) {
        requireConfigured();
        String subject = codec.verify(token, clock.instant())
                .orElseThrow(() -> new SetPasswordNotAllowedException(
                        "Lien ket dat mat khau khong hop le hoac da het han"));
        if (api.findUserById(subject).isEmpty()) {
            throw new SetPasswordNotAllowedException(
                    "Lien ket dat mat khau khong hop le hoac da het han");
        }
        // DAY LA PHEP KIEM "MOT LAN". Nguon chan ly la trang thai that o Keycloak, khong phai mot
        // co trong CSDL cua he thong nay — xem javadoc SetPasswordTokenCodec.
        if (api.hasPasswordCredential(subject)) {
            throw new SetPasswordNotAllowedException(
                    "Tai khoan nay da co mat khau; hay dang nhap nhu binh thuong");
        }
        api.setPassword(subject, rawPassword);
        api.clearUpdatePasswordAction(subject);
        log.info("Tai khoan Keycloak {} da tu dat mat khau qua lien ket mot lan", subject);
    }

    /**
     * {@code UserRepresentation} → {@link IdentityAccount}.
     *
     * <p>{@code requiredActions} và {@code createdTimestamp} đi cùng chuyến: cả hai đã nằm sẵn
     * trong thân phản hồi mà realm vừa trả về, nên đọc chúng ở đây <b>không tốn một lượt gọi
     * nào</b> — còn đọc bằng một lời gọi thứ hai thì sẽ có chỗ quên gọi.</p>
     */
    private IdentityAccount toAccount(ObjectNode node, boolean justCreated) {
        String id = node.path("id").asText();
        return new IdentityAccount(id, node.path("username").asText(null),
                node.path("email").asText(null), api.hasPasswordCredential(id), justCreated,
                coTreoDoiMatKhau(node), createdAtOf(node));
    }

    /** Realm còn treo {@code UPDATE_PASSWORD} — dấu vết riêng của luồng onboarding này. */
    private static boolean coTreoDoiMatKhau(ObjectNode node) {
        for (JsonNode action : node.path("requiredActions")) {
            if (KeycloakRequiredActions.UPDATE_PASSWORD.equals(action.asText())) {
                return true;
            }
        }
        return false;
    }

    /** {@code createdTimestamp} tính bằng mili-giây; vắng mặt thì trả {@code null}. */
    private static Instant createdAtOf(ObjectNode node) {
        JsonNode created = node.path("createdTimestamp");
        return created.isNumber() ? Instant.ofEpochMilli(created.asLong()) : null;
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IdentityProviderException(
                    "Cong danh tinh chua duoc cau hinh: thieu GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET");
        }
    }
}
