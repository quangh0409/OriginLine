package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.giapha.membership.application.ClanInviteNotUsableException;
import vn.giapha.membership.application.ClanInviteService;
import vn.giapha.membership.application.IssuedClanInvite;
import vn.giapha.membership.application.MembershipProblemCodes;
import vn.giapha.membership.application.command.IssueClanInviteCommand;
import vn.giapha.membership.application.command.RegisterWithClanInviteCommand;
import vn.giapha.membership.domain.ClanInviteUsability;
import vn.giapha.membership.domain.InvitationCode;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * <b>Mã mời dòng họ</b> trên hạ tầng thật — bốn chốt chặn của design 07 §1.2, từng chốt một.
 *
 * <h2>Vì sao phải là test tích hợp</h2>
 * Bốn điều chỉ quan sát được với CSDL thật, và cả bốn đều là bản chất của luồng này chứ không phải
 * chi tiết vận hành:
 * <ol>
 *   <li><b>Mã thô có nằm trong CSDL không.</b> Câu hỏi này vô nghĩa trên một kho dữ liệu trong bộ
 *       nhớ — chỉ một lần quét bảng thật mới trả lời được.</li>
 *   <li><b>Bộ đếm tăng đúng.</b> Nó được tăng bằng một câu {@code UPDATE ... use_count + 1} nguyên
 *       tử có điều kiện, không phải bằng đọc-rồi-ghi. Một kho dữ liệu trong bộ nhớ sẽ "xanh" với cả
 *       lối sai.</li>
 *   <li><b>{@code ux_clan_redemption_once}.</b> "Một tài khoản đếm một lượt trên một mã" là một chỉ
 *       mục duy nhất, không phải một câu {@code if}.</li>
 *   <li><b>Giới hạn tần suất trả 429.</b> Chỉ đo được khi đi qua cả chuỗi lọc và
 *       {@code @RestControllerAdvice} — tức là qua HTTP, không qua lời gọi service.</li>
 * </ol>
 */
@DisplayName("Mã mời dòng họ")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class ClanInviteFlowIT extends AbstractIntegrationTest {

    /** Địa chỉ người gọi — khoá của bộ đếm giới hạn tần suất. */
    private static final String IP_NGUOI_DANG_KY = "203.0.113.42";

    @Autowired
    private ClanInviteService clanInvites;

    @Autowired
    private MockMvc mockMvc;

    private UUID hoiDongUserId;

    @BeforeEach
    void dungDongHo() {
        // invitation_attempt khong co khoa ngoai nao nen KHONG bi TRUNCATE ... CASCADE cua lop cha
        // keo theo. Khong don o day thi bo dem tran sang ca sau va gioi han tan suat se chan mot ca
        // test hoan toan vo can.
        jdbc.execute("TRUNCATE TABLE invitation_attempt RESTART IDENTITY");

        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        UUID chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");

        UUID tocPersonId = seed(PersonFixtures.living("Nguyễn Văn Tộc").branch(goc).generation(5));
        hoiDongUserId = insertAppUser("sub-hoi-dong", tocPersonId);
        // Phan cong KHONG gan chi = pham vi toan dong ho.
        assignBranchRole(hoiDongUserId, "COUNCIL", null);

        UUID bonPersonId =
                seed(PersonFixtures.living("Nguyễn Văn Bốn").branch(chiGiap).generation(6));
        assignBranchRole(insertAppUser("sub-bon", bonPersonId), "BRANCH_HEAD", chiGiap);
    }

    // -------------------------------------------------------------------------------------
    // Tiện ích
    // -------------------------------------------------------------------------------------

    private IssuedClanInvite hoiDongPhat(Integer maxUses) {
        authenticateAs("sub-hoi-dong", "COUNCIL");
        return clanInvites.issue(new IssueClanInviteCommand("Nhóm Zalo họ Nguyễn", null, maxUses,
                null));
    }

    /** Người lạ hoàn toàn: chưa có token, chưa có {@code app_user}. Dùng lối khai email. */
    private void dangKyBangToken(String code, String keycloakSub) {
        authenticateAs(keycloakSub, "MEMBER");
        clanInvites.register(new RegisterWithClanInviteCommand(code, null, keycloakSub,
                IP_NGUOI_DANG_KY));
    }

    private int boDemCua(UUID codeId) {
        Integer count = jdbc.queryForObject(
                "SELECT use_count FROM clan_invite_code WHERE id = ?", Integer.class, codeId);
        return count == null ? 0 : count;
    }

    private void datHanVaoQuaKhu(UUID codeId) {
        jdbc.update("UPDATE clan_invite_code SET expires_at = now() - interval '1 day' WHERE id = ?",
                codeId);
    }

    // =====================================================================================
    // CHỐT 3 — đếm lượt dùng
    // =====================================================================================

    @Test
    @DisplayName("ba người đăng ký bằng một mã → bộ đếm là 3, và nhật ký ghi đủ ba người")
    void boDemTangDungTheoSoNguoiDangKy() {
        IssuedClanInvite phat = hoiDongPhat(null);
        assertThat(boDemCua(phat.invite().id())).isZero();

        dangKyBangToken(phat.code(), "sub-lan");
        dangKyBangToken(phat.code(), "sub-mai");
        dangKyBangToken(phat.code(), "sub-hoa");

        // DAY LA CHOT QUAN TRONG NHAT. Hoi dong nhin vao dung con so nay de biet ma da ro.
        assertThat(boDemCua(phat.invite().id())).isEqualTo(3);

        // Va nhat ky noi duoc AI, khong chi noi BAO NHIEU — thu truy duoc nguoi dua ma ra ngoai.
        List<UUID> daDung = jdbc.queryForList(
                "SELECT app_user_id FROM clan_invite_redemption WHERE code_id = ?",
                UUID.class, phat.invite().id());
        assertThat(daDung).hasSize(3).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("cùng một tài khoản bấm hai lần → vẫn đúng MỘT lượt")
    void bamHaiLanKhongDemThanhHaiLuot() {
        IssuedClanInvite phat = hoiDongPhat(null);

        dangKyBangToken(phat.code(), "sub-lan");
        dangKyBangToken(phat.code(), "sub-lan");

        // Bam lai, mang chap chon, tai lai trang — deu la chuyen BINH THUONG, khong phai loi.
        // Khong co ux_clan_redemption_once thi bo dem phong len vi nhung lan bam lai vo hai, va
        // Hoi dong se thu hoi mot ma lanh vi tuong no da ro.
        assertThat(boDemCua(phat.invite().id())).isEqualTo(1);
        assertThat(countRows("clan_invite_redemption")).isEqualTo(1);
    }

    @Test
    @DisplayName("đăng ký xong thì tài khoản CHƯA gắn nhân khẩu nào — phải tự nhận mình đã")
    void dangKyXongVanChuaVaoPha() {
        IssuedClanInvite phat = hoiDongPhat(null);
        dangKyBangToken(phat.code(), "sub-lan");

        // Doc lai tu CSDL chu khong tin doi tuong trong tay.
        assertThat(jdbc.queryForObject(
                "SELECT person_id FROM app_user WHERE keycloak_sub = 'sub-lan'", UUID.class))
                .isNull();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM app_user WHERE keycloak_sub = 'sub-lan'", String.class))
                .isEqualTo("PENDING");
        // Va KHONG co don tu nhan nao duoc tao tu dong: he thong khong doan xem ho la ai.
        assertThat(countRows("person_claim")).isZero();
    }

    // =====================================================================================
    // CHỐT 1 — có hạn dùng · CHỐT 2 — thu hồi được · trần lượt dùng
    // =====================================================================================

    @Test
    @DisplayName("mã hết hạn thì không đăng ký được, và bộ đếm không nhúc nhích")
    void maHetHanBiTuChoi() {
        IssuedClanInvite phat = hoiDongPhat(null);
        datHanVaoQuaKhu(phat.invite().id());

        authenticateAs("sub-lan", "MEMBER");
        assertThatThrownBy(() -> clanInvites.register(new RegisterWithClanInviteCommand(
                phat.code(), null, "Lan", IP_NGUOI_DANG_KY)))
                .isInstanceOf(ClanInviteNotUsableException.class)
                .extracting(ex -> ((ClanInviteNotUsableException) ex).usability())
                .isEqualTo(ClanInviteUsability.EXPIRED);

        assertThat(boDemCua(phat.invite().id())).isZero();
        assertThat(countRows("clan_invite_redemption")).isZero();
    }

    @Test
    @DisplayName("thu hồi đóng cửa ngay, NHƯNG người đã vào thì không bị ảnh hưởng")
    void thuHoiKhongDungToiNguoiDaVao() {
        IssuedClanInvite phat = hoiDongPhat(null);
        dangKyBangToken(phat.code(), "sub-lan");

        authenticateAs("sub-hoi-dong", "COUNCIL");
        clanInvites.revoke(phat.invite().id(), "Ma bi dan len nhom Zalo cong khai");

        authenticateAs("sub-mai", "MEMBER");
        assertThatThrownBy(() -> clanInvites.register(new RegisterWithClanInviteCommand(
                phat.code(), null, "Mai", IP_NGUOI_DANG_KY)))
                .isInstanceOf(ClanInviteNotUsableException.class)
                .extracting(ex -> ((ClanInviteNotUsableException) ex).usability())
                .isEqualTo(ClanInviteUsability.REVOKED);

        // "Dong lai ngay ma khong anh huong nguoi da vao" — tai khoan cua ba Lan khong treo vao ma.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE keycloak_sub = 'sub-lan'", Integer.class))
                .isEqualTo(1);
        // Va bo dem GIU NGUYEN con so cu: chinh no la thu dang giu sau khi thu hoi.
        assertThat(boDemCua(phat.invite().id())).isEqualTo(1);
    }

    @Test
    @DisplayName("chạm trần lượt dùng → từ chối, phân biệt được với hết hạn")
    void chamTranLuotDung() {
        IssuedClanInvite phat = hoiDongPhat(1);
        dangKyBangToken(phat.code(), "sub-lan");

        authenticateAs("sub-mai", "MEMBER");
        assertThatThrownBy(() -> clanInvites.register(new RegisterWithClanInviteCommand(
                phat.code(), null, "Mai", IP_NGUOI_DANG_KY)))
                .isInstanceOf(ClanInviteNotUsableException.class)
                .extracting(ex -> ((ClanInviteNotUsableException) ex).getCode())
                // Ma loi RIENG: ma van con han, van chua bi thu hoi, chi la tran day. Loi di tiep
                // khac han mot ma qua han — xin Hoi dong nang tran hoac phat ma moi.
                .isEqualTo(MembershipProblemCodes.CLAN_INVITE_EXHAUSTED);

        assertThat(boDemCua(phat.invite().id())).isEqualTo(1);
    }

    @Test
    @DisplayName("mã không khớp gì trả 404 chung, không xác nhận mã có tồn tại hay không")
    void maKhongKhopTra404Chung() {
        hoiDongPhat(null);
        authenticateAsGuest();
        assertThatThrownBy(() -> clanInvites.preview("ZZZZZ-ZZZZZ", IP_NGUOI_DANG_KY))
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((NotFoundException) ex).getCode())
                .isEqualTo(MembershipProblemCodes.NOT_FOUND);
    }

    // =====================================================================================
    // Bí mật: mã thô không bao giờ chạm CSDL
    // =====================================================================================

    @Test
    @DisplayName("MÃ THÔ KHÔNG nằm ở đâu trong cơ sở dữ liệu — chỉ có băm")
    void maThoKhongNamTrongCsdl() {
        IssuedClanInvite phat = hoiDongPhat(null);
        dangKyBangToken(phat.code(), "sub-lan");

        String maTho = phat.code();
        String maChuanHoa = InvitationCode.normalize(maTho);

        // Quet MOI cot chu cua bang ma, bang nhat ky dung ma, va ca audit_log — mot bi mat lot vao
        // audit_log la lot VINH VIEN vi do la bang chi ghi them.
        String sql = """
                SELECT count(*) FROM (
                  SELECT c.id::text || coalesce(c.code_hash,'') || coalesce(c.label,'')
                         || coalesce(c.note,'') || coalesce(c.revoked_reason,'') AS t
                    FROM clan_invite_code c
                  UNION ALL
                  SELECT coalesce(a.entity_type,'') || coalesce(a.entity_id,'')
                         || coalesce(a.note,'') || coalesce(a.after::text,'')
                         || coalesce(a.before::text,'') AS t
                    FROM audit_log a
                ) q
                 WHERE q.t ILIKE '%' || ? || '%' OR q.t ILIKE '%' || ? || '%'
                """;
        Integer hits = jdbc.queryForObject(sql, Integer.class, maTho, maChuanHoa);
        assertThat(hits)
                .as("ma tho (hoac dang chuan hoa cua no) khong duoc xuat hien o bat cu dau")
                .isZero();

        // Va thu duy nhat duoc luu dung la bam SHA-256 cua dang chuan hoa.
        assertThat(jdbc.queryForObject(
                "SELECT code_hash FROM clan_invite_code WHERE id = ?", String.class,
                phat.invite().id()))
                .isEqualTo(InvitationCode.sha256Hex(maChuanHoa));
    }

    // =====================================================================================
    // Phân quyền: phát và thu hồi là việc của Hội đồng
    // =====================================================================================

    @Test
    @DisplayName("Trưởng chi KHÔNG phát được mã dòng họ — hậu quả của nó là cả họ")
    void truongChiKhongPhatDuocMaDongHo() {
        authenticateAs("sub-bon", "BRANCH_HEAD");
        assertThatThrownBy(() -> clanInvites.issue(new IssueClanInviteCommand("Thử")))
                .isInstanceOf(ForbiddenException.class)
                .extracting(ex -> ((ForbiddenException) ex).getCode())
                .isEqualTo(MembershipProblemCodes.FORBIDDEN);
        assertThat(countRows("clan_invite_code")).isZero();
    }

    @Test
    @DisplayName("Trưởng chi KHÔNG xem được bộ đếm — màn giám sát là của Hội đồng")
    void truongChiKhongXemDuocDanhSach() {
        hoiDongPhat(null);
        authenticateAs("sub-bon", "BRANCH_HEAD");
        assertThatThrownBy(() -> clanInvites.list(0, 20))
                .isInstanceOf(ForbiddenException.class);
    }

    // =====================================================================================
    // CHỐT 4 — giới hạn tần suất, đo qua HTTP vì 429 chỉ có nghĩa ở đó
    // =====================================================================================

    @Test
    @DisplayName("dò mã quá nhiều lần → 429 kèm RATE_LIMITED và Retry-After")
    void quaTanSuatTra429() throws Exception {
        // Nguong mac dinh: 10 lan that bai trong 60 phut (giapha.membership.invitation.*).
        for (int lan = 0; lan < 10; lan++) {
            mockMvc.perform(post("/api/v1/clan-invites/lookup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"ZZZZZ-ZZZZZ\"}"))
                    // Khong xac nhan ma co ton tai hay khong: cung mot 404 cho moi ma khong khop.
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(post("/api/v1/clan-invites/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ZZZZZ-ZZZZZ\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(MembershipProblemCodes.RATE_LIMITED))
                // 429 thi DOI, 422 thi sua du lieu roi gui lai ngay — client phan ung khac han nhau.
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());

        // Va ca mot ma THAT cung bi chan trong cua so ay: chan la chan nguoi goi, khong phai chan ma.
        IssuedClanInvite phat = hoiDongPhat(null);
        mockMvc.perform(post("/api/v1/clan-invites/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + phat.code() + "\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("đăng ký bằng SỐ ĐIỆN THOẠI — ô đăng nhập nhận cả hai thì ô đăng ký cũng vậy")
    void dangKyBangSoDienThoai() throws Exception {
        IssuedClanInvite phat = hoiDongPhat(null);

        // Khong gui token: day la duong that cua mot nguoi chua co tai khoan nao. Cong danh tinh
        // chua duoc cau hinh trong moi truong test nen no se dung o buoc Keycloak va tra 503 —
        // dieu can ghim o day la no KHONG con dung o buoc kiem khuon du lieu nua.
        //
        // 503 cung la bang chung cua mot bat bien khac: khi cong danh tinh hong thi CHUA mot luot
        // nao bi tieu, nen nguoi dung bam lai duoc.
        mockMvc.perform(post("/api/v1/clan-invites/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + phat.code()
                                + "\",\"loginId\":\"0912 345 678\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value(MembershipProblemCodes.IDENTITY_PROVIDER_UNAVAILABLE));
        assertThat(boDemCua(phat.invite().id())).isZero();

        // Mot chuoi khong doc duoc thanh email lan so may thi van bi tu choi — va bi tu choi som,
        // truoc khi cham toi Keycloak.
        mockMvc.perform(post("/api/v1/clan-invites/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + phat.code()
                                + "\",\"loginId\":\"khong-phai-gi-ca\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(MembershipProblemCodes.VALIDATION_FAILED));
        assertThat(boDemCua(phat.invite().id())).isZero();
    }

    @Test
    @DisplayName("tên cũ `email` vẫn nhận — bản giao diện đang chạy không gãy")
    void tenCuEmailVanNhan() throws Exception {
        IssuedClanInvite phat = hoiDongPhat(null);
        mockMvc.perform(post("/api/v1/clan-invites/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + phat.code()
                                + "\",\"email\":\"ba.lan@example.com\"}"))
                // Di toi tan cong danh tinh, tuc da doc duoc dinh danh tu ten cu.
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("danh sách mã đi kèm MẪU SỐ — một bộ đếm không có mẫu số thì không ai phán xét được")
    void danhSachMaDiKemMauSo() {
        IssuedClanInvite phat = hoiDongPhat(null);
        dangKyBangToken(phat.code(), "sub-lan");

        authenticateAs("sub-hoi-dong", "COUNCIL");
        var danhSach = clanInvites.list(0, 20);

        assertThat(danhSach.invites()).extracting(view -> view.id())
                .containsExactly(phat.invite().id());
        assertThat(danhSach.invites().get(0).useCount()).isEqualTo(1);
        // Hai nhan khau con song da gieo o dungDongHo(): cu Toc va ong Bon. Con so nay la thu bien
        // "da dung 400 lan" thanh mot cau hoi thay vi mot con so trang tri.
        assertThat(danhSach.clanLivingPersonCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("xem mã không cần token, và KHÔNG lộ bộ đếm cho người cầm mã")
    void xemMaKhongCanTokenVaKhongLoBoDem() throws Exception {
        IssuedClanInvite phat = hoiDongPhat(600);
        dangKyBangToken(phat.code(), "sub-lan");

        mockMvc.perform(post("/api/v1/clan-invites/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + phat.code() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clanName").value("Dòng họ Nguyễn"))
                .andExpect(jsonPath("$.expiresAt").exists())
                // useCount/remainingUses/label la cong cu giam sat cua Hoi dong. Noi "ma nay con 3
                // luot" cho mot nguoi chua dang nhap la noi cho ke do biet minh dang o dau.
                .andExpect(jsonPath("$.useCount").doesNotExist())
                .andExpect(jsonPath("$.remainingUses").doesNotExist())
                .andExpect(jsonPath("$.label").doesNotExist());
    }
}
