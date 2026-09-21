package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.giapha.media.domain.MediaLimits;

/**
 * Màn <b>"Bài của tôi"</b> — {@code GET /api/v1/posts?mine=true} — và chính sách tải tệp do máy
 * chủ công bố.
 *
 * <h2>Hai thứ tưởng không liên quan, nhưng cùng một bài học</h2>
 * Cả hai đều ra đời vì giao diện <b>đã buộc phải tự xoay</b> khi máy chủ không nói ra điều cần
 * nói, và cả hai lỗi ấy đều thuộc loại <i>im lặng</i>:
 * <ul>
 *   <li><b>Thiếu {@code ?mine}</b> — màn hình bắn bốn lượt gọi song song (một cho mỗi trạng thái)
 *       rồi lọc tiếp ở client. Hậu quả không phải chậm mà là <b>sai</b>: phân trang của máy chủ
 *       đếm trên toàn bộ tập, còn phép lọc của client chỉ nhìn thấy trang đầu — nên một người viết
 *       nhiều <b>mất bài cũ mà không có gì báo</b>. Ca thứ hai dưới đây dựng đúng tình huống ấy.</li>
 *   <li><b>Thiếu {@code /media/policy}</b> — mỗi bên tự đặt một bộ trần, và người dùng chờ hết
 *       thanh tiến trình rồi mới bị từ chối.</li>
 * </ul>
 */
@DisplayName("Bài của tôi (?mine) và chính sách tải tệp do máy chủ công bố")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class PostMineFilterIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private UUID chiAt;

    @BeforeEach
    void dungDongHo() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");

        UUID truongAt = seed(PersonFixtures.living("Nguyễn Văn Ất").branch(chiAt).generation(6));
        assignBranchRole(insertAppUser("sub-truong-at", truongAt), "BRANCH_HEAD", chiAt);

        UUID mai = seed(PersonFixtures.living("Nguyễn Thị Mai").branch(chiAt).generation(7));
        insertAppUser("sub-mai", mai);
    }

    @Test
    @DisplayName("?mine=true chỉ trả bài của chính mình, và tổng phân trang cũng theo đó")
    void mineChiTraBaiCuaChinhMinh() throws Exception {
        taoNhap(mai(), "Bài của Mai 1");
        taoNhap(mai(), "Bài của Mai 2");
        taoNhap(truongAt(), "Bài của Trưởng chi");

        MvcResult cuaMai = mockMvc.perform(get("/api/v1/posts?mine=true").with(mai())).andReturn();
        JsonNode trang = than(cuaMai);
        assertThat(trang.get("items")).hasSize(2);
        // Tong CUNG phai theo bo loc — khong thi thanh phan trang noi "3" trong khi chi co 2 dong.
        assertThat(trang.get("page").get("totalElements").asLong()).isEqualTo(2L);

        // Khong co ?mine thi Truong chi van chi thay bai cua minh (nhap cua nguoi khac la kin),
        // nhung do la mot luat KHAC — ?mine khong duoc phep thay the no.
        MvcResult khongLoc = mockMvc.perform(get("/api/v1/posts").with(mai())).andReturn();
        assertThat(than(khongLoc).get("items")).hasSize(2);
    }

    /**
     * Ca dựng đúng cái hỏng mà lọc-ở-client gây ra.
     *
     * <p>Mai có 3 bản nháp, Trưởng chi có 2. Xin trang đầu <b>kích thước 2</b>: lọc trong SQL trả
     * 2 bài <i>của Mai</i> và tổng 3. Lọc ở client sẽ nhận 2 bài đầu của tập trộn rồi ném đi những
     * bài không phải của mình — và màn hình hiện <b>ít hơn</b> con số nó vừa tự in ra.</p>
     */
    @Test
    @DisplayName("?mine kết hợp phân trang: lọc chạy TRƯỚC khi cắt trang, không sau")
    void mineChayTruocKhiCatTrang() throws Exception {
        taoNhap(mai(), "Nháp Mai A");
        taoNhap(truongAt(), "Nháp Trưởng chi A");
        taoNhap(mai(), "Nháp Mai B");
        taoNhap(truongAt(), "Nháp Trưởng chi B");
        taoNhap(mai(), "Nháp Mai C");

        MvcResult kq = mockMvc.perform(get("/api/v1/posts?mine=true&page=0&size=2").with(mai()))
                .andReturn();
        JsonNode trang = than(kq);
        assertThat(trang.get("items")).hasSize(2);
        assertThat(trang.get("page").get("totalElements").asLong()).isEqualTo(3L);
        for (JsonNode bai : trang.get("items")) {
            assertThat(bai.get("title").asText()).startsWith("Nháp Mai");
        }
    }

    @Test
    @DisplayName("?mine=true kết hợp được với ?status")
    void mineKetHopStatus() throws Exception {
        UUID nhap = taoNhap(mai(), "Còn nháp");
        UUID daGui = taoNhap(mai(), "Đã gửi duyệt");
        mockMvc.perform(post("/api/v1/posts/" + daGui + "/submit").with(mai())).andReturn();

        MvcResult kq = mockMvc.perform(get("/api/v1/posts?mine=true&status=DRAFT").with(mai()))
                .andReturn();
        assertThat(than(kq).get("items")).hasSize(1);
        assertThat(than(kq).get("items").get(0).get("id").asText()).isEqualTo(nhap.toString());
    }

    /**
     * Ca này là hợp đồng giữa hai bên: <b>trần đến từ máy chủ</b>. Nếu ai đó đổi
     * {@code MediaLimits} mà quên điểm cuối này, ca kiểm đỏ ngay — chứ không phải người dùng phát
     * hiện bằng cách chờ hết một thanh tiến trình.
     */
    @Test
    @DisplayName("/media/policy công bố đúng bộ trần trong MediaLimits, không phải một bản chép")
    void chinhSachTaiTepDenTuMayChu() throws Exception {
        MvcResult kq = mockMvc.perform(get("/api/v1/media/policy").with(mai())).andReturn();
        assertThat(kq.getResponse().getStatus()).isEqualTo(200);

        JsonNode p = than(kq);
        assertThat(p.get("maxImageBytes").asLong()).isEqualTo(MediaLimits.MAX_IMAGE_BYTES);
        assertThat(p.get("maxVideoBytes").asLong()).isEqualTo(MediaLimits.MAX_VIDEO_BYTES);
        assertThat(p.get("maxVideoDurationSeconds").asInt())
                .isEqualTo(MediaLimits.MAX_VIDEO_SECONDS);
        assertThat(p.get("maxMediaPerPost").asInt()).isEqualTo(MediaLimits.MAX_MEDIA_PER_POST);
        assertThat(p.get("maxAltLength").asInt()).isEqualTo(MediaLimits.MAX_ALT_LENGTH);
        assertThat(p.get("imageContentTypes")).hasSize(3);
        assertThat(p.get("videoContentTypes")).hasSize(2);
        assertThat(p.get("uploadUrlTtlSeconds").asInt())
                .isEqualTo((int) MediaLimits.UPLOAD_TICKET_TTL.toSeconds());

        // Day la LOI DUY NHAT cua nhom `media` duoc phep vao bo nho dem: chinh sach la du lieu
        // cong khai cua he thong, khong phai du lieu ca nhan.
        assertThat(kq.getResponse().getHeader("Cache-Control")).contains("max-age=900");
    }

    // -------------------------------------------------------------------------------------

    private UUID taoNhap(RequestPostProcessor ai, String tieuDe) throws Exception {
        MvcResult kq = mockMvc.perform(post("/api/v1/posts")
                        .with(ai)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "title", tieuDe, "body", "Nội dung thử nghiệm."))))
                .andReturn();
        assertThat(kq.getResponse().getStatus()).isEqualTo(201);
        return UUID.fromString(than(kq).get("id").asText());
    }

    private JsonNode than(MvcResult kq) throws Exception {
        return json.readTree(kq.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static RequestPostProcessor mai() {
        return jwt().jwt(b -> b.subject("sub-mai"))
                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }

    private static RequestPostProcessor truongAt() {
        return jwt().jwt(b -> b.subject("sub-truong-at"))
                .authorities(new SimpleGrantedAuthority("ROLE_BRANCH_HEAD"));
    }
}
