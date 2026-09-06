package vn.giapha.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.AppUserStatus;
import vn.giapha.membership.support.ClanFixture;
import vn.giapha.membership.support.TestSecurity;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Tự khởi tạo tài khoản ở lần gọi đầu và ghép tài khoản vào cây phả hệ.
 *
 * <p>Chuỗi {@code keycloak_sub → app_user → person}: backend không quản lý mật khẩu, nó chỉ giữ
 * mắt xích mà Keycloak không biết.</p>
 */
class AppUserProvisioningServiceTest {

    private final ClanFixture clan = new ClanFixture();
    private final AppUserProvisioningService service = new AppUserProvisioningService(
            clan.appUsers, clan.guard, clan.scopes, clan.auditTrail);

    @AfterEach
    void donSecurityContext() {
        TestSecurity.logout();
    }

    @Nested
    @DisplayName("Tự khởi tạo ở lần đăng nhập đầu")
    class TuKhoiTao {

        @Test
        @DisplayName("Lần gọi đầu tạo dòng app_user ở trạng thái PENDING")
        void lanGoiDauTaoTaiKhoan() {
            // Khong co buoc dang ky rieng: ai dang nhap duoc qua Keycloak thi lan goi API dau tien
            // se tao ra dong app_user. Nhung PENDING chua gan voi nhan khau nao nen chua thay duoc
            // gi ngoai du lieu cong khai — viec ghep vao cay la quyet dinh cua Hoi dong Toc bieu.
            TestSecurity.loginAsWithProfile("sub-lan-dau", "nguyenvana", "a@example.test", "MEMBER");

            Optional<AppUser> created = service.ensureCurrentUser();

            assertThat(created).isPresent();
            assertThat(created.get().keycloakSub()).isEqualTo("sub-lan-dau");
            assertThat(created.get().status()).isEqualTo(AppUserStatus.PENDING);
            assertThat(created.get().personId()).isNull();
            assertThat(created.get().lastLoginAt()).isNotNull();
        }

        @Test
        @DisplayName("Lần gọi thứ hai KHÔNG tạo thêm tài khoản, chỉ làm mới hồ sơ")
        void lanGoiSauChiLamMoi() {
            TestSecurity.loginAsWithProfile("sub-lan-dau", "ten-cu", "cu@example.test", "MEMBER");
            AppUser lanDau = service.ensureCurrentUser().orElseThrow();

            TestSecurity.loginAsWithProfile("sub-lan-dau", "ten-moi", "moi@example.test", "MEMBER");
            AppUser lanHai = service.ensureCurrentUser().orElseThrow();

            assertThat(lanHai.id()).isEqualTo(lanDau.id());
            assertThat(lanHai.displayName()).isEqualTo("ten-moi");
            assertThat(lanHai.email()).isEqualTo("moi@example.test");
        }

        @Test
        @DisplayName("Khách vãng lai: không có gì để tạo")
        void khachKhongTao() {
            TestSecurity.logout();

            assertThat(service.ensureCurrentUser()).isEmpty();
            assertThat(clan.appUsers.saveCount()).isZero();
        }

        @Test
        @DisplayName("Hai request song song cùng tạo một tài khoản: đọc lại bản kia vừa ghi")
        void duaTranhLanDangNhapDau() {
            // PWA hay ban nhieu request song song ngay khi mo app. ux_app_user_keycloak_sub chan o
            // CSDL; o day bat lai roi doc lai — chu khong de nguoi dung nhan mot loi 500 o dung
            // giay dau tien ho dung he thong.
            AppUser luongKia = AppUser.register(UUID.randomUUID(), "sub-dua-tranh", null, null);
            clan.appUsers.simulateRaceWith(luongKia);
            TestSecurity.loginAs("sub-dua-tranh", "MEMBER");

            AppUser ketQua = service.ensureCurrentUser().orElseThrow();

            assertThat(ketQua.id()).isEqualTo(luongKia.id());
        }

        @Test
        @DisplayName("Lỗi khác lúc ghi thì để lan lên, không nuốt")
        void loiKhacKhongNuot() {
            clan.appUsers.failNextSaveWith(new IllegalStateException("mat ket noi"));
            TestSecurity.loginAs("sub-loi", "MEMBER");

            assertThatThrownBy(service::ensureCurrentUser)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("requireCurrentUser ném ACCOUNT_NOT_PROVISIONED khi chưa có tài khoản")
        void chuaCoTaiKhoan() {
            TestSecurity.loginAs("sub-chua-tao", "MEMBER");

            assertThatThrownBy(service::requireCurrentUser)
                    .isInstanceOf(NotFoundException.class)
                    .extracting(ex -> ((NotFoundException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.ACCOUNT_NOT_PROVISIONED);
        }
    }

    @Nested
    @DisplayName("Ghép tài khoản với nhân khẩu")
    class GhepVaoCay {

        @Test
        @DisplayName("Hội đồng Tộc biểu ghép được; tài khoản chuyển sang ACTIVE")
        void hoiDongGhepDuoc() {
            AppUser hoiDong = clan.account("sub-hoi-dong", clan.gocId);
            clan.councilWide(hoiDong);
            TestSecurity.loginAs("sub-hoi-dong", "COUNCIL");
            AppUser moi = clan.unlinkedAccount("sub-moi");
            UUID nhanKhau = clan.personIn(clan.chiGiapId);

            AppUser sau = service.linkToPerson(moi.id(), nhanKhau);

            assertThat(sau.personId()).isEqualTo(nhanKhau);
            assertThat(sau.status()).isEqualTo(AppUserStatus.ACTIVE);
        }

        @Test
        @DisplayName("Thành viên và Trưởng chi KHÔNG ghép được")
        void vaiThapKhongGhep() {
            // Ghep sai la trao cho mot nguoi quyen xem Tang 3 cua nguoi khac duoi danh nghia
            // "ho so cua minh" — thao tac nhay cam nhat cua ca context.
            AppUser moi = clan.unlinkedAccount("sub-moi");
            UUID nhanKhau = clan.personIn(clan.chiGiapId);

            AppUser truongChi = clan.account("sub-truong-chi", clan.chiGiapId);
            clan.branchHeadOf(truongChi, clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi", "BRANCH_HEAD");

            assertThatThrownBy(() -> service.linkToPerson(moi.id(), nhanKhau))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("Một nhân khẩu chỉ ghép được với một tài khoản")
        void motNhanKhauMotTaiKhoan() {
            AppUser hoiDong = clan.account("sub-hoi-dong", clan.gocId);
            clan.councilWide(hoiDong);
            TestSecurity.loginAs("sub-hoi-dong", "COUNCIL");
            AppUser daGhep = clan.account("sub-da-ghep", clan.chiGiapId);
            AppUser moi = clan.unlinkedAccount("sub-moi");

            assertThatThrownBy(() -> service.linkToPerson(moi.id(), daGhep.personId()))
                    .isInstanceOf(DomainException.class)
                    .extracting(ex -> ((DomainException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("Không tự đổi tài khoản sang nhân khẩu khác")
        void khongTuDoiNhanKhau() {
            AppUser hoiDong = clan.account("sub-hoi-dong", clan.gocId);
            clan.councilWide(hoiDong);
            TestSecurity.loginAs("sub-hoi-dong", "COUNCIL");
            AppUser daGhep = clan.account("sub-da-ghep", clan.chiGiapId);

            assertThatThrownBy(() ->
                    service.linkToPerson(daGhep.id(), clan.personIn(clan.chiAtId)))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("Tài khoản không tồn tại -> NOT_FOUND")
        void taiKhoanKhongTonTai() {
            AppUser hoiDong = clan.account("sub-hoi-dong", clan.gocId);
            clan.councilWide(hoiDong);
            TestSecurity.loginAs("sub-hoi-dong", "COUNCIL");

            assertThatThrownBy(() ->
                    service.linkToPerson(UUID.randomUUID(), clan.personIn(clan.chiGiapId)))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Khoá và mở khoá tài khoản")
    class KhoaTaiKhoan {

        @Test
        @DisplayName("Chỉ Quản trị hệ thống đổi được trạng thái tài khoản")
        void chiAdminDoiTrangThai() {
            AppUser nanNhan = clan.account("sub-nan-nhan", clan.chiGiapId);

            AppUser hoiDong = clan.account("sub-hoi-dong", clan.gocId);
            clan.councilWide(hoiDong);
            TestSecurity.loginAs("sub-hoi-dong", "COUNCIL");
            assertThatThrownBy(() -> service.changeStatus(nanNhan.id(),
                    AppUserStatus.SUSPENDED, "tranh chấp dữ liệu"))
                    .isInstanceOf(ForbiddenException.class);

            AppUser admin = clan.account("sub-admin", clan.gocId);
            clan.adminWide(admin);
            TestSecurity.loginAs("sub-admin", "ADMIN");
            assertThat(service.changeStatus(nanNhan.id(), AppUserStatus.SUSPENDED, "tranh chấp")
                    .status()).isEqualTo(AppUserStatus.SUSPENDED);
        }

        @Test
        @DisplayName("Vết audit ghi cả trạng thái trước và sau")
        void vetAuditTruocSau() {
            AppUser nanNhan = clan.account("sub-nan-nhan", clan.chiGiapId);
            AppUser admin = clan.account("sub-admin", clan.gocId);
            clan.adminWide(admin);
            TestSecurity.loginAs("sub-admin", "ADMIN");
            clan.auditLog.clear();

            service.changeStatus(nanNhan.id(), AppUserStatus.DISABLED, "Yêu cầu theo Nghị định 13");

            var entry = clan.auditLog.last().entry();
            assertThat(entry.entityType()).isEqualTo("AppUser");
            assertThat(entry.action().name()).isEqualTo("UPDATE");
            assertThat(entry.before()).containsEntry("status", "ACTIVE");
            assertThat(entry.after()).containsEntry("status", "DISABLED");
            assertThat(entry.changedFields()).containsExactly("status");
        }
    }

    @Test
    @DisplayName("Ảnh chụp audit KHÔNG chứa email — Tầng 3 không lọt vào bảng chỉ ghi thêm")
    void anhChupAuditKhongCoEmail() {
        // app_user.email duoc V5 chu thich ro la du lieu Tang 3; audit_log co trigger chan UPDATE
        // nen mot dia chi lot vao do la lot vinh vien.
        TestSecurity.loginAsWithProfile("sub-moi", "nguyenvana", "rieng-tu@example.test", "MEMBER");

        service.ensureCurrentUser();

        var entry = clan.auditLog.last().entry();
        assertThat(entry.action().name()).isEqualTo("CREATE");
        assertThat(entry.after()).doesNotContainKey("email");
        assertThat(String.valueOf(entry.after())).doesNotContain("rieng-tu@example.test");
    }
}
