package vn.giapha.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.membership.application.command.GrantRoleCommand;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.BranchAssignment;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.membership.support.ClanFixture;
import vn.giapha.membership.support.TestSecurity;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Cấp và thu hồi vai trò kèm phạm vi chi/ngành.
 *
 * <p>Ba luật không được nới: {@code BRANCH_HEAD} bắt buộc kèm chi; chỉ vai kỹ thuật {@code ADMIN}
 * cấp được {@code ADMIN}; Trưởng chi không cấp quyền cho ai cả.</p>
 */
class RoleAssignmentServiceTest {

    private final ClanFixture clan = new ClanFixture();
    private final RoleAssignmentService service = new RoleAssignmentService(
            clan.assignments, clan.appUsers, clan.branches, clan.scopes, clan.guard,
            clan.auditTrail);

    @AfterEach
    void donSecurityContext() {
        TestSecurity.logout();
    }

    /** Hội đồng Tộc biểu đang đăng nhập — thẩm quyền nội dung của dòng họ. */
    private AppUser dangNhapHoiDong() {
        AppUser hoiDong = clan.account("sub-hoi-dong", clan.gocId);
        clan.councilWide(hoiDong);
        TestSecurity.loginAs("sub-hoi-dong", "COUNCIL");
        return hoiDong;
    }

    /** Quản trị hệ thống đang đăng nhập — thẩm quyền kỹ thuật. */
    private AppUser dangNhapQuanTri() {
        AppUser admin = clan.account("sub-admin", clan.gocId);
        clan.adminWide(admin);
        TestSecurity.loginAs("sub-admin", "ADMIN");
        return admin;
    }

    @Nested
    @DisplayName("Ai được cấp quyền")
    class AiDuocCapQuyen {

        @Test
        @DisplayName("Hội đồng Tộc biểu cấp được vai Trưởng chi")
        void hoiDongCapTruongChi() {
            AppUser hoiDong = dangNhapHoiDong();
            AppUser ungVien = clan.account("sub-ung-vien", clan.chiGiapId);

            BranchAssignment capMoi = service.grant(new GrantRoleCommand(ungVien.id(),
                    RoleCode.BRANCH_HEAD, clan.chiGiapId, null, null, "Nhiệm kỳ 2026-2031"));

            assertThat(capMoi.role()).isEqualTo(RoleCode.BRANCH_HEAD);
            assertThat(capMoi.branchPath()).isEqualTo(ClanFixture.CHI_GIAP);
            assertThat(capMoi.grantedBy()).isEqualTo(hoiDong.id());
        }

        @Test
        @DisplayName("Trưởng chi KHÔNG cấp quyền cho ai cả")
        void truongChiKhongCapQuyen() {
            // Neu cap duoc, nguoi quan mot nhanh se dung duoc ca mot tang quyen song song ben duoi
            // minh ma Hoi dong khong thay.
            AppUser truongChi = clan.account("sub-truong-chi", clan.chiGiapId);
            clan.branchHeadOf(truongChi, clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi", "BRANCH_HEAD");
            AppUser ungVien = clan.account("sub-ung-vien", clan.chiGiapId);

            assertThatThrownBy(() -> service.grant(new GrantRoleCommand(ungVien.id(),
                    RoleCode.BRANCH_HEAD, clan.chiGiapId, null, null, null)))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("Thành viên và Khách đều không cấp được")
        void thanhVienVaKhachKhongCap() {
            AppUser ungVien = clan.account("sub-ung-vien", clan.chiGiapId);

            clan.account("sub-thanh-vien", clan.chiGiapId);
            TestSecurity.loginAs("sub-thanh-vien", "MEMBER");
            assertThatThrownBy(() -> service.grant(new GrantRoleCommand(ungVien.id(),
                    RoleCode.BRANCH_HEAD, clan.chiGiapId, null, null, null)))
                    .isInstanceOf(ForbiddenException.class);

            TestSecurity.logout();
            assertThatThrownBy(() -> service.grant(new GrantRoleCommand(ungVien.id(),
                    RoleCode.BRANCH_HEAD, clan.chiGiapId, null, null, null)))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("Hai trục thẩm quyền: nội dung dòng họ và kỹ thuật")
    class HaiTrucThamQuyen {

        @Test
        @DisplayName("Hội đồng Tộc biểu KHÔNG tự nâng được vai Quản trị hệ thống")
        void hoiDongKhongCapAdmin() {
            // De Hoi dong tu nang quyen ky thuat la bien mot tranh chap noi bo trong ho thanh mot
            // su co an ninh.
            dangNhapHoiDong();
            AppUser ungVien = clan.account("sub-ung-vien", clan.gocId);

            assertThatThrownBy(() -> service.grant(new GrantRoleCommand(ungVien.id(),
                    RoleCode.ADMIN, null, null, null, null)))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Quan tri he thong");
        }

        @Test
        @DisplayName("Chỉ Quản trị hệ thống cấp được vai Quản trị hệ thống")
        void chiAdminCapAdmin() {
            dangNhapQuanTri();
            AppUser ungVien = clan.account("sub-ung-vien", clan.gocId);

            BranchAssignment capMoi = service.grant(new GrantRoleCommand(ungVien.id(),
                    RoleCode.ADMIN, null, null, null, null));

            assertThat(capMoi.role()).isEqualTo(RoleCode.ADMIN);
            assertThat(capMoi.coversEverything()).isTrue();
        }

        @Test
        @DisplayName("Hội đồng Tộc biểu không thu hồi được vai Quản trị hệ thống")
        void hoiDongKhongThuHoiAdmin() {
            dangNhapQuanTri();
            AppUser nanNhan = clan.account("sub-admin-khac", clan.gocId);
            BranchAssignment cua = service.grant(new GrantRoleCommand(nanNhan.id(),
                    RoleCode.ADMIN, null, null, null, null));

            dangNhapHoiDong();

            assertThatThrownBy(() -> service.revoke(cua.id(), "bãi nhiệm"))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(clan.assignments.byId(cua.id())).isPresent();
        }
    }

    @Nested
    @DisplayName("Phân công phải có nghĩa")
    class PhanCongPhaiCoNghia {

        @Test
        @DisplayName("BRANCH_HEAD thiếu chi -> INVALID_ROLE_ASSIGNMENT")
        void truongChiPhaiKemChi() {
            // Mot Truong chi khong gioi han pham vi la mot Hoi dong Toc bieu doi ten khac — va la
            // loai quyen ma khong ai ra lai duoc tu bang phan cong.
            dangNhapHoiDong();
            AppUser ungVien = clan.account("sub-ung-vien", clan.chiGiapId);

            assertThatThrownBy(() -> service.grant(new GrantRoleCommand(ungVien.id(),
                    RoleCode.BRANCH_HEAD, null, null, null, null)))
                    .isInstanceOf(DomainException.class)
                    .extracting(ex -> ((DomainException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.INVALID_ROLE_ASSIGNMENT);
        }

        @Test
        @DisplayName("Vai toàn dòng họ KHÔNG được giới hạn vào một chi")
        void vaiToanDongHoKhongGioiHan() {
            dangNhapHoiDong();
            AppUser ungVien = clan.account("sub-ung-vien", clan.chiGiapId);

            assertThatThrownBy(() -> service.grant(new GrantRoleCommand(ungVien.id(),
                    RoleCode.COUNCIL, clan.chiGiapId, null, null, null)))
                    .isInstanceOf(DomainException.class)
                    .extracting(ex -> ((DomainException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.INVALID_ROLE_ASSIGNMENT);
        }

        @Test
        @DisplayName("Vai Khách là mặc định cho người chưa đăng nhập, không cấp được")
        void khongCapVaiKhach() {
            dangNhapHoiDong();
            AppUser ungVien = clan.account("sub-ung-vien", clan.chiGiapId);

            assertThatThrownBy(() -> service.grant(new GrantRoleCommand(ungVien.id(),
                    RoleCode.GUEST, null, null, null, null)))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("Nhiệm kỳ ngược mốc bị từ chối")
        void nhiemKyNguocMoc() {
            dangNhapHoiDong();
            AppUser ungVien = clan.account("sub-ung-vien", clan.chiGiapId);
            LocalDate homNay = LocalDate.now();

            assertThatThrownBy(() -> service.grant(new GrantRoleCommand(ungVien.id(),
                    RoleCode.BRANCH_HEAD, clan.chiGiapId, homNay, homNay.minusDays(1), null)))
                    .isInstanceOf(DomainException.class)
                    .extracting(ex -> ((DomainException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("Tài khoản không tồn tại -> NOT_FOUND")
        void taiKhoanKhongTonTai() {
            dangNhapHoiDong();

            assertThatThrownBy(() -> service.grant(new GrantRoleCommand(UUID.randomUUID(),
                    RoleCode.BRANCH_HEAD, clan.chiGiapId, null, null, null)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Chi không tồn tại (hoặc đã xoá mềm) -> NOT_FOUND")
        void chiKhongTonTai() {
            dangNhapHoiDong();
            AppUser ungVien = clan.account("sub-ung-vien", clan.chiGiapId);
            clan.branches.softDelete(clan.chiAtId);

            assertThatThrownBy(() -> service.grant(new GrantRoleCommand(ungVien.id(),
                    RoleCode.BRANCH_HEAD, clan.chiAtId, null, null, null)))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Thu hồi")
    class ThuHoi {

        @Test
        @DisplayName("Thu hồi xoá dòng phân công và để lại vết REVOKE_ROLE")
        void thuHoiXoaDong() {
            // Ngoai le co chu y cua quy tac chi-xoa-mem: quy tac ay bao ve node pha he khoi dut
            // lien ket, con mot dong cap quyen thi cang bien mat som cang tot.
            dangNhapHoiDong();
            AppUser ungVien = clan.account("sub-ung-vien", clan.chiGiapId);
            BranchAssignment cua = service.grant(new GrantRoleCommand(ungVien.id(),
                    RoleCode.BRANCH_HEAD, clan.chiGiapId, null, null, null));
            clan.auditLog.clear();

            service.revoke(cua.id(), "Bãi nhiệm theo nghị quyết Hội đồng");

            assertThat(clan.assignments.byId(cua.id())).isEmpty();
            assertThat(clan.auditLog.last().entry().action().name()).isEqualTo("REVOKE_ROLE");
            assertThat(clan.auditLog.last().entry().note()).contains("Bãi nhiệm");
            // Vet phai giu lai anh chup TRUOC khi xoa: sau khi xoa thi khong con gi de tra nguoc.
            assertThat(clan.auditLog.last().entry().before())
                    .containsEntry("role", "BRANCH_HEAD");
            assertThat(clan.auditLog.last().entry().after()).isNull();
        }

        @Test
        @DisplayName("Thu hồi phân công không tồn tại -> NOT_FOUND")
        void thuHoiKhongTonTai() {
            dangNhapHoiDong();

            assertThatThrownBy(() -> service.revoke(UUID.randomUUID(), null))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Thu hồi rồi thì phạm vi mất ngay ở lần kiểm quyền kế tiếp")
        void thuHoiCoHieuLucNgay() {
            dangNhapHoiDong();
            AppUser truongChi = clan.account("sub-truong-chi", clan.chiGiapId);
            BranchAssignment cua = service.grant(new GrantRoleCommand(truongChi.id(),
                    RoleCode.BRANCH_HEAD, clan.chiGiapId, null, null, null));

            TestSecurity.loginAs("sub-truong-chi", "BRANCH_HEAD");
            assertThat(clan.scopes.currentScope().managesBranch(ClanFixture.CHI_GIAP)).isTrue();

            TestSecurity.loginAs("sub-hoi-dong", "COUNCIL");
            service.revoke(cua.id(), "bãi nhiệm");

            TestSecurity.loginAs("sub-truong-chi", "BRANCH_HEAD");
            assertThat(clan.scopes.currentScope().managesBranch(ClanFixture.CHI_GIAP)).isFalse();
        }
    }

    @Nested
    @DisplayName("Xem phân công")
    class XemPhanCong {

        @Test
        @DisplayName("Ai cũng xem được phân công của chính mình")
        void xemCuaMinh() {
            AppUser toi = clan.account("sub-toi", clan.chiGiapId);
            clan.branchHeadOf(toi, clan.chiGiapId);
            TestSecurity.loginAs("sub-toi", "BRANCH_HEAD");

            assertThatCode(() -> service.assignmentsOf(toi.id())).doesNotThrowAnyException();
            assertThat(service.assignmentsOf(toi.id())).hasSize(1);
        }

        @Test
        @DisplayName("Xem phân công của người khác thì phải là vai toàn dòng họ")
        void xemCuaNguoiKhac() {
            AppUser nguoiKhac = clan.account("sub-nguoi-khac", clan.chiAtId);
            clan.account("sub-toi", clan.chiGiapId);
            TestSecurity.loginAs("sub-toi", "MEMBER");

            assertThatThrownBy(() -> service.assignmentsOf(nguoiKhac.id()))
                    .isInstanceOf(ForbiddenException.class);

            dangNhapHoiDong();
            assertThatCode(() -> service.assignmentsOf(nguoiKhac.id()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Tra ai đang là Trưởng chi của một chi cụ thể")
        void traTruongChiCuaMotChi() {
            AppUser truongChi = clan.account("sub-truong-chi", clan.chiGiapId);
            clan.branchHeadOf(truongChi, clan.chiGiapId);
            AppUser truongChiAt = clan.account("sub-truong-chi-at", clan.chiAtId);
            clan.branchHeadOf(truongChiAt, clan.chiAtId);

            assertThat(service.headsOfBranch(clan.chiGiapId))
                    .extracting(BranchAssignment::appUserId)
                    .containsExactly(truongChi.id());
        }
    }
}
