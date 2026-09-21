package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import vn.giapha.genealogy.domain.PrivacyConsent;
import vn.giapha.genealogy.domain.PrivacyFieldGroup;
import vn.giapha.genealogy.domain.ShareScope;

/**
 * Đường ảnh/video <b>đầu đến cuối trên hạ tầng thật</b>: PostgreSQL + Apache AGE + <b>MinIO
 * thật</b>, mọi luật đi qua HTTP.
 *
 * <h2>Không một ca nào ở đây giả lập kho</h2>
 * Đây là điểm khác biệt quan trọng nhất so với việc mock {@code ObjectStoragePort}. Ba bất biến
 * của đợt này <b>chỉ tồn tại</b> khi có một kho thật:
 * <ol>
 *   <li><b>"Tệp chỉ có thật sau khi backend nhìn thấy nó."</b> Một mock luôn trả về thứ ta bảo nó
 *       trả; chỉ một kho thật mới chứng minh được rằng xác nhận một khoá <i>chưa hề được tải
 *       lên</i> thì bị từ chối.</li>
 *   <li><b>URL đã ký thật sự tải lên được.</b> Test tự {@code PUT} lên URL mà backend phát, bằng
 *       một {@link HttpClient} thường — tức đúng thứ trình duyệt làm. Một chữ ký sai (tên máy chủ
 *       lệch, hạn sai đơn vị) sẽ lộ ngay ở đây chứ không lộ ở môi trường thật.</li>
 *   <li><b>Đường dọn THẬT SỰ dọn.</b> Ca cuối hỏi thẳng MinIO xem đối tượng còn không, sau khi
 *       bài đã bị gỡ và lệnh dọn đã chạy. "Hàng trong bảng đổi trạng thái" không phải bằng chứng
 *       dữ liệu đã biến mất, và dưới Nghị định 13/2023 thì đúng chỗ ấy mới là điều phải chứng
 *       minh.</li>
 * </ol>
 *
 * <h2>Kho được tua thời gian bằng cách hạ ân hạn, không bằng cách ngủ</h2>
 * Ân hạn dọn tệp mồ côi là 24 giờ. Ca kiểm không chờ 24 giờ và cũng không sửa đồng hồ: nó
 * <b>lùi {@code confirmed_at} của hàng</b> bằng một câu SQL, rồi gọi lệnh dọn qua HTTP. Cách này
 * giữ nguyên toàn bộ đường mã thật — cùng câu truy vấn, cùng phép so, cùng lối xoá — và chỉ đổi
 * một dữ kiện mà thời gian lẽ ra sẽ tự đổi.
 */
@DisplayName("Ảnh & video — tải lên, gắn vào bài, báo gỡ, dọn tệp mồ côi (hạ tầng thật)")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class MediaFlowIT extends AbstractIntegrationTest {

    /**
     * MinIO thật. Cùng tag với {@code infra/docker-compose.yml} để test không lệch phiên bản với
     * môi trường dev — một chữ ký hợp lệ ở bản này mà không hợp lệ ở bản kia là loại lỗi chỉ lộ ra
     * khi triển khai.
     */
    @SuppressWarnings("resource")
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z"))
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", "giapha")
            .withEnv("MINIO_ROOT_PASSWORD", "giapha123")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).forStatusCode(200));

    static {
        MINIO.start();
    }

    @DynamicPropertySource
    static void kho(DynamicPropertyRegistry registry) {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        // MOT dia chi duy nhat o day: backend va "trinh duyet" (HttpClient cua test) deu goi cung
        // mot noi, nen khong co bay `public-endpoint`. Trong Docker Compose that thi hai dia chi
        // khac nhau — xem javadoc MinioProperties.
        registry.add("giapha.media.endpoint", () -> endpoint);
        registry.add("giapha.media.access-key", () -> "giapha");
        registry.add("giapha.media.secret-key", () -> "giapha123");
        registry.add("giapha.media.bucket", () -> "giapha-media-test");
        registry.add("giapha.media.auto-create-bucket", () -> "true");
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private vn.giapha.media.domain.port.ObjectStoragePort storage;

    private UUID goc;
    private UUID chiAt;
    private UUID chiBinh;
    private UUID thanhVienBinhPersonId;
    private UUID nguoiSongKinPersonId;

    @BeforeEach
    void dungDongHo() {
        goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");
        chiBinh = insertBranch("Chi Bính", "goc.chi_binh", goc, "CHI");

        UUID truongAtPersonId = seed(PersonFixtures.living("Nguyễn Văn Ất").branch(chiAt)
                .generation(6));
        assignBranchRole(insertAppUser("sub-truong-at", truongAtPersonId), "BRANCH_HEAD", chiAt);

        UUID truongBinhPersonId = seed(PersonFixtures.living("Nguyễn Văn Bính").branch(chiBinh)
                .generation(6));
        assignBranchRole(insertAppUser("sub-truong-binh", truongBinhPersonId),
                "BRANCH_HEAD", chiBinh);

        UUID hoiDongPersonId = seed(PersonFixtures.living("Nguyễn Văn Tộc").branch(goc)
                .generation(5));
        assignBranchRole(insertAppUser("sub-hoi-dong", hoiDongPersonId), "COUNCIL", null);

        // Quan tri he thong — nguoi duy nhat bam duoc lenh don tep.
        assignBranchRole(insertAppUser("sub-admin", null), "ADMIN", null);

        thanhVienBinhPersonId = seed(PersonFixtures.living("Nguyễn Thị Mai").branch(chiBinh)
                .generation(7));
        insertAppUser("sub-thanh-vien-binh", thanhVienBinhPersonId);

        // Nguoi con song de MOI nhom truong o muc mac dinh = KIN, ke ca birthDetailAndPhoto.
        nguoiSongKinPersonId = seed(PersonFixtures.living("Nguyễn Văn Kín").branch(chiAt)
                .generation(7).consent(PrivacyConsent.allPrivate()));
    }

    // =====================================================================================
    // 1. Chữ ký byte
    // =====================================================================================

    /**
     * Người dùng đổi tên một tệp văn bản thành {@code .jpg} rồi tải lên.
     *
     * <p>Backend <b>không hề thấy</b> tên tệp hay {@code Content-Type} — tệp đi thẳng lên MinIO.
     * Thứ duy nhất đáng tin là những byte đầu mà chính backend đọc ngược về, và ca này chứng minh
     * nó thật sự được đọc.</p>
     */
    @Test
    @DisplayName("Tệp sai chữ ký byte bị từ chối 422, và đối tượng bị XOÁ KHỎI KHO ngay")
    void saiChuKyByte_tuChoi_vaDonLuon() throws Exception {
        JsonNode phieu = xinPhieu(truongChiAt(), "IMAGE", 2048);
        String khoa = phieu.get("mediaKey").asText();

        // Tai len mot tep van ban, dat ten .jpg. Trinh duyet nao cung lam duoc dieu nay.
        taiLen(phieu, "Day la mot tep van ban chu khong phai anh.".getBytes(StandardCharsets.UTF_8));
        assertThat(storage.stat(khoa)).as("tep DA nam tren kho truoc khi xac nhan").isPresent();

        MvcResult kq = xacNhan(truongChiAt(), phieu, "Ảnh lễ giỗ");

        assertThat(kq.getResponse().getStatus()).isEqualTo(422);
        assertThat(ma(kq)).isEqualTo("MEDIA_BAD_SIGNATURE");
        // Xoa NGAY, khong doi luot don: mot ke lap lai thao tac nay se bien kho thanh bai rac
        // trong vai phut.
        assertThat(storage.stat(khoa))
                .as("tep sai dinh dang phai bi xoa khoi kho ngay khi tu choi")
                .isEmpty();
    }

    @Test
    @DisplayName("Xin phiếu ảnh nhưng tải lên video → từ chối, loại khai báo không phải phép kiểm")
    void khaiAnhNhungTaiVideo() throws Exception {
        JsonNode phieu = xinPhieu(truongChiAt(), "IMAGE", 4096);
        taiLen(phieu, mp4(1_000, 5_000));

        MvcResult kq = xacNhan(truongChiAt(), phieu, "Ảnh");
        assertThat(kq.getResponse().getStatus()).isEqualTo(422);
        assertThat(ma(kq)).isEqualTo("MEDIA_BAD_SIGNATURE");
        assertThat(than(kq).get("detail").asText()).contains("video");
    }

    // =====================================================================================
    // 2. Trần — từ chối TRƯỚC KHI tốn băng thông
    // =====================================================================================

    /**
     * Ca này chứng minh một điều rất cụ thể: lời từ chối đến <b>trước</b> khi một byte nào rời máy
     * người dùng. Không có nó thì một người ở vùng sóng yếu sẽ chờ hết 150 MiB rồi mới biết.
     */
    @Test
    @DisplayName("Khai kích thước vượt trần → 413 ngay ở bước xin phiếu, KHÔNG phát URL nào")
    void vuotTran_tuChoiTruocKhiTonBangThong() throws Exception {
        MvcResult kq = mockMvc.perform(post("/api/v1/media/upload-tickets")
                        .with(truongChiAt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VIDEO\",\"sizeBytes\":" + (200L * 1024 * 1024) + "}"))
                .andReturn();

        assertThat(kq.getResponse().getStatus()).isEqualTo(413);
        assertThat(ma(kq)).isEqualTo("MEDIA_TOO_LARGE");
        // Cau tu choi phai noi ra CON SO va cach chua, khong phai "tep qua lon" tron.
        assertThat(than(kq).get("detail").asText()).contains("100").contains("1080p");
        // Va khong co URL nao duoc phat — khong co gi trong than phan hoi de tai len ca.
        assertThat(kq.getResponse().getContentAsString()).doesNotContain("uploadUrl");
    }

    @Test
    @DisplayName("Video dài quá trần bị từ chối 422 sau khi ĐO thời lượng từ header, không tin client")
    void videoQuaDai() throws Exception {
        JsonNode phieu = xinPhieu(truongChiAt(), "VIDEO", 8192);
        taiLen(phieu, mp4(1_000, 300_000));          // 300 giay

        MvcResult kq = xacNhan(truongChiAt(), phieu, null);
        assertThat(kq.getResponse().getStatus()).isEqualTo(422);
        assertThat(ma(kq)).isEqualTo("MEDIA_TOO_LONG");
        assertThat(than(kq).get("detail").asText()).contains("300").contains("120");
    }

    // =====================================================================================
    // 3. Bất biến: tệp chỉ có thật sau khi backend nhìn thấy nó
    // =====================================================================================

    /**
     * <b>Bất biến lớn nhất của cả đường này.</b> Tin lời client là cách một bài viết trỏ vào một
     * tệp không tồn tại — và triệu chứng xuất hiện ở máy người đọc, không ở log của người tải.
     */
    @Test
    @DisplayName("Xác nhận một phiếu mà kho CHƯA HỀ có tệp → 409 MEDIA_NOT_UPLOADED")
    void xacNhanKhiChuaTaiGi() throws Exception {
        JsonNode phieu = xinPhieu(truongChiAt(), "IMAGE", 2048);
        // KHONG goi taiLen(). Client "quen" buoc giua, hoac co tinh bo qua.

        MvcResult kq = xacNhan(truongChiAt(), phieu, "Ảnh không tồn tại");
        assertThat(kq.getResponse().getStatus()).isEqualTo(409);
        assertThat(ma(kq)).isEqualTo("MEDIA_NOT_UPLOADED");
        assertThat(than(kq).get("detail").asText()).contains("uploadUrl");
    }

    @Test
    @DisplayName("Ảnh KHÔNG có chữ thay ảnh bị từ chối — nhưng tệp KHÔNG bị xoá")
    void anhThieuChuThayAnh() throws Exception {
        JsonNode phieu = xinPhieu(truongChiAt(), "IMAGE", 2048);
        String khoa = phieu.get("mediaKey").asText();
        taiLen(phieu, jpeg());

        MvcResult kq = xacNhan(truongChiAt(), phieu, null);
        // 422, khong phai 400: than yeu cau dung cu phap hoan toan, thu thieu la mot DIEU KIEN
        // NGHIEP VU (chu thay anh bat buoc voi anh). Giao dien phan nhanh theo `code`.
        assertThat(kq.getResponse().getStatus()).isEqualTo(422);
        assertThat(ma(kq)).isEqualTo("VALIDATION_FAILED");
        assertThat(than(kq).get("detail").asText()).contains("WCAG");

        // Tep hoan toan hop le — nguoi dung chi can go them mot cau roi goi lai. Xoa o day bat ho
        // tai lai 8 MiB vi mot o input bo trong.
        assertThat(storage.stat(khoa)).isPresent();
        MvcResult lanHai = xacNhan(truongChiAt(), phieu, "Con cháu dâng hương tại từ đường");
        assertThat(lanHai.getResponse().getStatus()).isEqualTo(200);
        assertThat(than(lanHai).get("alt").asText())
                .isEqualTo("Con cháu dâng hương tại từ đường");
    }

    // =====================================================================================
    // 4. Phạm vi chi
    // =====================================================================================

    /**
     * Trưởng chi Ất có vai {@code BRANCH_HEAD} hợp lệ — nhưng bài này là bản nháp của một người
     * chi Bính. Chỉ <b>tác giả</b> gắn được tệp, nên vai trò không cứu được ông.
     */
    @Test
    @DisplayName("Người ngoài phạm vi KHÔNG gắn được tệp vào bài của chi khác (403)")
    void nguoiNgoaiPhamVi_khongGanDuocTep() throws Exception {
        UUID baiCuaChiBinh = taoNhap(thanhVienBinh(), "Lễ giỗ chi Bính");
        UUID tepCuaTruongAt = tepSanSang(truongChiAt(), "Ảnh của chi Ất");

        MvcResult kq = mockMvc.perform(put("/api/v1/posts/" + baiCuaChiBinh + "/media")
                        .with(truongChiAt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaIds\":[\"" + tepCuaTruongAt + "\"]}"))
                .andReturn();

        assertThat(kq.getResponse().getStatus()).isEqualTo(403);
    }

    /**
     * Lỗ hổng cụ thể mà ca này chặn: đoán khoá của một tệp người khác vừa tải lên rồi gắn nó vào
     * bài của mình.
     */
    @Test
    @DisplayName("Không gắn được tệp do NGƯỜI KHÁC tải lên vào bài của mình (403 MEDIA_NOT_OWNED)")
    void khongGanDuocTepCuaNguoiKhac() throws Exception {
        UUID tepCuaTruongAt = tepSanSang(truongChiAt(), "Ảnh của chi Ất");
        UUID baiCuaMai = taoNhap(thanhVienBinh(), "Bài của Mai");

        MvcResult kq = mockMvc.perform(put("/api/v1/posts/" + baiCuaMai + "/media")
                        .with(thanhVienBinh())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaIds\":[\"" + tepCuaTruongAt + "\"]}"))
                .andReturn();

        assertThat(kq.getResponse().getStatus()).isEqualTo(403);
        assertThat(ma(kq)).isEqualTo("MEDIA_NOT_OWNED");
    }

    // =====================================================================================
    // 5. Gắn vào bài, và đường dọn
    // =====================================================================================

    @Test
    @DisplayName("Bài mang một DANH SÁCH ảnh, đúng thứ tự, mỗi ảnh kèm URL đã ký tải được thật")
    void baiMangDanhSachAnh() throws Exception {
        UUID bai = taoNhap(thanhVienBinh(), "Lễ giỗ Tổ");
        UUID anh1 = tepSanSang(thanhVienBinh(), "Toàn cảnh sân từ đường");
        UUID anh2 = tepSanSang(thanhVienBinh(), "Con cháu dâng hương");

        MvcResult kq = mockMvc.perform(put("/api/v1/posts/" + bai + "/media")
                        .with(thanhVienBinh())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaIds\":[\"" + anh2 + "\",\"" + anh1 + "\"]}"))
                .andReturn();

        assertThat(kq.getResponse().getStatus()).isEqualTo(200);
        JsonNode media = than(kq).get("media");
        assertThat(media).hasSize(2);
        // Thu tu la thu tu GUI XUONG, khong phai thu tu tai len.
        assertThat(media.get(0).get("id").asText()).isEqualTo(anh2.toString());
        assertThat(media.get(1).get("id").asText()).isEqualTo(anh1.toString());
        assertThat(media.get(0).get("position").asInt()).isZero();
        assertThat(media.get(0).get("alt").asText()).isNotBlank();

        // URL da ky phai TAI DUOC THAT — day la thu phan biet mot chu ky dung voi mot chuoi trong
        // nhu dung.
        String url = media.get(0).get("url").asText();
        HttpResponse<byte[]> tai = HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(tai.statusCode()).isEqualTo(200);
        assertThat(tai.body()).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);

        // Va phan hoi KHONG duoc vao bo nho dem dung chung: no cho mot URL da ky.
        assertThat(kq.getResponse().getHeader("Cache-Control")).contains("no-store");
        // getHeader() chi tra ve gia tri DAU TIEN, va bo loc CORS da dat `Vary: Origin` truoc khi
        // controller chay. Phai doc CA TAP — mot phep so tren getHeader() se do o day va lam nguoi
        // sua tuong `varyBy` khong chay, trong khi no chay hoan toan binh thuong.
        assertThat(kq.getResponse().getHeaders("Vary")).contains("Authorization");
    }

    /**
     * Ca <b>bắt buộc</b> của đợt này, và nó phải hỏi thẳng kho.
     *
     * <p>"Hàng trong bảng đã đổi trạng thái" không phải bằng chứng dữ liệu đã biến mất. Dưới Nghị
     * định 13/2023 thì đúng chỗ ấy mới là điều phải chứng minh — và một tệp được đánh dấu đã dọn
     * trong khi byte vẫn nằm trên kho là thứ tệ nhất: nó vừa tồn tại vừa vô hình với mọi báo
     * cáo.</p>
     */
    @Test
    @DisplayName("Gỡ bài → tệp thành mồ côi → lệnh dọn THẬT SỰ xoá byte khỏi kho")
    void goBai_tepThanhMoCoi_vaDuongDonThatSuDon() throws Exception {
        UUID bai = taoNhap(thanhVienBinh(), "Bài sẽ bị gỡ");
        UUID anh = tepSanSang(thanhVienBinh(), "Ảnh sẽ thành mồ côi");
        String khoa = khoaCua(anh);

        mockMvc.perform(put("/api/v1/posts/" + bai + "/media")
                        .with(thanhVienBinh())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaIds\":[\"" + anh + "\"]}"))
                .andReturn();
        assertThat(storage.stat(khoa)).isPresent();
        assertThat(soLienKet(anh)).isEqualTo(1);

        // --- Go bai (bo ban nhap). Bai O LAI (xoa mem tuyet doi), tep thi mat chu. ---
        mockMvc.perform(post("/api/v1/posts/" + bai + "/withdraw")
                        .with(thanhVienBinh())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Viết lại cho gọn\"}"))
                .andReturn();

        assertThat(countRows("post")).as("go bai la doi trang thai, khong xoa hang").isEqualTo(1);
        assertThat(soLienKet(anh)).as("tep da mat chu").isZero();
        assertThat(storage.stat(khoa))
                .as("chua den luot don thi byte van con — an han la co y")
                .isPresent();

        // --- An han 24 gio: lui confirmed_at thay vi cho, va thay vi sua dong ho toan cuc. ---
        jdbc.update("UPDATE media_asset SET confirmed_at = now() - interval '48 hours'"
                + " WHERE id = ?", anh);

        MvcResult don = mockMvc.perform(post("/api/v1/admin/media/gc").with(quanTri())).andReturn();
        assertThat(don.getResponse().getStatus()).isEqualTo(200);
        assertThat(than(don).get("orphanAssets").asInt()).isEqualTo(1);
        assertThat(than(don).get("storageFailures").asInt()).isZero();

        // DAY la cau hoi that: byte con tren kho khong?
        assertThat(storage.stat(khoa)).as("duong don phai THAT SU xoa byte").isEmpty();
        // Va so van con: ai tai len cai gi luc nao, va no da bi don khi nao.
        assertThat(jdbc.queryForObject("SELECT status FROM media_asset WHERE id = ?",
                String.class, anh)).isEqualTo("PURGED");
        assertThat(jdbc.queryForObject("SELECT purged_at IS NOT NULL FROM media_asset WHERE id = ?",
                Boolean.class, anh)).isTrue();
    }

    @Test
    @DisplayName("Xin URL rồi bỏ ngang → phiếu quá hạn được dọn, kể cả khi chưa tải gì lên")
    void xinUrlRoiBoNgang() throws Exception {
        JsonNode phieu = xinPhieu(truongChiAt(), "IMAGE", 2048);
        UUID mediaId = UUID.fromString(phieu.get("mediaId").asText());

        jdbc.update("UPDATE media_asset SET ticket_expires_at = now() - interval '3 hours'"
                + " WHERE id = ?", mediaId);

        MvcResult don = mockMvc.perform(post("/api/v1/admin/media/gc").with(quanTri())).andReturn();
        assertThat(than(don).get("expiredTickets").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM media_asset WHERE id = ?",
                String.class, mediaId)).isEqualTo("PURGED");
    }

    @Test
    @DisplayName("Lệnh dọn chỉ dành cho Quản trị hệ thống — Trưởng chi bị 403")
    void lenhDonChiDanhChoQuanTri() throws Exception {
        assertThat(mockMvc.perform(post("/api/v1/admin/media/gc").with(truongChiAt()))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    // =====================================================================================
    // 6. Đường báo gỡ
    // =====================================================================================

    @Test
    @DisplayName("Báo gỡ → người duyệt trong phạm vi gỡ được, và byte biến mất NGAY (không ân hạn)")
    void baoGo_nguoiDuyetGoDuoc_vaByteBienMatNgay() throws Exception {
        UUID bai = taoNhap(thanhVienBinh(), "Bài có ảnh nhạy cảm");
        UUID anh = tepSanSang(thanhVienBinh(), "Ảnh tập thể");
        String khoa = khoaCua(anh);
        mockMvc.perform(put("/api/v1/posts/" + bai + "/media")
                .with(thanhVienBinh())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[\"" + anh + "\"]}")).andReturn();

        // Tac gia gui duyet roi Truong chi Binh duyet — bai len trang chu, moi thanh vien thay anh.
        mockMvc.perform(post("/api/v1/posts/" + bai + "/submit").with(thanhVienBinh())).andReturn();
        mockMvc.perform(post("/api/v1/posts/" + bai + "/review")
                .with(truongChiBinh())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approve\":true}")).andReturn();

        // --- Mot nguoi trong ho bao go ---
        MvcResult donKq = mockMvc.perform(post("/api/v1/media/" + anh + "/reports")
                        .with(truongChiAt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"RIENG_TU\",\"note\":\"Trong ảnh có cháu nhỏ nhà tôi\"}"))
                .andReturn();
        assertThat(donKq.getResponse().getStatus()).isEqualTo(201);
        UUID donId = UUID.fromString(than(donKq).get("id").asText());

        // Nguoi duyet NHIN THAY duoc thu minh dang quyet.
        MvcResult hangDoi = mockMvc.perform(get("/api/v1/media/reports").with(truongChiBinh()))
                .andReturn();
        assertThat(than(hangDoi)).hasSize(1);
        assertThat(than(hangDoi).get(0).get("mediaUrl").asText()).startsWith("http");

        // --- Go ---
        MvcResult go = mockMvc.perform(post("/api/v1/media/reports/" + donId + "/takedown")
                        .with(truongChiBinh())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Đã gỡ theo đề nghị\"}"))
                .andReturn();
        assertThat(go.getResponse().getStatus()).isEqualTo(200);
        assertThat(than(go).get("status").asText()).isEqualTo("ACTIONED");

        // KHONG an han: byte bien mat ngay, chua chay lenh don nao ca.
        assertThat(storage.stat(khoa))
                .as("mot tam anh dang lam lo du lieu ca nhan phai ngung duoc phuc vu NGAY")
                .isEmpty();
        // Bai van con tren trang chu, chi la khong con anh.
        MvcResult doc = mockMvc.perform(get("/api/v1/posts/" + bai).with(truongChiAt())).andReturn();
        assertThat(than(doc).get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(than(doc).get("media")).isEmpty();
    }

    /**
     * Người báo phải là người <b>vốn có</b> thẩm quyền duyệt trong phạm vi ấy — nếu không thì
     * {@code BRANCH_SCOPE_VIOLATION} nổ trước và ca kiểm không nói được điều nó định nói. Ở đây
     * Trưởng chi Bính báo một tệp trong bài của <i>chính chi mình</i>: ông thừa quyền gỡ, và đúng
     * vì thế luật "không tự duyệt việc của mình" mới là thứ chặn ông lại.
     */
    @Test
    @DisplayName("Người báo KHÔNG tự duyệt đơn của mình, dù thừa quyền (403 SELF_REVIEW_FORBIDDEN)")
    void nguoiBaoKhongTuDuyet() throws Exception {
        UUID donId = donBaoGo(truongChiBinh());
        MvcResult kq = mockMvc.perform(post("/api/v1/media/reports/" + donId + "/takedown")
                        .with(truongChiBinh())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn();
        assertThat(kq.getResponse().getStatus()).isEqualTo(403);
        assertThat(ma(kq)).isEqualTo("SELF_REVIEW_FORBIDDEN");
    }

    @Test
    @DisplayName("Bấm báo lần thứ hai không dìm thêm hàng đợi (409 REPORT_DUPLICATE)")
    void bamBaoHaiLan() throws Exception {
        donBaoGo();
        MvcResult lai = mockMvc.perform(post("/api/v1/media/" + tepDaGan + "/reports")
                        .with(truongChiAt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"RIENG_TU\",\"note\":\"Nhắc lại\"}"))
                .andReturn();
        assertThat(lai.getResponse().getStatus()).isEqualTo(409);
        assertThat(ma(lai)).isEqualTo("REPORT_DUPLICATE");
    }

    // =====================================================================================
    // 7. Ảnh chân dung — nhóm trường birthDetailAndPhoto, KHÔNG luật riêng tư mới
    // =====================================================================================

    /**
     * Ca <b>bắt buộc</b> của đợt này, và nó kiểm ở <i>hai</i> chỗ, vì có hai đường lộ khác nhau:
     * <ol>
     *   <li>{@code PersonDto.avatarKey} — đường cũ, vốn đã đúng từ V8. Ca này chỉ xác nhận rằng
     *       việc thêm <i>đường ghi</i> không làm hỏng nó.</li>
     *   <li>{@code POST /media/view-urls} — <b>đường mới</b>, và là chỗ thật sự có thể rò. Người
     *       ngoài mà cầm được khoá (từ một ảnh chụp màn hình, một lịch sử trình duyệt) vẫn không
     *       được ký một URL đọc: <b>khoá không phải giấy thông hành</b>.</li>
     * </ol>
     */
    @Test
    @DisplayName("Ảnh chân dung người CÒN SỐNG không lọt ra ngoài bộ lọc nhóm trường")
    void anhChanDungNguoiSong_khongLotRaNgoai() throws Exception {
        // Hoi dong (pham vi toan dong ho) dat anh chan dung cho nguoi dang de moi nhom o muc KIN.
        UUID anh = tepSanSang(hoiDong(), "Ảnh chân dung");
        MvcResult dat = mockMvc.perform(put("/api/v1/persons/" + nguoiSongKinPersonId + "/avatar")
                        .with(hoiDong())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaId\":\"" + anh + "\"}"))
                .andReturn();
        assertThat(dat.getResponse().getStatus()).isEqualTo(200);

        String khoa = khoaCua(anh);
        assertThat(khoaAnhChanDung(nguoiSongKinPersonId))
                .as("khoa anh chan dung va media_link duoc ghi trong CUNG mot giao dich")
                .isEqualTo(khoa);

        // --- Duong 1: ho so. Nguoi ngoai chi KHONG thay khoa ---
        MvcResult hoSo = mockMvc.perform(get("/api/v1/persons/" + nguoiSongKinPersonId)
                .with(thanhVienBinh())).andReturn();
        assertThat(hoSo.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .as("khoa anh chan dung cua nguoi con song khong duoc xuat hien o BAT KY dau trong"
                        + " than phan hoi, ke ca mot truong phu ma nguoi viet code quen mat")
                .doesNotContain(khoa);

        // --- Duong 2: xin URL doc voi khoa da biet. DAY moi la duong co the ro ---
        MvcResult xinUrl = mockMvc.perform(post("/api/v1/media/view-urls")
                        .with(thanhVienBinh())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"" + khoa + "\"]}"))
                .andReturn();
        assertThat(xinUrl.getResponse().getStatus()).isEqualTo(200);
        assertThat(than(xinUrl).get("urls").size())
                .as("khoa khong phai giay thong hanh: moi lan ky la mot lan bo loc chay lai")
                .isZero();

        // --- Chinh chu / vai toan dong ho thi ky duoc ---
        MvcResult cuaHoiDong = mockMvc.perform(post("/api/v1/media/view-urls")
                        .with(hoiDong())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"" + khoa + "\"]}"))
                .andReturn();
        assertThat(than(cuaHoiDong).get("urls").get(khoa).asText()).startsWith("http");
    }

    @Test
    @DisplayName("Gỡ ảnh chân dung: con trỏ được xoá, tệp thành mồ côi chứ không mất ngay")
    void goAnhChanDung() throws Exception {
        UUID anh = tepSanSang(hoiDong(), "Ảnh chân dung");
        mockMvc.perform(put("/api/v1/persons/" + nguoiSongKinPersonId + "/avatar")
                .with(hoiDong())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaId\":\"" + anh + "\"}")).andReturn();
        String khoa = khoaCua(anh);

        MvcResult go = mockMvc.perform(delete("/api/v1/persons/" + nguoiSongKinPersonId + "/avatar")
                .with(hoiDong())).andReturn();
        assertThat(go.getResponse().getStatus()).isEqualTo(200);

        assertThat(khoaAnhChanDung(nguoiSongKinPersonId)).isNull();
        assertThat(soLienKet(anh)).isZero();
        assertThat(storage.stat(khoa))
                .as("mot lan bam nham khong duoc phep lam mat vinh vien mot anh chan dung cu")
                .isPresent();
    }

    @Test
    @DisplayName("Video KHÔNG làm được ảnh chân dung")
    void videoKhongLamAnhChanDung() throws Exception {
        JsonNode phieu = xinPhieu(hoiDong(), "VIDEO", 8192);
        taiLen(phieu, mp4(1_000, 5_000));
        MvcResult xn = xacNhan(hoiDong(), phieu, null);
        UUID video = UUID.fromString(than(xn).get("id").asText());

        MvcResult kq = mockMvc.perform(put("/api/v1/persons/" + nguoiSongKinPersonId + "/avatar")
                        .with(hoiDong())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaId\":\"" + video + "\"}"))
                .andReturn();
        assertThat(kq.getResponse().getStatus()).isEqualTo(422);
        assertThat(ma(kq)).isEqualTo("VALIDATION_FAILED");
    }

    // =====================================================================================
    // 8. audit_log không chứa khoá đối tượng
    // =====================================================================================

    /**
     * Một khoá đối tượng <b>ký được thành URL đọc</b>, nên với ảnh chân dung của người còn sống nó
     * là dữ liệu Tầng 3 theo đúng nghĩa của BA v2 §10 — không phải một định danh vô hại.
     *
     * <p>Ca này đọc thẳng bảng, vì điều cần chứng minh chính là <b>nội dung đã nằm trên đĩa</b>.</p>
     */
    @Test
    @DisplayName("audit_log KHÔNG chứa khoá đối tượng — một khoá đoán được cũng là dữ liệu")
    void auditKhongChuaKhoaDoiTuong() throws Exception {
        UUID anh = tepSanSang(hoiDong(), "Ảnh chân dung");
        mockMvc.perform(put("/api/v1/persons/" + nguoiSongKinPersonId + "/avatar")
                .with(hoiDong())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaId\":\"" + anh + "\"}")).andReturn();

        String khoa = khoaCua(anh);
        String nhatKy = String.join("\n", jdbc.queryForList(
                "SELECT coalesce(\"before\"::text,'') || coalesce(\"after\"::text,'')"
                        + " || coalesce(note,'') FROM audit_log", String.class));

        assertThat(nhatKy)
                .as("khoa doi tuong khong duoc xuat hien trong audit_log duoi bat ky dang nao")
                .doesNotContain(khoa);
    }

    // =====================================================================================
    // Tiện ích
    // =====================================================================================

    /** Tệp đã gắn của ca gần nhất — dùng bởi {@link #bamBaoHaiLan()}. */
    private UUID tepDaGan;

    private UUID donBaoGo() throws Exception {
        return donBaoGo(truongChiAt());
    }

    private UUID donBaoGo(RequestPostProcessor nguoiBao) throws Exception {
        UUID bai = taoNhap(thanhVienBinh(), "Bài có ảnh");
        UUID anh = tepSanSang(thanhVienBinh(), "Ảnh tập thể");
        tepDaGan = anh;
        mockMvc.perform(put("/api/v1/posts/" + bai + "/media")
                .with(thanhVienBinh())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[\"" + anh + "\"]}")).andReturn();
        mockMvc.perform(post("/api/v1/posts/" + bai + "/submit").with(thanhVienBinh())).andReturn();
        mockMvc.perform(post("/api/v1/posts/" + bai + "/review")
                .with(truongChiBinh())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approve\":true}")).andReturn();

        MvcResult don = mockMvc.perform(post("/api/v1/media/" + anh + "/reports")
                        .with(nguoiBao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"RIENG_TU\",\"note\":\"Có cháu nhỏ trong ảnh\"}"))
                .andReturn();
        assertThat(don.getResponse().getStatus()).isEqualTo(201);
        return UUID.fromString(than(don).get("id").asText());
    }

    private JsonNode xinPhieu(RequestPostProcessor ai, String loai, long soByte) throws Exception {
        MvcResult kq = mockMvc.perform(post("/api/v1/media/upload-tickets")
                        .with(ai)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"" + loai + "\",\"sizeBytes\":" + soByte + "}"))
                .andReturn();
        assertThat(kq.getResponse().getStatus()).isEqualTo(200);
        return than(kq);
    }

    /** Tải thẳng lên kho bằng URL đã ký — đúng thứ trình duyệt làm, không qua backend. */
    private void taiLen(JsonNode phieu, byte[] noiDung) throws Exception {
        HttpResponse<Void> kq = HTTP.send(
                HttpRequest.newBuilder(URI.create(phieu.get("uploadUrl").asText()))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(noiDung))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(kq.statusCode())
                .as("URL da ky phai TAI DUOC THAT; 403 o day nghia la chu ky sai")
                .isBetween(200, 299);
    }

    private MvcResult xacNhan(RequestPostProcessor ai, JsonNode phieu, String alt) throws Exception {
        String than = alt == null ? "{}" : json.writeValueAsString(java.util.Map.of("alt", alt));
        return mockMvc.perform(post("/api/v1/media/" + phieu.get("mediaId").asText() + "/confirm")
                .with(ai)
                .contentType(MediaType.APPLICATION_JSON)
                .content(than)).andReturn();
    }

    /** Một tấm ảnh JPEG đã qua đủ ba bước và sẵn sàng gắn vào bản ghi. */
    private UUID tepSanSang(RequestPostProcessor ai, String alt) throws Exception {
        JsonNode phieu = xinPhieu(ai, "IMAGE", 4096);
        taiLen(phieu, jpeg());
        MvcResult kq = xacNhan(ai, phieu, alt);
        assertThat(kq.getResponse().getStatus()).isEqualTo(200);
        return UUID.fromString(than(kq).get("id").asText());
    }

    private UUID taoNhap(RequestPostProcessor ai, String tieuDe) throws Exception {
        MvcResult kq = mockMvc.perform(post("/api/v1/posts")
                        .with(ai)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "title", tieuDe, "body", "Nội dung bài viết thử nghiệm."))))
                .andReturn();
        assertThat(kq.getResponse().getStatus()).isEqualTo(201);
        return UUID.fromString(than(kq).get("id").asText());
    }

    /**
     * Khoá ảnh chân dung — <b>trong JSONB, không phải một cột</b>.
     *
     * <p>{@code person.attributes -> '_profile' ->> 'avatarKey'}. Bản đầu của ca kiểm này viết
     * {@code SELECT avatar_key FROM person} và chết với <i>column does not exist</i> — đúng cái
     * bẫy mà cả {@code V17} lẫn bản nháp đầu của {@code V19} đã viết nhầm.</p>
     */
    private String khoaAnhChanDung(UUID personId) {
        return jdbc.queryForObject(
                "SELECT attributes -> '_profile' ->> 'avatarKey' FROM person WHERE id = ?",
                String.class, personId);
    }

    private String khoaCua(UUID mediaId) {
        return jdbc.queryForObject("SELECT object_key FROM media_asset WHERE id = ?",
                String.class, mediaId);
    }

    private int soLienKet(UUID mediaId) {
        return jdbc.queryForObject("SELECT count(*) FROM media_link WHERE media_id = ?",
                Integer.class, mediaId);
    }

    private JsonNode than(MvcResult kq) throws Exception {
        return json.readTree(kq.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String ma(MvcResult kq) throws Exception {
        JsonNode body = than(kq);
        return body.has("code") ? body.get("code").asText() : null;
    }

    private static RequestPostProcessor nhuLa(String sub, String authority) {
        return jwt().jwt(b -> b.subject(sub)).authorities(new SimpleGrantedAuthority(authority));
    }

    private static RequestPostProcessor truongChiAt() {
        return nhuLa("sub-truong-at", "ROLE_BRANCH_HEAD");
    }

    private static RequestPostProcessor truongChiBinh() {
        return nhuLa("sub-truong-binh", "ROLE_BRANCH_HEAD");
    }

    private static RequestPostProcessor hoiDong() {
        return nhuLa("sub-hoi-dong", "ROLE_COUNCIL");
    }

    private static RequestPostProcessor quanTri() {
        return nhuLa("sub-admin", "ROLE_ADMIN");
    }

    private static RequestPostProcessor thanhVienBinh() {
        return nhuLa("sub-thanh-vien-binh", "ROLE_MEMBER");
    }

    // --- Mẫu byte ---

    private static byte[] jpeg() {
        byte[] b = new byte[2048];
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        b[3] = (byte) 0xE0;
        return b;
    }

    /** MP4 tối thiểu có hộp {@code mvhd} đọc được — đủ để đo thời lượng, không cần luồng hình. */
    private static byte[] mp4(int timescale, int duration) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] prefix = new byte[64];
        System.arraycopy("ftyp".getBytes(StandardCharsets.ISO_8859_1), 0, prefix, 4, 4);
        System.arraycopy("isom".getBytes(StandardCharsets.ISO_8859_1), 0, prefix, 8, 4);
        out.writeBytes(prefix);
        out.writeBytes("mvhd".getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(new byte[] {0, 0, 0, 0});
        out.writeBytes(new byte[8]);
        out.writeBytes(ByteBuffer.allocate(4).putInt(timescale).array());
        out.writeBytes(ByteBuffer.allocate(4).putInt(duration).array());
        out.writeBytes(new byte[512]);
        return out.toByteArray();
    }
}
