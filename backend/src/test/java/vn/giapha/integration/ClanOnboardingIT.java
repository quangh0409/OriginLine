package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.giapha.membership.application.ClanInviteService;
import vn.giapha.membership.application.command.IssueClanInviteCommand;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.membership.domain.port.BranchLookupPort;
import vn.giapha.membership.support.FakeIdentityProvider;
import vn.giapha.membership.support.FakeRealmRoles;

/**
 * <b>"Khởi tạo dòng họ"</b> đi trọn qua HTTP trên hạ tầng thật: khởi tạo → bảng sẵn sàng từng bước
 * → tài khoản Hội đồng → khách đọc hồ sơ; cộng khoá nhãn gốc, khoá lạc quan, và hàm tự điền của V23.
 *
 * <h2>Vì sao phải là test tích hợp</h2>
 * Điều đáng canh nhất của lối này là <b>một lượt lưu = bốn việc trong một transaction</b> — hồ sơ,
 * chi gốc ({@code branch}), bộ luật {@code CLAN} ({@code kinship_rule_set}), {@code audit_log} —
 * ở ba context khác nhau. Không kho giả nào trả lời được "cả bốn cùng vào hay cùng không".
 *
 * <h2>Hai điều phải dọn tay</h2>
 * <ul>
 *   <li>{@code clan_profile} không có khoá ngoại nào nên {@code TRUNCATE branch ... CASCADE} của
 *       lớp cha không kéo nó theo; dọn cả trước <b>và sau</b> mỗi ca — hồ sơ sót lại sẽ đổi tên dòng
 *       họ trong thư mời của các IT membership chạy sau.</li>
 *   <li>{@code kinship_rule_set.branch_id} tham chiếu {@code branch}, nên chính lượt
 *       {@code TRUNCATE ... CASCADE} ấy xoá sạch cả bộ {@code DEFAULT} của R__seed. Bộ luật tầng
 *       {@code CLAN} cần một cha, nên ca nào cũng gieo lại dòng {@code DEFAULT} (id cố định, không
 *       luật — phép nối cha không cần luật).</li>
 * </ul>
 */
@DisplayName("Khởi tạo dòng họ")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class ClanOnboardingIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/clan-profile";
    private static final String DEFAULT_ID = "00000000-0000-0000-0000-0000000000b1";
    private static final ObjectMapper JSON = new ObjectMapper();

    @TestConfiguration
    static class CongGia {

        @Bean
        @Primary
        FakeIdentityProvider fakeIdentityProvider() {
            return new FakeIdentityProvider();
        }

        @Bean
        @Primary
        FakeRealmRoles fakeRealmRoles() {
            return new FakeRealmRoles();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeIdentityProvider realm;

    @Autowired
    private FakeRealmRoles realmRoles;

    @Autowired
    private ClanInviteService clanInvites;

    @Autowired
    private BranchLookupPort branchLookup;

    @BeforeEach
    void donVaGieo() {
        jdbc.execute("DELETE FROM clan_profile");
        jdbc.execute("TRUNCATE TABLE invitation_attempt RESTART IDENTITY");
        realmRoles.reset();
        jdbc.update("""
                INSERT INTO kinship_rule_set (id, code, name, scope, is_active)
                VALUES (CAST(? AS uuid), 'DEFAULT_BAC', 'Danh xưng mặc định — miền Bắc', 'DEFAULT', TRUE)
                ON CONFLICT (code) DO NOTHING
                """, DEFAULT_ID);
    }

    @AfterEach
    void donHoSo() {
        jdbc.execute("DELETE FROM clan_profile");
    }

    // =====================================================================================
    // Tiện ích
    // =====================================================================================

    private static RequestPostProcessor quanTri() {
        return jwt().jwt(b -> b.subject("sub-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor hoiDong() {
        return jwt().jwt(b -> b.subject("sub-hoi-dong"))
                .authorities(new SimpleGrantedAuthority("ROLE_COUNCIL"));
    }

    private static String hoSo(String name, String root, String region) {
        return """
                {"name": "%s", "shortName": "Nguyễn Đình", "region": "%s", "rootBranchPath": "%s",
                 "ancestralHall": {"name": "Từ đường họ Nguyễn Đình", "address": "Đông Ngạc, Hà Nội"},
                 "ancestorName": "Nguyễn Đình Công", "foundedNote": "Khai canh làng Đông Ngạc",
                 "gioTo": {"lunarDay": 12, "lunarMonth": 3}}
                """.formatted(name, region, root);
    }

    private MvcResult ghi(String body, String ifMatch) throws Exception {
        var request = put(BASE).with(quanTri()).contentType(MediaType.APPLICATION_JSON).content(body);
        if (ifMatch != null) {
            request = request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return mockMvc.perform(request).andReturn();
    }

    private String khoiTao(String name, String root, String region) throws Exception {
        MvcResult result = ghi(hoSo(name, root, region), null);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(201);
        return result.getResponse().getHeader(HttpHeaders.ETAG);
    }

    private JsonNode sanSang() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/readiness").with(quanTri()))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static JsonNode buoc(JsonNode readiness, String key) {
        for (JsonNode step : readiness.path("steps")) {
            if (key.equals(step.path("key").asText())) {
                return step;
            }
        }
        throw new AssertionError("Khong co buoc " + key);
    }

    private UUID chiGoc(String label) {
        return jdbc.queryForObject("SELECT id FROM branch WHERE path = CAST(? AS ltree)",
                UUID.class, label);
    }

    // =====================================================================================
    // Chưa khởi tạo
    // =====================================================================================

    @Test
    @DisplayName("chưa khởi tạo: khách nhận 200 initialized=false không kèm trường nào; quản trị nhận 404 CLAN_NOT_INITIALIZED")
    void chuaKhoiTao() throws Exception {
        mockMvc.perform(get("/api/v1/public/clan-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialized").value(false))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.rootBranchPath").doesNotExist());

        mockMvc.perform(get(BASE).with(quanTri()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAN_NOT_INITIALIZED"));
    }

    @Test
    @DisplayName("chỉ ADMIN: Hội đồng gọi màn khởi tạo nhận 403, khách nhận 401")
    void chiQuanTri() throws Exception {
        mockMvc.perform(put(BASE).with(hoiDong()).contentType(MediaType.APPLICATION_JSON)
                        .content(hoSo("Họ Lê", "ho_le", "BAC")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/readiness")).andExpect(status().isUnauthorized());
        assertThat(countRows("clan_profile")).isZero();
    }

    // =====================================================================================
    // Luồng trọn vẹn
    // =====================================================================================

    @Test
    @DisplayName("khởi tạo → sẵn sàng từng bước → Hội đồng → nhập phả → mã mời → khách đọc hồ sơ")
    void luongTronVen() throws Exception {
        JsonNode trang = sanSang();
        assertThat(trang.path("profile").path("done").asBoolean()).isFalse();
        assertThat(buoc(trang, "profile").path("hint").asText()).contains("khởi tạo");
        assertThat(buoc(trang, "done").path("done").asBoolean()).isFalse();

        // (1) KHOI TAO — mien Trung, ban cai trang (chua co chi nao).
        String etag = khoiTao("Họ Nguyễn Đình", "ho_nguyen_dinh", "TRUNG");
        assertThat(etag).isEqualTo("\"0\"");

        // Chi goc DONG_HO da duoc dung, cung ten va cung vung voi ho so.
        Map<String, Object> goc = jdbc.queryForMap(
                "SELECT id, name, slug, branch_kind, region FROM branch WHERE path = 'ho_nguyen_dinh'");
        assertThat(goc).containsEntry("name", "Họ Nguyễn Đình").containsEntry("slug", "ho_nguyen_dinh")
                .containsEntry("branch_kind", "DONG_HO").containsEntry("region", "TRUNG");
        // Bo luat CLAN gan chi goc, ke thua DEFAULT (chua co bo REGION TRUNG nao dang bat).
        Map<String, Object> boLuat = jdbc.queryForMap(
                "SELECT code, region, parent_id FROM kinship_rule_set WHERE scope = 'CLAN' AND branch_id = ?",
                goc.get("id"));
        assertThat(boLuat).containsEntry("code", "CLAN_HO_NGUYEN_DINH").containsEntry("region", "TRUNG");
        assertThat(boLuat.get("parent_id")).hasToString(DEFAULT_ID);
        // Ghi audit_log trong cung transaction.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_type = 'ClanProfile' AND action = 'CREATE'",
                Integer.class)).isEqualTo(1);

        JsonNode sauHoSo = sanSang();
        assertThat(sauHoSo.path("profile").path("done").asBoolean()).isTrue();
        assertThat(sauHoSo.path("profile").path("name").asText()).isEqualTo("Họ Nguyễn Đình");
        assertThat(sauHoSo.path("rootBranch").path("path").asText()).isEqualTo("ho_nguyen_dinh");
        assertThat(sauHoSo.path("kinshipRuleSet").path("region").asText()).isEqualTo("TRUNG");
        assertThat(sauHoSo.path("kinshipRuleSet").path("customized").asBoolean()).isFalse();
        assertThat(buoc(sauHoSo, "council").path("done").asBoolean()).isFalse();

        // (2) HOI DONG — email, gan ngay voi mot nhan khau con song.
        UUID tocTruong = seed(PersonFixtures.living("Nguyễn Đình Tộc").branch(chiGoc("ho_nguyen_dinh"))
                .generation(12));
        MvcResult lap = mockMvc.perform(post(BASE + "/council-accounts").with(quanTri())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "Cụ Tộc trưởng", "loginId": "toc.truong@example.vn",
                                 "personId": "%s"}""".formatted(tocTruong)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("COUNCIL"))
                .andExpect(jsonPath("$.setPasswordUrl").value(org.hamcrest.Matchers.startsWith(
                        "https://giapha.test/dat-mat-khau?token=")))
                .andExpect(jsonPath("$.setPasswordExpiresAt").exists())
                .andReturn();
        JsonNode taiKhoan = JSON.readTree(lap.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String sub = taiKhoan.path("keycloakSub").asText();
        UUID appUserId = UUID.fromString(taiKhoan.path("appUserId").asText());

        assertThat(realmRoles.rolesOf(sub)).containsExactly(RoleCode.COUNCIL);
        assertThat(jdbc.queryForMap("SELECT person_id, status, email FROM app_user WHERE id = ?",
                appUserId))
                .containsEntry("person_id", tocTruong).containsEntry("status", "ACTIVE")
                .containsEntry("email", "toc.truong@example.vn");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM branch_assignment ba JOIN role r ON r.id = ba.role_id
                 WHERE ba.app_user_id = ? AND r.code = 'COUNCIL' AND ba.branch_id IS NULL""",
                Integer.class, appUserId)).as("pham vi TOAN dong ho").isEqualTo(1);
        // Lien ket la bi mat: khong nam trong audit_log.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE after::text LIKE '%dat-mat-khau%'", Integer.class))
                .isZero();

        JsonNode sauHoiDong = sanSang();
        assertThat(sauHoiDong.path("council").path("count").asInt()).isEqualTo(1);
        assertThat(buoc(sauHoiDong, "council").path("done").asBoolean()).isTrue();
        assertThat(buoc(sauHoiDong, "import").path("done").asBoolean())
                .as("cu Toc truong da la mot nhan khau").isTrue();
        assertThat(sauHoiDong.path("persons").path("count").asLong()).isEqualTo(1);
        assertThat(buoc(sauHoiDong, "invite").path("done").asBoolean()).isFalse();

        // (3) MA MOI — chinh tai khoan Hoi dong vua lap phat ma.
        authenticateAs(sub, "COUNCIL");
        clanInvites.issue(new IssueClanInviteCommand("Nhóm Zalo họ Nguyễn Đình"));
        authenticateAsGuest();

        JsonNode xong = sanSang();
        assertThat(xong.path("inviteCodes").path("active").asInt()).isEqualTo(1);
        assertThat(xong.path("steps")).allSatisfy(step ->
                assertThat(step.path("done").asBoolean()).as(step.toString()).isTrue());
        assertThat(buoc(xong, "done").path("hint").asText()).isEqualTo("Dòng họ đã sẵn sàng sử dụng.");

        // (4) KHACH doc ho so — khong can token.
        mockMvc.perform(get("/api/v1/public/clan-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialized").value(true))
                .andExpect(jsonPath("$.name").value("Họ Nguyễn Đình"))
                .andExpect(jsonPath("$.rootBranchPath").value("ho_nguyen_dinh"))
                .andExpect(jsonPath("$.region").value("TRUNG"))
                .andExpect(jsonPath("$.ancestralHall.name").value("Từ đường họ Nguyễn Đình"))
                .andExpect(jsonPath("$.gioTo.lunarDay").value(12))
                .andExpect(jsonPath("$.gioTo.lunarMonth").value(3))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist());
    }

    // =====================================================================================
    // Khoá lạc quan
    // =====================================================================================

    @Test
    @DisplayName("sửa hồ sơ đã có: thiếu If-Match → 412, lệch → 409, đúng → 200 và ETag nhích")
    void khoaLacQuan() throws Exception {
        String etag = khoiTao("Họ Lê", "ho_le", "NAM");

        MvcResult thieu = ghi(hoSo("Họ Lê Văn", "ho_le", "NAM"), null);
        assertThat(thieu.getResponse().getStatus()).isEqualTo(412);
        assertThat(JSON.readTree(thieu.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("code").asText())
                .isEqualTo("PRECONDITION_REQUIRED");

        MvcResult lech = ghi(hoSo("Họ Lê Văn", "ho_le", "NAM"), "\"41\"");
        assertThat(lech.getResponse().getStatus()).isEqualTo(409);
        assertThat(JSON.readTree(lech.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("code").asText())
                .isEqualTo("OPTIMISTIC_LOCK_CONFLICT");

        MvcResult dung = ghi(hoSo("Họ Lê Văn", "ho_le", "NAM"), "W/" + etag);
        assertThat(dung.getResponse().getStatus()).isEqualTo(200);
        assertThat(dung.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo("\"1\"");

        mockMvc.perform(get(BASE).with(quanTri()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.name").value("Họ Lê Văn"))
                .andExpect(jsonPath("$.version").value(1));
        // Ten chi goc dong bo theo ten dong ho.
        assertThat(jdbc.queryForObject("SELECT name FROM branch WHERE path = 'ho_le'", String.class))
                .isEqualTo("Họ Lê Văn");
    }

    // =====================================================================================
    // Khoá nhãn gốc
    // =====================================================================================

    @Test
    @DisplayName("phả đã có người (kể cả đã xoá mềm) → đổi rootBranchPath nhận 409 CLAN_ROOT_LOCKED, không gì đổi")
    void khoaNhanGoc() throws Exception {
        String etag = khoiTao("Họ Lê", "ho_le", "NAM");
        UUID nguoi = seed(PersonFixtures.living("Lê Văn Một").branch(chiGoc("ho_le")).generation(3));
        jdbc.update("UPDATE person SET is_deleted = TRUE, deleted_at = now() WHERE id = ?", nguoi);

        MvcResult doi = ghi(hoSo("Họ Lê", "ho_le_moi", "NAM"), etag);
        assertThat(doi.getResponse().getStatus()).isEqualTo(409);
        assertThat(JSON.readTree(doi.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("code").asText())
                .isEqualTo("CLAN_ROOT_LOCKED");

        assertThat(jdbc.queryForObject("SELECT root_branch_path::text FROM clan_profile", String.class))
                .isEqualTo("ho_le");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM branch WHERE path = 'ho_le_moi'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("phả chưa có ai → đổi nhãn gốc đổi cả cây chi con, không dựng gốc thứ hai")
    void doiNhanKhiChuaCoNguoi() throws Exception {
        String etag = khoiTao("Họ Lê", "ho_le", "NAM");
        insertBranch("Chi Giáp", "ho_le.chi_giap", chiGoc("ho_le"), "CHI");

        MvcResult doi = ghi(hoSo("Họ Lê", "ho_le_moi", "NAM"), etag);
        assertThat(doi.getResponse().getStatus()).as(doi.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(200);

        List<String> paths = jdbc.queryForList("SELECT path::text FROM branch ORDER BY path",
                String.class);
        assertThat(paths).containsExactly("ho_le_moi", "ho_le_moi.chi_giap");
        assertThat(sanSang().path("branches").path("count").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("lần khởi tạo đầu trên một cây đã có gốc khác nhãn, đã có người → 409, không dựng gốc thứ hai")
    void khoiTaoTrenCayDaCoNguoi() throws Exception {
        UUID goc = insertBranch("Họ Trần", "ho_tran", null, "DONG_HO");
        seed(PersonFixtures.living("Trần Văn Một").branch(goc).generation(2));

        MvcResult result = ghi(hoSo("Họ Trần", "ho_tran_moi", "BAC"), null);
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(countRows("clan_profile")).isZero();
        assertThat(jdbc.queryForList("SELECT path::text FROM branch", String.class))
                .containsExactly("ho_tran");
    }

    // =====================================================================================
    // Bộ luật danh xưng tầng CLAN
    // =====================================================================================

    @Test
    @DisplayName("đổi vùng: luật Hội đồng đã sửa ở tầng CLAN giữ nguyên, chỉ cha kế thừa đổi sang bộ REGION đang bật")
    void doiVungKhongGhiDeLuatHoiDong() throws Exception {
        String etag = khoiTao("Họ Lê", "ho_le", "BAC");
        UUID clanSet = jdbc.queryForObject(
                "SELECT id FROM kinship_rule_set WHERE scope = 'CLAN' AND branch_id = ?", UUID.class,
                chiGoc("ho_le"));
        // Hoi dong tu khai mot luat rieng o tang CLAN.
        jdbc.update("""
                INSERT INTO kinship_rule (rule_set_id, relation_code, gen_delta, collateral_degree,
                                          side, gender, title, priority)
                VALUES (?, 'CHA', 1, 0, 'BLOOD', 'MALE', 'thầy', 10)""", clanSet);
        UUID luatId = jdbc.queryForObject(
                "SELECT id FROM kinship_rule WHERE rule_set_id = ?", UUID.class, clanSet);
        // Bo REGION NAM dang bat.
        UUID vungNam = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kinship_rule_set (id, code, name, scope, parent_id, region, is_active)
                VALUES (?, 'REGION_NAM_TEST', 'Miền Nam', 'REGION', CAST(? AS uuid), 'NAM', TRUE)""",
                vungNam, DEFAULT_ID);

        MvcResult doi = ghi(hoSo("Họ Lê", "ho_le", "NAM"), etag);
        assertThat(doi.getResponse().getStatus()).as(doi.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(200);

        Map<String, Object> sau = jdbc.queryForMap(
                "SELECT id, parent_id, region FROM kinship_rule_set WHERE scope = 'CLAN'");
        assertThat(sau).containsEntry("id", clanSet).containsEntry("parent_id", vungNam)
                .containsEntry("region", "NAM");
        assertThat(jdbc.queryForList("SELECT id FROM kinship_rule WHERE rule_set_id = ?", UUID.class,
                clanSet)).as("luat cua Hoi dong: cung id, khong bi xoa-chen lai").containsExactly(luatId);

        JsonNode readiness = sanSang();
        assertThat(readiness.path("kinshipRuleSet").path("customized").asBoolean()).isTrue();
        assertThat(readiness.path("kinshipRuleSet").path("region").asText()).isEqualTo("NAM");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_type = 'KinshipRuleSet' AND action = 'UPDATE'",
                Integer.class)).isEqualTo(1);
    }

    // =====================================================================================
    // Tài khoản Hội đồng
    // =====================================================================================

    @Test
    @DisplayName("định danh đã có chủ → 422 IDENTITY_ALREADY_REGISTERED, không gắn vai, không ghi gì")
    void dinhDanhDaCoChu() throws Exception {
        realm.seed("da.co@example.vn", true);
        mockMvc.perform(post(BASE + "/council-accounts").with(quanTri())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"Ông Ba\", \"loginId\": \"da.co@example.vn\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDENTITY_ALREADY_REGISTERED"));
        assertThat(realmRoles.calls()).isEmpty();
        assertThat(countRows("app_user")).isZero();
    }

    @Test
    @DisplayName("Hội đồng không tự lập thêm Hội đồng: chỉ ADMIN")
    void hoiDongKhongTuLap() throws Exception {
        // Realm gia la bean dung chung ca lop — dem truoc/sau thay vi doi no rong.
        int truoc = realm.size();
        mockMvc.perform(post(BASE + "/council-accounts").with(hoiDong())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"Ông Ba\", \"loginId\": \"ong.ba@example.vn\"}"))
                .andExpect(status().isForbidden());
        assertThat(realm.size()).as("403 xay ra TRUOC moi loi goi Keycloak").isEqualTo(truoc);
    }

    // =====================================================================================
    // Tên dòng họ cho context khác
    // =====================================================================================

    @Test
    @DisplayName("tên dòng họ trong thư mời lấy từ HỒ SƠ, không từ tên chi gốc")
    void tenDongHoLayTuHoSo() throws Exception {
        khoiTao("Họ Nguyễn Đình làng Đông Ngạc", "ho_nguyen_dinh", "BAC");
        // Ai do sua tay ten chi goc — ho so van la nguon chan ly.
        jdbc.update("UPDATE branch SET name = 'Tên chi gốc cũ' WHERE path = 'ho_nguyen_dinh'");
        assertThat(branchLookup.clanName()).contains("Họ Nguyễn Đình làng Đông Ngạc");

        jdbc.execute("DELETE FROM clan_profile");
        assertThat(branchLookup.clanName()).as("chua co ho so thi lui ve ten chi goc")
                .contains("Tên chi gốc cũ");
    }

    // =====================================================================================
    // V23 tự điền
    // =====================================================================================

    @Test
    @DisplayName("V23 tự điền hồ sơ từ gốc ltree đang chạy — bản cài cũ không rơi về initialized=false")
    void migrationTuDien() throws Exception {
        UUID goc = insertBranch("Họ Nguyễn Đình", "ho_nguyen_dinh", null, "DONG_HO");
        jdbc.update("UPDATE branch SET region = 'BAC' WHERE id = ?", goc);
        insertBranch("Chi Giáp", "ho_nguyen_dinh.chi_giap", goc, "CHI");
        insertBranch("Chi Ất", "ho_nguyen_dinh.chi_at", goc, "CHI");

        assertThat(jdbc.queryForObject("SELECT clan_profile_seed_from_branches()", Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT clan_profile_seed_from_branches()", Boolean.class))
                .as("lan hai khong lam gi").isFalse();

        mockMvc.perform(get("/api/v1/public/clan-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialized").value(true))
                .andExpect(jsonPath("$.name").value("Họ Nguyễn Đình"))
                .andExpect(jsonPath("$.rootBranchPath").value("ho_nguyen_dinh"))
                .andExpect(jsonPath("$.region").value("BAC"));

        // Ho so tu dien CHUA co bo luat CLAN: bang san sang noi dung viec can lam.
        JsonNode readiness = sanSang();
        assertThat(readiness.path("rootBranch").path("done").asBoolean()).isTrue();
        assertThat(readiness.path("kinshipRuleSet").path("done").asBoolean()).isFalse();
        assertThat(readiness.path("branches").path("count").asLong()).isEqualTo(2);
        assertThat(buoc(readiness, "profile").path("hint").asText()).contains("bấm Lưu một lần");

        // Bam Luu mot lan (voi ETag cua ho so tu dien) la du.
        String etag = mockMvc.perform(get(BASE).with(quanTri())).andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        MvcResult luu = ghi(hoSo("Họ Nguyễn Đình", "ho_nguyen_dinh", "BAC"), etag);
        assertThat(luu.getResponse().getStatus()).as(luu.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(200);
        assertThat(buoc(sanSang(), "profile").path("done").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("V23 trên bản cài trắng (không có chi nào) thì không tự điền gì")
    void migrationBanCaiTrang() {
        assertThat(jdbc.queryForObject("SELECT clan_profile_seed_from_branches()", Boolean.class))
                .isFalse();
        assertThat(countRows("clan_profile")).isZero();
    }
}
