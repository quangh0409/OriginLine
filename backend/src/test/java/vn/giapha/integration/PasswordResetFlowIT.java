package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.giapha.membership.application.MembershipProblemCodes;
import vn.giapha.membership.domain.port.IdentityProviderPort;
import vn.giapha.membership.domain.port.PasswordResetPort;
import vn.giapha.membership.support.FakeIdentityProvider;
import vn.giapha.membership.support.FakePasswordResetGateway;

/**
 * <b>Trưởng chi / Hội đồng cấp lại liên kết đặt mật khẩu</b>, trên hạ tầng thật, <b>chỉ qua HTTP</b>.
 *
 * <h2>Không một ca nào ở đây ghi thẳng vào bảng để đi qua một cổng</h2>
 * Dữ liệu nền (chi, nhân khẩu, tài khoản, phân vai) được gieo bằng SQL — đó là <i>bối cảnh</i>.
 * Nhưng mọi bước của chính luồng đang kiểm đều đi qua {@code POST /api/v1/password-resets} và
 * {@code POST /api/v1/invitations/set-password}, tức là qua Spring Security, qua controller, qua
 * {@code BranchScopeGuard}, qua transaction thật. Một ca "đốt liên kết" bằng một câu {@code UPDATE}
 * viết tay sẽ xanh mà không chứng minh được điều gì.
 *
 * <h2>Vì sao phải là test tích hợp</h2>
 * Bốn thứ chỉ quan sát được với PostgreSQL thật:
 * <ol>
 *   <li><b>{@code ux_password_reset_grant_open}</b> — "một tài khoản chỉ có một liên kết đang mở"
 *       là một chỉ mục, không phải một câu {@code if}; và phép thu-hồi-trước-khi-ghi chỉ kiểm được
 *       khi có một chỉ mục thật để vi phạm.</li>
 *   <li><b>{@code claim()} là một lệnh ghi có điều kiện</b> — "dùng một lần" nằm trong mệnh đề
 *       {@code WHERE}, nên nó chỉ thật khi có một máy chủ CSDL chạy nó.</li>
 *   <li><b>{@code audit_log}</b> — câu hỏi "ai cấp, cho ai, lúc nào" phải trả lời được bằng một
 *       câu {@code SELECT} trên bảng thật, không bằng một mock đã được gọi.</li>
 *   <li><b>Thông báo tới chính chủ</b> — nó đi qua RabbitMQ thật rồi mới vào
 *       {@code notification_inbox}.</li>
 * </ol>
 *
 * <h2>Hai cổng được thay bằng bản giả, và cả hai đều cần thiết</h2>
 * {@link FakePasswordResetGateway} dùng <b>codec thật</b> của mã sản xuất, nên ca "hết hạn thì vô
 * dụng" là một phép kiểm thật chứ không phải một cờ trong bản giả. {@link FakeIdentityProvider} có
 * mặt vì {@code /invitations/set-password} phục vụ <i>hai</i> loại token: thiếu nó thì một token
 * cấp-lại đã hết hạn sẽ rơi xuống nhánh luồng mời, gặp một cổng chưa cấu hình, và trả {@code 503}
 * thay vì {@code 410} — tức là ca test sẽ đo một hành vi chỉ tồn tại trong môi trường test.
 */
@DisplayName("Cấp lại liên kết đặt mật khẩu cho tài khoản đăng ký bằng số điện thoại")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class PasswordResetFlowIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Chờ tối đa cho tin đi qua RabbitMQ vào hộp thư. */
    private static final Duration CHO_TIN = Duration.ofSeconds(20);

    @TestConfiguration
    static class CongGia {

        @Bean
        @Primary
        FakePasswordResetGateway fakePasswordResetGateway() {
            return new FakePasswordResetGateway();
        }

        @Bean
        @Primary
        FakeIdentityProvider fakeIdentityProvider() {
            return new FakeIdentityProvider();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakePasswordResetGateway congCapLai;

    @Autowired
    private PasswordResetPort congCapLaiDaTiem;

    @Autowired
    private IdentityProviderPort congDanhTinhDaTiem;

    /** Ông Bốn — Trưởng Chi Giáp, người cấp. */
    private UUID bonPersonId;
    private UUID bonUserId;

    /** Cụ Bảy — Chi Giáp, đăng ký bằng SỐ ĐIỆN THOẠI, quên mật khẩu. Người được cấp. */
    private UUID bayPersonId;
    private UUID bayUserId;
    private static final String BAY_SUB = "sub-bay";

    /** Bà Bính — Chi Bính, ngoài phạm vi của ông Bốn. */
    private UUID binhPersonId;

    /** Hội đồng Tộc biểu — phạm vi toàn dòng họ. */
    private UUID hoiDongPersonId;
    private UUID hoiDongUserId;

    @BeforeEach
    void dungDongHo() {
        congCapLai.reset();

        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        UUID chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        UUID chiBinh = insertBranch("Chi Bính", "goc.chi_binh", goc, "CHI");

        bonPersonId = seed(PersonFixtures.living("Nguyễn Văn Bốn").branch(chiGiap).generation(6));
        bonUserId = insertAppUser("sub-bon", bonPersonId);
        assignBranchRole(bonUserId, "BRANCH_HEAD", chiGiap);

        bayPersonId = seed(PersonFixtures.living("Nguyễn Văn Bảy").branch(chiGiap).generation(5));
        bayUserId = insertAppUser(BAY_SUB, bayPersonId);
        // Tai khoan lap bang SO DIEN THOAI: khong co email trong realm. Day la ca duy nhat ma
        // luong nay phuc vu.
        congCapLai.seedPhoneAccount(BAY_SUB, "0912345678");

        binhPersonId = seed(PersonFixtures.living("Nguyễn Thị Bính").branch(chiBinh).generation(6));
        insertAppUser("sub-binh", binhPersonId);
        congCapLai.seedPhoneAccount("sub-binh", "0913000000");

        hoiDongPersonId = seed(PersonFixtures.living("Nguyễn Văn Tộc").branch(goc).generation(4));
        hoiDongUserId = insertAppUser("sub-hoi-dong", hoiDongPersonId);
        // branchId = null nghia la pham vi toan dong ho.
        assignBranchRole(hoiDongUserId, "COUNCIL", null);
        congCapLai.seedPhoneAccount("sub-hoi-dong", "0914000000");
    }

    // =====================================================================================
    // Tiện ích
    // =====================================================================================

    /**
     * Người gọi <b>đi qua cả chuỗi lọc Spring Security</b>, không phải một
     * {@code SecurityContextHolder} đặt tay: {@code SecurityContextHolderFilter} nạp lại ngữ cảnh ở
     * đầu mỗi request và ghi đè thread-local, nên một ngữ cảnh đặt trước lời gọi MockMvc sẽ bị mất
     * và ca test sẽ đo một luồng khách vãng lai.
     */
    private static RequestPostProcessor nguoiGoi(String sub, String role) {
        return jwt().jwt(builder -> builder.subject(sub).claim("preferred_username", sub))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private ResultActions capLaiRaw(String sub, String role, UUID personId) throws Exception {
        return mockMvc.perform(post("/api/v1/password-resets")
                .with(nguoiGoi(sub, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"personId\":\"" + personId + "\",\"note\":\"cu goi dien\"}"));
    }

    private String capLai(String sub, String role, UUID personId) throws Exception {
        return capLaiRaw(sub, role, personId)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String tokenCua(String phanHoi) throws Exception {
        String url = JSON.readTree(phanHoi).path("url").asText();
        String raw = url.substring(url.indexOf("token=") + "token=".length());
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    /** Gọi endpoint đặt mật khẩu — <b>không cần token đăng nhập</b>, đúng như người dùng thật. */
    private ResultActions datMatKhau(String token, String matKhau) throws Exception {
        return mockMvc.perform(post("/api/v1/invitations/set-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(
                        Map.of("token", token, "newPassword", matKhau))));
    }

    private int soLienKetCua(UUID appUserId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_grant WHERE target_app_user_id = ?",
                Integer.class, appUserId);
        return count == null ? 0 : count;
    }

    private Map<String, Object> lienKetMoiNhat(UUID appUserId) {
        return jdbc.queryForMap("SELECT * FROM password_reset_grant"
                + " WHERE target_app_user_id = ? ORDER BY issued_at DESC LIMIT 1", appUserId);
    }

    // =====================================================================================
    // 1. Phạm vi ltree — Trưởng chi Ất không cấp được cho người chi Bính
    // =====================================================================================

    @Test
    @DisplayName("Trưởng Chi Giáp KHÔNG cấp được cho người Chi Bính, và không một liên kết nào được đúc")
    void truongChiKhongVuotChiDuoc() throws Exception {
        capLaiRaw("sub-bon", "BRANCH_HEAD", binhPersonId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value(MembershipProblemCodes.BRANCH_SCOPE_VIOLATION));

        // KHONG co dong nao, va — quan trong hon — KHONG mot token nao duoc duc ra. Mot lien ket
        // duc roi vut di van la mot lien ket da ton tai.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_grant", Integer.class))
                .isZero();
        assertThat(congCapLai.issuedLinkCount()).isZero();
    }

    @Test
    @DisplayName("Hội đồng Tộc biểu cấp được cho cả hai chi")
    void hoiDongCapDuocToanHo() throws Exception {
        capLai("sub-hoi-dong", "COUNCIL", bayPersonId);
        capLai("sub-hoi-dong", "COUNCIL", binhPersonId);
        assertThat(soLienKetCua(bayUserId)).isOne();
    }

    // =====================================================================================
    // 2. Không ai tự cấp cho chính mình
    // =====================================================================================

    @Test
    @DisplayName("Không ai tự cấp cho chính mình — kể cả Hội đồng")
    void khongAiTuCapChoChinhMinh() throws Exception {
        capLaiRaw("sub-bon", "BRANCH_HEAD", bonPersonId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value(MembershipProblemCodes.SELF_PASSWORD_RESET_FORBIDDEN));

        // Vai toan dong ho khong mien cho ai ca: day la luat ve CHINH CHU, khong phai ve pham vi.
        capLaiRaw("sub-hoi-dong", "COUNCIL", hoiDongPersonId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value(MembershipProblemCodes.SELF_PASSWORD_RESET_FORBIDDEN));

        assertThat(congCapLai.issuedLinkCount()).isZero();
        assertThat(soLienKetCua(bonUserId)).isZero();
    }

    // =====================================================================================
    // 3. Đường đi trọn vẹn: cấp → chính chủ được báo → đặt lại mật khẩu → liên kết chết
    // =====================================================================================

    @Test
    @DisplayName("Cấp · báo chính chủ · đặt lại mật khẩu · liên kết chỉ dùng được MỘT lần")
    void duongDiTronVen() throws Exception {
        String phanHoi = capLai("sub-bon", "BRANCH_HEAD", bayPersonId);
        JsonNode body = JSON.readTree(phanHoi);

        assertThat(body.path("url").asText()).contains("token=");
        assertThat(body.path("expiresAt").asText()).isNotBlank();
        // Than phan hoi KHONG duoc chua keycloak_sub hay bat ky dinh danh dang nhap nao.
        assertThat(phanHoi).doesNotContain(BAY_SUB).doesNotContain("0912345678");
        assertThat(body.path("grant").path("target").path("displayName").asText())
                .isEqualTo("Nguyễn Văn Bảy");

        Map<String, Object> grant = lienKetMoiNhat(bayUserId);
        assertThat(grant.get("used_at")).isNull();
        assertThat(grant.get("issued_by")).isEqualTo(bonUserId);
        assertThat(grant.get("target_person_id")).isEqualTo(bayPersonId);

        // --- audit_log: ai cap, cho ai, luc nao ---
        Map<String, Object> vet = jdbc.queryForMap("SELECT * FROM audit_log"
                + " WHERE entity_type = 'PasswordResetGrant' AND action = 'CREATE'"
                + " ORDER BY at DESC LIMIT 1");
        assertThat(vet.get("entity_id")).isEqualTo(grant.get("id").toString());
        assertThat(vet.get("actor_user_id")).isEqualTo(bonUserId);
        assertThat(vet.get("actor_person_id")).isEqualTo(bonPersonId);
        assertThat(vet.get("at")).isNotNull();
        String sauKhiCap = String.valueOf(vet.get("after"));
        assertThat(sauKhiCap).contains(bayUserId.toString()).contains(bonUserId.toString());
        // KHONG mot manh nao cua lien ket duoc phep nam trong nhat ky.
        assertThat(sauKhiCap).doesNotContain(tokenCua(phanHoi));

        // --- chinh chu duoc bao, qua RabbitMQ that vao notification_inbox ---
        choDen(() -> demThongBaoCua(bayPersonId) >= 1);
        Map<String, Object> tin = jdbc.queryForMap("SELECT * FROM notification_inbox"
                + " WHERE recipient_person_id = ? ORDER BY created_at DESC LIMIT 1", bayPersonId);
        assertThat(tin.get("category")).isEqualTo("SYSTEM");
        assertThat(String.valueOf(tin.get("body"))).contains("sub-bon");
        // Thong bao KHONG duoc mang lien ket: no di qua man hinh khoa va ha tang ben thu ba.
        assertThat(String.valueOf(tin.get("body"))).doesNotContain(tokenCua(phanHoi));

        // --- nguoi ay tu dat lai mat khau ---
        String token = tokenCua(phanHoi);
        datMatKhau(token, "MatKhauMoi@2026").andExpect(status().isNoContent());
        assertThat(congCapLai.passwordOf(BAY_SUB)).contains("MatKhauMoi@2026");
        assertThat(lienKetMoiNhat(bayUserId).get("used_at")).isNotNull();

        // --- LAN THU HAI THI CHET. Day la ca quan trong nhat cua ca lop: neu no xanh sai thi
        //     Truong chi giu lai lien ket va dat lai mat khau cua nguoi khac bat cu luc nao. ---
        datMatKhau(token, "MotMatKhauKhac@2026")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code")
                        .value(MembershipProblemCodes.SET_PASSWORD_LINK_INVALID));
        assertThat(congCapLai.passwordOf(BAY_SUB)).contains("MatKhauMoi@2026");
    }

    // =====================================================================================
    // 4. Hết hạn thì vô dụng
    // =====================================================================================

    @Test
    @DisplayName("Liên kết quá hạn thì vô dụng, và không nói ra lý do")
    void hetHanThiVoDung() throws Exception {
        String token = tokenCua(capLai("sub-bon", "BRANCH_HEAD", bayPersonId));

        // Han mac dinh la 30 phut (giapha.keycloak.admin.link-ttl). Tua qua no.
        congCapLai.tuaDongHo(Duration.ofMinutes(31));

        datMatKhau(token, "MatKhauMoi@2026")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code")
                        .value(MembershipProblemCodes.SET_PASSWORD_LINK_INVALID));
        assertThat(congCapLai.passwordOf(BAY_SUB)).isEmpty();
        assertThat(lienKetMoiNhat(bayUserId).get("used_at")).isNull();
    }

    // =====================================================================================
    // 5. Cấp lại thì liên kết cũ chết ngay
    // =====================================================================================

    @Test
    @DisplayName("Cấp lại thu hồi liên kết cũ — hai liên kết cùng mở thì 'thu hồi' mất nghĩa")
    void capLaiThuHoiLienKetCu() throws Exception {
        String tokenCu = tokenCua(capLai("sub-bon", "BRANCH_HEAD", bayPersonId));
        String tokenMoi = tokenCua(capLai("sub-bon", "BRANCH_HEAD", bayPersonId));
        assertThat(tokenCu).isNotEqualTo(tokenMoi);

        datMatKhau(tokenCu, "MatKhauMoi@2026")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code")
                        .value(MembershipProblemCodes.SET_PASSWORD_LINK_INVALID));
        assertThat(congCapLai.passwordOf(BAY_SUB)).isEmpty();

        datMatKhau(tokenMoi, "MatKhauMoi@2026").andExpect(status().isNoContent());
        assertThat(congCapLai.passwordOf(BAY_SUB)).contains("MatKhauMoi@2026");
    }

    // =====================================================================================
    // 6. Tài khoản CÓ email thì từ chối — họ đã có đường tự lấy lại
    // =====================================================================================

    @Test
    @DisplayName("Tài khoản có địa chỉ thư thì từ chối: họ tự lấy lại được, mở thêm đường là mở thêm lỗ hổng")
    void taiKhoanCoEmailThiTuChoi() throws Exception {
        // Cu Bay doi sang mot tai khoan co email (hinh dang cua ca tai khoan Google/Zalo).
        congCapLai.seedEmailAccount(BAY_SUB, "bay@example.com");

        capLaiRaw("sub-bon", "BRANCH_HEAD", bayPersonId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code")
                        .value(MembershipProblemCodes.PASSWORD_RESET_NOT_ALLOWED));
        assertThat(congCapLai.issuedLinkCount()).isZero();
        assertThat(soLienKetCua(bayUserId)).isZero();
    }

    @Test
    @DisplayName("Nhân khẩu chưa có tài khoản thì không cấp lại — hãy phát lời mời")
    void chuaCoTaiKhoanThiKhongCapLai() throws Exception {
        UUID chuaCoTaiKhoan = seed(PersonFixtures.living("Nguyễn Thị Tám")
                .branch(jdbc.queryForObject("SELECT id FROM branch WHERE slug = 'chi_giap'",
                        UUID.class))
                .generation(7));

        capLaiRaw("sub-bon", "BRANCH_HEAD", chuaCoTaiKhoan)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code")
                        .value(MembershipProblemCodes.PASSWORD_RESET_NOT_ALLOWED));
    }

    // =====================================================================================
    // 7. Mật khẩu vi phạm chính sách realm thì liên kết KHÔNG cháy
    // =====================================================================================

    @Test
    @DisplayName("Realm từ chối mật khẩu vì chính sách -> liên kết vẫn còn dùng được")
    void matKhauViPhamChinhSachThiLienKetKhongChay() throws Exception {
        congCapLai.requirePasswordLength(12);
        String token = tokenCua(capLai("sub-bon", "BRANCH_HEAD", bayPersonId));

        datMatKhau(token, "ngan")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(MembershipProblemCodes.VALIDATION_FAILED));
        // Realm CHUA ghi gi, nen lien ket phai duoc tra ve trang thai chua dung: bat mot cu gõ sai
        // mot lan roi phai di xin lai lien ket la hong dung nhom nguoi luong nay phuc vu.
        assertThat(lienKetMoiNhat(bayUserId).get("used_at")).isNull();

        datMatKhau(token, "MatKhauDuDai@2026").andExpect(status().isNoContent());
        assertThat(congCapLai.passwordOf(BAY_SUB)).contains("MatKhauDuDai@2026");
        assertThat(lienKetMoiNhat(bayUserId).get("used_at")).isNotNull();
    }

    // =====================================================================================
    // 8. Hai cổng được tiêm đúng là bản giả — nếu không, mọi ca trên đo nhầm thứ
    // =====================================================================================

    @Test
    @DisplayName("Bản giả thật sự được tiêm vào ứng dụng")
    void banGiaDuocTiem() {
        assertThat(congCapLaiDaTiem).isSameAs(congCapLai);
        assertThat(congDanhTinhDaTiem).isInstanceOf(FakeIdentityProvider.class);
    }

    // =====================================================================================

    private int demThongBaoCua(UUID personId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM notification_inbox WHERE recipient_person_id = ?",
                Integer.class, personId);
        return count == null ? 0 : count;
    }

    /** Chờ một điều kiện bất đồng bộ; không dùng thư viện ngoài để khỏi thêm phụ thuộc. */
    private static void choDen(java.util.function.BooleanSupplier dieuKien)
            throws InterruptedException {
        long han = System.nanoTime() + CHO_TIN.toNanos();
        while (System.nanoTime() < han) {
            if (dieuKien.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Qua " + CHO_TIN.toSeconds()
                + "s ma thong bao van chua toi hop thu cua chinh chu");
    }
}
