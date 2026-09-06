package vn.giapha.membership.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.MemberScope;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.membership.support.ClanFixture;
import vn.giapha.membership.support.TestSecurity;

/**
 * Mặt tiền phân quyền: {@code keycloak_sub → app_user → person} cộng phạm vi chi/ngành.
 *
 * <p>Chạy trên {@link ClanFixture} — dây nối thật, chỉ kho dữ liệu và {@code SecurityContext} là
 * giả.</p>
 */
class MemberScopeServiceTest {

    private final ClanFixture clan = new ClanFixture();
    private final MemberScopeService scopes = clan.scopes;

    @AfterEach
    void donSecurityContext() {
        TestSecurity.logout();
    }

    @Nested
    @DisplayName("Ánh xạ keycloak_sub -> app_user -> person")
    class AnhXaDanhTinh {

        @Test
        @DisplayName("Token phân giải ra đúng tài khoản và nhân khẩu")
        void phanGiaiDayDu() {
            AppUser user = clan.account("sub-nguyenvana", clan.chiGiapId);
            TestSecurity.loginAs("sub-nguyenvana", "MEMBER");

            MemberScopeView scope = scopes.currentScope();

            assertThat(scope.appUserId()).isEqualTo(user.id());
            assertThat(scope.personId()).isEqualTo(user.personId());
            assertThat(scope.homeBranch()).isEqualTo(ClanFixture.CHI_GIAP);
            assertThat(scope.role()).isEqualTo("MEMBER");
        }

        @Test
        @DisplayName("Khoá nối là sub, không phải email — hai sub khác nhau là hai tài khoản")
        void khoaNoiLaSub() {
            AppUser google = clan.account("sub-google", clan.chiGiapId);
            AppUser zalo = clan.account("sub-zalo", clan.chiAtId);

            assertThat(scopes.personIdOf("sub-google")).contains(google.personId());
            assertThat(scopes.personIdOf("sub-zalo")).contains(zalo.personId());
            assertThat(scopes.personIdOf("sub-khong-ton-tai")).isEmpty();
        }

        @Test
        @DisplayName("Chiều ngược: từ nhân khẩu tra ra tài khoản")
        void chieuNguoc() {
            AppUser user = clan.account("sub-1", clan.chiGiapId);

            assertThat(scopes.appUserIdOfPerson(user.personId())).contains(user.id());
            assertThat(scopes.appUserIdOfPerson(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("Tài khoản chưa ghép vào cây: personId và chi nhà đều trống")
        void chuaGhepVaoCay() {
            clan.unlinkedAccount("sub-moi-dang-ky");
            TestSecurity.loginAs("sub-moi-dang-ky", "MEMBER");

            MemberScopeView scope = scopes.currentScope();

            assertThat(scope.personId()).isNull();
            assertThat(scope.homeBranch()).isNull();
            // Chua co "chi nha" thi moi phep xet cung chi cua Tang 2 deu truot — dung huong an toan.
            assertThat(scope.managesBranch(ClanFixture.CHI_GIAP)).isFalse();
        }
    }

    @Nested
    @DisplayName("Người gọi không có tài khoản")
    class KhongCoTaiKhoan {

        @Test
        @DisplayName("Không token -> Khách, không phạm vi nào")
        void khongToken() {
            TestSecurity.logout();

            MemberScopeView scope = scopes.currentScope();

            assertThat(scope.isGuest()).isTrue();
            assertThat(scope.appUserId()).isNull();
            assertThat(scope.managedBranches()).isEmpty();
            assertThat(scope.clanWide()).isFalse();
        }

        @Test
        @DisplayName("Khách ẩn danh của Spring Security cũng là Khách")
        void khachAnDanh() {
            TestSecurity.loginAsAnonymous();

            assertThat(scopes.currentScope().isGuest()).isTrue();
        }

        @Test
        @DisplayName("Token hợp lệ mà chưa có app_user: giữ vai, KHÔNG có phạm vi")
        void tokenChuaCoAppUser() {
            // Token noi duoc nguoi do la ai; chi du lieu cua dong ho moi noi duoc nguoi do quan
            // nhanh nao.
            TestSecurity.loginAs("sub-chua-tao-tai-khoan", "BRANCH_HEAD");

            MemberScopeView scope = scopes.currentScope();

            assertThat(scope.role()).isEqualTo("BRANCH_HEAD");
            assertThat(scope.appUserId()).isNull();
            assertThat(scope.managedBranches()).isEmpty();
            assertThat(scope.managesBranch(ClanFixture.CHI_GIAP)).isFalse();
        }

        @Test
        @DisplayName("Token không mang vai nào vẫn là Thành viên đã đăng nhập")
        void tokenKhongMangVai() {
            clan.account("sub-khong-vai", clan.chiGiapId);
            TestSecurity.loginAs("sub-khong-vai");

            assertThat(scopes.currentScope().role()).isEqualTo("MEMBER");
        }
    }

    @Nested
    @DisplayName("Phạm vi dựng từ bảng phân công")
    class PhamViTuPhanCong {

        @Test
        @DisplayName("Trưởng Chi Giáp chỉ nhận đúng phạm vi Chi Giáp")
        void truongChiGiap() {
            AppUser user = clan.account("sub-truong-chi-giap", clan.chiGiapId);
            clan.branchHeadOf(user, clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi-giap", "BRANCH_HEAD");

            MemberScopeView scope = scopes.currentScope();

            assertThat(scope.managedBranches()).containsExactly(ClanFixture.CHI_GIAP);
            assertThat(scope.managesBranch(ClanFixture.NGANH_TRUONG)).isTrue();
            assertThat(scope.managesBranch(ClanFixture.CHI_AT)).isFalse();
            assertThat(scope.clanWide()).isFalse();
        }

        @Test
        @DisplayName("Nhiệm kỳ đã mãn KHÔNG còn sinh ra phạm vi")
        void nhiemKyDaMan() {
            // Loc hieu luc phai nam o tang truy van; neu mot nhiem ky het han van duoc nap len roi
            // moi loc thi do la mot lan nua co hoi de ai do quen loc.
            AppUser user = clan.account("sub-cuu-truong-chi", clan.chiGiapId);
            clan.expiredBranchHeadOf(user, clan.chiGiapId);
            TestSecurity.loginAs("sub-cuu-truong-chi", "BRANCH_HEAD");

            MemberScopeView scope = scopes.currentScope();

            assertThat(scope.managedBranches()).isEmpty();
            assertThat(scope.managesBranch(ClanFixture.CHI_GIAP)).isFalse();
        }

        @Test
        @DisplayName("Hội đồng Tộc biểu: phạm vi toàn dòng họ, danh sách chi vẫn rỗng")
        void hoiDongTocBieu() {
            AppUser user = clan.account("sub-hoi-dong", clan.gocId);
            clan.councilWide(user);
            TestSecurity.loginAs("sub-hoi-dong", "COUNCIL");

            MemberScopeView scope = scopes.currentScope();

            assertThat(scope.clanWide()).isTrue();
            // Danh sach chi rong o day KHONG mau thuan: pham vi toan dong ho khong gan voi chi nao.
            assertThat(scope.managedBranches()).isEmpty();
        }

        @Test
        @DisplayName("Vai COUNCIL trong token mà không có phân công thì KHÔNG toàn dòng họ")
        void councilThieuPhanCong() {
            clan.account("sub-council-gia", clan.chiGiapId);
            TestSecurity.loginAs("sub-council-gia", "COUNCIL");

            assertThat(scopes.currentScope().clanWide()).isFalse();
        }

        @Test
        @DisplayName("Vai ADMIN là ngoại lệ: toàn quyền dù bảng phân công còn trống")
        void adminKhongCanPhanCong() {
            clan.account("sub-admin", clan.gocId);
            TestSecurity.loginAs("sub-admin", "ADMIN");

            assertThat(scopes.currentScope().clanWide()).isTrue();
        }

        @Test
        @DisplayName("Phạm vi được tra lại mỗi lần gọi — thu hồi có hiệu lực ngay")
        void khongCache() {
            // Cache lai nghia la mot Truong chi vua bi bai nhiem van duyet duoc du lieu cho toi khi
            // cache het han.
            AppUser user = clan.account("sub-bai-nhiem", clan.chiGiapId);
            var phanCong = clan.branchHeadOf(user, clan.chiGiapId);
            TestSecurity.loginAs("sub-bai-nhiem", "BRANCH_HEAD");
            assertThat(scopes.currentScope().managedBranches()).containsExactly(ClanFixture.CHI_GIAP);

            clan.assignments.delete(phanCong.id());

            assertThat(scopes.currentScope().managedBranches()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Vai trò được giao thật, không lấy từ token")
    class VaiDuocGiaoThat {

        @Test
        @DisplayName("Job nền không có JWT thì đọc vai từ bảng phân công")
        void doVaiTuBangPhanCong() {
            AppUser user = clan.account("sub-truong-chi", clan.chiGiapId);
            clan.branchHeadOf(user, clan.chiGiapId);

            assertThat(scopes.highestAssignedRole(user)).isEqualTo(RoleCode.BRANCH_HEAD);
        }

        @Test
        @DisplayName("Không phân công nào thì là Thành viên")
        void khongPhanCong() {
            AppUser user = clan.account("sub-thuong-dan", clan.chiAtId);

            assertThat(scopes.highestAssignedRole(user)).isEqualTo(RoleCode.MEMBER);
        }

        @Test
        @DisplayName("Nhiều phân công thì lấy vai cao nhất")
        void layVaiCaoNhat() {
            AppUser user = clan.account("sub-kiem-nhiem", clan.gocId);
            clan.branchHeadOf(user, clan.chiGiapId);
            clan.councilWide(user);

            assertThat(scopes.highestAssignedRole(user)).isEqualTo(RoleCode.COUNCIL);
        }

        @Test
        @DisplayName("scopeOfUser dựng lại phạm vi cho màn hình quản trị")
        void phamViCuaTaiKhoanBatKy() {
            AppUser user = clan.account("sub-nguoi-khac", clan.chiAtId);
            clan.branchHeadOf(user, clan.chiAtId);

            Optional<MemberScopeView> scope = scopes.scopeOfUser(user.id());

            assertThat(scope).isPresent();
            assertThat(scope.get().role()).isEqualTo("BRANCH_HEAD");
            assertThat(scope.get().managedBranches()).containsExactly(ClanFixture.CHI_AT);
            assertThat(scopes.scopeOfUser(UUID.randomUUID())).isEmpty();
        }
    }

    @Test
    @DisplayName("Bản domain và bản phẳng nói cùng một điều")
    void haiBanDongNhat() {
        AppUser user = clan.account("sub-doi-chieu", clan.chiGiapId);
        clan.branchHeadOf(user, clan.chiGiapId);
        TestSecurity.loginAs("sub-doi-chieu", "BRANCH_HEAD");

        MemberScope domain = scopes.currentMemberScope();
        MemberScopeView view = scopes.currentScope();

        assertThat(view.appUserId()).isEqualTo(domain.appUserId());
        assertThat(view.personId()).isEqualTo(domain.personId());
        assertThat(view.role()).isEqualTo(domain.role().name());
        assertThat(view.clanWide()).isEqualTo(domain.isClanWide());
        assertThat(view.managedBranches()).isEqualTo(domain.managedBranches());
        assertThat(view.homeBranch()).isEqualTo(domain.homeBranch());
    }
}
