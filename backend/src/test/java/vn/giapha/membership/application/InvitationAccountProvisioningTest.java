package vn.giapha.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import vn.giapha.membership.domain.InvitationStatus;
import vn.giapha.membership.domain.port.IdentityAccount;
import vn.giapha.membership.domain.port.IdentityProviderException;
import vn.giapha.membership.support.ClanFixture;
import vn.giapha.membership.support.TestSecurity;
import vn.giapha.shared.exception.DomainException;

/**
 * <b>Chỗ đứt cuối cùng của luồng mời</b>: người được mời chưa có tài khoản Keycloak nào, và realm
 * đặt {@code registrationAllowed: false} nên không ai lập được tài khoản ấy cho họ.
 *
 * <p>Bộ test này kiểm đúng cái lối vừa mở ra — {@code accept} không còn đòi token, tự lập tài khoản
 * và trả về liên kết đặt mật khẩu — cùng với ba ca hỏng mà thiết kế thứ tự thao tác sinh ra để
 * chịu được: <b>hỏng giữa chừng</b>, <b>tài khoản đã tồn tại</b>, <b>bấm hai lần</b>.</p>
 *
 * <p>Bất biến được nhắc lại ở gần như mọi ca dưới đây: <b>mã mời chỉ chết khi mọi thứ đã xong</b>.
 * Hỏng ở bất kỳ đâu thì mã vẫn {@code PENDING} và người dùng bấm lại được.</p>
 */
@DisplayName("Lập tài khoản Keycloak cho người được mời")
class InvitationAccountProvisioningTest {

    private static final String IP_KHACH = "203.0.113.42";
    private static final String EMAIL_BA_LAN = "ba.lan@example.com";

    private ClanFixture clan;
    private InvitationService invitations;
    private AppUser truongChiGiap;
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
        String code = invitations.issue(new IssueInvitationCommand(personId)).code();
        TestSecurity.logout();
        return code;
    }

    private InvitationStatus trangThaiMa() {
        return clan.invitations.all().get(0).status();
    }

    // =====================================================================================
    @Nested
    @DisplayName("Người chưa từng có tài khoản")
    class ChuaCoTaiKhoan {

        @Test
        @DisplayName("nhận lời mời KHÔNG cần token — tài khoản Keycloak được lập ngay tại đó")
        void nhanDuocMaKhongCanToken() {
            String code = truongChiPhatMa(baLan);

            AcceptedInvitation accepted = invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, "Nguyễn Thị Lan", IP_KHACH));

            // Day la ca duy nhat quan trong: mot nguoi khong co gi ngoai to phieu giay da vao duoc
            // he thong va da dung voi dung nhan khau cua minh.
            assertThat(clan.identityProvider.created()).hasSize(1);
            assertThat(accepted.user().personId()).isEqualTo(baLan);
            assertThat(accepted.user().status()).isEqualTo(AppUserStatus.ACTIVE);
            assertThat(trangThaiMa()).isEqualTo(InvitationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("phản hồi mang liên kết đặt mật khẩu — thứ frontend chuyển hướng tới")
        void phanHoiMangLienKetDatMatKhau() {
            String code = truongChiPhatMa(baLan);

            AcceptedInvitation accepted = invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH));

            assertThat(accepted.setPasswordLink()).isPresent();
            assertThat(accepted.setPasswordLink().orElseThrow().url()).contains("token=");
        }

        @Test
        @DisplayName("tài khoản app_user trỏ đúng sub của tài khoản Keycloak vừa lập")
        void appUserTroDungSub() {
            String code = truongChiPhatMa(baLan);

            AcceptedInvitation accepted = invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH));

            String sub = accepted.user().keycloakSub();
            assertThat(clan.identityProvider.bySubject(sub)).isPresent();
            assertThat(clan.identityProvider.bySubject(sub).orElseThrow().email())
                    .isEqualTo(EMAIL_BA_LAN);
        }

        @Test
        @DisplayName("không khai email thì từ chối, và mã KHÔNG cháy")
        void khongKhaiEmailThiTuChoi() {
            String code = truongChiPhatMa(baLan);

            assertThatThrownBy(() ->
                    invitations.accept(AcceptInvitationCommand.byEmail(code, "  ", null, IP_KHACH)))
                    .isInstanceOf(DomainException.class)
                    .extracting("code").isEqualTo(MembershipProblemCodes.VALIDATION_FAILED);

            assertThat(trangThaiMa()).isEqualTo(InvitationStatus.PENDING);
            assertThat(clan.identityProvider.size()).isZero();
        }

        @Test
        @DisplayName("chưa cấu hình cổng danh tính thì trả 503, và mã KHÔNG cháy")
        void chuaCauHinhThiBaoRoRang() {
            clan.identityProvider.notConfigured();
            String code = truongChiPhatMa(baLan);

            assertThatThrownBy(() -> invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH)))
                    .isInstanceOf(IdentityProviderException.class);

            // Nguoi dung khong sua duoc gi, nen cau tra loi dung la "thu lai sau" — va de cau ay
            // khong phai mot loi noi doi, ma moi phai con nguyen.
            assertThat(trangThaiMa()).isEqualTo(InvitationStatus.PENDING);
        }
    }

    // =====================================================================================
    @Nested
    @DisplayName("Hỏng giữa chừng — hai hệ thống, không transaction chung")
    class HongGiuaChung {

        @Test
        @DisplayName("ghép nhân khẩu THẤT BẠI thì mã mời KHÔNG cháy và người dùng thử lại được")
        void ghepNhanKhauHongThiMaVanSong() {
            String code = truongChiPhatMa(baLan);
            // Lan ghi CSDL dau tien no tung — dung canh "tao xong tai khoan Keycloak roi mat dien".
            clan.appUsers.failNextSaveWith(new IllegalStateException("mat ket noi CSDL"));

            assertThatThrownBy(() -> invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH)))
                    .isInstanceOf(IllegalStateException.class);

            // BAT BIEN QUAN TRONG NHAT CUA CA BAI NAY.
            assertThat(trangThaiMa()).isEqualTo(InvitationStatus.PENDING);
        }

        @Test
        @DisplayName("thử lại sau khi hỏng thì DÙNG LẠI tài khoản cũ, không đẻ tài khoản thứ hai")
        void thuLaiThiDungLaiTaiKhoanCu() {
            String code = truongChiPhatMa(baLan);
            clan.appUsers.failNextSaveWith(new IllegalStateException("mat ket noi CSDL"));
            assertThatThrownBy(() -> invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH)))
                    .isInstanceOf(IllegalStateException.class);
            int sauLanHong = clan.identityProvider.size();

            AcceptedInvitation accepted = invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH));

            // Day la ly do "tim truoc, tao sau" phai la mot bat bien chu khong phai mot toi uu:
            // khong co no thi lan thu hai va cham duplicateEmailsAllowed=false va nguoi dung ket han.
            assertThat(clan.identityProvider.size()).isEqualTo(sauLanHong);
            assertThat(accepted.user().personId()).isEqualTo(baLan);
            assertThat(trangThaiMa()).isEqualTo(InvitationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("Keycloak chết giữa chừng thì mã cũng KHÔNG cháy")
        void keycloakChetThiMaVanSong() {
            String code = truongChiPhatMa(baLan);
            clan.identityProvider.failNextWith(
                    new IdentityProviderException("Keycloak khong tra loi"));

            assertThatThrownBy(() -> invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH)))
                    .isInstanceOf(IdentityProviderException.class);

            assertThat(trangThaiMa()).isEqualTo(InvitationStatus.PENDING);
        }

        @Test
        @DisplayName("tài khoản Keycloak mồ côi KHÔNG bị xoá đi — xoá là tự tạo lỗi mới")
        void taiKhoanMoCoiKhongBiXoa() {
            String code = truongChiPhatMa(baLan);
            clan.appUsers.failNextSaveWith(new IllegalStateException("mat ket noi CSDL"));
            assertThatThrownBy(() -> invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH)))
                    .isInstanceOf(IllegalStateException.class);

            // No o lai, khong ghep voi nhan khau nao — dung trang thai cua moi tai khoan vua dang
            // nhap Google lan dau. Xoa no di thi hai nguoi bam gan nhau se giam: luong A tao,
            // luong B tim thay va dung, roi luong A hong va xoa mat tai khoan luong B vua ghep.
            assertThat(clan.identityProvider.findByEmail(EMAIL_BA_LAN)).isPresent();
        }
    }

    // =====================================================================================
    @Nested
    @DisplayName("Trùng lặp")
    class TrungLap {

        @Test
        @DisplayName("người đã có tài khoản Keycloak (email cũ) thì DÙNG LẠI, không tạo bản sao")
        void daCoTaiKhoanThiDungLai() {
            IdentityAccount cu = clan.identityProvider.seed(EMAIL_BA_LAN, true);
            String code = truongChiPhatMa(baLan);

            AcceptedInvitation accepted = invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH));

            assertThat(clan.identityProvider.created()).isEmpty();
            assertThat(accepted.user().keycloakSub()).isEqualTo(cu.subject());
            assertThat(accepted.user().personId()).isEqualTo(baLan);
        }

        @Test
        @DisplayName("tài khoản ĐÃ CÓ mật khẩu thì KHÔNG phát liên kết đặt mật khẩu")
        void daCoMatKhauThiKhongPhatLienKet() {
            clan.identityProvider.seed(EMAIL_BA_LAN, true);
            String code = truongChiPhatMa(baLan);

            AcceptedInvitation accepted = invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH));

            // Phat lien ket cho mot tai khoan DA co mat khau la mo mot loi doi mat khau cho bat ky
            // ai cam ma moi va doan dung email cua mot thanh vien cu. Vang mat o day la mot phep
            // chan, khong phai mot thieu sot.
            assertThat(accepted.setPasswordLink()).isEmpty();
        }

        @Test
        @DisplayName("tài khoản cũ CHƯA có mật khẩu (chỉ đăng nhập Google) thì vẫn phát liên kết")
        void taiKhoanChuaCoMatKhauThiVanPhatLienKet() {
            clan.identityProvider.seed(EMAIL_BA_LAN, false);
            String code = truongChiPhatMa(baLan);

            AcceptedInvitation accepted = invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH));

            assertThat(accepted.setPasswordLink()).isPresent();
        }

        @Test
        @DisplayName("bấm hai lần: lần hai báo mã đã dùng và KHÔNG tạo tài khoản thứ hai")
        void bamHaiLan() {
            String code = truongChiPhatMa(baLan);
            invitations.accept(AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH));

            assertThatThrownBy(() -> invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH)))
                    .isInstanceOf(InvitationNotUsableException.class)
                    .extracting("code")
                    .isEqualTo(MembershipProblemCodes.INVITATION_ALREADY_USED);

            assertThat(clan.identityProvider.created()).hasSize(1);
            assertThat(clan.appUsers.byPersonId(baLan)).isPresent();
        }

        @Test
        @DisplayName("một nhân khẩu vẫn chỉ gắn MỘT tài khoản, kể cả qua lối lập tài khoản mới")
        void motNhanKhauMotTaiKhoan() {
            String code = truongChiPhatMa(baLan);
            invitations.accept(AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH));

            // Truong chi phat lai — lan nay mot nguoi KHAC cam ma va khai email cua minh.
            TestSecurity.loginAs("sub-bon", "BRANCH_HEAD");
            assertThatThrownBy(() -> invitations.issue(new IssueInvitationCommand(baLan)))
                    .isInstanceOf(DomainException.class)
                    .extracting("code").isEqualTo(MembershipProblemCodes.PERSON_ALREADY_LINKED);
        }
    }

    // =====================================================================================
    @Nested
    @DisplayName("Người đã đăng nhập")
    class DaDangNhap {

        @Test
        @DisplayName("có token thì KHÔNG đụng tới Keycloak và KHÔNG phát liên kết")
        void coTokenThiKhongDungToiKeycloak() {
            String code = truongChiPhatMa(baLan);
            TestSecurity.loginAs("sub-lan", "MEMBER");

            AcceptedInvitation accepted = invitations.accept(
                    AcceptInvitationCommand.byCurrentUser(code, IP_KHACH));

            assertThat(clan.identityProvider.size()).isZero();
            assertThat(accepted.setPasswordLink()).isEmpty();
            assertThat(accepted.user().keycloakSub()).isEqualTo("sub-lan");
            assertThat(accepted.user().personId()).isEqualTo(baLan);
        }

        @Test
        @DisplayName("có token thì email gõ tay bị BỎ QUA — token là bằng chứng mạnh hơn")
        void emailBiBoQuaKhiCoToken() {
            String code = truongChiPhatMa(baLan);
            TestSecurity.loginAs("sub-lan", "MEMBER");

            AcceptedInvitation accepted = invitations.accept(AcceptInvitationCommand.byEmail(
                    code, "ke-gia-mao@example.com", "Ai Do", IP_KHACH));

            assertThat(accepted.user().keycloakSub()).isEqualTo("sub-lan");
            assertThat(clan.identityProvider.findByEmail("ke-gia-mao@example.com")).isEmpty();
        }
    }
}
