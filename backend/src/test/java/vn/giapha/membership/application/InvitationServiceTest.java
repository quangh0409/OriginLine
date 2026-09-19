package vn.giapha.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.membership.application.command.AcceptInvitationCommand;
import vn.giapha.membership.application.command.IssueInvitationCommand;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.AppUserStatus;
import vn.giapha.membership.domain.Invitation;
import vn.giapha.membership.domain.InvitationCode;
import vn.giapha.membership.domain.InvitationStatus;
import vn.giapha.membership.support.ClanFixture;
import vn.giapha.membership.support.TestSecurity;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Luồng mời, kiểm ở tầng application với kho dữ liệu giả.
 *
 * <p>Luật phân quyền, luật đếm và luật ghép tài khoản đều chạy bằng lớp production — chỉ kho dữ
 * liệu và {@code SecurityContext} là giả. Bộ test tích hợp {@code InvitationFlowIT} kiểm cùng các
 * ca này trên Postgres thật; ở đây kiểm được thêm những ca cần điều khiển đồng hồ và bộ đếm.</p>
 */
@DisplayName("Luồng mời người vào hệ thống")
class InvitationServiceTest {

    private static final String IP_KHACH = "203.0.113.7";

    private ClanFixture clan;
    private InvitationService invitations;

    /** Ông Bốn — Trưởng Chi Giáp, người phát lời mời trong mọi ca mặc định. */
    private AppUser truongChiGiap;

    /** Bà Lan — con dâu, nhân khẩu Chi Giáp, chưa có tài khoản. */
    private UUID baLan;

    @BeforeEach
    void dungDongHo() {
        clan = new ClanFixture();
        invitations = clan.invitationService();

        truongChiGiap = clan.account("sub-bon", clan.chiGiapId);
        clan.branchHeadOf(truongChiGiap, clan.chiGiapId);
        clan.invitees.living(truongChiGiap.personId(), "Nguyễn Văn Bốn", 6);

        baLan = clan.invitees.living(clan.personIn(clan.chiGiapId), "Nguyễn Thị Lan", 7);
    }

    @AfterEach
    void dangXuat() {
        TestSecurity.logout();
    }

    private String truongChiPhatMa(UUID personId) {
        TestSecurity.loginAs("sub-bon", "BRANCH_HEAD");
        return invitations.issue(new IssueInvitationCommand(personId)).code();
    }

    // =====================================================================================
    @Nested
    @DisplayName("Phát lời mời — phạm vi chi")
    class PhatLoiMoi {

        @Test
        @DisplayName("Trưởng chi phát được lời mời TRONG chi mình")
        void truongChiPhatDuocTrongChiMinh() {
            TestSecurity.loginAs("sub-bon", "BRANCH_HEAD");

            IssuedInvitation issued = invitations.issue(new IssueInvitationCommand(baLan));

            assertThat(issued.code()).isNotBlank();
            assertThat(issued.invitation().personId()).isEqualTo(baLan);
            assertThat(issued.invitation().invitedBy()).isEqualTo(truongChiGiap.id());
            assertThat(issued.invitation().status()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("Trưởng chi phát cho người NGOÀI chi mình thì bị từ chối")
        void truongChiKhongPhatDuocNgoaiChi() {
            UUID nguoiChiAt = clan.invitees.living(clan.personIn(clan.chiAtId), "Nguyễn Văn Ất", 7);
            TestSecurity.loginAs("sub-bon", "BRANCH_HEAD");

            assertThatThrownBy(() -> invitations.issue(new IssueInvitationCommand(nguoiChiAt)))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("code")
                    .isEqualTo(MembershipProblemCodes.BRANCH_SCOPE_VIOLATION);
            assertThat(clan.invitations.all()).isEmpty();
        }

        @Test
        @DisplayName("phạm vi phủ cả hậu duệ: Trưởng Chi Giáp mời được người ở Ngành Trưởng")
        void phamViPhuCaHauDue() {
            UUID chauNganhTruong =
                    clan.invitees.living(clan.personIn(clan.nganhTruongId), "Nguyễn Văn Cháu", 8);
            TestSecurity.loginAs("sub-bon", "BRANCH_HEAD");

            assertThat(invitations.issue(new IssueInvitationCommand(chauNganhTruong)).code())
                    .isNotBlank();
        }

        @Test
        @DisplayName("Hội đồng Tộc biểu phát được ở mọi chi")
        void hoiDongPhatDuocToanHo() {
            AppUser hoiDong = clan.account("sub-hoi-dong", clan.gocId);
            clan.councilWide(hoiDong);
            UUID nguoiChiAt = clan.invitees.living(clan.personIn(clan.chiAtId), "Nguyễn Văn Ất", 7);
            TestSecurity.loginAs("sub-hoi-dong", "COUNCIL");

            assertThat(invitations.issue(new IssueInvitationCommand(baLan)).code()).isNotBlank();
            assertThat(invitations.issue(new IssueInvitationCommand(nguoiChiAt)).code()).isNotBlank();
        }

        @Test
        @DisplayName("Thành viên thường không phát được lời mời nào")
        void thanhVienThuongKhongPhatDuoc() {
            clan.account("sub-mai", clan.chiGiapId);
            TestSecurity.loginAs("sub-mai", "MEMBER");

            assertThatThrownBy(() -> invitations.issue(new IssueInvitationCommand(baLan)))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("code").isEqualTo(MembershipProblemCodes.FORBIDDEN);
        }

        @Test
        @DisplayName("nhân khẩu chưa gắn chi thì chỉ vai toàn dòng họ mới mời được")
        void nhanKhauChuaGanChiChiHoiDongMoiDuoc() {
            UUID mocoi = clan.invitees.living(clan.personWithoutBranch(), "Nguyễn Vô Chi", null);
            TestSecurity.loginAs("sub-bon", "BRANCH_HEAD");

            // "Chi rong khong phai chi cong cong" — xem BranchScopeGuard.
            assertThatThrownBy(() -> invitations.issue(new IssueInvitationCommand(mocoi)))
                    .isInstanceOf(ForbiddenException.class);

            AppUser hoiDong = clan.account("sub-hoi-dong", clan.gocId);
            clan.councilWide(hoiDong);
            TestSecurity.loginAs("sub-hoi-dong", "COUNCIL");
            assertThat(invitations.issue(new IssueInvitationCommand(mocoi)).code()).isNotBlank();
        }

        @Test
        @DisplayName("mời người ĐÃ CÓ tài khoản thì bị từ chối NGAY LÚC PHÁT")
        void moiNguoiDaCoTaiKhoanBiTuChoiLucPhat() {
            AppUser daCo = clan.account("sub-da-co", clan.chiGiapId);
            clan.invitees.living(daCo.personId(), "Nguyễn Đã Có", 7);
            TestSecurity.loginAs("sub-bon", "BRANCH_HEAD");

            assertThatThrownBy(() ->
                    invitations.issue(new IssueInvitationCommand(daCo.personId())))
                    .isInstanceOf(DomainException.class)
                    .extracting("code").isEqualTo(MembershipProblemCodes.PERSON_ALREADY_LINKED);
            // Khong duoc de sinh ra mot ma roi moi phat hien: Truong chi da in phieu mat roi.
            assertThat(clan.invitations.all()).isEmpty();
        }

        @Test
        @DisplayName("không mời được người đã khuất")
        void khongMoiDuocNguoiDaKhuat() {
            UUID cuOng = clan.invitees.deceased(clan.personIn(clan.chiGiapId), "Nguyễn Văn Cả");
            TestSecurity.loginAs("sub-bon", "BRANCH_HEAD");

            assertThatThrownBy(() -> invitations.issue(new IssueInvitationCommand(cuOng)))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("da khuat");
        }

        @Test
        @DisplayName("không mời được nhân khẩu đã xoá mềm")
        void khongMoiDuocNhanKhauDaXoa() {
            UUID daXoa = clan.invitees.softDeleted(clan.personIn(clan.chiGiapId), "Nguyễn Đã Xoá");
            TestSecurity.loginAs("sub-bon", "BRANCH_HEAD");

            assertThatThrownBy(() -> invitations.issue(new IssueInvitationCommand(daXoa)))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("xoa khoi pha");
        }

        @Test
        @DisplayName("nhân khẩu không tồn tại thì 404, không phải 403")
        void nhanKhauKhongTonTai() {
            TestSecurity.loginAs("sub-bon", "BRANCH_HEAD");
            assertThatThrownBy(() ->
                    invitations.issue(new IssueInvitationCommand(UUID.randomUUID())))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("phát lại thu hồi mã cũ — hai mã cùng mở một hồ sơ thì \"thu hồi\" mất nghĩa")
        void phatLaiThuHoiMaCu() {
            String maCu = truongChiPhatMa(baLan);
            String maMoi = truongChiPhatMa(baLan);

            assertThat(maMoi).isNotEqualTo(maCu);
            assertThat(clan.invitations.all())
                    .filteredOn(invitation -> invitation.status() == InvitationStatus.REVOKED)
                    .hasSize(1);
            assertThatThrownBy(() -> invitations.preview(maCu, IP_KHACH))
                    .isInstanceOf(InvitationNotUsableException.class)
                    .extracting("code").isEqualTo(MembershipProblemCodes.INVITATION_REVOKED);
            assertThat(invitations.preview(maMoi, IP_KHACH).invitee().displayName())
                    .isEqualTo("Nguyễn Thị Lan");
        }

        @Test
        @DisplayName("mã THÔ không được lưu ở đâu cả — bản ghi chỉ giữ băm")
        void maThoKhongDuocLuu() {
            String code = truongChiPhatMa(baLan);
            Invitation stored = clan.invitations.all().get(0);

            assertThat(stored.codeHash()).isNotEqualTo(code);
            assertThat(stored.codeHash()).isEqualTo(InvitationCode.hash(code));
            assertThat(stored.codeHash()).doesNotContain(code.replace("-", ""));
            assertThat(stored.note()).isNull();
        }
    }

    // =====================================================================================
    @Nested
    @DisplayName("Xem lời mời")
    class XemLoiMoi {

        @Test
        @DisplayName("hiện tên người sẽ được gắn và tên người mời — ngoại lệ riêng tư có chủ ý")
        void hienTenNguoiDuocMoiVaNguoiMoi() {
            String code = truongChiPhatMa(baLan);
            TestSecurity.logout();

            InvitationPreview preview = invitations.preview(code, IP_KHACH);

            assertThat(preview.invitee().displayName()).isEqualTo("Nguyễn Thị Lan");
            assertThat(preview.invitee().generation()).isEqualTo(7);
            assertThat(preview.inviter().displayName()).isEqualTo("Nguyễn Văn Bốn");
            assertThat(preview.expiresAt()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("chức danh dòng tộc của người mời, KHÔNG phải vai kỹ thuật")
        void hienChucDanhDongToc() {
            clan.invitees.clanOffice(truongChiGiap.personId(), "Chi Giáp", "CHI");
            String code = truongChiPhatMa(baLan);
            TestSecurity.logout();

            assertThat(invitations.preview(code, IP_KHACH).inviter().clanTitle())
                    .isEqualTo("Trưởng Chi Giáp");
        }

        @Test
        @DisplayName("KHÔNG trả khoá nhân khẩu ra màn nhận lời mời")
        void khongTraKhoaNhanKhau() {
            String code = truongChiPhatMa(baLan);
            TestSecurity.logout();

            // Kieu tra ve khong co cho nao de dat personId — day la mot rang buoc cua trinh bien
            // dich, khong phai mot thoa thuan mieng. Khoa nhan khau chi xuat hien o lenh NHAN,
            // khi nguoi goi da co token.
            InvitationPreview preview = invitations.preview(code, IP_KHACH);
            assertThat(preview.invitee().getClass().getRecordComponents())
                    .extracting("name")
                    .containsExactly("displayName", "generation", "branch");
        }

        @Test
        @DisplayName("xem được mà KHÔNG cần đăng nhập — người bấm chính là người chưa có tài khoản")
        void xemDuocMaKhongCanDangNhap() {
            String code = truongChiPhatMa(baLan);
            TestSecurity.logout();

            assertThat(invitations.preview(code, IP_KHACH)).isNotNull();
        }

        @Test
        @DisplayName("mã không khớp lời mời nào dùng lại NOT_FOUND, không đẻ mã thứ tư")
        void maLaDungLaiNotFound() {
            assertThatThrownBy(() -> invitations.preview(InvitationCode.generate(), IP_KHACH))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("code").isEqualTo(MembershipProblemCodes.NOT_FOUND);
        }

        @Test
        @DisplayName("mã sai định dạng trả lời Y HỆT mã đúng định dạng mà không tồn tại")
        void maSaiDinhDangTraLoiYHet() {
            // Khac nhau o day thi ke do doc duoc DO DAI va BANG CHU cua ma ma khong can doan
            // trung lan nao — mot kenh phu re tien va hoan toan tranh duoc.
            assertThatThrownBy(() -> invitations.preview("khong-phai-ma", IP_KHACH))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("code").isEqualTo(MembershipProblemCodes.NOT_FOUND);
            assertThatThrownBy(() -> invitations.preview(InvitationCode.generate(), IP_KHACH))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("code").isEqualTo(MembershipProblemCodes.NOT_FOUND);
            assertThat(clan.attempts.failureCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("ba ca hỏng có ba mã lỗi RIÊNG — giao diện rẽ nhánh theo code")
        void baCaHongCoBaMaLoiRieng() {
            assertThatThrownBy(() -> invitations.preview(gieoMaHetHan(), IP_KHACH))
                    .isInstanceOf(InvitationNotUsableException.class)
                    .extracting("code").isEqualTo(MembershipProblemCodes.INVITATION_EXPIRED);

            String maThuHoi = truongChiPhatMa(baLan);
            TestSecurity.logout();
            invitations.decline(maThuHoi, IP_KHACH);
            assertThatThrownBy(() -> invitations.preview(maThuHoi, IP_KHACH))
                    .isInstanceOf(InvitationNotUsableException.class)
                    .extracting("code").isEqualTo(MembershipProblemCodes.INVITATION_REVOKED);

            String maDaDung = truongChiPhatMa(baLan);
            TestSecurity.loginAs("sub-lan", "MEMBER");
            invitations.accept(AcceptInvitationCommand.byCurrentUser(maDaDung, IP_KHACH));
            assertThatThrownBy(() -> invitations.preview(maDaDung, IP_KHACH))
                    .isInstanceOf(InvitationNotUsableException.class)
                    .extracting("code").isEqualTo(MembershipProblemCodes.INVITATION_ALREADY_USED);
        }

        @Test
        @DisplayName("thân lỗi KHÔNG mang tên người mời hay người được mời")
        void thanLoiKhongMangTenNguoiMoi() {
            // Mot ma hong theo dinh nghia la ma CO THE dang nam trong tay nguoi la. Gan ten hay so
            // that cua mot Truong chi vao phan hoi loi la bien ke do ma thanh ke quet danh ba.
            String code = gieoMaHetHan();
            assertThatThrownBy(() -> invitations.preview(code, IP_KHACH))
                    .isInstanceOf(InvitationNotUsableException.class)
                    .hasMessageNotContaining("Nguy")
                    .hasMessageNotContaining("Lan")
                    .hasMessageNotContaining("Chi");
        }
    }

    // =====================================================================================
    @Nested
    @DisplayName("Nhận lời mời")
    class NhanLoiMoi {

        @Test
        @DisplayName("nhận xong là tài khoản gắn đúng nhân khẩu và ACTIVE — KHÔNG qua chờ duyệt")
        void nhanXongLaGanNgay() {
            String code = truongChiPhatMa(baLan);

            TestSecurity.loginAs("sub-lan", "MEMBER");
            AppUser lan = invitations.accept(AcceptInvitationCommand.byCurrentUser(code, IP_KHACH)).user();

            assertThat(lan.personId()).isEqualTo(baLan);
            assertThat(lan.status()).isEqualTo(AppUserStatus.ACTIVE);
            // Khong mot ChangeRequest nao duoc sinh ra: khong co hang doi nao de cho.
            assertThat(clan.requests.byPerson(baLan, null)).isEmpty();
        }

        @Test
        @DisplayName("mã dùng lại lần hai thất bại")
        void maDungLaiLanHaiThatBai() {
            String code = truongChiPhatMa(baLan);

            TestSecurity.loginAs("sub-lan", "MEMBER");
            invitations.accept(AcceptInvitationCommand.byCurrentUser(code, IP_KHACH));

            TestSecurity.loginAs("sub-nguoi-khac", "MEMBER");
            assertThatThrownBy(() -> invitations.accept(AcceptInvitationCommand.byCurrentUser(code, IP_KHACH)))
                    .isInstanceOf(InvitationNotUsableException.class)
                    .extracting("code").isEqualTo(MembershipProblemCodes.INVITATION_ALREADY_USED);
        }

        @Test
        @DisplayName("mã hết hạn thất bại, và lý do nói rõ là EXPIRED")
        void maHetHanThatBai() {
            String code = gieoMaHetHan();
            TestSecurity.loginAs("sub-lan", "MEMBER");

            assertThatThrownBy(() -> invitations.accept(AcceptInvitationCommand.byCurrentUser(code, IP_KHACH)))
                    .isInstanceOf(InvitationNotUsableException.class)
                    .extracting("usability").hasToString("EXPIRED");
        }

        @Test
        @DisplayName("mã đã bị thu hồi thất bại")
        void maDaThuHoiThatBai() {
            String code = truongChiPhatMa(baLan);
            TestSecurity.loginAs("sub-bon", "BRANCH_HEAD");
            invitations.revoke(clan.invitations.all().get(0).id(), "Gui nham so");

            TestSecurity.loginAs("sub-lan", "MEMBER");
            assertThatThrownBy(() -> invitations.accept(AcceptInvitationCommand.byCurrentUser(code, IP_KHACH)))
                    .isInstanceOf(InvitationNotUsableException.class)
                    .extracting("usability").hasToString("REVOKED");
        }

        @Test
        @DisplayName("chưa đăng nhập mà cũng không khai email thì không nhận được")
        void chuaDangNhapVaKhongKhaiEmail() {
            // Truoc day ca nay tra 403 "phai dang nhap truoc khi nhan loi moi" — va do chinh la
            // cho dut cua ca luong: nguoi duoc moi phai DA CO tai khoan moi nhan duoc loi moi,
            // trong khi realm dat registrationAllowed: false. Nay chua dang nhap van nhan duoc,
            // chi can khai dia chi thu de he thong lap tai khoan. Thieu ca hai thi khong con gi
            // de xac dinh danh tinh, nen day la loi DU LIEU chu khong phai loi QUYEN.
            String code = truongChiPhatMa(baLan);
            TestSecurity.logout();

            assertThatThrownBy(() ->
                    invitations.accept(AcceptInvitationCommand.byCurrentUser(code, IP_KHACH)))
                    .isInstanceOf(DomainException.class)
                    .extracting("code").isEqualTo(MembershipProblemCodes.VALIDATION_FAILED);
            // Va quan trong nhat: MA VAN CON DUNG DUOC.
            assertThat(clan.invitations.all().get(0).status())
                    .isEqualTo(InvitationStatus.PENDING);
        }

        @Test
        @DisplayName("tài khoản đã gắn nhân khẩu KHÁC thì không nhận thêm được")
        void taiKhoanDaGanNhanKhauKhac() {
            clan.account("sub-mai", clan.chiGiapId);
            String code = truongChiPhatMa(baLan);

            TestSecurity.loginAs("sub-mai", "MEMBER");
            assertThatThrownBy(() -> invitations.accept(AcceptInvitationCommand.byCurrentUser(code, IP_KHACH)))
                    .isInstanceOf(DomainException.class)
                    .extracting("code").isEqualTo(MembershipProblemCodes.ACCOUNT_ALREADY_LINKED);
        }

        @Test
        @DisplayName("\"Không phải tôi\" giết mã, và không cần đăng nhập")
        void khongPhaiToiGietMa() {
            String code = truongChiPhatMa(baLan);
            TestSecurity.logout();

            invitations.decline(code, IP_KHACH);

            assertThat(clan.invitations.all().get(0).status()).isEqualTo(InvitationStatus.REVOKED);
            TestSecurity.loginAs("sub-lan", "MEMBER");
            assertThatThrownBy(() -> invitations.accept(AcceptInvitationCommand.byCurrentUser(code, IP_KHACH)))
                    .isInstanceOf(InvitationNotUsableException.class);
        }
    }

    // =====================================================================================
    @Nested
    @DisplayName("Giới hạn tần suất")
    class GioiHanTanSuat {

        @Test
        @DisplayName("quá ngưỡng lần thử thất bại thì bị chặn")
        void quaNguongThiBiChan() {
            InvitationService chat = clan.invitationService(3);
            for (int i = 0; i < 3; i++) {
                String maLa = InvitationCode.generate();
                assertThatThrownBy(() -> chat.preview(maLa, IP_KHACH))
                        .isInstanceOf(NotFoundException.class);
            }
            assertThatThrownBy(() -> chat.preview(InvitationCode.generate(), IP_KHACH))
                    .isInstanceOf(TooManyAttemptsException.class);
        }

        @Test
        @DisplayName("người gọi khác không bị vạ lây")
        void nguoiGoiKhacKhongBiVaLay() {
            InvitationService chat = clan.invitationService(2);
            for (int i = 0; i < 2; i++) {
                String maLa = InvitationCode.generate();
                assertThatThrownBy(() -> chat.preview(maLa, IP_KHACH))
                        .isInstanceOf(NotFoundException.class);
            }
            assertThatThrownBy(() -> chat.preview(InvitationCode.generate(), IP_KHACH))
                    .isInstanceOf(TooManyAttemptsException.class);

            String code = truongChiPhatMa(baLan);
            TestSecurity.logout();
            assertThat(chat.preview(code, "198.51.100.9")).isNotNull();
        }

        @Test
        @DisplayName("CHỈ lần thất bại bị đếm — người gõ sai vài lần rồi gõ đúng không bị phạt")
        void chiLanThatBaiBiDem() {
            InvitationService chat = clan.invitationService(3);
            String code = truongChiPhatMa(baLan);
            TestSecurity.logout();

            for (int i = 0; i < 5; i++) {
                assertThat(chat.preview(code, IP_KHACH)).isNotNull();
            }
            assertThat(clan.attempts.failureCount()).isZero();
        }
    }

    // -------------------------------------------------------------------------------------

    /**
     * Gieo thẳng một lời mời đã quá hạn.
     *
     * <p>Đi qua {@code issue()} thì không dựng được ca này: hạn tối thiểu là một ngày, và test
     * không ngủ một ngày. Gieo thẳng vẫn đúng hình dạng vì {@code Invitation} tự tính hết hạn từ
     * {@code expiresAt} chứ không từ một cờ nào.</p>
     */
    private String gieoMaHetHan() {
        String code = InvitationCode.generate();
        clan.invitations.seed(Invitation.issue(UUID.randomUUID(), InvitationCode.hash(code),
                baLan, clan.chiGiapId, truongChiGiap.id(),
                Instant.now().minus(Duration.ofHours(1)), null));
        return code;
    }
}
