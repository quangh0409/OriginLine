package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.giapha.genealogy.domain.PrivacyConsent;
import vn.giapha.genealogy.domain.PrivacyFieldGroup;
import vn.giapha.genealogy.domain.ShareScope;

/**
 * Khách đọc {@code GET /api/v1/events} và {@code GET /api/v1/honours} — quyết định của chủ dự án
 * ngày 26/09/2026: <b>chỉ phần về người đã khuất</b>.
 *
 * <p>Chạy qua chuỗi HTTP thật (Spring Security + MVC) trên PostgreSQL + AGE thật, vì lỗi mà quyết
 * định này sửa nằm đúng ở ranh giới hai tầng: tầng ứng dụng đã có nhánh Khách từ lâu nhưng chuỗi
 * lọc trả {@code 401} trước khi nhánh ấy chạy — không một unit test nào thấy được điều đó.</p>
 *
 * <p>Ca then chốt: người còn sống ở đây đặt <b>mọi</b> nhóm riêng tư ở {@code CLAN}. "Cả họ"
 * nghĩa là những người đã được xác nhận trong họ, không phải người lạ không có tài khoản — nên
 * không một chữ nào của họ được lọt ra, kể cả chữ tự do ({@code title}, {@code location},
 * {@code description}, {@code issuer}). Phép khẳng định soi trên <b>chuỗi thô</b> của thân phản
 * hồi, giống {@code PublicPortalIT}: một trường mới thêm nhầm vào DTO sẽ lọt qua mọi
 * {@code jsonPath} nhưng không lọt qua phép này.</p>
 */
@DisplayName("Khách đọc giỗ/lễ và vinh danh — chỉ người đã khuất (quyết định 26/09/2026)")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class GuestEventsHonoursIT extends AbstractIntegrationTest {

    private static final String TEN_NGUOI_SONG = "Trần Thị Sống Lâu";

    /** Mọi chuỗi tự do gắn với người còn sống — không cái nào được xuất hiện với Khách. */
    private static final String[] DAU_VET_NGUOI_SONG = {
            TEN_NGUOI_SONG,
            "Mừng thọ bà Sống Lâu",
            "Nhà riêng số 7 ngõ Bí Mật",
            "Chuyện riêng của gia đình",
            "Việc riêng gắn người sống",
            "Thạc sĩ Y khoa",
            "Đại học Y Hà Nội",
            "Luận văn về tim mạch",
            "Cưới cháu Minh ở Hải Phòng",
            "Họp họ tại nhà anh Tư",
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private UUID cuTo;
    private UUID nguoiSong;

    private UUID gioCuTo;
    private UUID gioTo;
    private UUID mungThoNguoiSong;
    private UUID khacNguoiSong;
    private UUID cuoiHoiKhongGanNguoi;
    private UUID hopHoKhongGanNguoi;

    private UUID vinhDanhCuTo;
    private UUID vinhDanhCuToChoDuyet;
    private UUID vinhDanhNguoiSong;

    @BeforeEach
    void setUpDongHo() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        UUID chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");

        cuTo = seed(PersonFixtures.deceased("Nguyễn Phúc Thuỷ Tổ", 1950)
                .birthYear(1880).branch(chiGiap).generation(1));

        Map<PrivacyFieldGroup, ShareScope> moHet = new EnumMap<>(PrivacyFieldGroup.class);
        for (PrivacyFieldGroup group : PrivacyFieldGroup.values()) {
            moHet.put(group, ShareScope.CLAN);
        }
        nguoiSong = seed(PersonFixtures.living(TEN_NGUOI_SONG)
                .birthYear(1935).branch(chiGiap).generation(2)
                .consent(PrivacyConsent.of(moHet)));

        UUID hoiDongPerson = seed(PersonFixtures.living("Nguyễn Văn Hội Đồng")
                .birthYear(1960).branch(chiGiap).generation(3));
        UUID hoiDongUser = insertAppUser("sub-hoi-dong", hoiDongPerson);
        assignBranchRole(hoiDongUser, "COUNCIL", null);
        UUID nguoiKhai = insertAppUser("sub-nguoi-khai", null);

        gioCuTo = suKien(cuTo, "GIO", "Giỗ cụ Thuỷ Tổ", 5, 10, null, null, false);
        gioTo = suKien(null, "GIO_TO", "Giỗ Tổ họ Nguyễn", 3, 10, "Từ đường họ Nguyễn", null, true);
        mungThoNguoiSong = suKien(nguoiSong, "MUNG_THO", "Mừng thọ bà Sống Lâu", 2, 15,
                "Nhà riêng số 7 ngõ Bí Mật", "Chuyện riêng của gia đình", false);
        khacNguoiSong = suKien(nguoiSong, "KHAC", "Việc riêng gắn người sống", 4, 4, null, null,
                false);
        cuoiHoiKhongGanNguoi = suKien(null, "CUOI_HOI", "Cưới cháu Minh ở Hải Phòng", 9, 9, null,
                null, true);
        hopHoKhongGanNguoi = suKien(null, "HOP_HO", "Họp họ tại nhà anh Tư", 1, 20, null, null,
                true);

        vinhDanhCuTo = vinhDanh(cuTo, "Tiến sĩ khoa Giáp Thìn", "Triều Nguyễn", "PUBLISHED",
                nguoiKhai, hoiDongUser, "Đỗ đại khoa năm Giáp Thìn");
        vinhDanhCuToChoDuyet = vinhDanh(cuTo, "Cử nhân chờ duyệt", null, "PENDING", nguoiKhai,
                null, null);
        vinhDanhNguoiSong = vinhDanh(nguoiSong, "Thạc sĩ Y khoa", "Đại học Y Hà Nội",
                "PUBLISHED", nguoiKhai, hoiDongUser, "Luận văn về tim mạch");

        authenticateAsGuest();
    }

    // =====================================================================================
    // /events
    // =====================================================================================

    @Test
    @DisplayName("khách thấy giỗ cụ đã khuất và giỗ Tổ; không thấy việc nào của người sống")
    void khach_danhSachSuKien_chiNguoiDaKhuat() throws Exception {
        MvcResult result = goi(get("/api/v1/events").param("upcomingDays", "400").param("size", "100"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = than(result);
        assertThat(body).contains(gioCuTo.toString(), gioTo.toString(), "Giỗ cụ Thuỷ Tổ",
                "Giỗ Tổ họ Nguyễn");
        assertThat(body).doesNotContain(mungThoNguoiSong.toString(), khacNguoiSong.toString(),
                cuoiHoiKhongGanNguoi.toString(), hopHoKhongGanNguoi.toString());
        khongCoDauVetNguoiSong(body);
    }

    @Test
    @DisplayName("khách mở chi tiết: sự kiện người đã khuất 200, của người sống 404 như id bịa")
    void khach_chiTietSuKien() throws Exception {
        MvcResult daKhuat = goi(get("/api/v1/events/{id}", gioCuTo));
        assertThat(daKhuat.getResponse().getStatus()).isEqualTo(200);
        assertThat(than(daKhuat)).contains("Giỗ cụ Thuỷ Tổ");

        for (UUID id : new UUID[] {mungThoNguoiSong, khacNguoiSong, cuoiHoiKhongGanNguoi,
                hopHoKhongGanNguoi}) {
            MvcResult an = goi(get("/api/v1/events/{id}", id));
            assertThat(an.getResponse().getStatus()).as("su kien %s", id).isEqualTo(404);
            khongCoDauVetNguoiSong(than(an));
        }
    }

    @Test
    @DisplayName("đối chứng: Hội đồng vẫn thấy việc của người sống — tập dữ liệu không rỗng giả")
    void hoiDong_vanThayViecNguoiSong() throws Exception {
        MvcResult result = goi(get("/api/v1/events").param("upcomingDays", "400")
                .param("size", "100").with(hoiDong()));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(than(result)).contains(mungThoNguoiSong.toString(),
                hopHoKhongGanNguoi.toString());
    }

    // =====================================================================================
    // /honours
    // =====================================================================================

    @Test
    @DisplayName("khách thấy vinh danh đã duyệt của cụ đã khuất; tổng đếm không lộ người sống")
    void khach_danhSachVinhDanh_chiNguoiDaKhuat() throws Exception {
        MvcResult result = goi(get("/api/v1/honours").param("size", "100"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = than(result);
        JsonNode tree = json.readTree(body);
        assertThat(tree.path("items")).hasSize(1);
        assertThat(tree.path("items").get(0).path("id").asText()).isEqualTo(vinhDanhCuTo.toString());
        assertThat(tree.path("items").get(0).path("title").asText()).isEqualTo("Tiến sĩ khoa Giáp Thìn");
        assertThat(tree.path("items").get(0).path("reviewedBy").isNull()
                || tree.path("items").get(0).path("reviewedBy").isMissingNode())
                .as("khoa tai khoan nguoi duyet khong di ra cho Khach").isTrue();
        // Tong dem la mot kenh lo rieng: "co 2 vinh danh" khi Khach chi thay 1 da noi rang co mot
        // vinh danh cua nguoi song.
        assertThat(tree.path("page").path("totalElements").asLong()).isEqualTo(1L);
        assertThat(body).doesNotContain(vinhDanhNguoiSong.toString(),
                vinhDanhCuToChoDuyet.toString(), "Cử nhân chờ duyệt");
        khongCoDauVetNguoiSong(body);
    }

    @Test
    @DisplayName("khách hỏi hàng đợi duyệt nhận trang rỗng, không 403")
    void khach_hoiHangDoiDuyet_nhanTrangRong() throws Exception {
        MvcResult result = goi(get("/api/v1/honours").param("status", "PENDING"));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(than(result)).path("items")).isEmpty();
    }

    @Test
    @DisplayName("khách mở chi tiết vinh danh: người sống và bản chờ duyệt đều 404")
    void khach_chiTietVinhDanh() throws Exception {
        MvcResult daKhuat = goi(get("/api/v1/honours/{id}", vinhDanhCuTo));
        assertThat(daKhuat.getResponse().getStatus()).isEqualTo(200);
        assertThat(than(daKhuat)).contains("Tiến sĩ khoa Giáp Thìn");

        for (UUID id : new UUID[] {vinhDanhNguoiSong, vinhDanhCuToChoDuyet}) {
            MvcResult an = goi(get("/api/v1/honours/{id}", id));
            assertThat(an.getResponse().getStatus()).as("vinh danh %s", id).isEqualTo(404);
            khongCoDauVetNguoiSong(than(an));
        }
    }

    @Test
    @DisplayName("đối chứng: Hội đồng thấy vinh danh của người sống đã mở CLAN")
    void hoiDong_thayVinhDanhNguoiSong() throws Exception {
        MvcResult result = goi(get("/api/v1/honours").param("size", "100").with(hoiDong()));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(than(result)).contains(vinhDanhNguoiSong.toString(), "Thạc sĩ Y khoa");
    }

    @Test
    @DisplayName("những lối không thuộc quyết định vẫn đóng: /posts và đếm hàng đợi vinh danh")
    void loiKhongThuocQuyetDinh_vanDong() throws Exception {
        assertThat(goi(get("/api/v1/posts")).getResponse().getStatus()).isEqualTo(401);
        assertThat(goi(get("/api/v1/honours/pending/count")).getResponse().getStatus())
                .isEqualTo(401);
    }

    // =====================================================================================
    // Tiện ích
    // =====================================================================================

    private UUID suKien(UUID personId, String type, String title, int month, int day,
                        String location, String description, boolean clanLevel) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO event (id, person_id, event_type, title, lunar_date,"
                        + " is_lunar_based, is_recurring, is_clan_level, location, description)"
                        + " VALUES (?, ?, ?, ?, CAST(? AS jsonb), TRUE, TRUE, ?, ?, ?)",
                id, personId, type, title,
                "{\"month\":" + month + ",\"day\":" + day + "}", clanLevel, location, description);
        return id;
    }

    private UUID vinhDanh(UUID personId, String title, String issuer, String status,
                          UUID createdBy, UUID reviewedBy, String description) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO honour (id, person_id, kind, title, year, issuer, description,"
                        + " status, created_by, reviewed_by, reviewed_at)"
                        + " VALUES (?, ?, 'DO_DAT', ?, 1904, ?, ?, ?, ?, ?,"
                        + " CASE WHEN CAST(? AS uuid) IS NULL THEN NULL ELSE now() END)",
                id, personId, title, issuer, description, status, createdBy, reviewedBy,
                reviewedBy);
        return id;
    }

    private RequestPostProcessor hoiDong() {
        return jwt().jwt(builder -> builder.subject("sub-hoi-dong"))
                .authorities(new SimpleGrantedAuthority("ROLE_COUNCIL"));
    }

    private MvcResult goi(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder).andReturn();
    }

    private static String than(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private void khongCoDauVetNguoiSong(String body) {
        // Bo "instance" cua RFC 7807: do la URI chinh nguoi goi vua gui (xem PublicPortalIT).
        String daBoInstance = body.replaceAll("\"instance\"\\s*:\\s*\"[^\"]*\"", "\"instance\":\"\"");
        assertThat(daBoInstance).doesNotContain(nguoiSong.toString());
        for (String dauVet : DAU_VET_NGUOI_SONG) {
            assertThat(daBoInstance).as("dau vet nguoi song: %s", dauVet).doesNotContain(dauVet);
        }
    }
}
