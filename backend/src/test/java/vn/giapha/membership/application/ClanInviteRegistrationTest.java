package vn.giapha.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.membership.application.command.IssueClanInviteCommand;
import vn.giapha.membership.application.command.RegisterWithClanInviteCommand;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.AppUserStatus;
import vn.giapha.membership.domain.port.IdentityAccount;
import vn.giapha.membership.support.ClanFixture;
import vn.giapha.membership.support.TestSecurity;
import vn.giapha.shared.exception.DomainException;

/**
 * Đăng ký bằng <b>mã mời dòng họ</b> — endpoint công khai duy nhất lập được tài khoản.
 *
 * <h2>Vì sao lớp test này tồn tại bên cạnh {@code ClanInviteFlowIT}</h2>
 * Môi trường tích hợp <b>không cấu hình cổng danh tính</b>: mọi lượt đăng ký không token dừng ở
 * {@code 503} trước khi chạm tới Keycloak. Vì vậy ca nguy hiểm nhất của luồng này — "người đăng ký
 * khai <i>email của người khác</i>, và người ấy đã có sẵn một tài khoản Keycloak chưa đặt mật
 * khẩu" — không quan sát được ở đó. Ở đây realm là {@code FakeIdentityProvider}, nên dựng được
 * đúng trạng thái ấy.
 *
 * <h2>Trạng thái "đã tồn tại nhưng chưa có mật khẩu" KHÔNG hiếm</h2>
 * Nó là trạng thái mà {@code InvitationService.accept} để lại cho <b>mọi</b> người được mời chưa
 * bấm vào liên kết — và những tài khoản ấy đã gắn một nhân khẩu trong phả. Mã dòng họ thì cả họ
 * cầm (thiết kế nói thẳng: dán vào nhóm Zalo). Nên kẻ tấn công chỉ cần một địa chỉ thư.
 */
@DisplayName("Đăng ký bằng mã mời dòng họ")
class ClanInviteRegistrationTest {

    private static final String IP_KHACH = "203.0.113.77";
    private static final String EMAIL_BA_LAN = "ba.lan@example.com";

    private ClanFixture clan;
    private ClanInviteService clanInvites;

    @BeforeEach
    void dungDongHo() {
        clan = new ClanFixture();
        clanInvites = clan.clanInviteService();
        AppUser hoiDong = clan.account("sub-hoi-dong", clan.gocId);
        clan.councilWide(hoiDong);
    }

    @AfterEach
    void dangXuat() {
        TestSecurity.logout();
    }

    private String hoiDongPhatMa() {
        TestSecurity.loginAs("sub-hoi-dong", "COUNCIL");
        String code = clanInvites.issue(new IssueClanInviteCommand("Nhom Zalo ho Nguyen")).code();
        TestSecurity.logout();
        return code;
    }

    private ClanRegistration dangKyKhongToken(String code, String loginId, String tenTuKhai) {
        return clanInvites.register(
                new RegisterWithClanInviteCommand(code, loginId, tenTuKhai, IP_KHACH));
    }

    // =====================================================================================
    @Nested
    @DisplayName("Người hoàn toàn mới")
    class NguoiMoi {

        @Test
        @DisplayName("lập tài khoản, phát liên kết đặt mật khẩu, trạng thái PENDING")
        void lapTaiKhoanMoi() {
            ClanRegistration registration =
                    dangKyKhongToken(hoiDongPhatMa(), "nguoi.moi@example.com", "Nguoi Moi");

            assertThat(registration.setPasswordLink()).isPresent();
            assertThat(registration.user().status()).isEqualTo(AppUserStatus.PENDING);
            assertThat(registration.user().personId()).isNull();
            assertThat(clan.identityProvider.created()).hasSize(1);
        }
    }

    // =====================================================================================
    @Nested
    @DisplayName("Định danh đã có chủ — lối chiếm tài khoản")
    class DinhDanhDaCoChu {

        /** Bà Lan: đã được mời, tài khoản Keycloak đã lập, <b>chưa</b> bấm vào liên kết. */
        private UUID nhanKhauBaLan;
        private AppUser taiKhoanBaLan;
        private IdentityAccount keycloakBaLan;

        @BeforeEach
        void baLanDuocMoiNhungChuaDatMatKhau() {
            // seedOrphan, KHONG phai seed(..., false): ba Lan da duoc moi va tai khoan cua ba con
            // treo UPDATE_PASSWORD. Nho vay dieu kien THU NHAT cua IdentityReclaimPolicy DUNG, va
            // thu that su chan lai la dieu kien thu hai — "da co dong app_user". Neu dung mot tai
            // khoan kieu Google o day thi ca hai dieu kien cung chan, va dieu kien thu hai se
            // khong duoc bai kiem nao canh.
            keycloakBaLan = clan.identityProvider.seedOrphan(EMAIL_BA_LAN, clan.clock.instant());
            nhanKhauBaLan = clan.personIn(clan.chiGiapId);
            taiKhoanBaLan = clan.appUsers.seed(new AppUser(UUID.randomUUID(),
                    keycloakBaLan.subject(), nhanKhauBaLan, EMAIL_BA_LAN, "Nguyễn Thị Lan",
                    AppUserStatus.ACTIVE, "vi", null, 0L));
        }

        @Test
        @DisplayName("KHÔNG phát liên kết đặt mật khẩu cho tài khoản của người khác")
        void khongPhatLienKetChoTaiKhoanCuaNguoiKhac() {
            String code = hoiDongPhatMa();

            // Ke tan cong: khong token, chi co ma dong ho va dia chi thu cua ba Lan.
            assertThatThrownBy(() -> dangKyKhongToken(code, EMAIL_BA_LAN, "Ke Gia Mao"))
                    .isInstanceOf(DomainException.class)
                    .extracting("code")
                    .isEqualTo(MembershipProblemCodes.IDENTITY_ALREADY_REGISTERED);

            // Khong mot lien ket nao duoc duc ra: ba Lan van la nguoi duy nhat dat duoc mat khau.
            assertThat(clan.identityProvider.issuedTokenCount()).isZero();
            assertThat(clan.identityProvider.bySubject(keycloakBaLan.subject()).orElseThrow()
                    .hasPassword()).isFalse();
        }

        @Test
        @DisplayName("KHÔNG ghi đè tên hiển thị và địa chỉ thư của người khác")
        void khongGhiDeHoSoCuaNguoiKhac() {
            String code = hoiDongPhatMa();

            assertThatThrownBy(() -> dangKyKhongToken(code, EMAIL_BA_LAN, "Ke Gia Mao"))
                    .isInstanceOf(DomainException.class);

            // Ten hien thi nay la thu Truong chi doc trong hang cho duyet don — sua duoc no la sua
            // dung thu ma nguoi duyet dung de nhan ra nguoi that.
            AppUser sauKhiThu = clan.appUsers.byId(taiKhoanBaLan.id()).orElseThrow();
            assertThat(sauKhiThu.displayName()).isEqualTo("Nguyễn Thị Lan");
            assertThat(sauKhiThu.email()).isEqualTo(EMAIL_BA_LAN);
            assertThat(sauKhiThu.personId()).isEqualTo(nhanKhauBaLan);
        }

        @Test
        @DisplayName("không tiêu một lượt nào của mã, và KHÔNG ghi vết dùng mã")
        void khongTieuLuotNao() {
            String code = hoiDongPhatMa();
            UUID codeId = clan.clanInvites.codeRepository.all(10, 0).get(0).id();

            assertThatThrownBy(() -> dangKyKhongToken(code, EMAIL_BA_LAN, "Ke Gia Mao"))
                    .isInstanceOf(DomainException.class);

            assertThat(clan.clanInvites.useCountOf(codeId)).isZero();
            assertThat(clan.clanInvites.redemptionsOf(codeId)).isEmpty();
        }

        /**
         * <b>MỆNH ĐỀ 4</b> — người đã đăng ký bằng mã dòng họ nhưng <i>chưa bấm vào liên kết</i>.
         *
         * <p>Tài khoản ấy còn treo {@code UPDATE_PASSWORD} y như một tài khoản mồ côi, nhưng đã có
         * dòng {@code app_user} ở trạng thái chờ. "Chưa gắn nhân khẩu" <b>không</b> có nghĩa là
         * "chưa có chủ".</p>
         */
        @Test
        @DisplayName("tài khoản CHƯA gắn nhân khẩu cũng được che — nó cũng có chủ")
        void taiKhoanChuaGanNhanKhauCungDuocChe() {
            IdentityAccount cu = clan.identityProvider.seedOrphan("ong.hai@example.com",
                    clan.clock.instant());
            clan.appUsers.seed(AppUser.register(UUID.randomUUID(), cu.subject(),
                    "ong.hai@example.com", "Nguyễn Văn Hai"));
            String code = hoiDongPhatMa();

            assertThatThrownBy(() -> dangKyKhongToken(code, "ong.hai@example.com", "Ke Gia Mao"))
                    .isInstanceOf(DomainException.class)
                    .extracting("code")
                    .isEqualTo(MembershipProblemCodes.IDENTITY_ALREADY_REGISTERED);
            assertThat(clan.identityProvider.issuedTokenCount()).isZero();
        }

        @Test
        @DisplayName("lối dò tài khoản bị TÍNH VÀO giới hạn tần suất")
        void loiDoTaiKhoanBiTinhVaoGioiHanTanSuat() {
            ClanInviteService gioiHanChat = clan.clanInviteService(2);
            String code = hoiDongPhatMa();

            for (int lan = 0; lan < 2; lan++) {
                assertThatThrownBy(() -> gioiHanChat.register(new RegisterWithClanInviteCommand(
                        code, EMAIL_BA_LAN, "Ke Gia Mao", IP_KHACH)))
                        .isInstanceOf(DomainException.class);
            }

            // Lan thu ba: chan truoc khi tra ma. Khong dem thi day la mot may do tai khoan khong
            // gioi han — hoi mot lan mot dia chi thu, doc cau tra loi.
            assertThatThrownBy(() -> gioiHanChat.register(new RegisterWithClanInviteCommand(
                    code, EMAIL_BA_LAN, "Ke Gia Mao", IP_KHACH)))
                    .isInstanceOf(TooManyAttemptsException.class);
        }
    }

    // =====================================================================================
    @Nested
    @DisplayName("Tài khoản mồ côi — bấm lại được, nhưng có hạn")
    class TaiKhoanMoCoi {

        /**
         * <b>MỆNH ĐỀ 1</b> — hỏng ở bước ghi cơ sở dữ liệu thì người dùng bấm lại được.
         *
         * <p>Keycloak và Postgres là hai hệ thống không có transaction chung, và thứ tự thao tác cố
         * ý lập tài khoản <i>trước</i> khi ghi. Cái còn lại sau một lần hỏng là tài khoản Keycloak
         * <b>không ai sở hữu</b>: không mật khẩu, không dòng {@code app_user}, còn nguyên
         * {@code UPDATE_PASSWORD}. Không cứu trạng thái ấy thì người bấm lại bị chính phép chặn từ
         * chối, lần nào cũng vậy.</p>
         */
        @Test
        @DisplayName("ghi CSDL hỏng rồi bấm lại: ĐI TIẾP ĐƯỢC, và không đẻ tài khoản thứ hai")
        void bamLaiSauKhiGhiHongThiDiTiepDuoc() {
            String code = hoiDongPhatMa();
            clan.appUsers.failNextSaveWith(new IllegalStateException("mat ket noi CSDL"));
            assertThatThrownBy(() -> dangKyKhongToken(code, EMAIL_BA_LAN, "Nguyễn Thị Lan"))
                    .isInstanceOf(IllegalStateException.class);
            int sauLanHong = clan.identityProvider.size();

            ClanRegistration registration = dangKyKhongToken(code, EMAIL_BA_LAN, "Nguyễn Thị Lan");

            assertThat(registration.setPasswordLink()).isPresent();
            assertThat(registration.user().status()).isEqualTo(AppUserStatus.PENDING);
            assertThat(clan.identityProvider.size()).isEqualTo(sauLanHong);
        }

        @Test
        @DisplayName("mồ côi QUÁ HẠN thì hết đòi lại được — mục tiêu đứng yên thì không được để yên")
        void moCoiQuaHanThiHetDoiLaiDuoc() {
            String code = hoiDongPhatMa();
            clan.appUsers.failNextSaveWith(new IllegalStateException("mat ket noi CSDL"));
            assertThatThrownBy(() -> dangKyKhongToken(code, EMAIL_BA_LAN, "Nguyễn Thị Lan"))
                    .isInstanceOf(IllegalStateException.class);

            clan.clock.tien(java.time.Duration.ofHours(2));

            assertThatThrownBy(() -> dangKyKhongToken(code, EMAIL_BA_LAN, "Nguyễn Thị Lan"))
                    .isInstanceOf(DomainException.class)
                    .extracting("code")
                    .isEqualTo(MembershipProblemCodes.IDENTITY_ALREADY_REGISTERED);
        }

        /**
         * <b>MỆNH ĐỀ 3</b> — tài khoản chỉ đăng nhập Google, <b>không</b> có dòng {@code app_user}.
         *
         * <p>Cố ý không gieo {@code app_user}: nếu có, điều kiện "chưa ai nhận" sẽ chặn trước và
         * điều kiện đang được kiểm ở đây — không treo {@code UPDATE_PASSWORD}, vì Google không bao
         * giờ đặt dấu ấy — sẽ không được bài kiểm nào canh.</p>
         */
        @Test
        @DisplayName("tài khoản Google chưa từng dùng ứng dụng: vẫn bị từ chối")
        void taiKhoanGoogleChuaTungDungUngDungVanBiTuChoi() {
            IdentityAccount google = clan.identityProvider.seed("ong.google@example.com", false);
            assertThat(clan.appUsers.byKeycloakSub(google.subject())).isEmpty();
            String code = hoiDongPhatMa();

            assertThatThrownBy(() ->
                    dangKyKhongToken(code, "ong.google@example.com", "Ke Gia Mao"))
                    .isInstanceOf(DomainException.class)
                    .extracting("code")
                    .isEqualTo(MembershipProblemCodes.IDENTITY_ALREADY_REGISTERED);

            assertThat(clan.identityProvider.issuedTokenCount()).isZero();
            assertThat(clan.identityProvider.bySubject(google.subject()).orElseThrow()
                    .hasPassword()).isFalse();
        }

        /**
         * Đặt mật khẩu xong thì <b>thôi mồ côi</b> — realm gỡ {@code UPDATE_PASSWORD}.
         *
         * <p>Đây là chốt giữ cho cửa sổ mồ côi không biến thành một lối vào vĩnh viễn: ngay khi chủ
         * tài khoản dùng liên kết, dấu vết "chưa ai nhận" biến mất, kể cả khi vẫn còn trong cửa sổ
         * thời gian.</p>
         */
        @Test
        @DisplayName("đặt mật khẩu xong thì thôi mồ côi ngay, dù vẫn còn trong cửa sổ")
        void datMatKhauXongThiThoiMoCoi() {
            String code = hoiDongPhatMa();
            clan.appUsers.failNextSaveWith(new IllegalStateException("mat ket noi CSDL"));
            assertThatThrownBy(() -> dangKyKhongToken(code, EMAIL_BA_LAN, "Nguyễn Thị Lan"))
                    .isInstanceOf(IllegalStateException.class);

            // Chu tai khoan dung lien ket cua lan truoc: realm go UPDATE_PASSWORD va ghi mat khau.
            IdentityAccount moCoi =
                    clan.identityProvider.findByEmail(EMAIL_BA_LAN).orElseThrow();
            clan.identityProvider.completeSetPassword(
                    clan.identityProvider.issueSetPasswordLink(moCoi).orElseThrow().url()
                            .replaceAll(".*token=", ""),
                    "mat-khau-moi");

            assertThatThrownBy(() -> dangKyKhongToken(code, EMAIL_BA_LAN, "Ke Gia Mao"))
                    .isInstanceOf(DomainException.class)
                    .extracting("code")
                    .isEqualTo(MembershipProblemCodes.IDENTITY_ALREADY_REGISTERED);
        }
    }

    // =====================================================================================
    @Nested
    @DisplayName("Người đã đăng nhập")
    class NguoiDaDangNhap {

        @Test
        @DisplayName("có token thì dùng danh tính của token, không hỏi tới email tự khai")
        void coTokenThiDungDanhTinhCuaToken() {
            String code = hoiDongPhatMa();
            TestSecurity.loginAs("sub-google", "MEMBER");

            ClanRegistration registration = clanInvites.register(new RegisterWithClanInviteCommand(
                    code, EMAIL_BA_LAN, "Ai Do", IP_KHACH));

            assertThat(registration.user().keycloakSub()).isEqualTo("sub-google");
            assertThat(registration.setPasswordLink()).isEmpty();
            assertThat(clan.identityProvider.size()).isZero();
        }
    }
}
