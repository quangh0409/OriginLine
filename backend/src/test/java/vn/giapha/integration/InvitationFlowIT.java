package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import vn.giapha.membership.application.command.AcceptInvitationCommand;
import vn.giapha.membership.application.InvitationNotUsableException;
import vn.giapha.membership.application.InvitationPreview;
import vn.giapha.membership.application.InvitationService;
import vn.giapha.membership.application.IssuedInvitation;
import vn.giapha.membership.application.MembershipProblemCodes;
import vn.giapha.membership.application.command.IssueInvitationCommand;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.InvitationCode;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * <b>Luồng mời người vào hệ thống</b>, trên hạ tầng thật.
 *
 * <p>Trước bộ này, cửa duy nhất để đưa người thứ tư vào hệ thống là gõ tay một dòng
 * {@code app_user} rồi gọi {@code linkToPerson()} với quyền toàn dòng họ — tức là không có cửa nào
 * dùng được thật. Mỗi ca dưới đây canh một mặt của cửa mới.</p>
 *
 * <h2>Vì sao phải là test tích hợp</h2>
 * Bốn thứ chỉ quan sát được với CSDL thật:
 * <ol>
 *   <li><b>Mã thô có nằm trong CSDL không.</b> Câu hỏi này không có nghĩa trên một kho dữ liệu
 *       trong bộ nhớ — chỉ một lần quét bảng thật mới trả lời được.</li>
 *   <li><b>{@code ux_app_user_person}.</b> "Một nhân khẩu chỉ gắn một tài khoản" là một chỉ mục
 *       duy nhất, không phải một câu {@code if}.</li>
 *   <li><b>{@code ux_invitation_open_person}.</b> Phát lại phải thu hồi mã cũ <i>trước khi</i> chèn
 *       mã mới, và thứ tự flush ấy chỉ kiểm được khi có một chỉ mục thật để vi phạm.</li>
 *   <li><b>Không có hàng đợi chờ duyệt.</b> Bằng chứng là bảng {@code change_request} trống và
 *       {@code app_user.status} đã là {@code ACTIVE} — đọc từ CSDL, không từ đối tượng trong tay.</li>
 * </ol>
 */
@DisplayName("Mời người vào hệ thống")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class InvitationFlowIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /** Địa chỉ của người nhận lời mời — khoá của bộ đếm giới hạn tần suất. */
    private static final String IP_BA_LAN = "203.0.113.7";

    @Autowired
    private InvitationService invitations;

    private UUID chiGiap;
    private UUID chiAt;

    /** Ông Bốn — Trưởng Chi Giáp, người phát lời mời. */
    private UUID bonPersonId;
    private UUID bonUserId;

    /** Bà Lan — con dâu, nhân khẩu Chi Giáp, chưa có tài khoản. Người được mời. */
    private UUID lanPersonId;

    @BeforeEach
    void dungDongHo() {
        // invitation_attempt khong co khoa ngoai nao nen KHONG bi TRUNCATE ... CASCADE cua lop cha
        // keo theo. Khong don o day thi bo dem tran sang ca sau va gioi han tan suat se chan mot
        // ca test hoan toan vo can.
        jdbc.execute("TRUNCATE TABLE invitation_attempt RESTART IDENTITY");

        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");

        bonPersonId = seed(PersonFixtures.living("Nguyễn Văn Bốn").branch(chiGiap).generation(6));
        bonUserId = insertAppUser("sub-bon", bonPersonId);
        assignBranchRole(bonUserId, "BRANCH_HEAD", chiGiap);

        UUID hoiDongPersonId =
                seed(PersonFixtures.living("Nguyễn Văn Tộc").branch(goc).generation(5));
        // Hội đồng Tộc biểu — phân công KHÔNG gắn chi, nghĩa là phạm vi toàn dòng họ.
        assignBranchRole(insertAppUser("sub-hoi-dong", hoiDongPersonId), "COUNCIL", null);

        lanPersonId = seed(PersonFixtures.living("Nguyễn Thị Lan").branch(chiGiap).generation(7));
    }

    // -------------------------------------------------------------------------------------
    // Tiện ích
    // -------------------------------------------------------------------------------------

    private IssuedInvitation truongChiPhat(UUID personId) {
        authenticateAs("sub-bon", "BRANCH_HEAD");
        return invitations.issue(new IssueInvitationCommand(personId));
    }

    private String trangThaiTaiKhoan(UUID appUserId) {
        return jdbc.queryForObject("SELECT status FROM app_user WHERE id = ?", String.class, appUserId);
    }

    private UUID nhanKhauCuaTaiKhoan(UUID appUserId) {
        return jdbc.queryForObject("SELECT person_id FROM app_user WHERE id = ?", UUID.class, appUserId);
    }

    private String trangThaiLoiMoi(UUID personId) {
        return jdbc.queryForObject(
                "SELECT status FROM invitation WHERE person_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class, personId);
    }

    // =====================================================================================
    // Phát → nhận
    // =====================================================================================

    @Test
    @DisplayName("phát → nhận → tài khoản gắn đúng nhân khẩu, KHÔNG qua trạng thái chờ duyệt")
    void phatRoiNhanThiGanNgay() {
        IssuedInvitation issued = truongChiPhat(lanPersonId);

        // Ba Lan dang nhap lan dau bang tai khoan Keycloak vua lap: chua co dong app_user nao.
        authenticateAs("sub-lan", "MEMBER");
        AppUser lan = invitations.accept(AcceptInvitationCommand.byCurrentUser(issued.code(), IP_BA_LAN)).user();

        // Doc lai tu CSDL chu khong tin doi tuong trong tay.
        assertThat(nhanKhauCuaTaiKhoan(lan.id())).isEqualTo(lanPersonId);
        assertThat(trangThaiTaiKhoan(lan.id())).isEqualTo("ACTIVE");
        assertThat(trangThaiLoiMoi(lanPersonId)).isEqualTo("ACCEPTED");

        // DAY LA DIEM MAU CHOT: khong mot hang doi nao duoc sinh ra. Truong chi da chi dich danh
        // nguoi minh moi, he thong khong hoi lai mot cau da co dap an.
        assertThat(countRows("change_request")).isZero();

        // Va tai khoan da co nhan khau ngay trong cung mot transaction, khong phai sau mot buoc duyet.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE status = 'PENDING'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("màn nhận lời mời hiện tên người được mời VÀ tên người mời")
    void manNhanLoiMoiHienTenNguoiMoi() {
        IssuedInvitation issued = truongChiPhat(lanPersonId);
        authenticateAsGuest();

        InvitationPreview preview = invitations.preview(issued.code(), IP_BA_LAN);

        assertThat(preview.clanName()).isEqualTo("Dòng họ Nguyễn");
        assertThat(preview.invitee().displayName()).isEqualTo("Nguyễn Thị Lan");
        assertThat(preview.invitee().generation()).isEqualTo(7);
        assertThat(preview.invitee().branch().name()).isEqualTo("Chi Giáp");
        assertThat(preview.invitee().branch().path().value()).isEqualTo("goc.chi_giap");
        // "Loi moi den tu mot con nguoi, khong tu mot he thong" — ten tran, khong kinh ngu.
        assertThat(preview.inviter().displayName()).isEqualTo("Nguyễn Văn Bốn");
    }

    @Test
    @DisplayName("chức danh DÒNG TỘC của người mời, không phải vai kỹ thuật")
    void manNhanLoiMoiHienChucDanhDongToc() {
        // Ong Bon vua mang vai ky thuat BRANCH_HEAD, vua la Truong chi theo huyet thong.
        // Man nhan loi moi phai in cai thu hai: khong ai trong ho tu gioi thieu la "BRANCH_HEAD".
        jdbc.update("UPDATE branch SET head_person_id = ? WHERE id = ?", bonPersonId, chiGiap);
        IssuedInvitation issued = truongChiPhat(lanPersonId);
        authenticateAsGuest();

        assertThat(invitations.preview(issued.code(), IP_BA_LAN).inviter().clanTitle())
                .isEqualTo("Trưởng Chi Giáp");
    }

    @Test
    @DisplayName("không giữ chức danh dòng tộc thì trường ấy VẮNG, và đó là câu trả lời đúng")
    void khongGiuChucDanhThiVang() {
        IssuedInvitation issued = truongChiPhat(lanPersonId);
        authenticateAsGuest();

        assertThat(invitations.preview(issued.code(), IP_BA_LAN).inviter().clanTitle()).isNull();
    }

    // =====================================================================================
    // Mã chết: dùng lại · hết hạn · thu hồi
    // =====================================================================================

    @Test
    @DisplayName("mã dùng lại lần hai THẤT BẠI — bí mật một lần")
    void maDungLaiLanHaiThatBai() {
        IssuedInvitation issued = truongChiPhat(lanPersonId);

        authenticateAs("sub-lan", "MEMBER");
        invitations.accept(AcceptInvitationCommand.byCurrentUser(issued.code(), IP_BA_LAN));

        // Mot nguoi khac cam duoc ma (to phieu bi nhat, tin nhan bi chuyen tiep).
        authenticateAs("sub-nguoi-la", "MEMBER");
        assertThatThrownBy(() -> invitations.accept(AcceptInvitationCommand.byCurrentUser(issued.code(), IP_BA_LAN)))
                .isInstanceOf(InvitationNotUsableException.class)
                .extracting("code").isEqualTo(MembershipProblemCodes.INVITATION_ALREADY_USED);

        // Va khong co tai khoan thu hai nao gan vao ba Lan.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE person_id = ?",
                Integer.class, lanPersonId)).isEqualTo(1);
    }

    @Test
    @DisplayName("mã HẾT HẠN thất bại")
    void maHetHanThatBai() {
        IssuedInvitation issued = truongChiPhat(lanPersonId);

        // Day la buoc duy nhat cua bo test nay khong di qua use case that, va no khong di vong qua
        // mot cong kiem duyet nao: no chi day DONG HO ve qua khu. Ma van la ma that, bam van la
        // bam that, va het han van duoc tinh bang dung phep so sanh cua production.
        jdbc.update("UPDATE invitation SET expires_at = now() - interval '1 hour'"
                + " WHERE person_id = ?", lanPersonId);

        authenticateAs("sub-lan", "MEMBER");
        assertThatThrownBy(() -> invitations.accept(AcceptInvitationCommand.byCurrentUser(issued.code(), IP_BA_LAN)))
                .isInstanceOf(InvitationNotUsableException.class)
                .extracting("usability").hasToString("EXPIRED");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE person_id = ?",
                Integer.class, lanPersonId)).isZero();
        // Trang thai luu tru van la PENDING: het han la phep so sanh, khong phai mot dong du lieu
        // can mot job di lat co.
        assertThat(trangThaiLoiMoi(lanPersonId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("\"Không phải tôi\" giết mã ngay, và không cần đăng nhập")
    void khongPhaiToiGietMa() {
        IssuedInvitation issued = truongChiPhat(lanPersonId);
        authenticateAsGuest();

        invitations.decline(issued.code(), IP_BA_LAN);

        assertThat(trangThaiLoiMoi(lanPersonId)).isEqualTo("REVOKED");
        authenticateAs("sub-lan", "MEMBER");
        assertThatThrownBy(() -> invitations.accept(AcceptInvitationCommand.byCurrentUser(issued.code(), IP_BA_LAN)))
                .isInstanceOf(InvitationNotUsableException.class)
                .extracting("usability").hasToString("REVOKED");
    }

    @Test
    @DisplayName("phát lại thu hồi mã cũ — ux_invitation_open_person không cho hai mã cùng mở")
    void phatLaiThuHoiMaCu() {
        IssuedInvitation cu = truongChiPhat(lanPersonId);
        IssuedInvitation moi = truongChiPhat(lanPersonId);

        assertThat(moi.code()).isNotEqualTo(cu.code());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM invitation WHERE person_id = ? AND status = 'PENDING'",
                Integer.class, lanPersonId)).isEqualTo(1);

        authenticateAs("sub-lan", "MEMBER");
        assertThatThrownBy(() -> invitations.accept(AcceptInvitationCommand.byCurrentUser(cu.code(), IP_BA_LAN)))
                .isInstanceOf(InvitationNotUsableException.class);
        assertThat(invitations.accept(AcceptInvitationCommand.byCurrentUser(moi.code(), IP_BA_LAN)).user().personId()).isEqualTo(lanPersonId);
    }

    // =====================================================================================
    // Phạm vi chi
    // =====================================================================================

    @Test
    @DisplayName("Trưởng chi phát cho người NGOÀI chi mình bị từ chối")
    void truongChiPhatNgoaiChiBiTuChoi() {
        UUID nguoiChiAt = seed(PersonFixtures.living("Nguyễn Văn Ất").branch(chiAt).generation(7));
        authenticateAs("sub-bon", "BRANCH_HEAD");

        assertThatThrownBy(() -> invitations.issue(new IssueInvitationCommand(nguoiChiAt)))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo(MembershipProblemCodes.BRANCH_SCOPE_VIOLATION);

        assertThat(countRows("invitation")).isZero();
    }

    @Test
    @DisplayName("Hội đồng Tộc biểu phát được ở mọi chi")
    void hoiDongPhatDuocToanHo() {
        UUID nguoiChiAt = seed(PersonFixtures.living("Nguyễn Văn Ất").branch(chiAt).generation(7));
        authenticateAs("sub-hoi-dong", "COUNCIL");

        assertThat(invitations.issue(new IssueInvitationCommand(lanPersonId)).code()).isNotBlank();
        assertThat(invitations.issue(new IssueInvitationCommand(nguoiChiAt)).code()).isNotBlank();
        assertThat(countRows("invitation")).isEqualTo(2);
    }

    @Test
    @DisplayName("mời người ĐÃ CÓ tài khoản bị từ chối NGAY LÚC PHÁT")
    void moiNguoiDaCoTaiKhoanBiTuChoiLucPhat() {
        UUID maiPersonId =
                seed(PersonFixtures.living("Nguyễn Thị Mai").branch(chiGiap).generation(7));
        insertAppUser("sub-mai", maiPersonId);
        authenticateAs("sub-bon", "BRANCH_HEAD");

        assertThatThrownBy(() -> invitations.issue(new IssueInvitationCommand(maiPersonId)))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo(MembershipProblemCodes.PERSON_ALREADY_LINKED);

        // Khong duoc de sinh ma roi moi phat hien: luc ay Truong chi da in phieu va gui tin nhan.
        assertThat(countRows("invitation")).isZero();
    }

    @Test
    @DisplayName("danh sách lời mời bị cắt theo phạm vi: Trưởng Chi Giáp không thấy mã của Chi Ất")
    void danhSachBiCatTheoPhamVi() {
        UUID nguoiChiAt = seed(PersonFixtures.living("Nguyễn Văn Ất").branch(chiAt).generation(7));
        authenticateAs("sub-hoi-dong", "COUNCIL");
        invitations.issue(new IssueInvitationCommand(nguoiChiAt));
        invitations.issue(new IssueInvitationCommand(lanPersonId));

        authenticateAs("sub-bon", "BRANCH_HEAD");
        assertThat(invitations.inScope(0, 50))
                .extracting("personId")
                .containsExactly(lanPersonId);

        authenticateAs("sub-hoi-dong", "COUNCIL");
        assertThat(invitations.inScope(0, 50)).hasSize(2);
    }

    /**
     * Khối {@code invitee} phải đi hết đường ra tới JSON.
     *
     * <h2>Vì sao ca này tồn tại: tầng application ĐÃ tra sẵn, chỉ tầng api bỏ rơi</h2>
     * {@code InvitationService#list} truyền {@code Invitee} vào {@link InvitationView}, nên mọi
     * bài kiểm gọi thẳng service đều xanh — kể cả khi {@code InvitationDto} không chở trường ấy.
     * Triệu chứng chỉ hiện ra trên giao diện: mỗi dòng "lời mời đã phát" đọc là "Không rõ nhân
     * khẩu", và người sửa sẽ đi tìm ở tầng dữ liệu, nơi không có gì hỏng cả.
     *
     * <p>Nên ca này <b>phải</b> đi qua HTTP. Gọi service ở đây là kiểm lại đúng thứ đã xanh sẵn.</p>
     *
     * <h2>Và ba trường ấy là ĐÚNG BA trường</h2>
     * Không năm sinh, không nơi ở, không điện thoại. Một danh sách bị chụp màn hình chỉ được tiết
     * lộ đủ để nhận ra ai, không hơn.
     */
    @Test
    @DisplayName("GET /invitations chở khối invitee — đúng ba trường, không một trường nào hơn")
    void danhSachChoKhoiNhanKhau() throws Exception {
        truongChiPhat(lanPersonId);

        mockMvc.perform(get("/api/v1/invitations")
                        .with(jwt().jwt(b -> b.subject("sub-bon"))
                                .authorities(new SimpleGrantedAuthority("ROLE_BRANCH_HEAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].personId").value(lanPersonId.toString()))
                // Khong co ba dong nay thi man "loi moi da phat" la mot cot UUID.
                .andExpect(jsonPath("$[0].invitee.displayName").value("Nguyễn Thị Lan"))
                .andExpect(jsonPath("$[0].invitee.generation").value(7))
                .andExpect(jsonPath("$[0].invitee.branchName").value("Chi Giáp"))
                // ...va KHONG hon. Them mot truong vao day la mo rong dung thu mot anh chup man
                // hinh se tiet lo.
                .andExpect(jsonPath("$[0].invitee.birthYear").doesNotExist())
                .andExpect(jsonPath("$[0].invitee.phone").doesNotExist())
                .andExpect(jsonPath("$[0].invitee.branchPath").doesNotExist())
                .andExpect(jsonPath("$[0].invitee.branchId").doesNotExist())
                // Ma tho va bam cua no khong bao gio roi may chu qua duong nay.
                .andExpect(jsonPath("$[0].code").doesNotExist())
                .andExpect(jsonPath("$[0].codeHash").doesNotExist());
    }

    // =====================================================================================
    // Mã thô không nằm trong CSDL
    // =====================================================================================

    @Test
    @DisplayName("MÃ THÔ không nằm ở bất kỳ cột nào của bảng invitation")
    void maThoKhongNamTrongBangInvitation() {
        IssuedInvitation issued = truongChiPhat(lanPersonId);
        String raw = issued.code();
        String normalized = InvitationCode.normalize(raw);

        // Ep ca DONG sang text: khong phai liet ke tung cot, nen khong the sot mot cot moi them.
        List<String> rows = jdbc.queryForList("SELECT i::text FROM invitation i", String.class);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).doesNotContain(raw).doesNotContain(normalized);
        // ... va cot bam thi phai dung bang SHA-256 cua ma da chuan hoa.
        assertThat(jdbc.queryForObject("SELECT code_hash FROM invitation", String.class))
                .isEqualTo(InvitationCode.hash(raw));
    }

    @Test
    @DisplayName("MÃ THÔ không lọt vào audit_log — bảng chỉ ghi thêm, lọt là lọt vĩnh viễn")
    void maThoKhongLotVaoAuditLog() {
        IssuedInvitation issued = truongChiPhat(lanPersonId);
        authenticateAs("sub-lan", "MEMBER");
        invitations.accept(AcceptInvitationCommand.byCurrentUser(issued.code(), IP_BA_LAN));

        String raw = issued.code();
        String normalized = InvitationCode.normalize(raw);
        String hash = InvitationCode.hash(raw);

        List<String> rows = jdbc.queryForList("SELECT a::text FROM audit_log a", String.class);

        assertThat(rows).isNotEmpty();
        for (String row : rows) {
            assertThat(row).doesNotContain(raw).doesNotContain(normalized)
                    // Bam cung khong: no cho phep kiem chung offline mot ma doan duoc, tuc be mat
                    // lop gioi han tan suat.
                    .doesNotContain(hash);
        }
        // Nhung viec phat va viec nhan thi VAN phai co vet.
        assertThat(rows).anyMatch(row -> row.contains("Phat loi moi vao he thong"));
        assertThat(rows).anyMatch(row -> row.contains("Nhan loi moi"));
    }

    @Test
    @DisplayName("mỗi lần thử mã để lại một dòng invitation_attempt, và chỉ băm của IP")
    void moiLanThuDeLaiMotDong() {
        authenticateAsGuest();
        String maLa = InvitationCode.generate();

        // Ma khong khop loi moi nao dung lai NOT_FOUND san co, khong de ma thu tu.
        assertThatThrownBy(() -> invitations.preview(maLa, IP_BA_LAN))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo(MembershipProblemCodes.NOT_FOUND);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM invitation_attempt");
        assertThat(row).containsEntry("outcome", "FAILED");
        assertThat((String) row.get("client_key")).hasSize(64).isNotEqualTo(IP_BA_LAN);
        // Ma vua thu KHONG duoc luu, ke ca ma sai: no van la mot chuoi nguoi dung go vao.
        assertThat(row.values().toString()).doesNotContain(InvitationCode.normalize(maLa));
    }
}
