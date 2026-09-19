package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.membership.application.AcceptedInvitation;
import vn.giapha.membership.application.BranchScopeGuard;
import vn.giapha.membership.application.InvitationLinker;
import vn.giapha.membership.application.InvitationService;
import vn.giapha.membership.application.InviteThrottle;
import vn.giapha.membership.application.IssuedInvitation;
import vn.giapha.membership.application.MemberScopeService;
import vn.giapha.membership.application.SetPasswordService;
import vn.giapha.membership.application.command.AcceptInvitationCommand;
import vn.giapha.membership.application.command.IssueInvitationCommand;
import vn.giapha.membership.domain.port.AppUserRepository;
import vn.giapha.membership.domain.port.BranchLookupPort;
import vn.giapha.membership.domain.port.IdentityAccount;
import vn.giapha.membership.domain.port.InvitationRepository;
import vn.giapha.membership.domain.port.InviteeLookupPort;
import vn.giapha.membership.domain.port.SetPasswordNotAllowedException;
import vn.giapha.membership.infrastructure.keycloak.KeycloakAdminProperties;
import vn.giapha.membership.infrastructure.keycloak.KeycloakIdentityProviderAdapter;

/**
 * <b>Luồng mời chạy trên một Keycloak THẬT</b> — từ lúc Trưởng chi phát mã tới lúc người được mời
 * đăng nhập được bằng mật khẩu do chính họ đặt.
 *
 * <h2>Vì sao ca này không thể tuyên bố xong bằng unit test</h2>
 * Mọi thứ quan trọng ở đây nằm <i>ngoài</i> mã nguồn dự án: Admin REST API của Keycloak có nhận
 * đúng thân yêu cầu không · tài khoản dịch vụ có đủ quyền với đúng <b>một</b> vai
 * {@code manage-users} không · {@code requiredActions: UPDATE_PASSWORD} có thực sự được ghi không ·
 * và — câu hỏi duy nhất thật sự quan trọng — <b>người ấy có đăng nhập được không</b>. Một bản giả
 * trả lời "có" cho cả bốn mà không chứng minh gì cả.
 *
 * <h2>Không chạy mặc định, và đó là chủ ý</h2>
 * Bỏ qua khi thiếu Docker hoặc thiếu {@code GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET}. Vòng CI không có
 * Keycloak, và một test đỏ vì hạ tầng vắng mặt sẽ nhanh chóng bị người ta học cách bỏ qua — lúc ấy
 * nó không còn canh gì nữa. Cách chạy:
 *
 * <pre>
 * export GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET="..."   # xem infra/keycloak-admin-dev.example.txt
 * ./mvnw test -Dtest=InvitationKeycloakLiveIT
 * </pre>
 *
 * <p>Postgres đi qua Testcontainers như mọi IT khác, nên test này <b>không</b> ghi vào cơ sở dữ
 * liệu đang phục vụ ai. Riêng Keycloak thì là realm dev thật; mỗi lần chạy để lại một tài khoản
 * {@code live-check-...@giapha.test} — cố ý đặt tên như vậy để nhận ra và dọn được.</p>
 */
@DisplayName("Lập tài khoản Keycloak cho người được mời — trên Keycloak thật")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
@EnabledIfEnvironmentVariable(named = "GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET", matches = ".+")
class InvitationKeycloakLiveIT extends AbstractIntegrationTest {

    private static final String IP_NGUOI_NHAN = "203.0.113.99";

    /** Mật khẩu do "người dùng" chọn; phải qua chính sách mật khẩu của realm. */
    private static final String MAT_KHAU_TU_DAT = "Giapha#2026-BaLan";

    private static final String KEYCLOAK = "http://localhost:8081";
    private static final String REALM = "giapha";

    @Autowired
    private InvitationRepository invitationRepository;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private InviteeLookupPort inviteeLookup;
    @Autowired
    private BranchLookupPort branchLookup;
    @Autowired
    private MemberScopeService memberScopes;
    @Autowired
    private BranchScopeGuard branchScopeGuard;
    @Autowired
    private InvitationLinker linker;
    @Autowired
    private InviteThrottle throttle;
    @Autowired
    private AuditTrailService auditTrail;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private KeycloakIdentityProviderAdapter identityProvider;
    private InvitationService invitations;
    private SetPasswordService setPasswords;

    private UUID chiGiap;
    private UUID lanPersonId;

    @BeforeEach
    void dungDongHoVaNoiVaoKeycloakThat() {
        jdbc.execute("TRUNCATE TABLE invitation_attempt RESTART IDENTITY");

        KeycloakAdminProperties properties = new KeycloakAdminProperties();
        properties.setServerUrl(KEYCLOAK);
        properties.setRealm(REALM);
        properties.setClientId("giapha-provisioner");
        properties.setClientSecret(System.getenv("GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET"));
        // Khuon dia chi that cua frontend; test chi can doc lai duoc token tu day.
        properties.setSetPasswordUrl("http://localhost:3000/vi/dat-mat-khau?token={token}");

        identityProvider = new KeycloakIdentityProviderAdapter(properties, objectMapper);
        invitations = new InvitationService(invitationRepository, appUserRepository, inviteeLookup,
                branchLookup, memberScopes, branchScopeGuard, linker, identityProvider, throttle,
                auditTrail, 7);
        setPasswords = new SetPasswordService(identityProvider);

        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");

        UUID bonPersonId =
                seed(PersonFixtures.living("Nguyễn Văn Bốn").branch(chiGiap).generation(6));
        assignBranchRole(insertAppUser("sub-bon", bonPersonId), "BRANCH_HEAD", chiGiap);

        lanPersonId = seed(PersonFixtures.living("Nguyễn Thị Lan").branch(chiGiap).generation(7));
    }

    @Test
    @DisplayName("phát mã → nhận KHÔNG token → tài khoản Keycloak mới → đặt mật khẩu → ĐĂNG NHẬP ĐƯỢC")
    void nguoiChuaCoTaiKhoanVaoDuocHeThong() throws Exception {
        assertThat(identityProvider.isConfigured())
                .as("thieu GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET thi ca ca nay vo nghia").isTrue();

        String email = "live-check-" + UUID.randomUUID() + "@giapha.test";

        // (1) Truong chi phat ma.
        authenticateAs("sub-bon", "BRANCH_HEAD");
        IssuedInvitation issued = invitations.issue(new IssueInvitationCommand(lanPersonId));

        // (2) Ba Lan bam "Dung la toi" — KHONG co token nao trong tay.
        xoaMoiToken();
        AcceptedInvitation accepted = invitations.accept(AcceptInvitationCommand.byEmail(
                issued.code(), email, "Nguyễn Thị Lan", IP_NGUOI_NHAN));

        // (3) Tai khoan Keycloak MOI da ton tai that — hoi thang realm, khong tin doi tuong tra ve.
        IdentityAccount trongRealm = identityProvider.findByEmail(email).orElseThrow();
        assertThat(trongRealm.subject()).isEqualTo(accepted.user().keycloakSub());
        assertThat(trongRealm.hasPassword()).as("tai khoan moi lap KHONG duoc co mat khau nao")
                .isFalse();
        assertThat(requiredActionsOf(trongRealm.subject())).contains("UPDATE_PASSWORD");

        // (4) Va no da duoc gan dung nhan khau trong pha — doc lai tu CSDL.
        assertThat(jdbc.queryForObject("SELECT person_id FROM app_user WHERE keycloak_sub = ?",
                UUID.class, trongRealm.subject())).isEqualTo(lanPersonId);
        assertThat(jdbc.queryForObject("SELECT status FROM app_user WHERE keycloak_sub = ?",
                String.class, trongRealm.subject())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM invitation WHERE person_id = ?",
                String.class, lanPersonId)).isEqualTo("ACCEPTED");

        // (5) Lien ket dat mat khau duoc phat ra.
        String url = accepted.setPasswordLink().orElseThrow().url();
        String token = URLDecoder.decode(url.substring(url.indexOf("token=") + 6),
                StandardCharsets.UTF_8);

        // (6) Chinh ba Lan dat mat khau cua minh.
        setPasswords.setPassword(token, MAT_KHAU_TU_DAT);

        // (7) BANG CHUNG DUY NHAT THAT SU QUAN TRONG: ba Lan dang nhap duoc.
        JsonNode dangNhap = dangNhapBangMatKhau(email, MAT_KHAU_TU_DAT);
        assertThat(dangNhap.path("access_token").asText()).isNotBlank();
        assertThat(subjectCuaToken(dangNhap.path("access_token").asText()))
                .isEqualTo(trongRealm.subject());

        // (8) Va lien ket ay da CHET — kiem tren chinh Keycloak that, khong tren mot ban gia.
        // Tinh "mot lan" o day khong den tu mot co trong CSDL ma tu trang thai that: tai khoan gio
        // da co credential mat khau, nen dieu kien phat sinh lien ket sai vinh vien.
        assertThat(identityProvider.findByEmail(email).orElseThrow().hasPassword()).isTrue();
        assertThatThrownBy(() -> setPasswords.setPassword(token, "Mot-Ke-Khac#2026"))
                .isInstanceOf(SetPasswordNotAllowedException.class);
        // Mat khau cu van dung — lan thu hai khong doi duoc gi ca.
        assertThat(dangNhapBangMatKhau(email, MAT_KHAU_TU_DAT).path("access_token").asText())
                .isNotBlank();
    }

    // -------------------------------------------------------------------------------------
    // Tiện ích gọi thẳng Keycloak — CỐ Ý không dùng lại adapter đang được kiểm
    // -------------------------------------------------------------------------------------

    /**
     * Đăng nhập bằng {@code password} grant của client {@code giapha-frontend}.
     *
     * <p>Gọi thẳng endpoint token chứ không qua bất kỳ lớp nào của dự án: nếu lời khẳng định "người
     * ấy đăng nhập được" lại đi qua chính mã đang được kiểm thì nó chứng minh rất ít.</p>
     */
    private JsonNode dangNhapBangMatKhau(String username, String password) throws Exception {
        String form = "grant_type=password&client_id=giapha-frontend"
                + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                + "&scope=openid";
        HttpResponse<String> response = http.send(HttpRequest
                .newBuilder(URI.create(KEYCLOAK + "/realms/" + REALM
                        + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as("Keycloak tu choi dang nhap: %s", response.body()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private String subjectCuaToken(String accessToken) throws Exception {
        String payload = accessToken.split("\\.")[1];
        return objectMapper.readTree(java.util.Base64.getUrlDecoder().decode(payload))
                .path("sub").asText();
    }

    private String requiredActionsOf(String subject) throws Exception {
        String token = tokenTaiKhoanDichVu();
        HttpResponse<String> response = http.send(HttpRequest
                .newBuilder(URI.create(KEYCLOAK + "/admin/realms/" + REALM + "/users/" + subject))
                .header("Authorization", "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body()).path("requiredActions").toString();
    }

    private String tokenTaiKhoanDichVu() throws Exception {
        String form = "grant_type=client_credentials&client_id=giapha-provisioner"
                + "&client_secret=" + URLEncoder.encode(
                        System.getenv("GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET"), StandardCharsets.UTF_8);
        HttpResponse<String> response = http.send(HttpRequest
                .newBuilder(URI.create(KEYCLOAK + "/realms/" + REALM
                        + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body()).path("access_token").asText();
    }

    /** Bỏ mọi {@code Authentication} — người được mời chưa có token nào. */
    private void xoaMoiToken() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
