package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.giapha.genealogy.application.BranchQueryService;
import vn.giapha.genealogy.application.view.BranchRef;

/**
 * <b>{@code Query.branches}</b> — trường đã khai trong schema từ đầu nhưng chưa từng có resolver,
 * nên mọi lượt hỏi đều chết bằng {@code "declared as a non null type, but ... returned a null
 * value"}. Bốn màn đang dùng nó; riêng ô chọn <i>phạm vi</i> khi tạo việc họ là trường bắt buộc,
 * nên thiếu nó thì Trưởng chi không tạo được sự kiện nào.
 *
 * <h2>Vì sao mọi ca đi qua HTTP</h2>
 * Hai thứ chỉ tồn tại ở tầng cổng: bản thân việc <i>có</i> resolver (thiếu nó thì lỗi nằm ở
 * {@code errors[]}, không phải ở một ngoại lệ Java nào), và {@code memberCount} là {@code Int!} —
 * trả {@code null} cho một chi rỗng sẽ làm hỏng <b>cả</b> truy vấn chứ không riêng một dòng. Gọi
 * thẳng service thì cả hai đều xanh mà không kiểm được gì.
 *
 * <h2>Bản đồ dữ liệu — mỗi nhân khẩu tồn tại để phá đúng một luật</h2>
 * <pre>
 * goc                      (Dòng họ Nguyễn)
 *   ├── goc.chi_giap       Chi Giáp   — 1 đã khuất, 1 còn sống
 *   │     └── goc.chi_giap.nganh_a    — 1 còn sống   (chứng minh "và các nhánh con")
 *   ├── goc.chi_at         Chi Ất     — 1 đã khuất, 1 CÒN SỐNG đã xoá mềm
 *   └── goc.chi_cu         Chi Cũ     — chi đã xoá mềm, không được xuất hiện
 * </pre>
 * cộng một nhân khẩu <b>chưa gắn chi</b>, thứ không được cộng vào bộ đếm của bất kỳ chi nào.
 */
@DisplayName("Query.branches: cây chi/ngành, rootPath cắt cây con, memberCount đếm SAU bộ lọc")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class BranchesGraphQlIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String QUERY = """
            query BranchesQuery($rootPath: String) {
              branches(rootPath: $rootPath) { id name path region memberCount }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BranchQueryService branchQuery;

    private UUID goc;
    private UUID chiGiap;
    private UUID nganhA;
    private UUID chiAt;
    private UUID chiDaXoa;

    @BeforeEach
    void setUpDongHo() {
        goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        nganhA = insertBranch("Ngành A", "goc.chi_giap.nganh_a", chiGiap, "NGANH");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");
        chiDaXoa = insertBranch("Chi Cũ", "goc.chi_cu", goc, "CHI");
        jdbc.update("UPDATE branch SET is_deleted = TRUE WHERE id = ?", chiDaXoa);
        // Vùng miền quyết định bộ quy tắc danh xưng cấp REGION; client hỏi trường này nên nó phải
        // đi hết đường từ cột `branch.region` ra JSON.
        jdbc.update("UPDATE branch SET region = 'BAC' WHERE id = ?", chiGiap);

        seed(PersonFixtures.deceased("Nguyễn Phúc Tổ Giáp", 1950)
                .birthYear(1880).generation(1).branch(chiGiap));
        seed(PersonFixtures.living("Nguyễn Văn Sống Giáp")
                .birthYear(1975).generation(4).branch(chiGiap));
        seed(PersonFixtures.living("Nguyễn Văn Ngành A")
                .birthYear(1980).generation(5).branch(nganhA));
        seed(PersonFixtures.deceased("Nguyễn Phúc Tổ Ất", 1955)
                .birthYear(1885).generation(1).branch(chiAt));

        UUID xoaMemConSong = seed(PersonFixtures.living("Nguyễn Văn Trùng Bản")
                .birthYear(1979).generation(4).branch(chiAt));
        jdbc.update("UPDATE person SET is_deleted = TRUE, deleted_at = now() WHERE id = ?",
                xoaMemConSong);

        // Chưa gắn chi: "không có chi" KHÔNG phải "thuộc mọi chi" - xem GenealogyAccessGuard.
        seed(PersonFixtures.living("Nguyễn Văn Chưa Gắn Chi").birthYear(1990).generation(5));

        UUID taiKhoanThanhVien = insertAppUser("sub-thanh-vien", null);
        assignBranchRole(taiKhoanThanhVien, "MEMBER", chiGiap);
        UUID taiKhoanTruongChi = insertAppUser("sub-truong-chi-giap", null);
        assignBranchRole(taiKhoanTruongChi, "BRANCH_HEAD", chiGiap);
        insertAppUser("sub-hoi-dong", null);
        authenticateAsGuest();
    }

    // =========================================================================================
    // Có resolver, và trả đúng danh sách theo thứ tự ổn định
    // =========================================================================================

    @Test
    @DisplayName("Trả đủ chi chưa xoá, sắp ổn định theo ltree path — không còn lỗi non-null trả null")
    void traDuChi_sapOnDinhTheoPath() throws Exception {
        JsonNode than = goi(thanhVien(), null);

        assertThat(than.path("errors").isMissingNode())
                .as("trường khai trong schema mà không ai hiện thực thì mọi lượt hỏi đều chết ở"
                        + " errors[], không phải ở dữ liệu: %s", than.path("errors"))
                .isTrue();
        assertThat(paths(than))
                .as("ltree = thứ tự cây: con đứng ngay sau cha, client chỉ cần đếm dấu chấm")
                .containsExactly("goc", "goc.chi_at", "goc.chi_giap", "goc.chi_giap.nganh_a");
        assertThat(paths(goi(thanhVien(), null)))
                .as("hai lượt gọi cùng tham số phải cho cùng một thứ tự")
                .isEqualTo(paths(than));
    }

    @Test
    @DisplayName("Tên có dấu và region đi trọn đường ra JSON; chi đã xoá mềm không xuất hiện")
    void tenCoDau_region_vaChiDaXoa() throws Exception {
        JsonNode than = goi(thanhVien(), null);
        Map<String, JsonNode> theoPath = theoPath(than);

        assertThat(theoPath.get("goc.chi_giap").path("name").asText())
                .as("path sinh từ slug không dấu; tên hiển thị có dấu nằm ở name")
                .isEqualTo("Chi Giáp");
        assertThat(theoPath.get("goc.chi_giap").path("region").asText()).isEqualTo("BAC");
        assertThat(theoPath.get("goc.chi_at").path("region").isNull()).isTrue();
        assertThat(theoPath).doesNotContainKey("goc.chi_cu");
        assertThat(theoPath.get("goc.chi_giap").path("id").asText()).isEqualTo(chiGiap.toString());
    }

    // =========================================================================================
    // rootPath cắt đúng cây con
    // =========================================================================================

    @Test
    @DisplayName("rootPath lấy chính chi ấy VÀ mọi nhánh dưới nó, không lấy chi anh em")
    void rootPath_catDungCayCon() throws Exception {
        assertThat(paths(goi(thanhVien(), "goc.chi_giap")))
                .as("<@ đọc là 'nằm trong': gồm cả chính nó. Viết ngược thành @> sẽ ra tổ tiên")
                .containsExactly("goc.chi_giap", "goc.chi_giap.nganh_a");

        assertThat(paths(goi(thanhVien(), "goc.chi_at"))).containsExactly("goc.chi_at");
        assertThat(paths(goi(thanhVien(), "goc.chi_giap.nganh_a")))
                .containsExactly("goc.chi_giap.nganh_a");
    }

    @Test
    @DisplayName("rootPath không tồn tại hoặc không phải ltree hợp lệ: danh sách rỗng, không phải 500")
    void rootPath_laKhongHopLe_traRong() throws Exception {
        assertThat(paths(goi(thanhVien(), "goc.khong_co_that"))).isEmpty();
        JsonNode than = goi(thanhVien(), "Chi Giáp");
        assertThat(than.path("errors").isMissingNode())
                .as("chuỗi lạ từ client không được thành INTERNAL_ERROR trần trụi").isTrue();
        assertThat(paths(than)).isEmpty();
    }

    // =========================================================================================
    // memberCount — cộng dồn nhánh con, bỏ người chưa gắn chi
    // =========================================================================================

    @Test
    @DisplayName("memberCount gồm cả nhánh con; người chưa gắn chi không thuộc bộ đếm nào")
    void memberCount_congDonNhanhCon_boNguoiChuaGanChi() throws Exception {
        Map<String, Integer> counts = counts(goi(thanhVien(), null));

        assertThat(counts.get("goc.chi_giap.nganh_a")).isEqualTo(1);
        assertThat(counts.get("goc.chi_giap"))
                .as("1 tổ + 1 người sống ở chi + 1 người ở ngành con")
                .isEqualTo(3);
        assertThat(counts.get("goc.chi_at"))
                .as("người còn sống đã xoá mềm không tồn tại với Thành viên")
                .isEqualTo(1);
        assertThat(counts.get("goc"))
                .as("người chưa gắn chi không được cộng cho gốc: 'không có chi' không phải"
                        + " 'thuộc mọi chi'")
                .isEqualTo(4);
    }

    // =========================================================================================
    // CA QUAN TRỌNG NHẤT — bộ đếm đã thật sự đi qua bộ lọc
    // =========================================================================================

    /**
     * Bộ đếm phải <b>khác nhau giữa hai người gọi</b>, nếu không thì nó chỉ là một con số trông có
     * vẻ đúng.
     *
     * <h2>Vì sao trục phân biệt ở đây là bản ghi đã xoá mềm chứ là gì khác</h2>
     * Luật đang chạy ({@code PrivacyTierService.canSee}) có đúng hai mệnh đề: <i>Khách không thấy
     * người còn sống</i> và <i>bản ghi xoá mềm chỉ vai toàn dòng họ thấy</i>. Cổng GraphQL đòi
     * token nên Khách không tới được đường này, và <b>mọi tài khoản đã đăng nhập đều thấy người
     * còn sống tồn tại</b> (Tầng 1: tên + đời + quan hệ lõi). Vậy trên đường HTTP, trục duy nhất
     * mà hai người gọi thật sự lệch nhau là bản ghi xoá mềm — và ở đây nó là một người <b>còn
     * sống</b> đã xoá mềm, nên câu "trong chi có người còn sống mà người gọi hạn chế không đếm
     * được" đúng theo nghĩa đen.
     *
     * <p>Mệnh đề còn lại (Khách) được phủ bởi {@link #khach_khongDemDuocNguoiConSong} vì nó không
     * biểu diễn được qua HTTP.</p>
     */
    @Test
    @DisplayName("memberCount của Thành viên NHỎ HƠN của Hội đồng — bộ đếm đi qua bộ lọc thật")
    void memberCountCuaThanhVien_nhoHonCuaHoiDong() throws Exception {
        Map<String, Integer> cuaThanhVien = counts(goi(thanhVien(), null));
        Map<String, Integer> cuaHoiDong = counts(goi(hoiDong(), null));

        assertThat(cuaHoiDong.get("goc.chi_at"))
                .as("Hội đồng thấy bản ghi đã xoá mềm nên đếm cả người ấy")
                .isEqualTo(2);
        assertThat(cuaThanhVien.get("goc.chi_at"))
                .as("đếm TRƯỚC bộ lọc thì con số này cũng bằng 2 và chi Ất vừa nói ra một người"
                        + " mà Thành viên không được biết là tồn tại")
                .isEqualTo(1)
                .isLessThan(cuaHoiDong.get("goc.chi_at"));
        assertThat(cuaThanhVien.get("goc")).isLessThan(cuaHoiDong.get("goc"));
        assertThat(cuaThanhVien.get("goc.chi_giap"))
                .as("chi không có bản ghi ẩn thì hai vai phải thấy cùng một con số — nếu lệch,"
                        + " bộ lọc đang giấu nhầm người")
                .isEqualTo(cuaHoiDong.get("goc.chi_giap"));
    }

    /**
     * Mệnh đề thứ hai của cùng một luật, kiểm ở tầng service vì cổng GraphQL đòi token.
     *
     * <p>Đây là ca mà chú thích trong schema và trong {@code queries.ts} nói tới: một bộ đếm thô
     * sẽ cho Khách biết trong chi ấy có bao nhiêu người còn sống, chỉ bằng cách trừ hai con số.
     * Cổng đang đóng, nhưng luật không được phép phụ thuộc vào việc cổng còn đóng.</p>
     */
    @Test
    @DisplayName("Khách: bộ đếm rụng hết người còn sống, chỉ còn người đã khuất")
    void khach_khongDemDuocNguoiConSong() {
        authenticateAsGuest();
        List<BranchRef> tatCa = branchQuery.list(null);
        Map<UUID, Integer> counts = branchQuery.memberCounts(tatCa);

        assertThat(counts.get(chiGiap))
                .as("chi Giáp còn 1 tổ đã khuất; hai người còn sống không tồn tại với Khách")
                .isEqualTo(1);
        assertThat(counts.get(nganhA))
                .as("ngành A chỉ có người còn sống -> Khách đếm 0, không phải 1")
                .isZero();
        assertThat(counts.get(goc)).isEqualTo(2);
    }

    // =========================================================================================
    // Phạm vi đọc của Trưởng chi
    // =========================================================================================

    /**
     * Trưởng chi hỏi cây con của chi khác thì <b>vẫn nhận đủ danh sách</b>, và nhận con số của
     * chính mình.
     *
     * <p>Lý do là luật đang chạy, không phải một lựa chọn mới ở đây: {@code BranchScopeGuard} và
     * {@code GenealogyAccessGuard} chỉ nói về quyền <b>ghi</b>/<b>duyệt</b>, còn mọi lối
     * <b>đọc</b> siêu dữ liệu chi đang chạy (facet chi của {@code GET /directory}, tham số
     * {@code branchId} của {@code /persons/search}, {@code GET /import/branches}) đều cho mọi
     * thành viên thấy đủ mọi chi. Cắt danh sách theo vai ở riêng cổng này sẽ là luật thứ ba, và
     * nó sẽ làm ba ô lọc của FE (tìm kiếm, sự kiện, vinh danh) mất lựa chọn mà không báo lỗi —
     * màn tạo việc họ tự bớt chi ngoài phạm vi bằng {@code me.managedBranches}, ở client.</p>
     *
     * <p>Thứ bị siết là con số: Trưởng chi không có phạm vi toàn dòng họ nên bộ đếm của họ giống
     * Thành viên, không giống Hội đồng.</p>
     */
    @Test
    @DisplayName("Trưởng chi hỏi rootPath của chi khác: nhận đủ tên chi, nhưng con số là của họ")
    void truongChi_hoiChiKhac_nhanDuTenChi_nhungConSoLaCuaHo() throws Exception {
        JsonNode than = goi(truongChiGiap(), "goc.chi_at");

        assertThat(paths(than))
                .as("tên chi và ltree path là thứ in trong gia phả giấy, không phải dữ liệu cá nhân")
                .containsExactly("goc.chi_at");
        assertThat(counts(than).get("goc.chi_at"))
                .as("Trưởng chi không phải vai toàn dòng họ nên không đếm được bản ghi đã xoá mềm")
                .isEqualTo(1)
                .isEqualTo(counts(goi(thanhVien(), "goc.chi_at")).get("goc.chi_at"));
    }

    // =========================================================================================
    // Tiện ích
    // =========================================================================================

    private JsonNode goi(RequestPostProcessor nguoiGoi, String rootPath) throws Exception {
        Map<String, Object> bien = new LinkedHashMap<>();
        bien.put("rootPath", rootPath);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", QUERY);
        body.put("variables", bien);

        MockHttpServletRequestBuilder request = post("/api/v1/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsBytes(body))
                .with(nguoiGoi);
        MvcResult result = mockMvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> paths(JsonNode than) {
        List<String> paths = new ArrayList<>();
        for (JsonNode branch : than.path("data").path("branches")) {
            paths.add(branch.path("path").asText());
        }
        return paths;
    }

    private static Map<String, JsonNode> theoPath(JsonNode than) {
        Map<String, JsonNode> byPath = new LinkedHashMap<>();
        for (JsonNode branch : than.path("data").path("branches")) {
            byPath.put(branch.path("path").asText(), branch);
        }
        return byPath;
    }

    private static Map<String, Integer> counts(JsonNode than) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JsonNode branch : than.path("data").path("branches")) {
            assertThat(branch.path("memberCount").isInt())
                    .as("memberCount là Int! — null ở một chi làm hỏng cả truy vấn")
                    .isTrue();
            counts.put(branch.path("path").asText(), branch.path("memberCount").asInt());
        }
        return counts;
    }

    private static RequestPostProcessor nhuLa(String sub, String authority) {
        return jwt().jwt(builder -> builder.subject(sub))
                .authorities(new SimpleGrantedAuthority(authority));
    }

    private static RequestPostProcessor thanhVien() {
        return nhuLa("sub-thanh-vien", "ROLE_MEMBER");
    }

    private static RequestPostProcessor truongChiGiap() {
        return nhuLa("sub-truong-chi-giap", "ROLE_BRANCH_HEAD");
    }

    private static RequestPostProcessor hoiDong() {
        return nhuLa("sub-hoi-dong", "ROLE_COUNCIL");
    }
}
