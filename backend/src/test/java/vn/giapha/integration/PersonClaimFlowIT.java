package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.giapha.membership.application.MembershipProblemCodes;
import vn.giapha.membership.application.MyPersonClaims;
import vn.giapha.membership.application.PersonClaimService;
import vn.giapha.membership.application.PersonClaimView;
import vn.giapha.membership.application.command.ReviewPersonClaimCommand;
import vn.giapha.membership.application.command.SubmitNewPersonClaimCommand;
import vn.giapha.membership.application.command.SubmitPersonClaimCommand;
import vn.giapha.membership.domain.RelativeKind;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.vo.Gender;

/**
 * <b>Đơn tự nhận mình trong phả</b> trên hạ tầng thật — hai loại đơn, sáu ca biên, năm ràng buộc.
 *
 * <h2>Vì sao phải là test tích hợp</h2>
 * <ol>
 *   <li><b>Phạm vi chi là một phép so {@code ltree} chạy trong SQL.</b> Trưởng chi Ất không duyệt
 *       được đơn của chi Bính, và câu trả lời ấy đến từ toán tử {@code ltree[] @> ltree} của
 *       Postgres — không có bản mô phỏng nào trong bộ nhớ nói đúng về nó.</li>
 *   <li><b>"Đơn bị từ chối không để lại nhân khẩu nào" chỉ chứng minh được bằng một phép đếm trên
 *       bảng thật.</b> Đây là ràng buộc 1 của design 07 §1.5, và nó không có ý nghĩa nào khác ngoài
 *       "hãy đếm {@code person} trước và sau".</li>
 *   <li><b>Cạnh AGE và dòng {@code relationship} phải cân nhau.</b> Ràng buộc 4. Chỉ một đồ thị
 *       Apache AGE thật mới trả lời được, và đó đúng là bất biến nặng nhất của hệ thống.</li>
 *   <li><b>{@code ux_app_user_person}.</b> "Một nhân khẩu một tài khoản" là một chỉ mục duy nhất,
 *       không phải một câu {@code if}.</li>
 * </ol>
 */
@DisplayName("Đơn tự nhận mình trong phả")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class PersonClaimFlowIT extends AbstractIntegrationTest {

    @Autowired
    private PersonClaimService claims;

    @Autowired
    private MockMvc mockMvc;

    private UUID chiGiap;
    private UUID chiAt;

    /** Ông Bốn — Trưởng Chi Giáp, người duyệt. */
    private UUID bonPersonId;

    /** Ông Năm — Trưởng Chi Ất. Không được đụng vào đơn của Chi Giáp. */
    private UUID namPersonId;

    /** Bà Lan — nhân khẩu Chi Giáp, còn sống, chưa có tài khoản. Người được nhận. */
    private UUID lanPersonId;
    private UUID trongPersonId;

    /** Cụ Tổ — đã khuất. Không ai nhận mình là cụ được. */
    private UUID cuToPersonId;

    /** Tài khoản của người vừa đăng ký bằng mã dòng họ: chưa gắn nhân khẩu nào. */
    private UUID nguoiGuiUserId;

    @BeforeEach
    void dungDongHo() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");

        cuToPersonId = seed(PersonFixtures.deceased("Nguyễn Phúc Tổ", 1890).branch(goc)
                .generation(1));

        bonPersonId = seed(PersonFixtures.living("Nguyễn Văn Bốn").branch(chiGiap).generation(6));
        assignBranchRole(insertAppUser("sub-bon", bonPersonId), "BRANCH_HEAD", chiGiap);

        namPersonId = seed(PersonFixtures.living("Nguyễn Văn Năm").branch(chiAt).generation(6));
        assignBranchRole(insertAppUser("sub-nam", namPersonId), "BRANCH_HEAD", chiAt);

        UUID tocPersonId = seed(PersonFixtures.living("Nguyễn Văn Tộc").branch(goc).generation(5));
        assignBranchRole(insertAppUser("sub-hoi-dong", tocPersonId), "COUNCIL", null);

        lanPersonId = seed(PersonFixtures.living("Nguyễn Thị Lan").branch(chiGiap).generation(7));

        // Ho so CHUA co so dien thoai — o lien he trong la o duy nhat luong duyet don duoc ghi vao.
        trongPersonId = seed(PersonFixtures.living("Nguyễn Thị Trống").branch(chiGiap)
                .generation(7).withoutContact());

        nguoiGuiUserId = insertUnlinkedUser("sub-nguoi-moi", "Nguyễn Thị Lan (tự khai)");
    }

    // -------------------------------------------------------------------------------------
    // Tiện ích
    // -------------------------------------------------------------------------------------

    /** Tài khoản vừa đăng ký bằng mã dòng họ: {@code person_id} rỗng, trạng thái chờ. */
    private UUID insertUnlinkedUser(String keycloakSub, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user (id, keycloak_sub, person_id, display_name, status)"
                + " VALUES (?, ?, NULL, ?, 'PENDING')", id, keycloakSub, displayName);
        return id;
    }

    private PersonClaimView guiDonNhanMinh(String keycloakSub, UUID personId) {
        authenticateAs(keycloakSub, "MEMBER");
        return claims.submitExisting(new SubmitPersonClaimCommand(personId, "0912345678",
                "Chau la con thu hai cua ong Nguyen Van Bon, que Dai Lan."));
    }

    private PersonClaimView guiDonChuaCoTrongPha(String keycloakSub, String hoTen, UUID nguoiThan,
                                                 RelativeKind quanHe) {
        authenticateAs(keycloakSub, "MEMBER");
        return claims.submitNew(new SubmitNewPersonClaimCommand(hoTen, 1998, Gender.FEMALE,
                nguoiThan, quanHe, "0912345678", "Chau la con dau moi, cuoi thang 3 nam nay."));
    }

    private PersonClaimView duyetBoi(String keycloakSub, String vai, UUID claimId) {
        authenticateAs(keycloakSub, vai);
        return claims.review(new ReviewPersonClaimCommand(claimId, true, "Da goi kiem chung"));
    }

    private UUID nhanKhauCuaTaiKhoan(UUID appUserId) {
        return jdbc.queryForObject("SELECT person_id FROM app_user WHERE id = ?", UUID.class,
                appUserId);
    }

    private String trangThaiDon(UUID claimId) {
        return jdbc.queryForObject("SELECT status FROM person_claim WHERE id = ?", String.class,
                claimId);
    }

    // =====================================================================================
    // Đơn "tôi là người này trong phả"
    // =====================================================================================

    @Test
    @DisplayName("duyệt đơn nhận mình → tài khoản gắn ĐÚNG nhân khẩu, và thành ACTIVE")
    void duyetDonNhanMinhThiGanDungNhanKhau() {
        PersonClaimView don = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);
        assertThat(trangThaiDon(don.id())).isEqualTo("PENDING");
        // RANG BUOC 1 tuong duong cho don EXISTING: gui don khong dong vao pha mot dong nao.
        assertThat(nhanKhauCuaTaiKhoan(nguoiGuiUserId)).isNull();

        duyetBoi("sub-bon", "BRANCH_HEAD", don.id());

        // Doc lai tu CSDL chu khong tin doi tuong trong tay.
        assertThat(nhanKhauCuaTaiKhoan(nguoiGuiUserId)).isEqualTo(lanPersonId);
        assertThat(jdbc.queryForObject("SELECT status FROM app_user WHERE id = ?", String.class,
                nguoiGuiUserId)).isEqualTo("ACTIVE");
        assertThat(trangThaiDon(don.id())).isEqualTo("APPROVED");
        // Don EXISTING KHONG tao nhan khau nao.
        assertThat(jdbc.queryForObject(
                "SELECT created_person_id FROM person_claim WHERE id = ?", UUID.class, don.id()))
                .isNull();
    }

    @Test
    @DisplayName("Trưởng chi Ất KHÔNG duyệt được đơn trỏ vào người Chi Giáp")
    void truongChiKhacChiKhongDuyetDuoc() {
        PersonClaimView don = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);

        authenticateAs("sub-nam", "BRANCH_HEAD");
        assertThatThrownBy(() -> claims.review(
                new ReviewPersonClaimCommand(don.id(), true, "Toi duyet ho")))
                .isInstanceOf(ForbiddenException.class)
                .extracting(ex -> ((ForbiddenException) ex).getCode())
                // Du vai, sai nhanh — hai dieu do dan toi hai hanh dong khac han nhau cho nguoi dung.
                .isEqualTo(MembershipProblemCodes.BRANCH_SCOPE_VIOLATION);

        assertThat(trangThaiDon(don.id())).isEqualTo("PENDING");
        assertThat(nhanKhauCuaTaiKhoan(nguoiGuiUserId)).isNull();
    }

    @Test
    @DisplayName("Trưởng chi Ất còn KHÔNG THẤY đơn ấy trong hàng chờ — lọc chạy trong SQL")
    void truongChiKhacChiKhongThayDon() {
        PersonClaimView don = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);

        authenticateAs("sub-nam", "BRANCH_HEAD");
        assertThat(claims.pendingForReview(0, 20)).isEmpty();
        assertThat(claims.countPendingForReview()).isZero();

        authenticateAs("sub-bon", "BRANCH_HEAD");
        assertThat(claims.pendingForReview(0, 20)).extracting(PersonClaimView::id)
                .containsExactly(don.id());

        // Hoi dong thay duoc ca ho.
        authenticateAs("sub-hoi-dong", "COUNCIL");
        assertThat(claims.pendingForReview(0, 20)).hasSize(1);
    }

    @Test
    @DisplayName("nhận nhầm người đã khuất → chặn cứng, và nói thẳng vì sao")
    void nhanNguoiDaKhuatBiChanCung() {
        authenticateAs("sub-nguoi-moi", "MEMBER");
        assertThatThrownBy(() -> claims.submitExisting(
                new SubmitPersonClaimCommand(cuToPersonId, "0912345678", "Chau la cu To")))
                .isInstanceOf(DomainException.class)
                // Cung ma loi voi hai ca kia — nhung thong diep noi thang, vi ho so nguoi da khuat
                // von cong khai nen noi ro o buoc nay khong lo them gi.
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(MembershipProblemCodes.CLAIM_TARGET_UNAVAILABLE);
        authenticateAs("sub-nguoi-moi", "MEMBER");
        assertThatThrownBy(() -> claims.submitExisting(
                new SubmitPersonClaimCommand(cuToPersonId, "0912345678", "Chau la cu To")))
                .hasMessageContaining("da khuat");
        assertThat(countRows("person_claim")).isZero();
    }

    @Test
    @DisplayName("nhận nhầm người đã có tài khoản → chặn, nhưng nói CHUNG CHUNG")
    void nhanNguoiDaCoTaiKhoanThiNoiChungChung() {
        // Ong Bon da co tai khoan (sub-bon).
        authenticateAs("sub-nguoi-moi", "MEMBER");
        assertThatThrownBy(() -> claims.submitExisting(
                new SubmitPersonClaimCommand(bonPersonId, "0912345678", "Toi la ong Bon")))
                .isInstanceOf(DomainException.class)
                // MOT ma cho BA ly do (da khuat / da co tai khoan / da xoa mem). Gop lai la thu giu
                // cho man nay khong thanh cong cu liet ke AI DA VAO he thong: gui thu lan luot tung
                // o roi doc ma loi se ra danh sach nhung nguoi CHUA co tai khoan — tuc danh sach de
                // mao danh. Tach khoi VALIDATION_FAILED thi khac: no khong noi them gi ve NGUOI bi
                // nhan, chi noi cho client biet cai o vua chon la thu khong dung duoc.
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(MembershipProblemCodes.CLAIM_TARGET_UNAVAILABLE);

        authenticateAs("sub-nguoi-moi", "MEMBER");
        assertThatThrownBy(() -> claims.submitExisting(
                new SubmitPersonClaimCommand(bonPersonId, "0912345678", "Toi la ong Bon")))
                .hasMessageNotContainingAny("tai khoan", "da co");
    }

    @Test
    @DisplayName("hai người cùng nhận một nhân khẩu → Trưởng chi thấy CẢ HAI, rồi chọn")
    void haiNguoiCungNhanMotNhanKhau() {
        UUID nguoiThuHaiUserId = insertUnlinkedUser("sub-nguoi-moi-2", "Nguyễn Thị Lan (người 2)");

        PersonClaimView donMot = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);
        PersonClaimView donHai = guiDonNhanMinh("sub-nguoi-moi-2", lanPersonId);

        authenticateAs("sub-bon", "BRANCH_HEAD");
        // KHONG uu tien nguoi gui truoc: trung ten trong dong ho la chuyen thuong, va nguoi gui
        // truoc chua chac la nguoi dung.
        assertThat(claims.pendingForReview(0, 20)).extracting(PersonClaimView::id)
                .containsExactlyInAnyOrder(donMot.id(), donHai.id());

        // Truong chi chon don thu HAI.
        duyetBoi("sub-bon", "BRANCH_HEAD", donHai.id());

        assertThat(nhanKhauCuaTaiKhoan(nguoiThuHaiUserId)).isEqualTo(lanPersonId);
        assertThat(nhanKhauCuaTaiKhoan(nguoiGuiUserId)).isNull();
        // Don khong duoc chon phai duoc DONG TUONG MINH: de no nam lai PENDING la de lai mot don
        // vinh vien khong duyet duoc (nhan khau da co tai khoan) lam nghen hang cho mai mai.
        assertThat(trangThaiDon(donMot.id())).isEqualTo("REJECTED");
        authenticateAs("sub-bon", "BRANCH_HEAD");
        assertThat(claims.pendingForReview(0, 20)).isEmpty();
    }

    @Test
    @DisplayName("bị từ chối thì gửi lại được, nhưng có giới hạn số lần")
    void gioiHanSoLanGuiLai() {
        for (int lan = 0; lan < 3; lan++) {
            PersonClaimView don = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);
            authenticateAs("sub-bon", "BRANCH_HEAD");
            claims.review(new ReviewPersonClaimCommand(don.id(), false,
                    "Goi so nay khong ai nghe may"));
        }

        authenticateAs("sub-nguoi-moi", "MEMBER");
        assertThatThrownBy(() -> claims.submitExisting(
                new SubmitPersonClaimCommand(lanPersonId, "0912345678", "Lan thu tu")))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                // Khong gioi han thi man nay thanh cach do dung nguoi bang cach thu lan luot.
                .isEqualTo(MembershipProblemCodes.CLAIM_LIMIT_REACHED);
    }

    @Test
    @DisplayName("một tài khoản chỉ có MỘT đơn đang chờ")
    void motTaiKhoanMotDonDangCho() {
        guiDonNhanMinh("sub-nguoi-moi", lanPersonId);
        authenticateAs("sub-nguoi-moi", "MEMBER");
        assertThatThrownBy(() -> claims.submitExisting(
                new SubmitPersonClaimCommand(cuToPersonId, "0912345678", "Don thu hai")))
                .isInstanceOf(DomainException.class)
                // Ma rieng, vi hanh dong tiep theo rat cu the va giao dien LAM HO DUOC: rut don cu
                // roi gui lai. Rut khong tinh vao gioi han gui lai.
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(MembershipProblemCodes.CLAIM_ALREADY_OPEN);
    }

    // =====================================================================================
    // Lối "Tôi chưa có trong phả" — đây là lối GHI VÀO PHẢ
    // =====================================================================================

    @Test
    @DisplayName("RÀNG BUỘC 1: gửi đơn KHÔNG tạo nhân khẩu; từ chối cũng KHÔNG để lại node ma")
    void donBiTuChoiKhongDeLaiNhanKhauNao() {
        int truocKhiGui = countRows("person");
        int canhTruocKhiGui = graphEdgeTotal();

        PersonClaimView don = guiDonChuaCoTrongPha("sub-nguoi-moi", "Trần Thị Mai", bonPersonId,
                RelativeKind.SPOUSE);

        // Gui don khong tao gi ca — xoa mem la luat tuyet doi cua du an, nen tao truoc roi xoa sau
        // se de lai mot NODE MA trong pha cho moi don bi tu choi.
        assertThat(countRows("person")).isEqualTo(truocKhiGui);
        assertThat(graphNodeCount()).isEqualTo(truocKhiGui);

        authenticateAs("sub-bon", "BRANCH_HEAD");
        claims.review(new ReviewPersonClaimCommand(don.id(), false,
                "Khong doi chieu duoc voi so giay"));

        assertThat(trangThaiDon(don.id())).isEqualTo("REJECTED");
        assertThat(countRows("person")).isEqualTo(truocKhiGui);
        assertThat(graphNodeCount()).isEqualTo(truocKhiGui);
        assertThat(graphEdgeTotal()).isEqualTo(canhTruocKhiGui);
        assertThat(jdbc.queryForObject(
                "SELECT created_person_id FROM person_claim WHERE id = ?", UUID.class, don.id()))
                .isNull();
        assertThat(nhanKhauCuaTaiKhoan(nguoiGuiUserId)).isNull();
    }

    @Test
    @DisplayName("RÀNG BUỘC 2: không chỉ ra người thân thì không gửi được đơn")
    void khongCoNguoiThanThiKhongGuiDuoc() {
        authenticateAs("sub-nguoi-moi", "MEMBER");
        assertThatThrownBy(() -> claims.submitNew(new SubmitNewPersonClaimCommand(
                "Trần Thị Mai", 1998, Gender.FEMALE, null, null, "0912345678", "Chau moi ve")))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                // Tach khoi CLAIM_TARGET_UNAVAILABLE: hai luong dan toi hai man hinh khac nhau —
                // mot ben la "chon o khac tren pha do", ben kia la "chon nguoi than khac".
                .isEqualTo(MembershipProblemCodes.CLAIM_RELATIVE_UNUSABLE);
    }

    @Test
    @DisplayName("RÀNG BUỘC 3: đơn chạy qua bộ dò trùng NGAY LÚC GỬI, và ảnh chụp được lưu lại")
    void doTrungChayLucGuiVaDuocLuuLai() {
        // Ba Mai DA CO trong pha: cung ten, cung nam sinh, cung gioi, cung chi. Day dung la nguoi
        // ma nguoi khai co the la — kich ban bo do sinh ra de phuc vu.
        //
        // Nam sinh la thanh phan BAT BUOC de bo do mo mieng: DuplicateScorer doi "bang chung ngay
        // thang" (nam sinh khop hoac ngay gio khop) truoc khi cong bat ky diem ten nao. Khong co
        // no thi trung ten + cung chi van ra rong — va do la thiet ke dung, vi ca mot doi mang
        // chung chu dem la chuyen binh thuong trong mot dong ho dong nguoi.
        UUID maiDaCoPersonId = seed(PersonFixtures.living("Trần Thị Mai")
                .gender(Gender.FEMALE).birthYear(1998).branch(chiGiap).generation(7));

        PersonClaimView don = guiDonChuaCoTrongPha("sub-nguoi-moi", "Trần Thị Mai", bonPersonId,
                RelativeKind.SPOUSE);

        authenticateAs("sub-bon", "BRANCH_HEAD");
        assertThat(claims.byId(don.id()).duplicateSuspects())
                .as("Truong chi phai thay ngay 'co the day la nguoi nay' thay vi tao mot ban trung")
                .isNotEmpty()
                .anyMatch(nghi -> maiDaCoPersonId.equals(nghi.personId()));

        // Anh chup duoc LUU vao don, khong do lai luc duyet: Truong chi phai thay dung thu ma he
        // thong da thay khi nhan don, chu khong phai mot ket qua khac vi pha da doi trong luc cho.
        String screening = jdbc.queryForObject(
                "SELECT screening::text FROM person_claim WHERE id = ?", String.class, don.id());
        assertThat(screening).contains(maiDaCoPersonId.toString());
        // Va KHONG chua gia tri doc tu pha: ben bi nghi co the la mot nguoi CON SONG o chi khac ma
        // nguoi doc don khong duoc xem ten hay nam sinh. Anh chup nay duoc LUU, nen mot gia tri
        // doc tu pha lot vao day la lot VINH VIEN — moi lan doc don ve sau khong con bo loc nao.
        assertThat(screening).doesNotContain("Mai").doesNotContain("1998");
    }

    @Test
    @DisplayName("trùng tên nhưng không có bằng chứng ngày tháng → KHÔNG kêu, và đó là đúng")
    void trungTenKhongDuDeKeu() {
        // Ba Lan da co trong pha, trung ten khit, cung chi — nhung khong co nam sinh de doi chieu.
        // Bo do im lang o day la CO Y: ca mot doi mang chung chu dem la tap quan dat ten cua nguoi
        // Viet, nen keu o moi lan trung ten se tao ra mot bien canh bao gia va Truong chi se bam
        // bo qua theo phan xa — luc do canh bao mat sach gia tri, ke ca nhung canh bao dung.
        PersonClaimView don = guiDonChuaCoTrongPha("sub-nguoi-moi", "Nguyễn Thị Lan", bonPersonId,
                RelativeKind.SPOUSE);
        authenticateAs("sub-bon", "BRANCH_HEAD");
        assertThat(claims.byId(don.id()).duplicateSuspects()).isEmpty();
        assertThat(lanPersonId).isNotNull();
    }

    @Test
    @DisplayName("RÀNG BUỘC 4: duyệt xong nhân khẩu mới có ĐỦ quan hệ, cạnh đồ thị khớp dòng bảng")
    void duyetDonChuaCoTrongPhaThiTaoNhanKhauDuQuanHe() {
        int nguoiTruoc = countRows("person");
        int canhTruoc = graphEdgeTotal();

        PersonClaimView don = guiDonChuaCoTrongPha("sub-nguoi-moi", "Trần Thị Mai", bonPersonId,
                RelativeKind.SPOUSE);
        PersonClaimView daDuyet = duyetBoi("sub-bon", "BRANCH_HEAD", don.id());

        UUID nguoiMoi = daDuyet.createdPersonId();
        assertThat(nguoiMoi).as("duyet don NEW_PERSON phai sinh dung mot nhan khau").isNotNull();
        assertThat(countRows("person")).isEqualTo(nguoiTruoc + 1);

        // Nhan khau moi co mat o CA HAI noi — dinh cua do thi va dong cua bang.
        assertThat(graphNodeExists(nguoiMoi)).isTrue();

        // QUAN HE: canh hon phoi noi ong Bon voi ba Mai. Ong Bon dung o dau `from` vi
        // spouse_order ("vo thu may") gan vao nguoi chong.
        assertThat(graphEdgeCount("SPOUSE", bonPersonId, nguoiMoi))
                .as("canh hon phoi phai co trong do thi AGE")
                .isEqualTo(1);
        Integer dongQuanHe = jdbc.queryForObject("""
                SELECT count(*) FROM relationship
                 WHERE from_person_id = ? AND to_person_id = ?
                   AND rel_type = 'SPOUSE' AND is_deleted = FALSE
                """, Integer.class, bonPersonId, nguoiMoi);
        assertThat(dongQuanHe).as("dong ban chieu phai duoc ghi CUNG LUC voi canh").isEqualTo(1);

        // BAT BIEN NANG NHAT CUA HE THONG, phat bieu bang mot phep dem: so canh do thi = so dong
        // quan he con hieu luc. Khong bao gio duoc lech, o bat ky duong ghi nao.
        Integer tongDongQuanHe = jdbc.queryForObject(
                "SELECT count(*) FROM relationship WHERE is_deleted = FALSE", Integer.class);
        assertThat(graphEdgeTotal()).isEqualTo(tongDongQuanHe);
        assertThat(graphEdgeTotal()).isEqualTo(canhTruoc + 1);

        // Va tai khoan duoc gan vao dung nhan khau vua tao, trong cung mot transaction.
        assertThat(nhanKhauCuaTaiKhoan(nguoiGuiUserId)).isEqualTo(nguoiMoi);
        assertThat(jdbc.queryForObject("SELECT status FROM app_user WHERE id = ?", String.class,
                nguoiGuiUserId)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("nối vào CHA thì nhân khẩu mới nhận đời thứ kế tiếp — hết node mồ côi")
    void noiVaoChaThiSuyDuocDoiThu() {
        PersonClaimView don = guiDonChuaCoTrongPha("sub-nguoi-moi", "Nguyễn Văn Sáu", bonPersonId,
                RelativeKind.FATHER);
        PersonClaimView daDuyet = duyetBoi("sub-bon", "BRANCH_HEAD", don.id());

        UUID nguoiMoi = daDuyet.createdPersonId();
        // Nhan canh trong do thi AGE la "PARENT"; "ruot hay nuoi" nam o thuoc tinh `type` cua canh,
        // con bang relationship thi luu PARENT_BIO/PARENT_ADOPT. Hai cach goi ten cua cung mot su
        // that — xem RelType#edgeLabel.
        assertThat(graphEdgeCount("PARENT", bonPersonId, nguoiMoi)).isEqualTo(1);
        Integer dongCha = jdbc.queryForObject("""
                SELECT count(*) FROM relationship
                 WHERE from_person_id = ? AND to_person_id = ?
                   AND rel_type = 'PARENT_BIO' AND is_deleted = FALSE
                """, Integer.class, bonPersonId, nguoiMoi);
        assertThat(dongCha).isEqualTo(1);

        // Day la ly do rang buoc 2 ton tai: khong co nguoi than thi khong suy ra duoc doi thu, va
        // mot nhan khau thieu doi thu thi pha do khong xep duoc vao hang nao.
        assertThat(jdbc.queryForObject("SELECT generation FROM person WHERE id = ?", Integer.class,
                nguoiMoi)).isEqualTo(7);
        // Chi ke thua tu nguoi cha.
        assertThat(jdbc.queryForObject("SELECT primary_branch_id FROM person WHERE id = ?",
                UUID.class, nguoiMoi)).isEqualTo(chiGiap);
    }

    @Test
    @DisplayName("RÀNG BUỘC 5: người mới còn sống nên mọi nhóm riêng tư mặc định KÍN")
    void nguoiMoiMacDinhKin() {
        PersonClaimView don = guiDonChuaCoTrongPha("sub-nguoi-moi", "Trần Thị Mai", bonPersonId,
                RelativeKind.SPOUSE);
        UUID nguoiMoi = duyetBoi("sub-bon", "BRANCH_HEAD", don.id()).createdPersonId();

        assertThat(jdbc.queryForObject("SELECT is_alive FROM person WHERE id = ?", Boolean.class,
                nguoiMoi)).isTrue();
        // KHONG mot nhom truong nao duoc mo san. Dieu nay ROI RA tu cach luu — mo hinh V8 ghi ro
        // ca nam nhom o muc PRIVATE khi tao nguoi con song — chu khong phai tu mot lenh dat co nao
        // trong luong duyet don. Neu mot ngay nao do o day xuat hien BRANCH hay CLAN thi nghia la
        // co ai do da them mot ban luat thu hai, va ca nay se do truoc khi du lieu that bi lo.
        String consent = jdbc.queryForObject("SELECT privacy_consent::text FROM person WHERE id = ?",
                String.class, nguoiMoi);
        assertThat(consent).isNotNull();
        for (String nhom : List.of("occupation", "residenceProvince", "residenceFull", "contact",
                "birthDetailAndPhoto")) {
            assertThat(jdbc.queryForObject(
                    "SELECT privacy_consent ->> ? FROM person WHERE id = ?", String.class,
                    nhom, nguoiMoi))
                    .as("nhom %s cua mot nguoi con song phai KIN", nhom)
                    .isEqualTo("PRIVATE");
        }
    }

    @Test
    @DisplayName("Trưởng chi Ất KHÔNG duyệt được đơn chưa-có-trong-phả trỏ vào người thân Chi Giáp")
    void phamViChiApDungCaChoDonChuaCoTrongPha() {
        PersonClaimView don = guiDonChuaCoTrongPha("sub-nguoi-moi", "Trần Thị Mai", bonPersonId,
                RelativeKind.SPOUSE);

        authenticateAs("sub-nam", "BRANCH_HEAD");
        assertThatThrownBy(() -> claims.review(
                new ReviewPersonClaimCommand(don.id(), true, "Toi duyet ho")))
                .isInstanceOf(ForbiddenException.class);

        // Nguoi than duoc chi ra la thu quyet dinh AI DUYET — vi nguoi moi chua thuoc chi nao.
        assertThat(jdbc.queryForObject(
                "SELECT target_branch_id FROM person_claim WHERE id = ?", UUID.class, don.id()))
                .isEqualTo(chiGiap);
    }

    // =====================================================================================
    // Quyền đọc một lá đơn
    // =====================================================================================

    @Test
    @DisplayName("đơn chở số điện thoại nên chỉ người gửi và người duyệt đúng chi đọc được")
    void chiNguoiGuiVaNguoiDuyetDocDuocDon() {
        PersonClaimView don = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);

        authenticateAs("sub-nguoi-moi", "MEMBER");
        assertThat(claims.byId(don.id()).phone()).isEqualTo("0912345678");

        authenticateAs("sub-bon", "BRANCH_HEAD");
        assertThat(claims.byId(don.id()).phone()).isEqualTo("0912345678");

        authenticateAs("sub-nam", "BRANCH_HEAD");
        assertThatThrownBy(() -> claims.byId(don.id()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("người gửi rút đơn thì gửi lại được ngay, và KHÔNG tính vào giới hạn")
    void rutDonThiGuiLaiDuocNgay() {
        PersonClaimView don = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);
        authenticateAs("sub-nguoi-moi", "MEMBER");
        claims.cancel(don.id());
        assertThat(trangThaiDon(don.id())).isEqualTo("CANCELLED");

        PersonClaimView donMoi = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);
        assertThat(donMoi.id()).isNotEqualTo(don.id());

        authenticateAs("sub-nguoi-moi", "MEMBER");
        MyPersonClaims cuaToi = claims.mine(0, 20);
        assertThat(cuaToi.claims()).hasSize(2);
        // Rut don KHONG tinh vao gioi han: bo dem chi dem lan BI TU CHOI.
        assertThat(cuaToi.quota().rejected()).isZero();
        assertThat(cuaToi.quota().remaining()).isEqualTo(cuaToi.quota().max());
    }

    @Test
    @DisplayName("hạn mức gửi lại đi kèm danh sách đơn — client không phải đoán ngưỡng")
    void hanMucGuiLaiDiKemDanhSach() {
        PersonClaimView don = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);
        authenticateAs("sub-bon", "BRANCH_HEAD");
        claims.review(new ReviewPersonClaimCommand(don.id(), false, "Goi khong ai nghe may"));

        authenticateAs("sub-nguoi-moi", "MEMBER");
        MyPersonClaims cuaToi = claims.mine(0, 20);
        // Nguong nam o cau hinh may chu (giapha.membership.claim.max-rejected). Man hinh doc tu
        // day chu khong gan cung con so 3: gan cung se dung hom nay va am tham sai ngay Hoi dong
        // doi cau hinh — luc ay nguoi dung thay "con 1 lan" trong khi may chu da chan.
        assertThat(cuaToi.quota().rejected()).isEqualTo(1);
        assertThat(cuaToi.quota().max()).isEqualTo(3);
        assertThat(cuaToi.quota().remaining()).isEqualTo(2);
    }

    @Test
    @DisplayName("đơn mang chi đích ở dạng đọc được — 'đang chờ ai' không trả lời bằng một UUID")
    void donMangChiDichDocDuoc() {
        PersonClaimView don = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);
        assertThat(don.targetBranch()).isNotNull();
        assertThat(don.targetBranch().name()).isEqualTo("Chi Giáp");
        // Chuc danh la mot VAI, khong phai mot con nguoi: ten va so dien thoai cua Truong chi
        // khong duoc di ra man nay.
        assertThat(don.targetBranch().clanTitle()).isEqualTo("Trưởng Chi Giáp");
        assertThat(don.requesterDisplayName()).isNotBlank();
        assertThat(don.attemptNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("đơn đối thủ được ĐÁNH DẤU, nếu không thì đơn ở trang 2 là vô hình")
    void donDoiThuDuocDanhDau() {
        insertUnlinkedUser("sub-nguoi-moi-3", "Người thứ ba");
        PersonClaimView donMot = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);
        PersonClaimView donHai = guiDonNhanMinh("sub-nguoi-moi-3", lanPersonId);

        authenticateAs("sub-bon", "BRANCH_HEAD");
        assertThat(claims.byId(donMot.id()).competingClaimIds()).containsExactly(donHai.id());
        assertThat(claims.byId(donHai.id()).competingClaimIds()).containsExactly(donMot.id());

        // Sau khi mot don duoc duyet thi khong con tranh chap nao de danh dau.
        claims.review(new ReviewPersonClaimCommand(donHai.id(), true, "Da goi kiem chung"));
        assertThat(claims.byId(donHai.id()).competingClaimIds()).isEmpty();
    }

    @Test
    @DisplayName("đơn EXISTING KHÔNG có danh sách nghi trùng, đơn NEW_PERSON thì có (dù rỗng)")
    void baTrangThaiCuaAnhChupDoTrung() {
        // Ba trang thai nay chi co nghia TREN MAN DUYET — ban doc cua nguoi gui luon vang mat ca
        // hai truong, xem nguoiGuiKhongDocDuocNghiTrung.
        PersonClaimView nhanMinh = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);
        authenticateAs("sub-bon", "BRANCH_HEAD");
        // VANG MAT = chua quet bao gio. Mot mang rong o day se noi voi Truong chi rang he thong da
        // kiem trong khi no chua kiem.
        assertThat(claims.byId(nhanMinh.id()).duplicateSuspects()).isNull();

        authenticateAs("sub-nguoi-moi", "MEMBER");
        claims.cancel(nhanMinh.id());

        PersonClaimView chuaCo = guiDonChuaCoTrongPha("sub-nguoi-moi", "Trần Thị Mai", bonPersonId,
                RelativeKind.SPOUSE);
        authenticateAs("sub-bon", "BRANCH_HEAD");
        // MANG RONG = da quet, khong nghi ai. Hai cau khac nhau tren man hinh.
        assertThat(claims.byId(chuaCo.id()).duplicateSuspects()).isNotNull().isEmpty();
    }

    /**
     * Bộ dò trùng <b>không được nói chuyện với người gửi đơn</b>.
     *
     * <p>{@code ClaimDuplicateSuspect} không mang tên hay năm sinh — đúng — nhưng danh sách
     * {@code signals} thì mang: {@code NAM_SINH_KHOP} · {@code CUNG_CHI} ·
     * {@code CUNG_NGUYEN_QUAN} · {@code DOI_*}. Một tài khoản tự đăng ký, <b>chưa được duyệt</b>,
     * chỉ cần đoán một cái tên cộng một năm sinh là biết được trong chi B có một người
     * <i>còn sống</i> trùng năm sinh và trùng nguyên quán — dữ liệu Tầng 2 (BA v2 §10) rò qua một
     * kênh phụ kiểu bộ đếm, không qua bộ lọc phân tầng nào.</p>
     */
    @Test
    @DisplayName("người GỬI không đọc được danh sách nghi trùng — Trưởng chi thì đọc được")
    void nguoiGuiKhongDocDuocNghiTrung() {
        UUID maiDaCoPersonId = seed(PersonFixtures.living("Trần Thị Mai")
                .gender(Gender.FEMALE).birthYear(1998).branch(chiGiap).generation(7));

        // (a) Phan hoi cua chinh luot GUI.
        PersonClaimView don = guiDonChuaCoTrongPha("sub-nguoi-moi", "Trần Thị Mai", bonPersonId,
                RelativeKind.SPOUSE);
        assertThat(don.duplicateSuspects()).isNull();

        // (b) Doc lai qua /mine va /{id} — cung mot cau tra loi, khong co cua sau nao.
        authenticateAs("sub-nguoi-moi", "MEMBER");
        assertThat(claims.mine(0, 20).claims()).allSatisfy(view ->
                assertThat(view.duplicateSuspects()).isNull());
        assertThat(claims.byId(don.id()).duplicateSuspects()).isNull();

        // (c) Nhung anh chup VAN duoc luu va van toi tay nguoi duyet — phep chan nay khong lam
        // hong cong cu cua Truong chi.
        authenticateAs("sub-bon", "BRANCH_HEAD");
        assertThat(claims.byId(don.id()).duplicateSuspects())
                .isNotEmpty()
                .anyMatch(nghi -> maiDaCoPersonId.equals(nghi.personId()));
        assertThat(claims.pendingForReview(0, 20)).anySatisfy(view ->
                assertThat(view.duplicateSuspects()).isNotEmpty());
    }

    @Test
    @DisplayName("người GỬI không đọc được đơn đối thủ — đó là hoạt động của người thứ ba")
    void nguoiGuiKhongDocDuocDonDoiThu() {
        insertUnlinkedUser("sub-nguoi-moi-3", "Người thứ ba");
        PersonClaimView donMot = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);
        guiDonNhanMinh("sub-nguoi-moi-3", lanPersonId);

        authenticateAs("sub-nguoi-moi", "MEMBER");
        assertThat(claims.byId(donMot.id()).competingClaimIds()).isEmpty();

        authenticateAs("sub-bon", "BRANCH_HEAD");
        assertThat(claims.byId(donMot.id()).competingClaimIds()).hasSize(1);
    }

    /**
     * Rút đơn <b>không còn miễn phí</b>.
     *
     * <p>{@code rejectedCountOf} cố ý không đếm đơn tự rút, nên "gửi → rút → lặp" là một vòng lặp
     * vô hạn không tốn gì: nó đi thẳng qua cả {@code ux_person_claim_open_requester} (đơn đã đóng)
     * lẫn trần số lần bị từ chối (không có lần từ chối nào).</p>
     */
    @Test
    @DisplayName("gửi rồi rút, lặp mãi, thì cũng chạm trần — rút đơn không còn miễn phí")
    void guiRoiRutLapMaiThiCungChamTran() {
        // giapha.membership.claim.max-attempts mac dinh 10.
        for (int lan = 0; lan < 10; lan++) {
            PersonClaimView don = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);
            authenticateAs("sub-nguoi-moi", "MEMBER");
            claims.cancel(don.id());
        }

        authenticateAs("sub-nguoi-moi", "MEMBER");
        assertThatThrownBy(() -> claims.submitExisting(
                new SubmitPersonClaimCommand(lanPersonId, "0912345678", "Lan thu 11")))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(MembershipProblemCodes.CLAIM_LIMIT_REACHED);
    }

    @Test
    @DisplayName("huy hiệu đếm đơn trả 0 cho người không có quyền, KHÔNG ném 403")
    void huyHieuDemDonTraKhongChoNguoiKhongCoQuyen() {
        guiDonNhanMinh("sub-nguoi-moi", lanPersonId);

        // Nguoi vua dang ky, chua duoc duyet: huy hieu tren thanh dau trang van goi duoc.
        authenticateAs("sub-nguoi-moi", "MEMBER");
        assertThat(claims.countPendingForReview()).isZero();

        authenticateAs("sub-bon", "BRANCH_HEAD");
        assertThat(claims.countPendingForReview()).isEqualTo(1);
    }

    @Test
    @DisplayName("không ai được tự duyệt đơn của chính mình")
    void khongTuDuyetDonCuaMinh() {
        // Ong Bon vua la Truong chi vua la nguoi gui: tai khoan cua ong da gan person_id nen
        // khong gui don duoc — day la lop chan dau. Dung mot tai khoan Truong chi CHUA gan.
        UUID chuaGan = insertUnlinkedUser("sub-bon-2", "Nguyễn Văn Bốn (tài khoản mới)");
        assignBranchRole(chuaGan, "BRANCH_HEAD", chiGiap);

        PersonClaimView don = guiDonNhanMinh("sub-bon-2", lanPersonId);

        authenticateAs("sub-bon-2", "BRANCH_HEAD");
        assertThatThrownBy(() -> claims.review(
                new ReviewPersonClaimCommand(don.id(), true, "Toi tu duyet")))
                .isInstanceOf(ForbiddenException.class)
                .extracting(ex -> ((ForbiddenException) ex).getCode())
                .isEqualTo(MembershipProblemCodes.SELF_REVIEW_FORBIDDEN);

        assertThat(trangThaiDon(don.id())).isEqualTo("PENDING");
        assertThat(nhanKhauCuaTaiKhoan(chuaGan)).isNull();
    }

    // =====================================================================================
    // Hình dạng HTTP
    // =====================================================================================

    @Test
    @DisplayName("GET /person-claims/mine KHÔNG bị nuốt bởi GET /person-claims/{id}")
    void duongDanMineKhongBiNuot() throws Exception {
        guiDonNhanMinh("sub-nguoi-moi", lanPersonId);

        // Day la mot cai bay that trong cac bo gia lap: dang ky /{id} truoc thi "mine" bi doc thanh
        // mot dinh danh. Spring chon theo DO CU THE chu khong theo thu tu khai, nen /mine thang —
        // nhung mot @PathVariable String thay cho UUID o dong duoi se bien dieu nay thanh mot cai
        // bay that, va luc do ca bo phan trang im lang tra 404. Ghim lai.
        mockMvc.perform(get("/api/v1/person-claims/mine").with(nguoiGui()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claims").isArray())
                .andExpect(jsonPath("$.claims[0].targetBranch.clanTitle")
                        .value("Trưởng Chi Giáp"))
                // Han muc gui lai di kem, vi nguong nam o cau hinh may chu — client khong duoc doan.
                .andExpect(jsonPath("$.quota.max").value(3))
                .andExpect(jsonPath("$.quota.rejected").value(0))
                .andExpect(jsonPath("$.quota.remaining").value(3));
    }

    @Test
    @DisplayName("huy hiệu đếm đơn trả 200 với 0 cho người không có quyền, không phải 403")
    void huyHieuDemDonTra200ChoNguoiKhongCoQuyen() throws Exception {
        guiDonNhanMinh("sub-nguoi-moi", lanPersonId);

        mockMvc.perform(get("/api/v1/person-claims/pending/count").with(nguoiGui()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        mockMvc.perform(get("/api/v1/person-claims/pending/count").with(truongChiGiap()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("đơn EXISTING KHÔNG có khoá duplicateSuspects trong JSON — ba trạng thái phân biệt được")
    void jsonDonExistingKhongCoKhoaDoTrung() throws Exception {
        guiDonNhanMinh("sub-nguoi-moi", lanPersonId);
        mockMvc.perform(get("/api/v1/person-claims/mine").with(nguoiGui()))
                .andExpect(status().isOk())
                // VANG MAT, khong phai mang rong: don EXISTING chua bao gio duoc quet.
                .andExpect(jsonPath("$.claims[0].duplicateSuspects").doesNotExist())
                .andExpect(jsonPath("$.claims[0].competingClaimIds").isArray());
    }

    private RequestPostProcessor nguoiGui() {
        return jwt().jwt(builder -> builder.subject("sub-nguoi-moi"))
                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }

    private RequestPostProcessor truongChiGiap() {
        return jwt().jwt(builder -> builder.subject("sub-bon"))
                .authorities(new SimpleGrantedAuthority("ROLE_BRANCH_HEAD"));
    }

    @Test
    @DisplayName("đơn đã đóng thì không xử lại được")
    void donDaDongKhongXuLai() {
        PersonClaimView don = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);
        duyetBoi("sub-bon", "BRANCH_HEAD", don.id());

        authenticateAs("sub-bon", "BRANCH_HEAD");
        assertThatThrownBy(() -> claims.review(
                new ReviewPersonClaimCommand(don.id(), false, "Doi y")))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(MembershipProblemCodes.CLAIM_CLOSED);
    }

    // =====================================================================================
    // Số điện thoại trên đơn chảy vào hồ sơ nhân khẩu
    // =====================================================================================

    /** {@code attributes -> '_profile' -> 'contact' ->> 'phone'} — đúng bố cục {@code PersonMapper} ghi. */
    private String soDienThoaiTrenHoSo(UUID personId) {
        return jdbc.queryForObject(
                "SELECT attributes -> '_profile' -> 'contact' ->> 'phone' FROM person WHERE id = ?",
                String.class, personId);
    }

    @Test
    @DisplayName("duyệt đơn → số điện thoại người khai được ghi vào hồ sơ đang TRỐNG ô liên hệ")
    void duyetThiGhiSoDienThoaiVaoHoSoConTrong() {
        assertThat(soDienThoaiTrenHoSo(trongPersonId)).isNull();

        PersonClaimView don = guiDonNhanMinh("sub-nguoi-moi", trongPersonId);
        duyetBoi("sub-bon", "BRANCH_HEAD", don.id());

        assertThat(soDienThoaiTrenHoSo(trongPersonId)).isEqualTo("0912345678");
    }

    @Test
    @DisplayName("hồ sơ ĐÃ có số thì duyệt đơn KHÔNG ghi đè — số Trưởng chi đặt được giữ nguyên")
    void hoSoDaCoSoThiKhongGhiDe() {
        // PersonFixtures.living() mac dinh cho san mot so — day la so Truong chi da doi chieu.
        assertThat(soDienThoaiTrenHoSo(lanPersonId)).isEqualTo("0900000001");

        PersonClaimView don = guiDonNhanMinh("sub-nguoi-moi", lanPersonId);
        duyetBoi("sub-bon", "BRANCH_HEAD", don.id());

        // Ghi de lang le la mat mot du lieu DA KIEM ma khong ai thay, va trieu chung chi hien ra
        // nhieu thang sau khi mot loi nhac gio gui vao so khong con dung. Doi so da co thi di duong
        // sua ho so, noi co ETag va co nhat ky noi ro gia tri nao thay gia tri nao.
        assertThat(soDienThoaiTrenHoSo(lanPersonId)).isEqualTo("0900000001");
        assertThat(trangThaiDon(don.id())).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("ghi số KHÔNG mở nhóm riêng tư contact — hồ sơ vẫn KÍN")
    void ghiSoKhongMoNhomRiengTu() {
        PersonClaimView don = guiDonNhanMinh("sub-nguoi-moi", trongPersonId);
        duyetBoi("sub-bon", "BRANCH_HEAD", don.id());

        assertThat(soDienThoaiTrenHoSo(trongPersonId)).isEqualTo("0912345678");
        // Ghi mot so vao ho so KHONG phai la cong bo no. Neu o day thanh BRANCH hay CLAN thi luong
        // duyet don da tu quyet thay chu ho so ve dung loai du lieu ma Nghi dinh 13/2023 bat phai
        // co dong y.
        assertThat(jdbc.queryForObject(
                "SELECT privacy_consent ->> 'contact' FROM person WHERE id = ?", String.class,
                trongPersonId)).isEqualTo("PRIVATE");
    }

    @Test
    @DisplayName("audit_log ghi RẰNG liên hệ đã đổi, KHÔNG ghi con số — liên hệ là dữ liệu Tầng 3")
    void nhatKyKhongChuaSoDienThoai() {
        PersonClaimView don = guiDonNhanMinh("sub-nguoi-moi", trongPersonId);
        duyetBoi("sub-bon", "BRANCH_HEAD", don.id());

        Integer roRi = jdbc.queryForObject("""
                SELECT count(*) FROM audit_log
                 WHERE coalesce(before::text, '') LIKE '%0912345678%'
                    OR coalesce(after::text, '')  LIKE '%0912345678%'
                    OR coalesce(note, '')         LIKE '%0912345678%'
                """, Integer.class);
        assertThat(roRi).as("khong mot cot nao cua audit_log duoc mang so dien thoai").isZero();

        // Nhung phai co dau vet RANG da ghi — khong ghi gi ca cung sai.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log
                 WHERE entity_type = 'PersonClaim' AND 'phone' = ANY (changed_fields)
                """, Integer.class)).isPositive();
    }

    @Test
    @DisplayName("đơn chưa-có-trong-phả: nhân khẩu vừa tạo nhận luôn số điện thoại người khai")
    void nhanKhauMoiNhanLuonSoDienThoai() {
        PersonClaimView don = guiDonChuaCoTrongPha("sub-nguoi-moi", "Trần Thị Mai", bonPersonId,
                RelativeKind.SPOUSE);
        UUID nguoiMoi = duyetBoi("sub-bon", "BRANCH_HEAD", don.id()).createdPersonId();

        assertThat(soDienThoaiTrenHoSo(nguoiMoi)).isEqualTo("0912345678");
    }
}
