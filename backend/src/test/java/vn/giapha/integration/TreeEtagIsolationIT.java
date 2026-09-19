package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.giapha.genealogy.application.LinkRelationshipService;
import vn.giapha.genealogy.application.command.LinkRelationshipCommand;
import vn.giapha.genealogy.domain.RelType;

/**
 * <b>Bộ nhớ đệm của phả đồ không được trộn hai người gọi</b> — {@code GET /api/v1/tree} qua chuỗi
 * HTTP thật, trên PostgreSQL + Apache AGE + Redis thật.
 *
 * <h2>Hai tầng đệm, hai câu hỏi khác nhau</h2>
 * <ol>
 *   <li><b>{@code ETag} + {@code If-None-Match}</b> — đệm ở phía <i>trình duyệt</i>. Câu hỏi:
 *       {@code ETag} của vai A có bao giờ làm máy chủ trả {@code 304} cho vai B không? Kịch bản
 *       thật là <b>máy tính dùng chung ở nhà thờ họ</b>: hai người nối tiếp nhau trên cùng một
 *       trình duyệt. {@code Cache-Control: private} chặn proxy dùng chung nhưng <b>không</b> chặn
 *       chuyện này.</li>
 *   <li><b>Khoá Redis của khung xương cây</b> — đệm ở phía <i>máy chủ</i>, dùng chung cho mọi
 *       người dùng, nên nếu sai thì nghiêm trọng hơn hẳn. Câu hỏi khác hẳn câu trên, xem
 *       {@link #khungXuongDungChungNhungCayLocRieng()}.</li>
 * </ol>
 *
 * <h2>Vì sao "hai vai khác nhau" chưa phải phép thử đủ mạnh</h2>
 * Hai vai có thể tình cờ nhận <b>đúng một cây</b> — thường gặp ở một nhánh toàn người đã khuất,
 * nơi bộ lọc riêng tư không cắt gì cả. Khi đó cùng {@code ETag} là <i>đúng</i>. Cái sai là để
 * {@code ETag} <b>chỉ</b> phụ thuộc nội dung: sự trùng khớp hôm nay biến thành {@code 304} sai cho
 * người khác vào ngày dữ liệu tách ra. Vì vậy các ca dưới đây dùng hai <b>người gọi</b> khác nhau
 * chứ không chỉ hai vai, và ghim cả ca <i>cùng vai cùng chi</i> —
 * {@link #cungVaiCungChiNhungKhacNguoi_vanKhongDungChungETag()} — ca duy nhất mà vân tay
 * "vai + phạm vi" (không kèm {@code personId}) sẽ đỏ.
 */
@DisplayName("ETag và đệm phả đồ: 304 không bao giờ trả cho người khác")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class TreeEtagIsolationIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LinkRelationshipService linkRelationship;

    /** Thuỷ tổ đã khuất — gốc dùng chung cho mọi ca, ai cũng nhìn thấy. */
    private UUID thuyTo;
    /** Hai anh em còn sống, <b>cùng chi, cùng vai MEMBER</b> — khác nhau đúng ở chỗ là ai. */
    private UUID anh;
    private UUID em;

    @BeforeEach
    void setUpDongHo() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        UUID chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");

        authenticateAs("sub-admin", "ADMIN");

        thuyTo = seed(PersonFixtures.deceased("Nguyễn Phúc Thuỷ Tổ", 1900)
                .birthYear(1830).generation(1).branch(goc));
        UUID toChi = seed(PersonFixtures.deceased("Nguyễn Văn Tổ Giáp", 1960)
                .birthYear(1880).generation(2).branch(chiGiap));
        anh = seed(PersonFixtures.living("Nguyễn Văn Anh")
                .birthYear(1980).generation(3).branch(chiGiap));
        em = seed(PersonFixtures.living("Nguyễn Văn Em")
                .birthYear(1985).generation(3).branch(chiGiap));

        lamCha(thuyTo, toChi);
        lamCha(toChi, anh);
        lamCha(toChi, em);

        insertAppUser("sub-anh", anh);
        insertAppUser("sub-em", em);
        insertAppUser("sub-hoi-dong", null);

        authenticateAsGuest();
    }

    private void lamCha(UUID cha, UUID con) {
        linkRelationship.link(new LinkRelationshipCommand(cha, con, RelType.PARENT_BIO, null, null,
                null, null, null));
    }

    // =========================================================================================
    // ETag: khoá phải mang cả người gọi
    // =========================================================================================

    @Test
    @DisplayName("hai vai khác nhau, cùng rootId → hai ETag khác nhau")
    void haiVaiKhacNhau_haiETagKhacNhau() throws Exception {
        String etagThanhVien = etag(goi(thanhVien("sub-anh"), null));
        String etagHoiDong = etag(goi(hoiDong(), null));

        assertThat(etagThanhVien).isNotNull();
        assertThat(etagHoiDong).isNotNull();
        assertThat(etagThanhVien)
                .as("cung mot ETag cho hai cay khac nhau la moi cua mot 304 sai nguoi")
                .isNotEqualTo(etagHoiDong);
    }

    @Test
    @DisplayName("ETag của Hội đồng gửi bằng token Thành viên → 200, không phải 304")
    void eTagCuaVaiNayKhongDungDuocChoVaiKia() throws Exception {
        String etagHoiDong = etag(goi(hoiDong(), null));

        MvcResult result = goi(thanhVien("sub-anh"), etagHoiDong);

        assertThat(result.getResponse().getStatus())
                .as("304 o day la may chu noi voi Thanh vien rang ban cay DA LOC CHO HOI DONG"
                        + " ma ho dang giu van con dung")
                .isEqualTo(200);
        // Va than tra ve phai la cay cua CHINH ho, khong phai cua Hoi dong.
        assertThat(than(result).path("rootId").asText()).isEqualTo(thuyTo.toString());
    }

    @Test
    @DisplayName("cùng vai, cùng chi, khác người → vẫn không dùng chung ETag")
    void cungVaiCungChiNhungKhacNguoi_vanKhongDungChungETag() throws Exception {
        // Ca quyet dinh cho cau hoi "dua cai gi vao khoa". Neu van tay chi gom vai + pham vi chi
        // thi hai nguoi nay trung khoa — trong khi cay cua ho KHAC nhau o dung mot node: node cua
        // chinh ho, noi PersonVisibility mo them nhom truong cho chinh chu.
        String etagAnh = etag(goi(thanhVien("sub-anh"), null));
        String etagEm = etag(goi(thanhVien("sub-em"), null));

        assertThat(etagAnh).isNotEqualTo(etagEm);

        assertThat(goi(thanhVien("sub-em"), etagAnh).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("đối chứng: cùng một người gọi hai lần → vẫn 304, đệm không bị vô hiệu hoá")
    void cungMotNguoiGoi_vanNhan304() throws Exception {
        String lanDau = etag(goi(thanhVien("sub-anh"), null));

        MvcResult lanHai = goi(thanhVien("sub-anh"), lanDau);

        assertThat(lanHai.getResponse().getStatus())
                .as("gan nguoi goi vao khoa ma lam chet han 304 thi la doi mot loi lay mot loi khac")
                .isEqualTo(304);
        assertThat(lanHai.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo(lanDau);
    }

    @Test
    @DisplayName("phản hồi mang Vary: Authorization — private thôi không chặn được máy dùng chung")
    void phanHoiMangVaryAuthorization() throws Exception {
        MvcResult result = goi(thanhVien("sub-anh"), null);

        // Thieu header nay thi bo nho dem cua chinh trinh duyet coi URL la khoa duy nhat: nguoi
        // thu hai dang nhap trong vong max-age se duoc phuc vu lai than cua nguoi thu nhat ma
        // KHONG he goi lai may chu — luc ay ETag co dung cach may cung khong cuu duoc.
        assertThat(result.getResponse().getHeaderValues(HttpHeaders.VARY))
                .anyMatch(value -> String.valueOf(value).contains(HttpHeaders.AUTHORIZATION));

        // 304 cung phai mang Vary: mot phan hoi 304 cap nhat lai metadata cua muc cache da luu.
        assertThat(goi(thanhVien("sub-anh"), etag(result)).getResponse()
                .getHeaderValues(HttpHeaders.VARY))
                .anyMatch(value -> String.valueOf(value).contains(HttpHeaders.AUTHORIZATION));
    }

    // =========================================================================================
    // Khoá Redis của khung xương cây
    // =========================================================================================

    @Test
    @DisplayName("khung xương Redis dùng CHUNG cho mọi vai, nhưng cây trả ra thì lọc riêng")
    void khungXuongDungChungNhungCayLocRieng() throws Exception {
        // Hoi dong goi truoc -> khung xuong duoc nap vao Redis.
        MvcResult cuaHoiDong = goi(hoiDong(), null);
        assertThat(cuaHoiDong.getResponse().getStatus()).isEqualTo(200);

        // Khach goi sau. Neu khoa Redis co mang vai thi day se la mot lan MISS.
        MvcResult cuaKhach = mockMvc.perform(get("/api/v1/public/tree")
                .param("rootId", thuyTo.toString()).param("depth", "3")).andReturn();
        assertThat(cuaKhach.getResponse().getStatus()).isEqualTo(200);

        JsonNode hoiDong = than(cuaHoiDong);
        JsonNode khach = than(cuaKhach);

        // DUNG CHUNG khung xuong la co y va la thiet ke dung: khung xuong chi la danh sach id +
        // do sau, giong het nhau voi moi vai. Thu bao ve rieng tu la BUOC LOC chay sau no, luon
        // chay tuoi. Ghim ca hai menh de canh nhau, vi chung de bi doi lan.
        assertThat(hoiDong.path("nodes").size())
                .as("Hoi dong thay ca hai nguoi con song")
                .isGreaterThan(khach.path("nodes").size());
        // Doc tu MANG BYTE chu khong getContentAsString(): mac dinh cua MockHttpServletResponse la
        // ISO-8859-1, nen moi ten tieng Viet se khong bao gio khop va ca phep kiem "khong chua"
        // se xanh vi ly do sai.
        assertThat(new String(cuaHoiDong.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("Nguyễn Văn Anh");
        assertThat(new String(cuaKhach.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                .as("Khach khong duoc thay bat ky nguoi con song nao, du khung xuong lay tu cache"
                        + " da nap boi Hoi dong")
                .doesNotContain("Nguyễn Văn Anh")
                .doesNotContain("Nguyễn Văn Em")
                .doesNotContain(anh.toString())
                .doesNotContain(em.toString());
    }

    // =========================================================================================
    // Tiện ích
    // =========================================================================================

    private MvcResult goi(RequestPostProcessor nguoiGoi, String ifNoneMatch) throws Exception {
        var request = get("/api/v1/tree")
                .param("rootId", thuyTo.toString())
                .param("depth", "3")
                .with(nguoiGoi);
        if (ifNoneMatch != null) {
            request = request.header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static String etag(MvcResult result) {
        return result.getResponse().getHeader(HttpHeaders.ETAG);
    }

    private static JsonNode than(MvcResult result) throws Exception {
        return JSON.readTree(new String(result.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8));
    }

    private RequestPostProcessor thanhVien(String sub) {
        return jwt().jwt(builder -> builder.subject(sub))
                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }

    private RequestPostProcessor hoiDong() {
        return jwt().jwt(builder -> builder.subject("sub-hoi-dong"))
                .authorities(new SimpleGrantedAuthority("ROLE_COUNCIL"));
    }
}
