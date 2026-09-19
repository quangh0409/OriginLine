package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Year;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.genealogy.application.PersonQueryService;
import vn.giapha.genealogy.application.SoftDeletePersonService;
import vn.giapha.genealogy.application.VisibleTier;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.DatePrecision;
import vn.giapha.genealogy.domain.PrivacyConsent;
import vn.giapha.genealogy.domain.PrivacyFieldGroup;
import vn.giapha.genealogy.domain.PrivacyLevel;
import vn.giapha.genealogy.domain.ShareScope;
import vn.giapha.shared.vo.Gender;

/**
 * Phân tầng hiển thị theo BA v2 §10 / Nghị định 13/2023, chạy trên CSDL thật để cả chuỗi
 * {@code keycloak_sub → app_user → branch_assignment → ltree} cũng nằm trong vùng phủ.
 *
 * <p><b>Đây là lớp chịu trách nhiệm pháp lý của hệ thống.</b> Test unit trên
 * {@code PrivacyTierService} với {@code CallerContext} dựng tay sẽ bỏ lọt đúng chỗ hay hỏng nhất:
 * việc phân giải phạm vi chi/ngành từ CSDL. Một {@code branch_assignment} hết hạn, một
 * {@code person.primary_branch_id} rỗng, hay một path {@code ltree} sai — cả ba đều chỉ lộ ra khi
 * có CSDL thật.</p>
 *
 * <h2>Luật được kiểm chứng</h2>
 * <ol>
 *   <li>Người đã khuất là dữ liệu công khai.</li>
 *   <li><b>Khách không thấy bất kỳ người còn sống nào</b> — không phải "thấy mỗi tên", là không
 *       tồn tại trong phản hồi.</li>
 *   <li>Tầng 1 tên/đời/quan hệ lõi cho thành viên đã đăng nhập; Tầng 2 năm sinh/nghề/tỉnh cho cùng
 *       chi hoặc có phạm vi; Tầng 3 liên hệ/địa chỉ đầy đủ/ngày sinh đầy đủ/ảnh chỉ cho chính chủ,
 *       Admin và người được chủ thể opt-in.</li>
 *   <li>Trẻ vị thành niên ẩn tối đa.</li>
 * </ol>
 */
@DisplayName("Phân tầng riêng tư T1/T2/T3 (BA v2 §10)")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class PrivacyTierIT extends AbstractIntegrationTest {

    @Autowired
    private PersonQueryService personQuery;

    @Autowired
    private SoftDeletePersonService softDeletePerson;

    private UUID chiGiap;
    private UUID chiAt;

    /** Cụ tổ đã khuất — dữ liệu công khai. */
    private UUID cuTo;
    /** Cụ bà đã khuất — đối tượng của ca xoá mềm. */
    private UUID cuBa;
    /** Thành viên chi Giáp, có tài khoản {@code sub-giap}. */
    private UUID anhGiap;
    /** Thành viên chi Ất, có tài khoản {@code sub-at}. */
    private UUID anhAt;
    /** Người cùng chi Giáp, không có tài khoản — đối tượng bị xem. */
    private UUID chuGiap;
    /** Trẻ vị thành niên ở chi Giáp. */
    private UUID beGiap;
    /** Chi Giáp, tự nguyện mở Tầng 3 cho toàn dòng họ. */
    private UUID optInToanHo;
    /** Chi Giáp, chỉ mở Tầng 3 cho người cùng chi. */
    private UUID optInTheoChi;
    /** Chi Giáp, siết chặt hơn mặc định. */
    private UUID nguoiSietChat;
    /** Trưởng chi Giáp — có vai BRANCH_HEAD kèm phạm vi thật trong {@code branch_assignment}. */
    private UUID truongChiGiap;

    @BeforeEach
    void setUpClan() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");

        cuTo = seed(PersonFixtures.deceased("Nguyễn Phúc Thuỷ Tổ", 1960)
                .birthYear(1900).branch(chiGiap).generation(1).tabooName("Nguyễn Phúc Huý"));
        cuBa = seed(PersonFixtures.deceased("Trần Thị Tổ Mẫu", 1965)
                .gender(Gender.FEMALE).birthYear(1905).branch(chiGiap).generation(1));
        anhGiap = seed(PersonFixtures.living("Nguyễn Văn Giáp")
                .birthYear(1980).branch(chiGiap).generation(4).tabooName("Nguyễn Văn Kiêng"));
        anhAt = seed(PersonFixtures.living("Nguyễn Văn Ất")
                .birthYear(1985).branch(chiAt).generation(4));
        chuGiap = seed(PersonFixtures.living("Nguyễn Văn Chú")
                .birthYear(1970).branch(chiGiap).generation(3).tabooName("Nguyễn Văn Tị")
                // Mo hai nhom nhe cho nguoi cung chi — du de quan sat muc T2 ma khong cham
                // toi nhom nhay cam nao.
                .consent(PrivacyConsent.allPrivate()
                        .with(PrivacyFieldGroup.OCCUPATION, ShareScope.BRANCH)
                        .with(PrivacyFieldGroup.RESIDENCE_PROVINCE, ShareScope.BRANCH)));
        beGiap = seed(PersonFixtures.living("Nguyễn Văn Bé")
                .birthYear(Year.now().getValue() - 10).branch(chiGiap).generation(5)
                .privacy(PrivacyLevel.RESTRICTED));
        optInToanHo = seed(PersonFixtures.living("Nguyễn Văn Mở")
                .birthYear(1975).branch(chiGiap).generation(3).privacy(PrivacyLevel.CLAN_OPT_IN));
        optInTheoChi = seed(PersonFixtures.living("Nguyễn Văn Hé")
                .birthYear(1975).branch(chiGiap).generation(3).privacy(PrivacyLevel.BRANCH_OPT_IN));
        nguoiSietChat = seed(PersonFixtures.living("Nguyễn Văn Kín")
                .birthYear(1975).branch(chiGiap).generation(3).privacy(PrivacyLevel.RESTRICTED));
        truongChiGiap = seed(PersonFixtures.living("Nguyễn Văn Trưởng")
                .birthYear(1965).branch(chiGiap).generation(3));

        insertAppUser("sub-giap", anhGiap);
        insertAppUser("sub-at", anhAt);
        UUID truongChiUser = insertAppUser("sub-truongchi", truongChiGiap);
        assignBranchRole(truongChiUser, "BRANCH_HEAD", chiGiap);

        authenticateAsGuest();
    }

    // =====================================================================================
    // Khách vãng lai
    // =====================================================================================

    @Test
    @DisplayName("khách không nhận được BẤT KỲ người còn sống nào — kể cả người đã opt-in toàn họ")
    void khach_khongThayBatKyNguoiSongNao() {
        authenticateAsGuest();

        assertThat(personQuery.find(anhGiap)).as("thành viên còn sống").isEmpty();
        assertThat(personQuery.find(beGiap)).as("trẻ vị thành niên").isEmpty();
        assertThat(personQuery.find(chuGiap)).isEmpty();
        assertThat(personQuery.find(truongChiGiap)).isEmpty();
        // Opt-in là sự đồng ý chia sẻ với NGƯỜI TRONG HỌ, không phải với Internet.
        assertThat(personQuery.find(optInToanHo)).as("người đã opt-in toàn họ").isEmpty();
    }

    @Test
    @DisplayName("khách thấy dữ liệu phả hệ của người đã khuất")
    void khach_thayDuLieuPhaHeCuaNguoiDaKhuat() {
        authenticateAsGuest();

        PersonView view = personQuery.byId(cuTo);

        assertThat(view.access().visibleTier()).isEqualTo(VisibleTier.PUBLIC);
        assertThat(view.birth()).as("năm sinh của người đã khuất là dữ liệu phả hệ").isNotNull();
        assertThat(view.death()).isNotNull();
        assertThat(view.names()).as("người đã khuất hiện đủ các lớp tên, kể cả tên húy").hasSize(2);
    }

    /**
     * <h1>LỖI MAIN CODE ĐANG MỞ — rò rỉ dữ liệu Tầng 3 của người còn sống cho khách</h1>
     *
     * <p><b>Triệu chứng:</b> {@code GET /api/v1/persons/{id}} của một nhân khẩu <b>đã khuất</b>
     * trả về nguyên khối {@code contact} (điện thoại, email, Zalo) cho <b>khách chưa đăng nhập</b>.
     * Cùng lý do đó, {@code biography}, {@code avatarKey} và địa chỉ đầy đủ cũng đi ra.</p>
     *
     * <p><b>Nguyên nhân:</b>
     * {@code backend/src/main/java/vn/giapha/genealogy/application/VisibleTier.java:28} —</p>
     *
     * <pre>
     *   public boolean atLeast(VisibleTier other) {
     *       if (this == PUBLIC) return true;      // ← PUBLIC.atLeast(T3) == true
     *       ...
     *   }
     * </pre>
     *
     * <p>Vì thế ở {@code PrivacyTierService.toView} (dòng ~137) biểu thức</p>
     *
     * <pre>
     *   boolean contactVisible = tier3 || (publicTier &amp;&amp; (self || caller.role().isClanWide()));
     * </pre>
     *
     * <p>luôn đúng khi tầng là {@code PUBLIC}: {@code tier3} đã {@code true} nên vế bên phải —
     * chính là điều kiện "chỉ chính chủ hoặc vai toàn dòng họ" — <b>không bao giờ được xét</b>. Đó
     * là mã chết, và ý định viết trong comment ngay phía trên nó bị vô hiệu:</p>
     *
     * <blockquote>"Người đã khuất: dữ liệu phả hệ công khai, nhưng khối liên hệ thì không — số
     * điện thoại ghi trong hồ sơ một cụ đã mất trên thực tế là số của người thân đang sống."</blockquote>
     *
     * <p><b>Mức nghiêm trọng:</b> đây là dữ liệu cá nhân của người <b>còn sống</b> (thân nhân) bị
     * phát tán công khai — thuộc phạm vi Nghị định 13/2023, không chỉ là một lỗi hiển thị.</p>
     *
     * <p><b>Cách sửa gợi ý</b> (thuộc sở hữu của W2): tách bạch "tầng phả hệ" khỏi "quyền xem
     * Tầng 3". Ví dụ để {@code atLeast} trả {@code false} khi {@code other == T3} và
     * {@code this == PUBLIC}, hoặc bỏ hẳn {@code tier3} khỏi vế trái và tính
     * {@code contactVisible} từ {@code self || isAdmin || isClanWide || optIn}.</p>
     *
     * <p><b>Đã sửa</b> tại {@code PrivacyTierService}: {@code contactVisible} nay so bằng chính xác
     * {@code tier == VisibleTier.T3}, nên vế "chỉ chính chủ hoặc vai toàn dòng họ" sống lại.</p>
     *
     * <h2>Phạm vi: chỉ khối liên hệ, KHÔNG gồm tiểu sử và ảnh</h2>
     *
     * <p>Bản đầu của ca test này đòi giấu luôn {@code biography} và {@code avatarKey}. Khẳng định
     * đó <b>vượt quá đặc tả</b>. BA v2 §10 liệt kê thẳng cho người đã khuất: <i>"Tên, ngày sinh–mất,
     * <b>tiểu sử</b>, vai vế, mộ phần, vinh danh — Công khai, kể cả Khách"</i>; còn "ảnh cá nhân"
     * nằm ở dòng <b>Người sống — Tầng 3</b>. Tiểu sử và chân dung tiên tổ chính là nội dung mà cổng
     * thông tin dòng họ sinh ra để trưng bày (vinh danh, mộ phần, từ đường) — giấu chúng đi là
     * rút ruột sản phẩm chứ không phải bảo vệ ai.</p>
     *
     * <p>Ranh giới thật nằm ở chỗ khác: số điện thoại/email ghi trong hồ sơ một cụ đã mất trên thực
     * tế là <b>của người thân đang sống</b>. Đó mới là dữ liệu thuộc phạm vi Nghị định 13/2023.</p>
     */
    @Test
    @DisplayName("khách KHÔNG được thấy khối liên hệ trong hồ sơ người đã khuất")
    void khach_khongDuocThayKhoiLienHeCuaNguoiDaKhuat() {
        authenticateAsGuest();

        PersonView view = personQuery.byId(cuTo);

        // Số điện thoại ghi trong hồ sơ một cụ đã mất trên thực tế là số của người thân đang sống.
        assertThat(view.contact()).as("khối liên hệ phải bị giấu với khách").isNull();

        // Ngược lại, đây là thứ BA v2 §10 yêu cầu PHẢI công khai cho khách.
        assertThat(view.biography()).as("tiểu sử người đã khuất là công khai theo BA v2 §10").isNotNull();
    }

    // =====================================================================================
    // Tầng 1 / Tầng 2
    // =====================================================================================

    @Test
    @DisplayName("thành viên khác chi chỉ nhận Tầng 1: tên chính, đời, không có gì khác")
    void thanhVienKhacChi_chiNhanTang1() {
        authenticateAs("sub-at", "MEMBER");

        PersonView view = personQuery.byId(anhGiap);

        assertThat(view.access().visibleTier()).isEqualTo(VisibleTier.T1);
        assertThat(view.displayName()).isNotBlank();
        assertThat(view.generation()).as("đời thứ thuộc Tầng 1").isEqualTo(4);
        assertThat(view.names()).as("Tầng 1 chỉ trả tên chính, tên húy là dữ liệu lễ nghi").hasSize(1);
        assertThat(view.birth()).isNull();
        assertThat(view.occupation()).isNull();
        assertThat(view.nativePlace()).isNull();
        assertThat(view.currentPlaceProvince()).isNull();
        assertThat(view.currentPlaceFull()).isNull();
        assertThat(view.biography()).isNull();
        assertThat(view.avatarKey()).isNull();
        assertThat(view.contact()).isNull();
        assertThat(view.privacyConsent())
                .as("bảng đồng thuận của người khác không phải việc của người xem")
                .isNull();
    }

    @Test
    @DisplayName("thành viên cùng chi chỉ nhận đúng những nhóm trường chủ thể đã mở cho chi")
    void thanhVienCungChi_chiNhanDungNhomDaMo() {
        authenticateAs("sub-giap", "MEMBER");

        PersonView view = personQuery.byId(chuGiap);

        assertThat(view.access().visibleTier()).isEqualTo(VisibleTier.T2);
        assertThat(view.occupation()).as("đúng hai nhóm chủ thể đã mở cho người cùng chi").isNotNull();
        assertThat(view.currentPlaceProvince()).isNotNull();

        // Ba mảnh dưới đây KHÔNG thuộc nhóm trường nào nên chủ thể không mở được, và luật cố định
        // cho chúng là kín nhất có thể — nếu không, một người từng chọn RESTRICTED trước V8 sẽ bị
        // lộ thêm sau di trú. Xem PrivacyConsentMigrationIT.
        assertThat(view.nativePlace()).isNull();
        assertThat(view.birth()).isNull();
        assertThat(view.names()).as("tên húy là dữ liệu lễ nghi, không mở theo chi").hasSize(1);

        assertThat(view.currentPlaceFull()).as("nhóm địa chỉ đầy đủ vẫn đóng").isNull();
        assertThat(view.biography()).isNull();
        assertThat(view.avatarKey()).isNull();
        assertThat(view.contact()).isNull();
    }

    @Test
    @DisplayName("Hội đồng Tộc biểu đọc được cả nhóm Riêng tư — đó chính là định nghĩa của mức đó")
    void hoiDongTocBieu_docDuocCaNhomRiengTu() {
        authenticateAs("sub-hoidong", "COUNCIL");

        PersonView view = personQuery.byId(anhGiap);

        // THAY DOI CO Y so voi mo hinh cu (truoc V8): khi do COUNCIL bi ket o T2 va khong doc duoc
        // khoi lien he cua nguoi con song. Mo hinh dong thuan dinh nghia muc PRIVATE la
        // "chi chinh chu + Hoi dong", nen day la he qua truc tiep cua chinh dinh nghia ay.
        assertThat(view.access().visibleTier()).isEqualTo(VisibleTier.T3);
        assertThat(view.contact()).isNotNull();
    }

    @Test
    @DisplayName("Trưởng chi nhận Tầng 2 trong chi được giao nhưng chỉ Tầng 1 ở chi khác")
    void truongChi_phamViQuyetDinhTang_khongPhaiVaiTro() {
        authenticateAs("sub-truongchi", "BRANCH_HEAD");

        PersonView trongChi = personQuery.byId(chuGiap);
        assertThat(trongChi.access().visibleTier()).isEqualTo(VisibleTier.T2);
        assertThat(trongChi.access().canEdit()).isTrue();

        PersonView ngoaiChi = personQuery.byId(anhAt);
        assertThat(ngoaiChi.access().visibleTier())
                .as("có vai BRANCH_HEAD không đồng nghĩa được xem sâu ở chi khác")
                .isEqualTo(VisibleTier.T1);
        assertThat(ngoaiChi.access().canEdit()).isFalse();
        assertThat(ngoaiChi.occupation()).isNull();
    }

    // =====================================================================================
    // Tầng 3
    // =====================================================================================

    @Test
    @DisplayName("chính chủ nhận Tầng 3 đầy đủ")
    void chinhChu_nhanTang3() {
        authenticateAs("sub-giap", "MEMBER");

        PersonView view = personQuery.byId(anhGiap);

        assertThat(view.access().visibleTier()).isEqualTo(VisibleTier.T3);
        assertThat(view.access().isSelf()).isTrue();
        assertThat(view.contact()).isNotNull();
        assertThat(view.contact().phone()).isNotBlank();
        assertThat(view.contact().email()).isNotBlank();
        assertThat(view.currentPlaceFull()).isNotNull();
        assertThat(view.biography()).isNotNull();
        assertThat(view.avatarKey()).isNotNull();
        assertThat(view.birth().precision()).as("chính chủ thấy ngày sinh đầy đủ")
                .isEqualTo(DatePrecision.DAY);
        assertThat(view.privacyConsent()).isNotNull();
    }

    @Test
    @DisplayName("Quản trị hệ thống nhận Tầng 3")
    void quanTriHeThong_nhanTang3() {
        authenticateAs("sub-admin", "ADMIN");

        PersonView view = personQuery.byId(chuGiap);

        assertThat(view.access().visibleTier()).isEqualTo(VisibleTier.T3);
        assertThat(view.contact()).isNotNull();
        assertThat(view.currentPlaceFull()).isNotNull();
    }

    @Test
    @DisplayName("opt-in toàn họ mở Tầng 3 cho thành viên khác chi")
    void optInToanHo_moTang3ChoThanhVienKhacChi() {
        authenticateAs("sub-at", "MEMBER");

        PersonView view = personQuery.byId(optInToanHo);

        assertThat(view.access().visibleTier()).isEqualTo(VisibleTier.T3);
        assertThat(view.contact()).isNotNull();
    }

    @Test
    @DisplayName("opt-in theo chi chỉ mở cho người cùng chi, người khác chi vẫn ở Tầng 1")
    void optInTheoChi_chiMoChoNguoiCungChi() {
        authenticateAs("sub-giap", "MEMBER");
        PersonView cungChi = personQuery.byId(optInTheoChi);
        assertThat(cungChi.access().visibleTier()).isEqualTo(VisibleTier.T3);
        assertThat(cungChi.contact()).isNotNull();

        authenticateAs("sub-at", "MEMBER");
        PersonView khacChi = personQuery.byId(optInTheoChi);
        assertThat(khacChi.access().visibleTier()).isEqualTo(VisibleTier.T1);
        assertThat(khacChi.contact()).isNull();
        assertThat(khacChi.occupation()).isNull();
    }

    @Test
    @DisplayName("mức RESTRICTED giữ ở Tầng 1 kể cả với người cùng chi")
    void mucRestricted_giuOTang1_keCaNguoiCungChi() {
        authenticateAs("sub-giap", "MEMBER");

        PersonView view = personQuery.byId(nguoiSietChat);

        assertThat(view.access().visibleTier()).isEqualTo(VisibleTier.T1);
        assertThat(view.occupation()).isNull();
        assertThat(view.contact()).isNull();
    }

    // =====================================================================================
    // Trẻ vị thành niên
    // =====================================================================================

    @Test
    @DisplayName("trẻ vị thành niên luôn ở Tầng 1 với người cùng chi, dù chi đó là chi nhà")
    void treViThanhNien_anToiDa() {
        authenticateAs("sub-giap", "MEMBER");

        PersonView view = personQuery.byId(beGiap);

        assertThat(view.access().visibleTier()).isEqualTo(VisibleTier.T1);
        assertThat(view.birth()).isNull();
        assertThat(view.occupation()).isNull();
        assertThat(view.contact()).isNull();
    }

    // =====================================================================================
    // Xoá mềm
    // =====================================================================================

    @Test
    @DisplayName("bản ghi đã xoá mềm ẩn với thành viên thường, vẫn hiện với vai toàn dòng họ")
    void banGhiXoaMem_anVoiThanhVienThuong_hienVoiVaiToanDongHo() {
        authenticateAs("sub-admin", "ADMIN");
        softDeletePerson.softDelete(cuBa, "trùng bản ghi, gộp về hồ sơ khác");

        authenticateAs("sub-giap", "MEMBER");
        assertThat(personQuery.find(cuBa))
                .as("404 chứ không 403: trả 403 là tự xác nhận bản ghi có thật").isEmpty();

        authenticateAs("sub-hoidong", "COUNCIL");
        Optional<PersonView> hoiDong = personQuery.find(cuBa);
        assertThat(hoiDong).isPresent();
        assertThat(hoiDong.get().deleted()).isTrue();

        // Xoá mềm không bao giờ được rút node khỏi đồ thị — cây sẽ đứt ở giữa.
        assertThat(graphNodeExists(cuBa)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM person WHERE id = ?", Integer.class, cuBa))
                .as("xoá mềm là một lá cờ, không phải DELETE").isEqualTo(1);
    }
}
