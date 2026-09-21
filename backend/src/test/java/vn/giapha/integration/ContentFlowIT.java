package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.giapha.genealogy.domain.PrivacyConsent;
import vn.giapha.genealogy.domain.PrivacyFieldGroup;
import vn.giapha.genealogy.domain.ShareScope;

/**
 * <b>Bài viết và vinh danh</b> trên hạ tầng thật — toàn bộ luật đi qua HTTP.
 *
 * <h2>Vì sao mọi ca đều gọi qua MockMvc, không gọi thẳng service</h2>
 * Kỷ luật có sẵn của dự án: <b>không test nào ghi thẳng vào bảng để vượt một cổng</b>. Ở đợt này
 * điều đó đặc biệt quan trọng, vì ba trong bốn luật phải chứng minh chỉ tồn tại ở đường HTTP thật:
 * <ol>
 *   <li><b>Phạm vi chi là một phép so {@code ltree} chạy trong Postgres.</b> Trưởng chi Ất không
 *       duyệt được bài của chi Bính, và câu trả lời ấy đến từ toán tử {@code ltree[] @> ltree} —
 *       không có bản mô phỏng nào trong bộ nhớ nói đúng về nó.</li>
 *   <li><b>"Chưa vào phả thì chưa viết bài" là một luật của tầng use case</b>, và cột
 *       {@code author_person_id NOT NULL} chỉ là lưới cuối. Gieo thẳng một hàng {@code post} sẽ
 *       chứng minh cái lưới, không chứng minh cái luật.</li>
 *   <li><b>Bộ lọc nhóm trường riêng tư thứ sáu phụ thuộc NGƯỜI GỌI.</b> Nó chỉ chạy khi có một
 *       {@code SecurityContext} thật, và nó gọi xuyên sang context {@code genealogy}.</li>
 * </ol>
 *
 * <p>Luật thứ tư — {@code audit_log} không chứa dữ liệu Tầng 3 — thì ngược lại: nó <i>phải</i>
 * đọc thẳng bảng, vì điều cần chứng minh chính là nội dung đã nằm trên đĩa.</p>
 */
@DisplayName("Bài viết & vinh danh (context content)")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class ContentFlowIT extends AbstractIntegrationTest {

    /**
     * Một câu văn có <b>số điện thoại thật trong thân bài</b>. Người viết là bác 60 tuổi kể chuyện
     * họ hàng, và họ <i>sẽ</i> gõ số điện thoại vào bài — đó chính là lý do {@code audit_log} không
     * được chép thân bài.
     */
    private static final String THAN_BAI_CO_SO_DIEN_THOAI =
            "Chu nhat toi ca ho hop mat. Ai di duoc thi goi cho bac Tam so 0912345678 de dat com.";

    private static final String SO_DIEN_THOAI = "0912345678";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID chiAt;
    private UUID chiBinh;

    /** Trưởng Chi Ất. Không được đụng vào bài của Chi Bính. */
    private UUID truongAtUserId;

    /** Trưởng Chi Bính — người duyệt hợp lệ cho bài của chi mình. */
    private UUID truongBinhUserId;

    /** Hội đồng Tộc biểu: phạm vi toàn dòng họ. */
    private UUID hoiDongUserId;

    /** Thành viên thường của Chi Bính — tác giả. */
    private UUID thanhVienBinhUserId;

    /** Cụ Tổ, đã khuất: dữ liệu công khai, kể cả vinh danh. */
    private UUID cuToPersonId;

    /** Người còn sống, nhóm "vinh danh" ở mức mặc định = KÍN. */
    private UUID songKinPersonId;

    /** Người còn sống đã tự bật "cả họ xem" cho nhóm vinh danh. */
    private UUID songMoPersonId;

    @BeforeEach
    void dungDongHo() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");
        chiBinh = insertBranch("Chi Bính", "goc.chi_binh", goc, "CHI");

        UUID truongAtPersonId = seed(PersonFixtures.living("Nguyễn Văn Ất").branch(chiAt)
                .generation(6));
        truongAtUserId = insertAppUser("sub-truong-at", truongAtPersonId);
        assignBranchRole(truongAtUserId, "BRANCH_HEAD", chiAt);

        UUID truongBinhPersonId = seed(PersonFixtures.living("Nguyễn Văn Bính").branch(chiBinh)
                .generation(6));
        truongBinhUserId = insertAppUser("sub-truong-binh", truongBinhPersonId);
        assignBranchRole(truongBinhUserId, "BRANCH_HEAD", chiBinh);

        UUID hoiDongPersonId = seed(PersonFixtures.living("Nguyễn Văn Tộc").branch(goc)
                .generation(5));
        hoiDongUserId = insertAppUser("sub-hoi-dong", hoiDongPersonId);
        assignBranchRole(hoiDongUserId, "COUNCIL", null);

        UUID thanhVienBinhPersonId = seed(PersonFixtures.living("Nguyễn Thị Mai").branch(chiBinh)
                .generation(7));
        thanhVienBinhUserId = insertAppUser("sub-thanh-vien-binh", thanhVienBinhPersonId);

        cuToPersonId = seed(PersonFixtures.deceased("Nguyễn Phúc Tổ", 1890).branch(goc)
                .generation(1));

        // Mac dinh: PrivacyConsent.allPrivate() => nhom `honour` o muc PRIVATE.
        songKinPersonId = seed(PersonFixtures.living("Nguyễn Văn Kín").branch(chiAt)
                .generation(7).consent(PrivacyConsent.allPrivate()));

        songMoPersonId = seed(PersonFixtures.living("Nguyễn Văn Mở").branch(chiAt)
                .generation(7).consent(PrivacyConsent.allPrivate()
                        .with(PrivacyFieldGroup.HONOUR, ShareScope.CLAN)));
    }

    // =====================================================================================
    // Bài viết — quyền viết và quyền duyệt
    // =====================================================================================

        @Test
        @DisplayName("Tài khoản chưa được duyệt vào phả KHÔNG đăng bài được (403 AUTHOR_NOT_IN_PHA)")
        void chuaVaoPha_thiKhongVietDuocBai() throws Exception {
            // Nguoi vua dang ky bang ma moi dong ho: co tai khoan, xem duoc pha do, person_id rong.
            insertUnlinkedUser("sub-chua-vao-pha");

            MvcResult result = mockMvc.perform(post("/api/v1/posts")
                            .with(nhuLa("sub-chua-vao-pha", "ROLE_MEMBER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "Tôi muốn viết bài", "body": "Nội dung thử"}
                                    """))
                    .andReturn();

            assertThat(result.getResponse().getStatus())
                    .as("quyet dinh da chot: viet bai la tieng noi cua nguoi trong ho")
                    .isEqualTo(403);
            assertThat(ma(result)).isEqualTo("AUTHOR_NOT_IN_PHA");
            assertThat(demBang("post")).isZero();
        }

        @Test
        @DisplayName("Trưởng chi Ất KHÔNG duyệt được bài của người chi Bính")
        void truongChiAt_khongDuyetDuocBaiChiBinh() throws Exception {
            UUID postId = taoVaGuiDuyet(thanhVienBinh(), "Chuyện chi Bính");

            MvcResult tuChoi = mockMvc.perform(post("/api/v1/posts/" + postId + "/review")
                            .with(truongChiAt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"approve": true}
                                    """))
                    .andReturn();

            assertThat(tuChoi.getResponse().getStatus()).isEqualTo(403);
            assertThat(ma(tuChoi))
                    .as("dung vai, sai nhanh => BRANCH_SCOPE_VIOLATION, khong phai FORBIDDEN")
                    .isEqualTo("BRANCH_SCOPE_VIOLATION");
            assertThat(trangThaiBai(postId)).isEqualTo("PENDING");

            // Truong chi Binh thi duyet duoc — cung token, cung endpoint, khac nhanh.
            MvcResult duyet = mockMvc.perform(post("/api/v1/posts/" + postId + "/review")
                            .with(truongChiBinh())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"approve": true}
                                    """))
                    .andReturn();

            assertThat(duyet.getResponse().getStatus()).isEqualTo(200);
            assertThat(than(duyet).get("status").asText()).isEqualTo("PUBLISHED");
            assertThat(than(duyet).get("publishedAt").isNull()).isFalse();
        }

        @Test
        @DisplayName("Hội đồng duyệt được bài của mọi chi")
        void hoiDong_duyetDuocCaHo() throws Exception {
            UUID postId = taoVaGuiDuyet(thanhVienBinh(), "Chuyện cả họ");

            MvcResult duyet = mockMvc.perform(post("/api/v1/posts/" + postId + "/review")
                            .with(hoiDong())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"approve": true}
                                    """))
                    .andReturn();

            assertThat(duyet.getResponse().getStatus()).isEqualTo(200);
            assertThat(than(duyet).get("status").asText()).isEqualTo("PUBLISHED");
        }

        @Test
        @DisplayName("Không ai tự duyệt bài của chính mình — kể cả Trưởng chi trong nhánh mình")
        void khongTuDuyetDuocBaiCuaMinh() throws Exception {
            UUID postId = taoVaGuiDuyet(truongChiBinh(), "Bài của chính Trưởng chi");

            MvcResult result = mockMvc.perform(post("/api/v1/posts/" + postId + "/review")
                            .with(truongChiBinh())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"approve": true}
                                    """))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(403);
            assertThat(ma(result)).isEqualTo("SELF_REVIEW_FORBIDDEN");
            assertThat(trangThaiBai(postId)).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("Bản nháp CHỈ chính tác giả thấy — Hội đồng cũng không")
        void banNhap_chiTacGiaThay() throws Exception {
            UUID postId = taoNhap(thanhVienBinh(), "Đang viết dở");

            assertThat(danhSachId(thanhVienBinh(), "?status=DRAFT")).contains(postId);
            assertThat(danhSachId(hoiDong(), "?status=DRAFT"))
                    .as("mot ban nhap chua gui la thu chua ai duoc moc doc")
                    .doesNotContain(postId);

            assertThat(mockMvc.perform(get("/api/v1/posts/" + postId).with(hoiDong()))
                    .andReturn().getResponse().getStatus())
                    .as("khong duoc phep thay thi 404, khong phai 403 — 403 la tu xac nhan bai co that")
                    .isEqualTo(404);
        }

        @Test
        @DisplayName("Bài chờ duyệt chỉ hiện với người duyệt TRONG phạm vi")
        void hangDoiDuyet_loctheoPhamVi() throws Exception {
            UUID postId = taoVaGuiDuyet(thanhVienBinh(), "Chờ duyệt ở chi Bính");

            assertThat(danhSachId(truongChiBinh(), "?status=PENDING")).contains(postId);
            assertThat(danhSachId(truongChiAt(), "?status=PENDING")).doesNotContain(postId);
            assertThat(danhSachId(hoiDong(), "?status=PENDING")).contains(postId);

            assertThat(dem(truongChiAt(), "/api/v1/posts/pending/count")).isZero();
            assertThat(dem(truongChiBinh(), "/api/v1/posts/pending/count")).isEqualTo(1);
        }

        @Test
        @DisplayName("Trả lại kèm lý do đưa bài về bản nháp; thiếu lý do thì không trả lại được")
        void traLai_doiLyDo() throws Exception {
            UUID postId = taoVaGuiDuyet(thanhVienBinh(), "Bài cần sửa");

            MvcResult thieuLyDo = mockMvc.perform(post("/api/v1/posts/" + postId + "/review")
                            .with(truongChiBinh())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"approve": false}
                                    """))
                    .andReturn();
            assertThat(thieuLyDo.getResponse().getStatus()).isEqualTo(422);
            assertThat(trangThaiBai(postId)).isEqualTo("PENDING");

            MvcResult coLyDo = mockMvc.perform(post("/api/v1/posts/" + postId + "/review")
                            .with(truongChiBinh())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"approve": false, "note": "Thiếu năm mất của cụ"}
                                    """))
                    .andReturn();
            assertThat(coLyDo.getResponse().getStatus()).isEqualTo(200);
            assertThat(than(coLyDo).get("status").asText()).isEqualTo("DRAFT");
            assertThat(than(coLyDo).get("rejectReason").asText())
                    .as("ly do phai o lai tren ban nhap de nguoi viet con doc duoc trong luc sua")
                    .isEqualTo("Thiếu năm mất của cụ");
        }

        @Test
        @DisplayName("Sửa bản nháp đòi If-Match; phiên bản lệch thì 409")
        void suaNhap_doiIfMatch() throws Exception {
            UUID postId = taoNhap(thanhVienBinh(), "Bản nháp");

            assertThat(mockMvc.perform(patch("/api/v1/posts/" + postId)
                            .with(thanhVienBinh())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "Đổi tiêu đề"}
                                    """))
                    .andReturn().getResponse().getStatus())
                    .as("khong cho ghi mu: §2 doi luu nhap tu dong, tuc hai tab cung ghi la binh thuong")
                    .isEqualTo(412);

            MvcResult lechPhienBan = mockMvc.perform(patch("/api/v1/posts/" + postId)
                            .with(thanhVienBinh())
                            .header(HttpHeaders.IF_MATCH, "\"99\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "Đổi tiêu đề"}
                                    """))
                    .andReturn();
            assertThat(lechPhienBan.getResponse().getStatus()).isEqualTo(409);
            assertThat(ma(lechPhienBan)).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");

            MvcResult dungPhienBan = mockMvc.perform(patch("/api/v1/posts/" + postId)
                            .with(thanhVienBinh())
                            .header(HttpHeaders.IF_MATCH, "\"0\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "Đổi tiêu đề"}
                                    """))
                    .andReturn();
            assertThat(dungPhienBan.getResponse().getStatus()).isEqualTo(200);
            assertThat(than(dungPhienBan).get("title").asText()).isEqualTo("Đổi tiêu đề");
        }

        @Test
        @DisplayName("Gỡ bài là XOÁ MỀM: hàng ở lại, chỉ đổi trạng thái")
        void goBai_laXoaMem() throws Exception {
            UUID postId = taoVaGuiDuyet(thanhVienBinh(), "Bài sẽ bị gỡ");
            duyet(truongChiBinh(), postId);

            MvcResult go = mockMvc.perform(post("/api/v1/posts/" + postId + "/withdraw")
                            .with(truongChiBinh())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"reason": "Thông tin chưa chính xác"}
                                    """))
                    .andReturn();

            assertThat(go.getResponse().getStatus()).isEqualTo(200);
            assertThat(trangThaiBai(postId))
                    .as("mot bai da len trang chu roi bi go la du kien phai tra lai duoc")
                    .isEqualTo("WITHDRAWN");
            assertThat(demBang("post")).isEqualTo(1);
            assertThat(danhSachId(thanhVienBinh(), "?status=PUBLISHED")).doesNotContain(postId);
        }

        @Test
        @DisplayName("Trang chủ chỉ trả bài đã đăng, mới nhất trước")
        void trangChu_chiBaiDaDang() throws Exception {
            UUID daDang = taoVaGuiDuyet(thanhVienBinh(), "Bài đã đăng");
            duyet(truongChiBinh(), daDang);
            UUID conChoDuyet = taoVaGuiDuyet(thanhVienBinh(), "Bài còn chờ");

            MvcResult feed = mockMvc.perform(get("/api/v1/posts/feed?limit=10")
                    .with(thanhVienBinh())).andReturn();

            assertThat(feed.getResponse().getStatus()).isEqualTo(200);
            List<UUID> ids = ids(than(feed));
            assertThat(ids).contains(daDang).doesNotContain(conChoDuyet);
        }

        @Test
        @DisplayName("Tên tác giả đi qua bộ lọc riêng tư, không đọc thẳng bảng person")
        void tenTacGia_quaBoLocRiengTu() throws Exception {
            UUID postId = taoVaGuiDuyet(thanhVienBinh(), "Bài có tên tác giả");
            duyet(truongChiBinh(), postId);

            MvcResult xem = mockMvc.perform(get("/api/v1/posts/" + postId).with(truongChiAt()))
                    .andReturn();

            assertThat(xem.getResponse().getStatus()).isEqualTo(200);
            assertThat(than(xem).get("authorDisplayName").asText())
                    .as("thanh vien da dang nhap thay ten (Tang 1) cua nguoi con song o chi khac")
                    .isEqualTo("Nguyễn Thị Mai");
            // getHeaders(), KHONG phai getHeader(): bo loc CORS cung ghi mot dong Vary ("Origin"),
            // va getHeader() chi tra dong DAU TIEN — mot phep khang dinh nhu vay se do dung o ca
            // truong hop header cua ta van co mat.
            assertThat(String.join(", ", xem.getResponse().getHeaders(HttpHeaders.VARY)))
                    .as("than phan hoi phu thuoc nguoi doc — thieu Vary la may tinh chung o nha tho "
                            + "ho phuc vu lai noi dung da loc cho nguoi truoc")
                    .contains(HttpHeaders.AUTHORIZATION);
            assertThat(xem.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                    .as("no-store: 304 tra lai than da loc cho nguoi truoc la ro ri khong de lai log")
                    .contains("no-store");
        }

        @Test
        @DisplayName("reviewedByDisplayName hiện TÊN người duyệt, và tên ấy qua bộ lọc riêng tư")
        void tenNguoiDuyet_hienRaVaQuaBoLoc() throws Exception {
            UUID postId = taoVaGuiDuyet(thanhVienBinh(), "Bài để xem ai duyệt");
            duyet(truongChiBinh(), postId);

            MvcResult xem = mockMvc.perform(get("/api/v1/posts/" + postId).with(thanhVienBinh()))
                    .andReturn();

            assertThat(xem.getResponse().getStatus()).isEqualTo(200);
            assertThat(than(xem).get("reviewedBy").asText())
                    .as("cot reviewed_by giu KHOA TAI KHOAN, dung quy uoc change_request.reviewer_id")
                    .isEqualTo(truongBinhUserId.toString());
            assertThat(than(xem).get("reviewedByDisplayName").asText())
                    .as("checklist §2: ghi ro AI duyet — mot khoa tai khoan khong hien len man hinh duoc")
                    .isEqualTo("Nguyễn Văn Bính");
        }

        @Test
        @DisplayName("Bài chưa ai duyệt thì không có reviewedByDisplayName, và KHÔNG nổ")
        void baiChuaDuyet_khongCoTenNguoiDuyet() throws Exception {
            UUID postId = taoNhap(thanhVienBinh(), "Bản nháp chưa ai đụng");

            MvcResult xem = mockMvc.perform(get("/api/v1/posts/" + postId).with(thanhVienBinh()))
                    .andReturn();

            assertThat(xem.getResponse().getStatus())
                    .as("reviewedBy rong => khoa nhan khau rong => Map.copyOf(...).get(null) nem NPE"
                            + " neu khong chan; day la duong binh thuong nhat cua man soan bai")
                    .isEqualTo(200);
            assertThat(than(xem).has("reviewedByDisplayName")).isFalse();
            assertThat(than(xem).has("reviewedBy")).isFalse();
        }

        @Test
        @DisplayName("Hợp đồng KHÔNG còn coverImageKey — không có lối tải ảnh nào để trỏ tới")
        void hopDong_khongConKhoaAnh() throws Exception {
            UUID postId = taoNhap(thanhVienBinh(), "Bài không ảnh");

            MvcResult xem = mockMvc.perform(get("/api/v1/posts/" + postId).with(thanhVienBinh()))
                    .andReturn();

            assertThat(than(xem).has("coverImageKey"))
                    .as("mot truong tro vao hu khong te hon mot truong vang mat: backend chua co SDK"
                            + " S3/MinIO nao nen khong co loi tai anh len")
                    .isFalse();
        }

    // =====================================================================================
    // Vinh danh — nhóm trường riêng tư thứ sáu
    // =====================================================================================

        @Test
        @DisplayName("Vinh danh của người CÒN SỐNG chưa mở công tắc KHÔNG lọt ra ngoài")
        void nguoiConSongKin_khongLotRaNgoai() throws Exception {
            UUID honourId = khai(truongChiBinh(), songKinPersonId, "Tiến sĩ Luật", 2019);
            duyetVinhDanh(hoiDong(), honourId);

            // Nguoi doc: thanh vien thuong, khac chi, khong phai chinh chu, khong phai nguoi khai.
            assertThat(vinhDanhCuaNguoi(thanhVienBinh(), songKinPersonId))
                    .as("nhom truong thu sau mac dinh KIN — chinh chu tu quyet, khong ai quyet ho")
                    .isEmpty();

            assertThat(mockMvc.perform(get("/api/v1/honours/" + honourId).with(thanhVienBinh()))
                    .andReturn().getResponse().getStatus())
                    .as("khong duoc phep xem thi 404, khong phai 403")
                    .isEqualTo(404);

            // Chinh chu thi luon thay ban ghi cua minh.
            insertAppUser("sub-nguoi-kin", songKinPersonId);
            assertThat(vinhDanhCuaNguoi(nhuLaJwt("sub-nguoi-kin", "ROLE_MEMBER"), songKinPersonId))
                    .as("muc Rieng tu nghia la 'chi chinh chu + Hoi dong', khong phai 'khong ai'")
                    .contains(honourId);
        }

        @Test
        @DisplayName("Chủ thể tự bật 'cả họ xem' thì vinh danh hiện ra với mọi thành viên")
        void chuTheMoCongTac_thiHienRa() throws Exception {
            UUID honourId = khai(truongChiBinh(), songMoPersonId, "Thạc sĩ Y khoa", 2020);
            duyetVinhDanh(hoiDong(), honourId);

            assertThat(vinhDanhCuaNguoi(thanhVienBinh(), songMoPersonId)).contains(honourId);
        }

        @Test
        @DisplayName("Vinh danh của người ĐÃ KHUẤT là dữ liệu công khai")
        void nguoiDaKhuat_congKhai() throws Exception {
            UUID honourId = khai(truongChiBinh(), cuToPersonId, "Tiến sĩ khoa Nhâm Tuất", 1802);
            duyetVinhDanh(hoiDong(), honourId);

            assertThat(vinhDanhCuaNguoi(thanhVienBinh(), cuToPersonId))
                    .as("nguoi da khuat cong khai (BA v2 §10) — mo hinh dong thuan khong ap len ho")
                    .contains(honourId);
        }

        @Test
        @DisplayName("Bản ghi CHỜ DUYỆT vẫn vào hàng đợi dù nhóm trường đang kín")
        void choDuyet_vanVaoHangDoi() throws Exception {
            UUID honourId = khai(thanhVienBinh(), songKinPersonId, "Giải nhất tỉnh", 2024);

            assertThat(vinhDanhChoDuyet(truongChiAt()))
                    .as("chi At quan nguoi duoc vinh danh, nen ban ghi phai vao hang doi cua ho — "
                            + "neu bo loc rieng tu chan o day thi hang doi LUON rong")
                    .contains(honourId);
            assertThat(vinhDanhChoDuyet(truongChiBinh()))
                    .as("nguoi duoc vinh danh thuoc chi At, khong phai chi Binh")
                    .doesNotContain(honourId);
        }

        @Test
        @DisplayName("Trưởng chi Ất không duyệt được vinh danh của người chi Bính")
        void duyetVinhDanh_theoPhamVi() throws Exception {
            UUID nguoiChiBinh = seed(PersonFixtures.living("Nguyễn Văn Bảy").branch(chiBinh)
                    .generation(7));
            UUID honourId = khai(hoiDong(), nguoiChiBinh, "Huân chương Lao động", 2021);

            MvcResult tuChoi = mockMvc.perform(post("/api/v1/honours/" + honourId + "/review")
                            .with(truongChiAt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"approve": true}
                                    """))
                    .andReturn();

            assertThat(tuChoi.getResponse().getStatus()).isEqualTo(403);
            assertThat(ma(tuChoi)).isEqualTo("BRANCH_SCOPE_VIOLATION");
        }

        @Test
        @DisplayName("Lọc theo chi tính cả cây con; đếm được 'chi nào bao nhiêu người đỗ đạt'")
        void locTheoChi_tinhCaCayCon() throws Exception {
            UUID nganhDuoiChiAt = insertBranch("Ngành Trưởng", "goc.chi_at.nganh_truong",
                    chiAt, "NGANH");
            UUID cuDuoiNganh = seed(PersonFixtures.deceased("Nguyễn Phúc Cận", 1920)
                    .branch(nganhDuoiChiAt).generation(3));
            UUID honourId = khai(truongChiAt(), cuDuoiNganh, "Cử nhân", 1900);
            duyetVinhDanh(hoiDong(), honourId);

            assertThat(idsCuaTruyVan(thanhVienBinh(),
                    "/api/v1/honours?branchId=" + chiAt + "&kind=DO_DAT"))
                    .as("'Chi At co bao nhieu nguoi do dat' phai gom ca nganh/canh ben duoi")
                    .contains(honourId);
            assertThat(idsCuaTruyVan(thanhVienBinh(),
                    "/api/v1/honours?branchId=" + chiBinh + "&kind=DO_DAT"))
                    .doesNotContain(honourId);
        }

        @Test
        @DisplayName("DELETE là xoá mềm: hàng ở lại với cờ is_deleted")
        void xoaVinhDanh_laXoaMem() throws Exception {
            UUID honourId = khai(truongChiAt(), cuToPersonId, "Chức Tri phủ", 1850);
            duyetVinhDanh(hoiDong(), honourId);

            assertThat(mockMvc.perform(delete("/api/v1/honours/" + honourId).with(truongChiAt()))
                    .andReturn().getResponse().getStatus()).isEqualTo(204);

            assertThat(demBang("honour")).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT is_deleted FROM honour WHERE id = ?", Boolean.class, honourId))
                    .isTrue();
            assertThat(vinhDanhCuaNguoi(thanhVienBinh(), cuToPersonId)).doesNotContain(honourId);
        }

        @Test
        @DisplayName("Người chưa được duyệt vào phả cũng không khai vinh danh được")
        void chuaVaoPha_khongKhaiDuoc() throws Exception {
            insertUnlinkedUser("sub-chua-vao-pha-2");

            MvcResult result = mockMvc.perform(post("/api/v1/honours")
                            .with(nhuLa("sub-chua-vao-pha-2", "ROLE_MEMBER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"personId": "%s", "kind": "DO_DAT", "title": "Tiến sĩ"}
                                    """.formatted(cuToPersonId)))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(403);
            assertThat(ma(result)).isEqualTo("AUTHOR_NOT_IN_PHA");
            assertThat(demBang("honour")).isZero();
        }

        @Test
        @DisplayName("GET /honours?status= lọc được, nên hàng đợi không phải tải về rồi tự gom")
        void locTheoTrangThai() throws Exception {
            UUID choDuyet = khai(truongChiAt(), cuToPersonId, "Cử nhân chờ duyệt", 1880);
            UUID daDuyet = khai(truongChiAt(), cuToPersonId, "Tiến sĩ đã duyệt", 1890);
            duyetVinhDanh(hoiDong(), daDuyet);

            assertThat(idsCuaTruyVan(truongChiAt(), "/api/v1/honours?status=PENDING"))
                    .containsExactly(choDuyet);
            assertThat(idsCuaTruyVan(truongChiAt(), "/api/v1/honours?status=PUBLISHED"))
                    .containsExactly(daDuyet);
            assertThat(idsCuaTruyVan(truongChiAt(), "/api/v1/honours"))
                    .as("khong loc thi tra ve moi trang thai ma nguoi goi duoc thay")
                    .contains(choDuyet, daDuyet);
        }

        @Test
        @DisplayName("Vinh danh đã duyệt cũng ghi rõ tên người duyệt")
        void vinhDanh_ghiRoAiDuyet() throws Exception {
            UUID honourId = khai(truongChiAt(), cuToPersonId, "Hương cống", 1810);
            duyetVinhDanh(hoiDong(), honourId);

            MvcResult xem = mockMvc.perform(get("/api/v1/honours/" + honourId)
                    .with(thanhVienBinh())).andReturn();

            assertThat(xem.getResponse().getStatus()).isEqualTo(200);
            assertThat(than(xem).get("reviewedByDisplayName").asText())
                    .isEqualTo("Nguyễn Văn Tộc");
        }

    // =====================================================================================
    // audit_log
    // =====================================================================================

        @Test
        @DisplayName("audit_log ghi đủ vết nhưng KHÔNG chứa một mẩu dữ liệu Tầng 3 nào")
        void auditLog_khongChuaTang3() throws Exception {
            UUID postId = taoNhapVoiThan(thanhVienBinh(), "Họp mặt dòng họ",
                    THAN_BAI_CO_SO_DIEN_THOAI);
            guiDuyet(thanhVienBinh(), postId);
            duyet(truongChiBinh(), postId);

            UUID honourId = khai(truongChiAt(), songKinPersonId, "Tiến sĩ Luật, ĐH Luật Hà Nội",
                    2019);
            duyetVinhDanh(hoiDong(), honourId);

            String nhatKy = String.join("\n", jdbc.queryForList(
                    "SELECT COALESCE(before::text, '') || ' ' || COALESCE(after::text, '')"
                            + " || ' ' || COALESCE(note, '') FROM audit_log"
                            + " WHERE entity_type IN ('Post', 'Honour')", String.class));

            assertThat(nhatKy).isNotBlank();
            assertThat(nhatKy)
                    .as("than bai la van ban tu do; audit_log la bang CHI GHI THEM, lot vao la "
                            + "khong go ra duoc")
                    .doesNotContain(SO_DIEN_THOAI)
                    .doesNotContain(THAN_BAI_CO_SO_DIEN_THOAI)
                    .doesNotContain("Họp mặt dòng họ");
            assertThat(nhatKy)
                    .as("tu V17 vinh danh la nhom truong rieng tu thu sau — chep tieu de vao nhat "
                            + "ky nghia la nguoi bam 'rieng tu' van con nguyen mot ban sao")
                    .doesNotContain("Tiến sĩ Luật")
                    .doesNotContain("ĐH Luật Hà Nội");

            // ... nhung van phai tra loi duoc "ai doi cai gi, luc nao".
            assertThat(hanhDong("Post")).contains("CREATE", "UPDATE", "APPROVE");
            assertThat(hanhDong("Honour")).contains("CREATE", "APPROVE");
            assertThat(nhatKy).contains("\"status\": \"PUBLISHED\"");
        }

    // =====================================================================================
    // Tiện ích
    // =====================================================================================

    private UUID taoNhap(RequestPostProcessor nguoiGoi, String tieuDe) throws Exception {
        return taoNhapVoiThan(nguoiGoi, tieuDe, "Nội dung bài viết thử nghiệm.");
    }

    private UUID taoNhapVoiThan(RequestPostProcessor nguoiGoi, String tieuDe, String thanBai)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .with(nguoiGoi)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("title", tieuDe, "body", thanBai))))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("tao nhap that bai: %s", chuoiThan(result))
                .isEqualTo(201);
        return UUID.fromString(than(result).get("id").asText());
    }

    private void guiDuyet(RequestPostProcessor nguoiGoi, UUID postId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts/" + postId + "/submit")
                .with(nguoiGoi)).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("gui duyet that bai: %s", chuoiThan(result))
                .isEqualTo(200);
    }

    private UUID taoVaGuiDuyet(RequestPostProcessor nguoiGoi, String tieuDe) throws Exception {
        UUID postId = taoNhap(nguoiGoi, tieuDe);
        guiDuyet(nguoiGoi, postId);
        return postId;
    }

    private void duyet(RequestPostProcessor nguoiGoi, UUID postId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts/" + postId + "/review")
                        .with(nguoiGoi)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\": true}"))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("duyet that bai: %s", chuoiThan(result))
                .isEqualTo(200);
    }

    private UUID khai(RequestPostProcessor nguoiGoi, UUID personId, String tieuDe, int nam)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/honours")
                        .with(nguoiGoi)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "personId", personId.toString(),
                                "kind", "DO_DAT",
                                "title", tieuDe,
                                "year", nam))))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("khai vinh danh that bai: %s", chuoiThan(result))
                .isEqualTo(201);
        return UUID.fromString(than(result).get("id").asText());
    }

    private void duyetVinhDanh(RequestPostProcessor nguoiGoi, UUID honourId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/honours/" + honourId + "/review")
                        .with(nguoiGoi)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\": true}"))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("duyet vinh danh that bai: %s", chuoiThan(result))
                .isEqualTo(200);
    }

    private List<UUID> vinhDanhCuaNguoi(RequestPostProcessor nguoiGoi, UUID personId)
            throws Exception {
        return idsCuaTruyVan(nguoiGoi, "/api/v1/honours?personId=" + personId);
    }

    private List<UUID> vinhDanhChoDuyet(RequestPostProcessor nguoiGoi) throws Exception {
        return idsCuaTruyVan(nguoiGoi, "/api/v1/honours?status=PENDING");
    }

    private List<UUID> idsCuaTruyVan(RequestPostProcessor nguoiGoi, String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url).with(nguoiGoi)).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("truy van %s that bai: %s", url, chuoiThan(result))
                .isEqualTo(200);
        return ids(than(result).get("items"));
    }

    private List<UUID> danhSachId(RequestPostProcessor nguoiGoi, String query) throws Exception {
        return idsCuaTruyVan(nguoiGoi, "/api/v1/posts" + query);
    }

    private long dem(RequestPostProcessor nguoiGoi, String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url).with(nguoiGoi)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return than(result).get("count").asLong();
    }

    private static List<UUID> ids(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                .map(node -> UUID.fromString(node.get("id").asText()))
                .toList();
    }

    private String trangThaiBai(UUID postId) {
        return jdbc.queryForObject("SELECT status FROM post WHERE id = ?", String.class, postId);
    }

    private long demBang(String table) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return count == null ? 0L : count;
    }

    private List<String> hanhDong(String entityType) {
        return jdbc.queryForList("SELECT action FROM audit_log WHERE entity_type = ?",
                String.class, entityType);
    }

    /** Tài khoản đã đăng ký nhưng chưa được gắn nhân khẩu — {@code person_id} rỗng. */
    private void insertUnlinkedUser(String keycloakSub) {
        jdbc.update("INSERT INTO app_user (id, keycloak_sub, person_id, display_name, status)"
                + " VALUES (?, ?, NULL, ?, 'PENDING')", UUID.randomUUID(), keycloakSub, keycloakSub);
    }

    private JsonNode than(MvcResult result) throws Exception {
        return objectMapper.readTree(chuoiThan(result));
    }

    private static String chuoiThan(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String ma(MvcResult result) throws Exception {
        JsonNode node = new ObjectMapper().readTree(chuoiThan(result));
        return node.has("code") ? node.get("code").asText() : null;
    }

    private static RequestPostProcessor nhuLa(String sub, String authority) {
        return jwt().jwt(builder -> builder.subject(sub))
                .authorities(new SimpleGrantedAuthority(authority));
    }

    private static RequestPostProcessor nhuLaJwt(String sub, String authority) {
        return nhuLa(sub, authority);
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

    private static RequestPostProcessor thanhVienBinh() {
        return nhuLa("sub-thanh-vien-binh", "ROLE_MEMBER");
    }
}
