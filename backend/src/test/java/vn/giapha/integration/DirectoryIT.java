package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
import vn.giapha.shared.vo.Gender;

/**
 * <b>Danh bạ dòng họ</b> — {@code GET /api/v1/directory}, chạy qua toàn bộ chuỗi HTTP thật
 * (Spring Security + MVC + RFC 7807) trên PostgreSQL + Apache AGE thật.
 *
 * <h2>Vì sao mọi ca đều đi qua HTTP, không gọi thẳng service</h2>
 * Hai trong năm ràng buộc của màn này <b>chỉ tồn tại ở tầng HTTP</b>: Khách nhận {@code 401} (do
 * chuỗi lọc Spring Security), và trường bị giấu phải <b>biến mất khỏi JSON</b> chứ không phải hiện
 * ra thành {@code null} (do {@code @JsonInclude(NON_NULL)}). Gọi thẳng service thì cả hai đều xanh
 * mà không kiểm được gì.
 *
 * <h2>Bản đồ dữ liệu</h2>
 * Một dòng họ nhỏ, hai chi, và <b>mỗi nhân khẩu tồn tại để phá đúng một luật</b> nếu luật ấy sai:
 * <ul>
 *   <li>{@link #moCaHo} — mở nghề + tỉnh ở mức <i>Cả họ xem</i>: phải hiện với mọi thành viên.</li>
 *   <li>{@link #kinHoanToan} — chưa mở nhóm nào: <b>không</b> được xuất hiện với bất kỳ ai, kể cả
 *       Hội đồng.</li>
 *   <li>{@link #moCungChi} — mức <i>Cùng chi</i>: hiện với người chi Giáp, ẩn với người chi Ất.</li>
 *   <li>{@link #nguoiXemChiGiap} — chính người đang xem, đã đóng hết công tắc: <b>không</b> được
 *       thấy chính mình (nếu thấy, họ sẽ tin rằng cả họ cũng thấy mình).</li>
 *   <li>{@link #treViThanhNien} — mở hết mọi nhóm ở mức <i>Cả họ xem</i> nhưng dưới 18 tuổi: vẫn
 *       ẩn tối đa, vì một đứa trẻ không tự quyết được việc công khai dữ liệu của mình.</li>
 *   <li>{@link #daKhuat} — người đã khuất: công khai ở mọi nơi khác, nhưng danh bạ là màn hình của
 *       <b>người sống</b> nên không được lọt vào, và cũng không được tính vào mẫu số.</li>
 *   <li>{@link #daXoaMem} — đã mở hết nhưng đã xoá mềm: không hiện, không đếm.</li>
 * </ul>
 */
@DisplayName("Danh bạ dòng họ: chỉ người sống đã tự mở, coverage do máy chủ đếm, Khách nhận 401")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class DirectoryIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private UUID chiGiap;
    private UUID chiAt;

    private UUID moCaHo;
    private UUID kinHoanToan;
    private UUID moCungChi;
    private UUID moCaHoChiAt;
    private UUID nguoiXemChiGiap;
    private UUID nguoiXemChiAt;
    private UUID treViThanhNien;
    private UUID daKhuat;
    private UUID daXoaMem;

    @BeforeEach
    void setUpDongHo() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");

        moCaHo = seed(PersonFixtures.living("Nguyễn Văn Mở")
                .birthYear(1975).generation(4).branch(chiGiap)
                .occupation("Giáo viên").province("Hà Nội")
                .consent(mo(ShareScope.CLAN, PrivacyFieldGroup.OCCUPATION,
                        PrivacyFieldGroup.RESIDENCE_PROVINCE)));

        kinHoanToan = seed(PersonFixtures.living("Nguyễn Văn Kín")
                .birthYear(1976).generation(4).branch(chiGiap)
                .occupation("Kỹ sư").province("Hải Phòng")
                .consent(PrivacyConsent.allPrivate()));

        moCungChi = seed(PersonFixtures.living("Nguyễn Văn Cùng Chi")
                .birthYear(1977).generation(4).branch(chiGiap)
                .occupation("Bác sĩ").province("Hà Nội")
                .consent(mo(ShareScope.BRANCH, PrivacyFieldGroup.OCCUPATION)));

        moCaHoChiAt = seed(PersonFixtures.living("Nguyễn Văn Ất")
                .birthYear(1978).generation(4).branch(chiAt)
                .occupation("Bác sĩ").province("Đà Nẵng")
                .consent(mo(ShareScope.CLAN, PrivacyFieldGroup.OCCUPATION,
                        PrivacyFieldGroup.RESIDENCE_PROVINCE)));

        nguoiXemChiGiap = seed(PersonFixtures.living("Nguyễn Thị Xem")
                .gender(Gender.FEMALE).birthYear(1980).generation(4).branch(chiGiap)
                .occupation("Kế toán").province("Hà Nội")
                .consent(PrivacyConsent.allPrivate()));

        nguoiXemChiAt = seed(PersonFixtures.living("Nguyễn Thị Ất")
                .gender(Gender.FEMALE).birthYear(1981).generation(4).branch(chiAt)
                .occupation("Kế toán").province("Đà Nẵng")
                .consent(PrivacyConsent.allPrivate()));

        treViThanhNien = seed(PersonFixtures.living("Nguyễn Văn Bé")
                .birthYear(2015).generation(5).branch(chiGiap)
                .occupation("Học sinh").province("Hà Nội")
                .consent(moTatCa(ShareScope.CLAN)));

        daKhuat = seed(PersonFixtures.deceased("Nguyễn Phúc Thuỷ Tổ", 1960)
                .birthYear(1880).generation(1).branch(chiGiap)
                .occupation("Nho sinh").province("Bắc Ninh"));

        daXoaMem = seed(PersonFixtures.living("Nguyễn Văn Trùng")
                .birthYear(1979).generation(4).branch(chiGiap)
                .occupation("Giáo viên").province("Hà Nội")
                .consent(moTatCa(ShareScope.CLAN)));
        jdbc.update("UPDATE person SET is_deleted = TRUE, deleted_at = now() WHERE id = ?", daXoaMem);

        UUID taiKhoanChiGiap = insertAppUser("sub-thanh-vien", nguoiXemChiGiap);
        insertAppUser("sub-thanh-vien-at", nguoiXemChiAt);
        insertAppUser("sub-hoi-dong", moCaHo);
        insertAppUser("sub-admin", null);
        assignBranchRole(taiKhoanChiGiap, "MEMBER", chiGiap);
        authenticateAsGuest();
    }

    /**
     * Số người còn sống chưa xoá mềm trong phả — mẫu số kỳ vọng của {@code coverage}.
     *
     * <p>Viết ra thành hằng số đếm tay chứ không đếm lại bằng SQL trong test: một phép đếm trong
     * test dùng chung câu lệnh với code đang kiểm sẽ xanh cả khi cả hai cùng sai.</p>
     */
    private static final int TONG_NGUOI_CON_SONG = 7;

    // =========================================================================================
    // Ràng buộc 4 — Khách nhận 401, KHÔNG phải danh sách rỗng
    // =========================================================================================

    @Test
    @DisplayName("Khách chưa đăng nhập nhận 401 — danh sách rỗng sẽ là lời nói dối có hình dạng dữ liệu")
    void khach_nhan401_khongPhaiDanhSachRong() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/directory")).andReturn();

        assertThat(result.getResponse().getStatus())
                .as("người còn sống không tồn tại với Khách; trả 200 kèm mảng rỗng là nói dối rằng"
                        + " dòng họ không có ai còn sống")
                .isEqualTo(401);
        assertThat(chuoiThan(result))
                .as("không một mẩu dữ liệu nhân khẩu nào được đi kèm phản hồi 401")
                .doesNotContain("Nguyễn")
                .doesNotContain("coverage");
    }

    // =========================================================================================
    // Ràng buộc 1 + 2 — chỉ người còn sống, và chỉ người đã tự mở
    // =========================================================================================

    @Test
    @DisplayName("Chưa mở nhóm nào thì không xuất hiện; mở 'cả họ xem' thì có")
    void chuaMoNhomNao_khongXuatHien() throws Exception {
        List<String> ids = idsCuaTrang(goi(thanhVienChiGiap()));

        assertThat(ids).contains(moCaHo.toString());
        assertThat(ids)
                .as("người chưa bật một công tắc nào không được lộ ra chỉ vì có người mở danh bạ")
                .doesNotContain(kinHoanToan.toString());
    }

    @Test
    @DisplayName("Người đã khuất, trẻ vị thành niên và bản ghi đã xoá mềm đều không vào danh bạ")
    void nguoiDaKhuat_treEm_daXoaMem_deuKhongVaoDanhBa() throws Exception {
        List<String> ids = idsCuaTrang(goi(thanhVienChiGiap()));

        assertThat(ids)
                .as("danh bạ là màn hình của người sống; người đã khuất đã có cổng công khai riêng")
                .doesNotContain(daKhuat.toString());
        assertThat(ids)
                .as("trẻ vị thành niên ẩn tối đa — chặn trước cả đồng thuận")
                .doesNotContain(treViThanhNien.toString());
        assertThat(ids).doesNotContain(daXoaMem.toString());
    }

    @Test
    @DisplayName("Mức 'Cùng chi' chỉ hiện với người cùng chi, không hiện với chi khác")
    void mucCungChi_chiHienVoiNguoiCungChi() throws Exception {
        assertThat(idsCuaTrang(goi(thanhVienChiGiap())))
                .contains(moCungChi.toString());

        assertThat(idsCuaTrang(goi(thanhVienChiAt())))
                .as("BRANCH nghĩa là 'người cùng chi tôi xem được', không phải 'ai đăng nhập cũng xem'")
                .doesNotContain(moCungChi.toString());
    }

    @Test
    @DisplayName("Trường thuộc nhóm chưa mở biến mất khỏi JSON, không hiện ra thành null hay ô rỗng")
    void truongChuaMo_bienMatKhoiJson() throws Exception {
        JsonNode dong = dongCua(goi(thanhVienChiGiap()), moCungChi);

        assertThat(dong.has("occupation"))
                .as("moCungChi mở nghề ở mức Cùng chi, người xem cùng chi Giáp nên phải thấy")
                .isTrue();
        assertThat(dong.has("currentPlaceProvince"))
                .as("tỉnh vẫn ở mức Riêng tư — phải VẮNG MẶT khỏi JSON, không phải null")
                .isFalse();
        assertThat(dong.get("displayName").asText())
                .as("tên và đời là dữ liệu phả hệ Tầng 1, không có công tắc")
                .isEqualTo("Nguyễn Văn Cùng Chi");
    }

    // =========================================================================================
    // Ràng buộc 3 — coverage do máy chủ đếm
    // =========================================================================================

    @Test
    @DisplayName("coverage đếm đúng: tử số là người đã mở cho CHÍNH người gọi, mẫu số là người còn sống")
    void coverage_demDung() throws Exception {
        JsonNode than = than(goi(thanhVienChiGiap()));
        JsonNode coverage = than.get("coverage");

        // Chi Giap thay: moCaHo (CLAN) + moCungChi (BRANCH, cung chi) + moCaHoChiAt (CLAN).
        assertThat(coverage.get("sharedCount").asLong()).isEqualTo(3);
        assertThat(coverage.get("livingCount").asLong())
                .as("mẫu số là mọi người còn sống chưa xoá mềm — Tầng 1 vốn đã cho thành viên thấy"
                        + " tên và đời của họ, nên đây là con số họ đếm được bằng tay")
                .isEqualTo(TONG_NGUOI_CON_SONG);
    }

    @Test
    @DisplayName("coverage bỏ qua bộ lọc: chọn một tỉnh không được làm tử số tụt xuống")
    void coverage_khongDoiKhiLoc() throws Exception {
        JsonNode khongLoc = than(goi(thanhVienChiGiap()));
        JsonNode coLoc = than(goi(thanhVienChiGiap().param("province", "Hà Nội")));

        assertThat(coLoc.get("items").size())
                .as("bộ lọc phải thật sự thu hẹp danh sách, nếu không ca này vô nghĩa")
                .isLessThan(khongLoc.get("items").size());
        assertThat(coLoc.get("coverage"))
                .as("'218 / 627' nói về mức độ tham gia của dòng họ, không về trang đang xem")
                .isEqualTo(khongLoc.get("coverage"));
    }

    @Test
    @DisplayName("Tử số riêng cho từng người gọi: người chi Ất không được tính người mở mức Cùng chi Giáp")
    void coverage_tuySoRiengTheoNguoiGoi() throws Exception {
        long chiGiapThay = than(goi(thanhVienChiGiap())).get("coverage").get("sharedCount").asLong();
        long chiAtThay = than(goi(thanhVienChiAt())).get("coverage").get("sharedCount").asLong();

        assertThat(chiGiapThay).isEqualTo(3);
        assertThat(chiAtThay)
                .as("chi Ất chỉ thấy hai người mở mức CLAN")
                .isEqualTo(2);
    }

    // =========================================================================================
    // Ràng buộc 5 — đặc quyền Hội đồng/Admin KHÔNG áp vào danh bạ
    // =========================================================================================

    @Test
    @DisplayName("Hội đồng Tộc biểu đọc ra đúng danh sách và đúng con số như một thành viên")
    void hoiDong_khongCoDacQuyenTrenDanhBa() throws Exception {
        JsonNode cuaHoiDong = than(goi(hoiDong()));

        assertThat(idsCua(cuaHoiDong))
                .as("nếu Hội đồng thấy 100% thì đúng người chịu trách nhiệm cải thiện lại là người"
                        + " duy nhất không nhìn thấy vấn đề")
                .doesNotContain(kinHoanToan.toString());
        assertThat(cuaHoiDong.get("coverage").get("sharedCount").asLong())
                .as("hai người đọc cùng một màn hình mà thấy hai con số khác nhau thì con số ấy"
                        + " không còn là thước đo của dòng họ nữa")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("Quản trị hệ thống cũng vậy — danh bạ không phải bề mặt quản trị")
    void admin_khongCoDacQuyenTrenDanhBa() throws Exception {
        JsonNode cuaAdmin = than(goi(quanTri()));

        assertThat(idsCua(cuaAdmin)).doesNotContain(kinHoanToan.toString());
        assertThat(cuaAdmin.get("coverage").get("livingCount").asLong())
                .isEqualTo(TONG_NGUOI_CON_SONG);
    }

    @Test
    @DisplayName("Người vừa đóng hết công tắc không thấy chính mình trong danh bạ")
    void chinhChu_dongHetCongTac_khongThayChinhMinh() throws Exception {
        assertThat(idsCuaTrang(goi(thanhVienChiGiap())))
                .as("thấy chính mình sẽ khiến họ tin rằng cả họ cũng thấy mình — màn hình phải nói"
                        + " đúng sự thật về thứ NGƯỜI KHÁC nhìn thấy")
                .doesNotContain(nguoiXemChiGiap.toString());
    }

    @Test
    @DisplayName("Hội đồng đọc hồ sơ riêng của mình thì vẫn thấy — đặc quyền chỉ bị bỏ ở danh bạ")
    void hoiDong_vanThayChinhMinhNeuDaMo() throws Exception {
        // sub-hoi-dong duoc ghep voi moCaHo, nguoi da mo muc CLAN -> van hien, nhung hien vi DONG
        // THUAN chu khong vi dac quyen.
        assertThat(idsCuaTrang(goi(hoiDong()))).contains(moCaHo.toString());
    }

    // =========================================================================================
    // Facets và bộ lọc
    // =========================================================================================

    @Test
    @DisplayName("facets chỉ liệt kê giá trị CÓ THẬT trong danh bạ của người gọi")
    void facets_chiLietKeGiaTriCoThat() throws Exception {
        JsonNode facets = than(goi(thanhVienChiGiap())).get("facets");

        assertThat(giaTriFacet(facets.get("provinces")))
                .as("Hải Phòng chỉ có ở người chưa mở nhóm nào — lọt vào facet là đã nói ra họ tồn tại")
                .containsExactlyInAnyOrder("Hà Nội", "Đà Nẵng");
        assertThat(giaTriFacet(facets.get("occupations")))
                .containsExactlyInAnyOrder("Giáo viên", "Bác sĩ");
        assertThat(facets.get("branches").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("facet đếm theo số dòng thật, không đếm cả người bị ẩn")
    void facet_demTheoSoDongThat() throws Exception {
        JsonNode facets = than(goi(thanhVienChiAt())).get("facets");

        // Chi At chi thay moCaHo (Ha Noi) va moCaHoChiAt (Da Nang), moi tinh mot nguoi.
        assertThat(soLuongFacet(facets.get("provinces"), "Hà Nội")).isEqualTo(1);
        assertThat(soLuongFacet(facets.get("provinces"), "Đà Nẵng")).isEqualTo(1);
    }

    @Test
    @DisplayName("Tìm theo tên không dấu vẫn khớp — phép bỏ dấu là vn_unaccent của CSDL")
    void loc_timTheoTenKhongDau() throws Exception {
        List<String> ids = idsCuaTrang(goi(thanhVienChiGiap().param("q", "nguyen van mo")));

        assertThat(ids).containsExactly(moCaHo.toString());
    }

    @Test
    @DisplayName("Lọc theo chi lấy cả cây con, và không kéo theo ai ngoài danh bạ")
    void loc_theoChi() throws Exception {
        List<String> ids = idsCuaTrang(goi(thanhVienChiGiap().param("branchId", chiAt.toString())));

        assertThat(ids).containsExactly(moCaHoChiAt.toString());
    }

    @Test
    @DisplayName("Phân trang đếm trên tập ĐÃ lọc riêng tư, và thứ tự tất định giữa hai lượt gọi")
    void phanTrang_tatDinh() throws Exception {
        JsonNode trang0 = than(goi(thanhVienChiGiap().param("size", "2").param("page", "0")));
        JsonNode trang1 = than(goi(thanhVienChiGiap().param("size", "2").param("page", "1")));

        assertThat(trang0.get("page").get("totalElements").asLong()).isEqualTo(3);
        assertThat(trang0.get("items").size()).isEqualTo(2);
        assertThat(trang1.get("items").size()).isEqualTo(1);

        List<String> gop = new ArrayList<>(idsCua(trang0));
        gop.addAll(idsCua(trang1));
        assertThat(gop).doesNotHaveDuplicates().hasSize(3);
        assertThat(idsCua(than(goi(thanhVienChiGiap().param("size", "2").param("page", "0")))))
                .as("hai lượt gọi cùng tham số phải cho cùng một trang, nếu không phân trang sẽ"
                        + " lặp hoặc bỏ sót người")
                .isEqualTo(idsCua(trang0));
    }

    // =========================================================================================
    // Tiện ích
    // =========================================================================================

    private static PrivacyConsent mo(ShareScope scope, PrivacyFieldGroup... groups) {
        PrivacyConsent consent = PrivacyConsent.allPrivate();
        for (PrivacyFieldGroup group : groups) {
            consent = consent.with(group, scope);
        }
        return consent;
    }

    private static PrivacyConsent moTatCa(ShareScope scope) {
        return mo(scope, PrivacyFieldGroup.values());
    }

    private MockHttpServletRequestBuilder thanhVienChiGiap() {
        return get("/api/v1/directory").with(nhuLa("sub-thanh-vien", "ROLE_MEMBER"));
    }

    private MockHttpServletRequestBuilder thanhVienChiAt() {
        return get("/api/v1/directory").with(nhuLa("sub-thanh-vien-at", "ROLE_MEMBER"));
    }

    private MockHttpServletRequestBuilder hoiDong() {
        return get("/api/v1/directory").with(nhuLa("sub-hoi-dong", "ROLE_COUNCIL"));
    }

    private MockHttpServletRequestBuilder quanTri() {
        return get("/api/v1/directory").with(nhuLa("sub-admin", "ROLE_ADMIN"));
    }

    private static RequestPostProcessor nhuLa(String sub, String authority) {
        return jwt().jwt(builder -> builder.subject(sub))
                .authorities(new SimpleGrantedAuthority(authority));
    }

    private MvcResult goi(MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult result = mockMvc.perform(builder).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result;
    }

    private static String chuoiThan(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static JsonNode than(MvcResult result) throws Exception {
        return JSON.readTree(chuoiThan(result));
    }

    private static List<String> idsCuaTrang(MvcResult result) throws Exception {
        return idsCua(than(result));
    }

    private static List<String> idsCua(JsonNode than) {
        List<String> ids = new ArrayList<>();
        than.get("items").forEach(node -> ids.add(node.get("personId").asText()));
        return ids;
    }

    private static JsonNode dongCua(MvcResult result, UUID personId) throws Exception {
        for (JsonNode node : than(result).get("items")) {
            if (personId.toString().equals(node.get("personId").asText())) {
                return node;
            }
        }
        throw new AssertionError("Khong tim thay " + personId + " trong danh ba");
    }

    private static List<String> giaTriFacet(JsonNode facet) {
        List<String> values = new ArrayList<>();
        facet.forEach(node -> values.add(node.get("value").asText()));
        return values;
    }

    private static long soLuongFacet(JsonNode facet, String value) {
        for (JsonNode node : facet) {
            if (value.equals(node.get("value").asText())) {
                return node.get("count").asLong();
            }
        }
        return 0;
    }
}
