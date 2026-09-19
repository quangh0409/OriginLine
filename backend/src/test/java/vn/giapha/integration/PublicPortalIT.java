package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import vn.giapha.genealogy.application.LinkRelationshipService;
import vn.giapha.genealogy.application.SoftDeletePersonService;
import vn.giapha.genealogy.application.command.LinkRelationshipCommand;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.shared.vo.Gender;

/**
 * <b>Cổng thông tin công khai cho Khách vãng lai</b> — {@code /api/v1/public/**}, chạy qua toàn bộ
 * chuỗi HTTP thật (Spring Security + MVC) trên PostgreSQL + Apache AGE thật.
 *
 * <h2>Đây là lớp test chịu trách nhiệm pháp lý của bề mặt không cần đăng nhập</h2>
 * Câu hỏi duy nhất mà mọi ca ở đây trả lời: <b>có một mẩu dữ liệu nào của người còn sống rời khỏi
 * tiến trình qua một endpoint công khai không?</b> Vì vậy phần lớn phép khẳng định không nhìn vào
 * một trường cụ thể mà soi <b>toàn bộ thân phản hồi</b> — tên, định danh, số điện thoại của người
 * còn sống đều không được xuất hiện ở bất kỳ đâu, kể cả trong một trường phụ mà người viết code
 * quên mất.
 *
 * <h2>Vì sao phải kiểm cả trường hợp có token</h2>
 * {@code /api/v1/public/**} là {@code permitAll}, và {@code permitAll} không có nghĩa là "không ai
 * đăng nhập". Nếu bề mặt công khai đổi nội dung theo token thì {@code Cache-Control: public} trên
 * chính nó trở thành lỗ rò rỉ. {@link #dungToken_vanChiNhanDuLieuMucKhach()} ghim điều đó.
 *
 * <h2>Bẫy đã biết mà bộ test này canh</h2>
 * <ul>
 *   <li>{@code PrivacyTierService.tierFor()} trả {@code T1} cho Khách nhìn người còn sống
 *       <b>thay vì từ chối</b> — an toàn chỉ vì mọi lối vào gọi {@code canSee} trước. Mỗi endpoint
 *       công khai ở đây tự chứng minh điều đó bằng một ca "người còn sống phải biến mất".</li>
 *   <li>{@code VisibleTier.atLeast()} trả {@code true} cho MỌI so sánh khi tier là {@code PUBLIC},
 *       mà người đã khuất luôn {@code PUBLIC} — nên khối liên hệ của <b>người đã khuất</b> (trên
 *       thực tế là số điện thoại của người thân đang sống) cũng không được lọt ra.</li>
 * </ul>
 */
@DisplayName("Cổng thông tin công khai cho Khách (BA v2 §10)")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class PublicPortalIT extends AbstractIntegrationTest {

    /**
     * Mỗi ca test dùng một địa chỉ IP riêng.
     *
     * <p>Hạn mức của {@code PublicRateLimitFilter} tính theo IP và bộ lọc là singleton dùng chung
     * cho cả context Spring đã cache. Nếu mọi ca cùng đi từ {@code 127.0.0.1} thì ca chạy sau sẽ
     * ăn hạn mức do ca chạy trước tiêu — một kiểu flaky rất khó lần ra.</p>
     */
    private static final AtomicInteger CLIENT_SEQ = new AtomicInteger();

    /** Số điện thoại mà {@code PersonFixtures} gắn cho MỌI nhân khẩu, sống lẫn đã khuất. */
    private static final String SO_DIEN_THOAI_FIXTURE = "0900000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LinkRelationshipService linkRelationship;

    @Autowired
    private SoftDeletePersonService softDeletePerson;

    /** Cụ tổ đã khuất — dữ liệu công khai theo BA v2 §10. */
    private UUID cuTo;
    /** Cụ bà <b>còn sống</b>, vợ cụ tổ — cạnh vợ chồng phải biến mất với Khách. */
    private UUID cuBaConSong;
    /** Con trưởng đã khuất. */
    private UUID conTruong;
    /** Con thứ <b>còn sống</b> — mắt xích bị lọc giữa hai đời đã khuất. */
    private UUID conThuConSong;
    /** Cháu đã khuất, <b>con của người còn sống</b> — ca thử then chốt của "cây có lỗ". */
    private UUID chauDaKhuat;
    /** Chắt còn sống. */
    private UUID chatConSong;
    /** Người đã khuất nhưng đã bị xoá mềm. */
    private UUID nguoiDaXoaMem;

    @BeforeEach
    void setUpDongHo() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        UUID chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");

        // Nối quan hệ đòi vai toàn dòng họ; sau khi gieo xong sẽ trả về Khách.
        authenticateAs("sub-admin", "ADMIN");

        cuTo = seed(PersonFixtures.deceased("Nguyễn Phúc Thuỷ Tổ", 1950)
                .birthYear(1880).branch(chiGiap).generation(1).tabooName("Nguyễn Phúc Huý"));
        cuBaConSong = seed(PersonFixtures.living("Trần Thị Sống Lâu")
                .gender(Gender.FEMALE).birthYear(1935).branch(chiGiap).generation(1));
        conTruong = seed(PersonFixtures.deceased("Nguyễn Văn Trưởng", 1990)
                .birthYear(1910).branch(chiGiap).generation(2));
        conThuConSong = seed(PersonFixtures.living("Nguyễn Văn Thứ")
                .birthYear(1950).branch(chiGiap).generation(2));
        chauDaKhuat = seed(PersonFixtures.deceased("Nguyễn Văn Cháu", 2005)
                .birthYear(1975).branch(chiGiap).generation(3));
        chatConSong = seed(PersonFixtures.living("Nguyễn Văn Chắt")
                .birthYear(2000).branch(chiGiap).generation(4));
        nguoiDaXoaMem = seed(PersonFixtures.deceased("Nguyễn Văn Trùng Lặp", 1970)
                .birthYear(1900).branch(chiGiap).generation(2));

        linkRelationship.link(new LinkRelationshipCommand(cuTo, cuBaConSong, RelType.SPOUSE, null, 1,
                null, null, null));
        lamCha(cuTo, conTruong);
        lamCha(cuTo, conThuConSong);
        lamCha(conThuConSong, chauDaKhuat);
        lamCha(chauDaKhuat, chatConSong);
        // Noi vao cay TRUOC khi xoa mem: co vay ca test "khach khong thay ban ghi da xoa mem" moi
        // that su kiem duoc dieu gi - mot node mo coi thi khong xuat hien du co xoa hay khong.
        lamCha(cuTo, nguoiDaXoaMem);

        softDeletePerson.softDelete(nguoiDaXoaMem, "trung lap khi nhap lieu");

        authenticateAsGuest();
    }

    private void lamCha(UUID cha, UUID con) {
        linkRelationship.link(new LinkRelationshipCommand(cha, con, RelType.PARENT_BIO, null, null,
                null, null, null));
    }

    // =====================================================================================
    // Tiện ích
    // =====================================================================================

    /** Địa chỉ riêng cho từng lượt gọi — xem {@link #CLIENT_SEQ}. */
    private RequestPostProcessor tuMotKhachLa() {
        String ip = "198.51.100." + (CLIENT_SEQ.incrementAndGet() % 250 + 1);
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private MvcResult goi(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder.with(tuMotKhachLa())).andReturn();
    }

    private static String than(MvcResult result) throws Exception {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Khẳng định trung tâm của cả lớp test: không một mẩu dữ liệu người còn sống nào có mặt.
     *
     * <p>Soi trên chuỗi thô chứ không trên JSON đã phân giải: một trường mới bị thêm nhầm vào DTO
     * công khai sẽ lọt qua mọi phép kiểm theo {@code jsonPath} nhưng không lọt qua phép này.</p>
     */
    private void khongCoDauVetNguoiConSong(String body) {
        // Bo truong "instance" cua RFC 7807 truoc khi soi: no la URI ma CHINH nguoi goi vua gui,
        // nen dinh danh xuat hien o do khong noi cho ho biet dieu gi ho chua biet. Bang chung la
        // ca test khach_hoiNguoiConSong_khongPhanBietDuocVoiIdBiaDat: 404 cua mot nguoi con song
        // va 404 cua mot id bia dat giong nhau tuyet doi ngoai chinh cai URI ay.
        String daBoInstance = body.replaceAll("\"instance\"\\s*:\\s*\"[^\"]*\"", "\"instance\":\"\"");
        assertThat(daBoInstance)
                .doesNotContain(cuBaConSong.toString())
                .doesNotContain(conThuConSong.toString())
                .doesNotContain(chatConSong.toString())
                .doesNotContain("Sống Lâu")
                .doesNotContain("Nguyễn Văn Thứ")
                .doesNotContain("Nguyễn Văn Chắt")
                .doesNotContain(SO_DIEN_THOAI_FIXTURE)
                .doesNotContain("nguoi@example.com")
                .doesNotContain("zalo-01");
    }

    // =====================================================================================
    // 1. Hồ sơ đơn lẻ
    // =====================================================================================

    @Test
    @DisplayName("khách xem được hồ sơ một cụ đã khuất — đây chính là lỗ hổng chức năng đã sửa")
    void khach_xemDuocNguoiDaKhuat() throws Exception {
        MvcResult result = goi(get("/api/v1/public/persons/{id}", cuTo));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = than(result);
        assertThat(body).contains("Nguyễn Phúc Thuỷ Tổ");
        khongCoDauVetNguoiConSong(body);

        mockMvc.perform(get("/api/v1/public/persons/{id}", cuTo).with(tuMotKhachLa()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAlive").value(false))
                .andExpect(jsonPath("$.generation").value(1))
                // Khoi lien he KHONG duoc lo ke ca voi nguoi da khuat: so dien thoai ghi trong ho so
                // mot cu da mat tren thuc te la so cua nguoi than dang song.
                .andExpect(jsonPath("$.contact").doesNotExist())
                .andExpect(jsonPath("$.currentPlaceFull").doesNotExist())
                .andExpect(jsonPath("$.occupation").doesNotExist())
                .andExpect(jsonPath("$.attributes").doesNotExist())
                .andExpect(jsonPath("$.privacyLevel").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.access").doesNotExist())
                .andExpect(jsonPath("$.isDeleted").doesNotExist());
    }

    @Test
    @DisplayName("hồ sơ công khai mang meta của Khách — ngăn hồ sơ nối được vào /public/persons/{id}")
    void khach_nhanDuocMetaNoiDungQuyenCuaMinh() throws Exception {
        // Khiem khuyet 3: khach bam vao mot node tren pha do cong khai thi ngan ho so goi
        // /api/v1/persons/{id} va an 401, vi PublicPersonDto khong co meta de noi sang ban cong khai.
        MvcResult result = goi(get("/api/v1/public/persons/{id}", cuTo));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = than(result);

        mockMvc.perform(get("/api/v1/public/persons/{id}", cuTo).with(tuMotKhachLa()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta").exists())
                .andExpect(jsonPath("$.meta.callerRole").value("GUEST"))
                .andExpect(jsonPath("$.meta.canEdit").value(false))
                .andExpect(jsonPath("$.meta.canDelete").value(false))
                .andExpect(jsonPath("$.meta.canRequestCorrection").value(false))
                .andExpect(jsonPath("$.meta.isSelf").value(false))
                // Nguoi da khuat luon PUBLIC. Day KHONG phai giay phep xem Tang 2/3: chinh
                // VisibleTier.atLeast() cu da bien PUBLIC thanh mot cai bay nhu the.
                .andExpect(jsonPath("$.meta.visibleTier").value("PUBLIC"));

        // meta noi ve QUYEN cua nguoi goi, nen no khong duoc keo theo mot mau du lieu nhan khau nao.
        khongCoDauVetNguoiConSong(body);
        assertThat(body).doesNotContain("\"contact\"").doesNotContain("\"occupation\"");
    }

    @Test
    @DisplayName("meta trên cổng công khai LUÔN là quyền của Khách, kể cả khi request mang token ADMIN")
    void metaCongKhaiLuonLaQuyenCuaKhach() throws Exception {
        // Neu meta doi theo token thi Cache-Control: public tren chinh phan hoi ay bien moi proxy
        // trung gian thanh mot cho ro ri quyen quan tri.
        RequestPostProcessor quanTri = jwt()
                .jwt(builder -> builder.subject("sub-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

        mockMvc.perform(get("/api/v1/public/persons/{id}", cuTo)
                        .with(tuMotKhachLa()).with(quanTri))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.callerRole").value("GUEST"))
                .andExpect(jsonPath("$.meta.canEdit").value(false))
                .andExpect(jsonPath("$.meta.canDelete").value(false));

        // Doi chung: CUNG nhan khau ay tren be mat thanh vien thi ADMIN sua duoc. Hai khang dinh
        // canh nhau chung minh meta cong khai bi HA QUYEN chu khong phai luon false vi mot hang so.
        mockMvc.perform(get("/api/v1/persons/{id}", cuTo).with(quanTri))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.callerRole").value("ADMIN"))
                .andExpect(jsonPath("$.meta.canEdit").value(true));
    }

    @Test
    @DisplayName("khách hỏi một người CÒN SỐNG nhận 404, không phải 403 — 403 là tự xác nhận có thật")
    void khach_hoiNguoiConSong_nhan404() throws Exception {
        MvcResult result = goi(get("/api/v1/public/persons/{id}", conThuConSong));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentType())
                .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        khongCoDauVetNguoiConSong(than(result));
    }

    @Test
    @DisplayName("404 của người còn sống KHÔNG phân biệt được với 404 của người không tồn tại")
    void khach_hoiNguoiConSong_khongPhanBietDuocVoiIdBiaDat() throws Exception {
        MvcResult coThat = goi(get("/api/v1/public/persons/{id}", chatConSong));
        MvcResult biaDat = goi(get("/api/v1/public/persons/{id}", UUID.randomUUID()));

        assertThat(coThat.getResponse().getStatus()).isEqualTo(biaDat.getResponse().getStatus());
        // Chi khac nhau o dinh danh trong URL; ma loi va tieu de phai trung khop tuyet doi.
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(than(coThat), "$.code"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.<String>read(than(biaDat), "$.code"));
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(than(coThat), "$.title"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.<String>read(than(biaDat), "$.title"));
    }

    @Test
    @DisplayName("quan hệ trên hồ sơ công khai chỉ gồm những cạnh mà đầu kia cũng đã khuất")
    void khach_chiThayQuanHeGiuaNhungNguoiDaKhuat() throws Exception {
        MvcResult result = goi(get("/api/v1/public/persons/{id}", cuTo));
        String body = than(result);

        // Cu to co: vo con song, con truong da khuat, con thu con song -> chi con DUY NHAT mot canh.
        List<String> dauKia = com.jayway.jsonpath.JsonPath.read(body, "$.relations[*].toPersonId");
        assertThat(dauKia).containsExactly(conTruong.toString());
        khongCoDauVetNguoiConSong(body);
    }

    @Test
    @DisplayName("bản ghi đã xoá mềm không hiện ra với khách")
    void khach_khongThayBanGhiDaXoaMem() throws Exception {
        MvcResult result = goi(get("/api/v1/public/persons/{id}", nguoiDaXoaMem));
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    // =====================================================================================
    // 2. Phả đồ
    // =====================================================================================

    @Test
    @DisplayName("phả đồ công khai bỏ hẳn người còn sống — cây có lỗ, không vá bằng node ẩn danh")
    void khach_phaDoBoHanNguoiConSong() throws Exception {
        MvcResult result = goi(get("/api/v1/public/tree")
                .param("rootId", cuTo.toString())
                .param("depth", "4"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = than(result);
        khongCoDauVetNguoiConSong(body);

        List<String> nodeIds = com.jayway.jsonpath.JsonPath.read(body, "$.nodes[*].id");
        assertThat(nodeIds).containsExactlyInAnyOrder(cuTo.toString(), conTruong.toString(),
                chauDaKhuat.toString());

        // Canh chi ton tai khi CA HAI dau deu hien duoc: cu to -> con truong la canh duy nhat.
        List<String> nguon = com.jayway.jsonpath.JsonPath.read(body, "$.edges[*].source");
        List<String> dich = com.jayway.jsonpath.JsonPath.read(body, "$.edges[*].target");
        assertThat(nguon).containsExactly(cuTo.toString());
        assertThat(dich).containsExactly(conTruong.toString());

        // Chinh la quyet dinh thiet ke: chau da khuat van hien dung doi cua minh, nhung parentIds
        // RONG vi cha con song. Khong chen node an danh, khong noi tat qua doi bi giau.
        List<List<String>> chaMeCuaChau = com.jayway.jsonpath.JsonPath.read(body,
                "$.nodes[?(@.id == '" + chauDaKhuat + "')].parentIds");
        assertThat(chaMeCuaChau).hasSize(1);
        assertThat(chaMeCuaChau.get(0)).isEmpty();

        mockMvc.perform(get("/api/v1/public/tree")
                        .param("rootId", cuTo.toString())
                        .param("depth", "4")
                        .with(tuMotKhachLa()))
                .andExpect(jsonPath("$.meta.guestFiltered").value(true))
                .andExpect(jsonPath("$.nodes[0].person.isAlive").value(false));
    }

    @Test
    @DisplayName("lấy phả đồ với gốc là người CÒN SỐNG nhận 404")
    void khach_phaDoGocNguoiConSong_nhan404() throws Exception {
        MvcResult result = goi(get("/api/v1/public/tree")
                .param("rootId", conThuConSong.toString()));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        khongCoDauVetNguoiConSong(than(result));
    }

    @Test
    @DisplayName("vượt trần độ sâu công khai bị chặn bằng 400, không âm thầm cắt bớt")
    void khach_vuotTranDoSau_nhan400() throws Exception {
        MvcResult result = goi(get("/api/v1/public/tree")
                .param("rootId", cuTo.toString())
                .param("depth", "10"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("không có tham số includeDeleted trên cổng công khai — gửi kèm cũng vô hiệu")
    void khach_khongMoDuocBanGhiDaXoaMem() throws Exception {
        MvcResult result = goi(get("/api/v1/public/tree")
                .param("rootId", cuTo.toString())
                .param("includeDeleted", "true"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(than(result)).doesNotContain(nguoiDaXoaMem.toString());
    }

    // =====================================================================================
    // 3. Tìm kiếm
    // =====================================================================================

    @Test
    @DisplayName("tìm kiếm công khai chỉ trả người đã khuất, kể cả khi từ khoá khớp người còn sống")
    void khach_timKiemChiRaNguoiDaKhuat() throws Exception {
        MvcResult result = goi(get("/api/v1/public/persons/search").param("q", "Nguyen Van"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = than(result);
        khongCoDauVetNguoiConSong(body);

        List<String> ten = com.jayway.jsonpath.JsonPath.read(body, "$.items[*].displayName");
        assertThat(ten).contains("Nguyễn Văn Trưởng", "Nguyễn Văn Cháu");
        List<Boolean> conSong = com.jayway.jsonpath.JsonPath.read(body, "$.items[*].isAlive");
        assertThat(conSong).isNotEmpty().allMatch(alive -> !alive);
    }

    @Test
    @DisplayName("tìm không dấu vẫn ra tên có dấu (FR-4.4) và vẫn chỉ ra người đã khuất")
    void khach_timKhongDau() throws Exception {
        MvcResult result = goi(get("/api/v1/public/persons/search").param("q", "nguyen van chau"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(than(result)).contains("Nguyễn Văn Cháu");
        khongCoDauVetNguoiConSong(than(result));
    }

    @Test
    @DisplayName("trang kết quả công khai không có totalElements — không đếm hộ dân số dòng họ")
    void khach_khongNhanDuocTongSoKetQua() throws Exception {
        mockMvc.perform(get("/api/v1/public/persons/search").param("q", "Nguyen")
                        .with(tuMotKhachLa()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").doesNotExist())
                .andExpect(jsonPath("$.page.totalPages").doesNotExist())
                .andExpect(jsonPath("$.page.hasNext").exists());
    }

    @Test
    @DisplayName("từ khoá quá ngắn bị chặn — một ký tự là phép liệt kê trá hình")
    void khach_tuKhoaQuaNgan_nhan400() throws Exception {
        assertThat(goi(get("/api/v1/public/persons/search").param("q", "N"))
                .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("phân trang sâu bị chặn — không lật trang tới khi hết dòng họ")
    void khach_phanTrangSau_nhan400() throws Exception {
        assertThat(goi(get("/api/v1/public/persons/search").param("q", "Nguyen").param("page", "9"))
                .getResponse().getStatus()).isEqualTo(400);
    }

    // =====================================================================================
    // 4. Ngữ cảnh Khách bị ép cứng
    // =====================================================================================

    @Test
    @DisplayName("mang token ADMIN vào cổng công khai vẫn chỉ nhận dữ liệu mức Khách")
    void dungToken_vanChiNhanDuLieuMucKhach() throws Exception {
        RequestPostProcessor quanTri = jwt()
                .jwt(builder -> builder.subject("sub-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

        // 1. Nguoi da khuat: van khong lo khoi lien he, du vai toan dong ho binh thuong duoc xem.
        MvcResult daKhuat = mockMvc.perform(get("/api/v1/public/persons/{id}", cuTo)
                .with(tuMotKhachLa()).with(quanTri)).andReturn();
        assertThat(daKhuat.getResponse().getStatus()).isEqualTo(200);
        khongCoDauVetNguoiConSong(than(daKhuat));

        // 2. Nguoi con song: van 404 — bat ke token.
        MvcResult conSong = mockMvc.perform(get("/api/v1/public/persons/{id}", conThuConSong)
                .with(tuMotKhachLa()).with(quanTri)).andReturn();
        assertThat(conSong.getResponse().getStatus()).isEqualTo(404);

        // 3. Pha do: van khong co nguoi con song nao.
        MvcResult cay = mockMvc.perform(get("/api/v1/public/tree")
                .param("rootId", cuTo.toString()).param("depth", "4")
                .with(tuMotKhachLa()).with(quanTri)).andReturn();
        khongCoDauVetNguoiConSong(than(cay));

        // 4. Tim kiem: van khong co nguoi con song nao.
        MvcResult tim = mockMvc.perform(get("/api/v1/public/persons/search").param("q", "Nguyen")
                .with(tuMotKhachLa()).with(quanTri)).andReturn();
        khongCoDauVetNguoiConSong(than(tim));
    }

    @Test
    @DisplayName("ép ngữ cảnh Khách không được làm hỏng danh tính của request kế tiếp")
    void epNguCanhKhach_khongLamRoRiSangRequestKeTiep() throws Exception {
        RequestPostProcessor thanhVien = jwt()
                .jwt(builder -> builder.subject("sub-thanh-vien"))
                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));

        mockMvc.perform(get("/api/v1/public/persons/{id}", cuTo).with(tuMotKhachLa())
                .with(thanhVien)).andExpect(status().isOk());

        // Ngay sau do, endpoint DANH CHO THANH VIEN van phai nhan ra token: neu PublicGuestScope
        // quen khoi phuc SecurityContext thi thread cua pool servlet se mang nga canh rong.
        mockMvc.perform(get("/api/v1/persons/{id}", cuTo).with(thanhVien))
                .andExpect(status().isOk());
    }

    // =====================================================================================
    // 5. Chống lạm dụng
    // =====================================================================================

    @Test
    @DisplayName("vượt hạn mức nhận 429 kèm Retry-After, và thân lỗi vẫn theo RFC 7807")
    void vuotHanMuc_nhan429() throws Exception {
        String ke = "203.0.113.77";
        RequestPostProcessor cungMotIp = request -> {
            request.setRemoteAddr(ke);
            return request;
        };

        MvcResult chan = null;
        // Han muc mac dinh 120 diem/phut, moi luot xem ho so ton 1 diem. Lap rong rai de khong
        // flaky khi vong lap roi dung vao ranh gioi phut.
        for (int i = 0; i < 400 && chan == null; i++) {
            MvcResult result = mockMvc.perform(get("/api/v1/public/persons/{id}", UUID.randomUUID())
                    .with(cungMotIp)).andReturn();
            if (result.getResponse().getStatus() == 429) {
                chan = result;
            }
        }

        assertThat(chan).as("phai chan duoc mot ke quet ho so lien tuc").isNotNull();
        assertThat(chan.getResponse().getHeader("Retry-After")).isNotNull();
        assertThat(chan.getResponse().getContentType())
                .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(than(chan)).contains("\"status\":429").contains("RATE_LIMITED");
    }

    @Test
    @DisplayName("tìm kiếm đắt hơn xem hồ sơ — lối liệt kê phải cạn hạn mức trước")
    void timKiem_tonHanMucNhieuHon() throws Exception {
        String ke = "203.0.113.78";
        RequestPostProcessor cungMotIp = request -> {
            request.setRemoteAddr(ke);
            return request;
        };

        int luotTruocKhiBiChan = 0;
        for (int i = 0; i < 400; i++) {
            MvcResult result = mockMvc.perform(get("/api/v1/public/persons/search")
                    .param("q", "Nguyen").with(cungMotIp)).andReturn();
            if (result.getResponse().getStatus() == 429) {
                break;
            }
            luotTruocKhiBiChan++;
        }

        // 120 diem / 5 diem moi luot = 24 luot. Khang dinh long de khong gan chat vao con so cau
        // hinh, nhung du chat de bat duoc truong hop ai do lo dat tim kiem ngang gia xem ho so.
        assertThat(luotTruocKhiBiChan).isPositive().isLessThan(60);
    }

    // =====================================================================================
    // 6. Bề mặt cố ý đóng
    // =====================================================================================

    @Test
    @DisplayName("mọi endpoint dành cho thành viên vẫn đòi đăng nhập — cổng công khai không nới ra")
    void beMatThanhVien_vanDoiDangNhap() throws Exception {
        assertThat(goi(get("/api/v1/persons/{id}", cuTo)).getResponse().getStatus())
                .isEqualTo(401);
        assertThat(goi(get("/api/v1/tree").param("rootId", cuTo.toString()))
                .getResponse().getStatus()).isEqualTo(401);
        assertThat(goi(get("/api/v1/persons/search").param("q", "Nguyen"))
                .getResponse().getStatus()).isEqualTo(401);
        assertThat(goi(get("/api/v1/events")).getResponse().getStatus()).isEqualTo(401);
        assertThat(goi(get("/api/v1/notifications")).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("cổng công khai chỉ đọc — mọi phương thức ghi bị chặn")
    void congCongKhai_chiDoc() throws Exception {
        int xoa = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/public/persons/{id}", cuTo).with(tuMotKhachLa()))
                .andReturn().getResponse().getStatus();
        int them = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/public/persons").with(tuMotKhachLa())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus();

        // 401 (chuoi bao mat chi mo GET) hoac 405 deu dat yeu cau; dieu KHONG duoc phep la 2xx.
        assertThat(xoa).isNotIn(200, 201, 204);
        assertThat(them).isNotIn(200, 201, 204);
    }
}
