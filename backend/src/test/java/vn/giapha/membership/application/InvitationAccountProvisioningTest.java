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

        /**
         * <b>MỆNH ĐỀ 1 — tài khoản mồ côi thì người dùng bấm lại được.</b>
         *
         * <h2>Ca này đã bị lật HAI lần; cả hai lần đều có lý do, và cả hai đều còn giá trị</h2>
         * <ol>
         *   <li><b>Bản gốc</b> ({@code thuLaiThiDungLaiTaiKhoanCu}): hỏng ở bước ghi CSDL thì mã
         *       mời vẫn {@code PENDING} và lần bấm lại <i>tìm thấy tài khoản Keycloak của lần
         *       trước rồi đi tiếp</i> — lời hứa trung tâm của thiết kế thứ tự thao tác: "mọi hỏng
         *       hóc rơi về cùng một trạng thái lành". Phép chặn duy nhất khi ấy là
         *       {@code hasPassword()}.</li>
         *   <li><b>Lật lần một</b> ({@code thuLaiSauKhiHongNayBiChan}): {@code hasPassword()} trả
         *       lời sai câu hỏi — tài khoản Google cũng "chưa có mật khẩu" — nên phép chặn được
         *       siết thành {@code justCreated()}. Lỗ hổng chiếm tài khoản đóng lại, nhưng lời hứa
         *       trên mất theo: người được mời bấm lại cũng bị chặn, <b>vĩnh viễn</b>.</li>
         *   <li><b>Lật lần hai</b> (ca hiện tại): hai lần trước <i>đều đúng</i>, chúng chỉ thiếu
         *       một phép phân biệt. Nay có: <b>dấu {@code UPDATE_PASSWORD} còn treo</b> (dấu vết
         *       riêng của luồng onboarding này — tài khoản Google không bao giờ có) <b>và</b>
         *       <b>chưa có dòng {@code app_user} nào</b> cho {@code sub} ấy (chưa ai nhận tài khoản
         *       này về mình). Xem {@code IdentityReclaimPolicy}.</li>
         * </ol>
         *
         * <p>Giữ lại cả ba chặng vì mỗi chặng là một câu hỏi mà người sửa tiếp theo sẽ hỏi lại.</p>
         */
        @Test
        @DisplayName("thử lại sau khi hỏng thì ĐI TIẾP ĐƯỢC — tài khoản mồ côi đòi lại được")
        void thuLaiSauKhiHongThiDiTiepDuoc() {
            String code = truongChiPhatMa(baLan);
            clan.appUsers.failNextSaveWith(new IllegalStateException("mat ket noi CSDL"));
            assertThatThrownBy(() -> invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH)))
                    .isInstanceOf(IllegalStateException.class);
            int sauLanHong = clan.identityProvider.size();

            AcceptedInvitation accepted = invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH));

            // Khong de ra tai khoan thu hai: "tim truoc, tao sau" van la bat bien.
            assertThat(clan.identityProvider.size()).isEqualTo(sauLanHong);
            // Va cu ay vao duoc that: co lien ket dat mat khau, co nhan khau, ma da chay.
            assertThat(accepted.setPasswordLink()).isPresent();
            assertThat(accepted.user().personId()).isEqualTo(baLan);
            assertThat(trangThaiMa()).isEqualTo(InvitationStatus.ACCEPTED);
        }

        /**
         * Trạng thái mồ côi <b>có hạn</b>: ngoài cửa sổ thì không đòi lại được nữa.
         *
         * <p>Một tài khoản mồ côi nằm đó vô hạn là một mục tiêu đứng yên — bất kỳ ai đoán đúng địa
         * chỉ thư đều đòi lại được nó, mãi mãi. Cửa sổ bằng đúng hạn của liên kết đặt mật khẩu: một
         * lần bấm lại thật xảy ra sau vài giây, không phải sau vài ngày.</p>
         */
        @Test
        @DisplayName("tài khoản mồ côi QUÁ HẠN thì hết đòi lại được — mục tiêu đứng yên thì không")
        void taiKhoanMoCoiQuaHanThiHetDoiLaiDuoc() {
            String code = truongChiPhatMa(baLan);
            clan.appUsers.failNextSaveWith(new IllegalStateException("mat ket noi CSDL"));
            assertThatThrownBy(() -> invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH)))
                    .isInstanceOf(IllegalStateException.class);

            clan.clock.tien(java.time.Duration.ofHours(2));

            assertThatThrownBy(() -> invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH)))
                    .isInstanceOf(DomainException.class)
                    .extracting("code")
                    .isEqualTo(MembershipProblemCodes.IDENTITY_ALREADY_REGISTERED);
            assertThat(trangThaiMa()).isEqualTo(InvitationStatus.PENDING);
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

        /**
         * "Tìm trước, tạo sau" <b>vẫn là bất biến</b> — chỉ khác là lượt nhận dừng lại ở đó.
         *
         * <p>Bản trước của ca này tên {@code daCoTaiKhoanThiDungLai} và khẳng định lượt nhận
         * <i>dùng lại</i> tài khoản cũ rồi ghép nó vào nhân khẩu. Phần "không đẻ bản sao" giữ
         * nguyên giá trị và vẫn được kiểm ở đây: không có nó thì realm từ chối vì
         * {@code duplicateEmailsAllowed: false} và người dùng nhận một lỗi 500 ở đúng giây đầu
         * tiên họ dùng hệ thống. Phần "rồi ghép nó vào nhân khẩu" là phần bị lật: người gọi chưa
         * chứng minh được mình sở hữu địa chỉ ấy.</p>
         */
        @Test
        @DisplayName("người đã có tài khoản Keycloak thì lượt nhận DỪNG — và vẫn không đẻ bản sao")
        void daCoTaiKhoanThiTuChoiNhungKhongDeBanSao() {
            IdentityAccount cu = clan.identityProvider.seed(EMAIL_BA_LAN, true);
            String code = truongChiPhatMa(baLan);

            assertThatThrownBy(() -> invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH)))
                    .isInstanceOf(DomainException.class)
                    .extracting("code")
                    .isEqualTo(MembershipProblemCodes.IDENTITY_ALREADY_REGISTERED);

            // Tim truoc, tao sau: khong co tai khoan thu hai nao cho cung mot dia chi.
            assertThat(clan.identityProvider.created()).isEmpty();
            assertThat(clan.identityProvider.size()).isEqualTo(1);
            // Va KHONG dong app_user nao duoc dung len cho tai khoan cu ay.
            assertThat(clan.appUsers.byKeycloakSub(cu.subject())).isEmpty();
            assertThat(clan.appUsers.byPersonId(baLan)).isEmpty();
            assertThat(trangThaiMa()).isEqualTo(InvitationStatus.PENDING);
        }

        /**
         * Phép chặn cũ ({@code hasPassword()}) <b>vẫn đúng, chỉ là không còn đủ</b>.
         *
         * <p>Bản trước để lượt nhận đi trọn vẹn và chỉ khẳng định {@code setPasswordLink()} rỗng,
         * với lý do: "phát liên kết cho một tài khoản đã có mật khẩu là mở một lối đổi mật khẩu cho
         * bất kỳ ai cầm mã mời và đoán đúng email của một thành viên cũ". Câu ấy vẫn đúng nguyên
         * văn. Cái nó bỏ sót là <b>tài khoản không có mật khẩu</b> — tài khoản Google — và là lượt
         * ghi ở bước (4), vốn vẫn chạm vào dòng {@code app_user} của người khác. Nay cả lượt nhận
         * dừng sớm hơn, nên hai lỗ ấy đóng bằng một chốt.</p>
         */
        @Test
        @DisplayName("tài khoản ĐÃ CÓ mật khẩu: chặn sớm hơn — không liên kết, không chạm app_user")
        void daCoMatKhauThiTuChoiTruocKhiGhi() {
            clan.identityProvider.seed(EMAIL_BA_LAN, true);
            String code = truongChiPhatMa(baLan);
            int soLanGhiTruoc = clan.appUsers.saveCount();

            assertThatThrownBy(() -> invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH)))
                    .isInstanceOf(DomainException.class);

            assertThat(clan.identityProvider.issuedTokenCount()).isZero();
            assertThat(clan.appUsers.saveCount()).isEqualTo(soLanGhiTruoc);
        }

        /**
         * <h2>Ca này TỪNG khẳng định điều ngược lại — và đó là chủ ý, cho tới khi bị lật</h2>
         * Bản trước của chính bài kiểm này tên là
         * {@code taiKhoanChuaCoMatKhauThiVanPhatLienKet} và khẳng định: <i>"tài khoản cũ chưa có
         * mật khẩu (chỉ đăng nhập Google) thì <b>vẫn</b> phát liên kết"</i>.
         *
         * <p><b>Lập luận cũ, và vì sao nó từng hợp lý.</b> Người được mời là người đã được Trưởng
         * chi chọn đích danh; mã mời là bí mật một lần, có hạn, gửi riêng cho họ. Một cụ từng bấm
         * "Đăng nhập bằng Google" một lần rồi quên bẵng thì vẫn nên nhận được phiếu mời giấy và
         * vào được hệ thống — chặn họ lại là chặn đúng nhóm người mà cả luồng mời sinh ra để phục
         * vụ. Phép chặn duy nhất khi ấy là {@code hasPassword()}: đã có mật khẩu thì đăng nhập như
         * bình thường.</p>
         *
         * <p><b>Vì sao nó bị lật.</b> {@code hasPassword()} không trả lời câu hỏi cần trả lời.
         * Câu hỏi là "người đang gõ có sở hữu địa chỉ thư này không", và <b>chuỗi người gọi tự
         * khai không chứng minh được điều đó</b>. Một tài khoản Keycloak dựng qua đăng nhập
         * Google/Zalo <i>không có</i> credential mật khẩu, nên nó rơi đúng vào nhánh "chưa có mật
         * khẩu" — và một liên kết đặt mật khẩu phát cho nó là <b>trao một cách đăng nhập mới</b>
         * vào một danh tính có chủ. Trạng thái ấy cũng là trạng thái mà chính luồng đăng ký bằng
         * mã dòng họ để lại cho mọi người chưa bấm vào liên kết. Người cầm mã mời cá nhân vì thế
         * chọn được <i>ai</i> bị chiếm, chỉ bằng cách gõ địa chỉ thư của người đó vào ô email.</p>
         *
         * <p>Chủ dự án đã lật quyết định này; đường mời cá nhân nay xử y hệt đường mã dòng họ. Cái
         * giá — cụ đăng nhập Google rồi quên — <b>nằm trong câu lỗi</b>, không nằm trong tài
         * liệu: xem {@code InvitationService#acceptAsNewAccount}.</p>
         */
        @Test
        @DisplayName("tài khoản cũ CHƯA có mật khẩu (đăng nhập Google) thì KHÔNG còn được phát"
                + " liên kết — quyết định cũ đã bị lật")
        void taiKhoanChuaCoMatKhauNayBiTuChoi() {
            // MENH DE 3. Tai khoan nay KHONG co dong app_user nao — co y: neu no co, thi dieu kien
            // "chua ai nhan" se chan truoc va dieu kien dang duoc kiem o day (khong treo
            // UPDATE_PASSWORD, vi Google khong bao gio dat dau ay) se khong duoc kiem boi ai ca.
            IdentityAccount cu = clan.identityProvider.seed(EMAIL_BA_LAN, false);
            String code = truongChiPhatMa(baLan);

            assertThatThrownBy(() -> invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH)))
                    .isInstanceOf(DomainException.class)
                    .extracting("code")
                    .isEqualTo(MembershipProblemCodes.IDENTITY_ALREADY_REGISTERED);

            // KHONG mot lien ket nao duoc duc ra — ke ca mot lien ket roi bi nem di cung la mot
            // lien ket da ton tai o Keycloak.
            assertThat(clan.identityProvider.issuedTokenCount()).isZero();
            assertThat(clan.identityProvider.bySubject(cu.subject()).orElseThrow().hasPassword())
                    .isFalse();
            // Va ma moi VAN CON DUNG DUOC: cu ay dang nhap roi bam lai la vao duoc.
            assertThat(trangThaiMa()).isEqualTo(InvitationStatus.PENDING);
        }

        /**
         * <b>MỆNH ĐỀ 2 — CA TẤN CÔNG GỐC. Nếu ca này xanh nhầm thì cả đợt vá vô nghĩa.</b>
         *
         * <p>Nạn nhân ở đây <b>còn treo {@code UPDATE_PASSWORD}</b> — đúng trạng thái mà
         * {@code invitations/accept} để lại cho mọi người được mời <i>chưa bấm vào liên kết</i> —
         * nên điều kiện thứ nhất của {@code IdentityReclaimPolicy} <b>đúng</b>. Thứ chặn lại là
         * điều kiện thứ hai: đã có dòng {@code app_user}, tức đã có người nhận tài khoản ấy về
         * mình, và ở đây nó còn gắn với một nhân khẩu trong phả.</p>
         *
         * <p>Ca này cố ý <b>không</b> dùng lại hình dạng "tài khoản Google" của
         * {@link #taiKhoanChuaCoMatKhauNayBiTuChoi}: nếu cả hai ca đều bị chặn bởi <i>cùng</i> một
         * điều kiện thì điều kiện còn lại không được kiểm bởi ca nào cả, và một lần sửa vô ý gỡ nó
         * đi sẽ đi qua bộ test êm ru.</p>
         */
        @Test
        @DisplayName("nạn nhân ĐƯỢC MỜI nhưng chưa bấm liên kết: vẫn bị từ chối — ca tấn công gốc")
        void nanNhanDuocMoiChuaBamLienKetVanBiTuChoi() {
            IdentityAccount nanNhan = clan.identityProvider.seedOrphan(EMAIL_BA_LAN,
                    clan.clock.instant());
            // Da co app_user GAN voi mot nhan khau — nguoi nay la mot thanh vien that trong pha.
            clan.appUsers.seed(new AppUser(UUID.randomUUID(), nanNhan.subject(), baLan,
                    EMAIL_BA_LAN, "Nguyễn Thị Lan", AppUserStatus.ACTIVE, "vi", null, 0L));
            UUID nhanKhauKhac = clan.invitees.living(clan.personIn(clan.chiGiapId),
                    "Nguyễn Văn Khác", 7);
            String code = truongChiPhatMa(nhanKhauKhac);

            assertThatThrownBy(() -> invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH)))
                    .isInstanceOf(DomainException.class)
                    .extracting("code")
                    .isEqualTo(MembershipProblemCodes.IDENTITY_ALREADY_REGISTERED);

            assertThat(clan.identityProvider.issuedTokenCount()).isZero();
            assertThat(clan.identityProvider.bySubject(nanNhan.subject()).orElseThrow()
                    .hasPassword()).isFalse();
            // Ho so cua ba Lan khong bi dung toi, va ba van gan dung nhan khau cua minh.
            AppUser sauKhiThu = clan.appUsers.byKeycloakSub(nanNhan.subject()).orElseThrow();
            assertThat(sauKhiThu.displayName()).isEqualTo("Nguyễn Thị Lan");
            assertThat(sauKhiThu.personId()).isEqualTo(baLan);
        }

        /**
         * <b>MỆNH ĐỀ 4 — đăng ký bằng mã dòng họ xong, đang chờ duyệt: vẫn bị từ chối.</b>
         *
         * <p>Tài khoản ấy cũng còn treo {@code UPDATE_PASSWORD} (chủ nó chưa bấm vào liên kết),
         * nhưng nó <b>đã có dòng {@code app_user}</b> ở trạng thái {@code PENDING} — đã có người
         * nhận nó về mình, dù chưa ai ghép họ vào phả. "Chưa gắn nhân khẩu" không có nghĩa là
         * "chưa có chủ".</p>
         */
        @Test
        @DisplayName("tài khoản đăng ký bằng mã dòng họ, đang chờ duyệt: vẫn bị từ chối")
        void taiKhoanDangKyBangMaDongHoDangChoVanBiTuChoi() {
            IdentityAccount daDangKy = clan.identityProvider.seedOrphan(EMAIL_BA_LAN,
                    clan.clock.instant());
            clan.appUsers.seed(AppUser.register(UUID.randomUUID(), daDangKy.subject(),
                    EMAIL_BA_LAN, "Người vừa đăng ký"));
            String code = truongChiPhatMa(baLan);

            assertThatThrownBy(() -> invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH)))
                    .isInstanceOf(DomainException.class)
                    .extracting("code")
                    .isEqualTo(MembershipProblemCodes.IDENTITY_ALREADY_REGISTERED);
            assertThat(clan.identityProvider.issuedTokenCount()).isZero();
        }

        /**
         * Cái giá của quyết định vừa lật phải <b>nằm trong câu lỗi</b>.
         *
         * <p>Người bị thiệt là một cụ từng đăng nhập bằng Google và <b>không nhớ</b> điều đó. Một
         * câu "không nhận được lời mời" trống rỗng sẽ đẩy cụ ấy đi gọi Trưởng chi. Thông điệp phải
         * <i>gợi</i> đúng nguyên nhân hay gặp mà <b>không xác nhận</b> tài khoản ấy có thật —
         * cùng kỷ luật chống dò tài khoản của lối mã dòng họ.</p>
         */
        @Test
        @DisplayName("câu lỗi gợi đúng nguyên nhân hay gặp mà không xác nhận tài khoản có thật")
        void cauLoiChiDuongChoCuDaDangNhapGoogle() {
            clan.identityProvider.seed(EMAIL_BA_LAN, false);
            String code = truongChiPhatMa(baLan);

            assertThatThrownBy(() -> invitations.accept(
                    AcceptInvitationCommand.byEmail(code, EMAIL_BA_LAN, null, IP_KHACH)))
                    .isInstanceOf(DomainException.class)
                    // Goi nguyen nhan hay gap...
                    .hasMessageContaining("Google")
                    // ...va noi loi di tiep, khong bo nguoi dung giua duong.
                    .hasMessageContaining("dang nhap")
                    // ...nhung KHONG xac nhan dut khoat rang dia chi nay da co tai khoan.
                    .hasMessageContaining("co the");
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
