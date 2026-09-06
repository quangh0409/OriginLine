package vn.giapha.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.membership.domain.MemberScope;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.vo.BranchPath;

/**
 * Cửa kiểm quyền <b>hai chiều</b>: vai trò <i>và</i> phạm vi chi/ngành.
 *
 * <p>Đây là lớp mà mọi lối ghi của hệ thống phải đi qua, nên ở đây kiểm cả <b>mã lỗi</b> chứ không
 * chỉ kiểm "có ném hay không": {@code FORBIDDEN} = sai vai, {@code BRANCH_SCOPE_VIOLATION} = đúng
 * vai nhưng sai nhánh. Người dùng cần phân biệt hai thứ ấy, và frontend phân nhánh xử lý theo
 * {@code code}.</p>
 */
class BranchScopeGuardTest {

    private static final BranchPath CHI_GIAP = BranchPath.of("goc.chi_giap");
    private static final BranchPath NGANH_TRUONG = BranchPath.of("goc.chi_giap.nganh_truong");
    private static final BranchPath CHI_AT = BranchPath.of("goc.chi_at");

    private final BranchScopeGuard guard = new BranchScopeGuard();

    private static MemberScope truongChi(BranchPath... scopes) {
        return new MemberScope(UUID.randomUUID(), UUID.randomUUID(), RoleCode.BRANCH_HEAD,
                List.of(scopes), CHI_GIAP, false);
    }

    private static MemberScope hoiDongTocBieu() {
        return new MemberScope(UUID.randomUUID(), UUID.randomUUID(), RoleCode.COUNCIL,
                List.of(), null, true);
    }

    private static MemberScope quanTriHeThong() {
        return new MemberScope(UUID.randomUUID(), UUID.randomUUID(), RoleCode.ADMIN,
                List.of(), null, false);
    }

    private static MemberScope thanhVien(UUID personId) {
        return new MemberScope(UUID.randomUUID(), personId, RoleCode.MEMBER,
                List.of(), CHI_GIAP, false);
    }

    @Nested
    @DisplayName("Quyền ghi")
    class QuyenGhi {

        @Test
        @DisplayName("Trưởng Chi Giáp sửa người thuộc Chi Ất -> BRANCH_SCOPE_VIOLATION")
        void truongChiVuotNhanh() {
            // Ca kiem tra quan trong nhat cua ca W6.
            assertThatThrownBy(() -> guard.requireWriteAccess(truongChi(CHI_GIAP), CHI_AT))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(ex -> ((ForbiddenException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.BRANCH_SCOPE_VIOLATION);
        }

        @Test
        @DisplayName("Trưởng Chi Giáp sửa được trong chi mình và cành con của nó")
        void truongChiTrongNhanh() {
            assertThatCode(() -> guard.requireWriteAccess(truongChi(CHI_GIAP), CHI_GIAP))
                    .doesNotThrowAnyException();
            assertThatCode(() -> guard.requireWriteAccess(truongChi(CHI_GIAP), NGANH_TRUONG))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Thành viên bị chặn bằng FORBIDDEN — sai vai, không phải sai nhánh")
        void thanhVienSaiVai() {
            // Hai ma loi dan toi hai hanh dong khac han: "ban khong co quyen nay" thi gui yeu cau
            // dinh chinh, con "ban co quyen nhung khong phai o chi do" thi bao Truong chi kia.
            assertThatThrownBy(() -> guard.requireWriteAccess(thanhVien(UUID.randomUUID()), CHI_GIAP))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(ex -> ((ForbiddenException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.FORBIDDEN);
        }

        @Test
        @DisplayName("Khách bị chặn")
        void khach() {
            assertThatThrownBy(() -> guard.requireWriteAccess(MemberScope.guest(), CHI_GIAP))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("Hội đồng Tộc biểu và Quản trị hệ thống ghi được mọi chi")
        void vaiToanDongHo() {
            assertThatCode(() -> guard.requireWriteAccess(hoiDongTocBieu(), CHI_AT))
                    .doesNotThrowAnyException();
            assertThatCode(() -> guard.requireWriteAccess(quanTriHeThong(), CHI_AT))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Đối tượng chưa gắn chi: chỉ vai toàn dòng họ đi qua")
        void doiTuongChuaGanChi() {
            assertThatCode(() -> guard.requireWriteAccess(hoiDongTocBieu(), null))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> guard.requireWriteAccess(truongChi(CHI_GIAP), null))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(ex -> ((ForbiddenException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.BRANCH_SCOPE_VIOLATION);
        }

        @Test
        @DisplayName("Trưởng chi chưa được giao chi nào thì không ghi được ở đâu cả")
        void truongChiKhongPhamVi() {
            assertThatThrownBy(() -> guard.requireWriteAccess(truongChi(), CHI_GIAP))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("Quyền duyệt yêu cầu đính chính")
    class QuyenDuyet {

        @Test
        @DisplayName("Duyệt là ghi — cùng luật phạm vi, không nới")
        void duyetLaGhi() {
            // Noi long o day la mo dung loi ma luong duyet sinh ra de dong: mot Truong chi khong
            // duoc cham vao chi khac, ke ca qua tay nguoi gui.
            assertThatCode(() -> guard.requireReviewAccess(truongChi(CHI_GIAP), NGANH_TRUONG))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> guard.requireReviewAccess(truongChi(CHI_GIAP), CHI_AT))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(ex -> ((ForbiddenException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.BRANCH_SCOPE_VIOLATION);
        }

        @Test
        @DisplayName("Thành viên không duyệt được — chặn ở bước xét vai, trước cả bước xét nhánh")
        void thanhVienKhongDuyet() {
            assertThatThrownBy(() -> guard.requireReviewAccess(thanhVien(UUID.randomUUID()), CHI_GIAP))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("duyet yeu cau dinh chinh");
        }
    }

    @Nested
    @DisplayName("Hai trục thẩm quyền: chức danh dòng tộc và vai kỹ thuật")
    class HaiTrucThamQuyen {

        @Test
        @DisplayName("Hội đồng Tộc biểu KHÔNG phải Quản trị hệ thống")
        void hoiDongKhongPhaiQuanTri() {
            // Hoi dong Toc bieu la tham quyen NOI DUNG cua dong ho; quan tri tai khoan va phan
            // quyen la tham quyen KY THUAT. Tron hai thu lai la cach mot tranh chap noi bo trong
            // ho bien thanh mot su co an ninh he thong.
            MemberScope hoiDong = hoiDongTocBieu();

            assertThatCode(() -> guard.requireClanWide(hoiDong, "duyệt nội dung"))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> guard.requireSystemAdmin(hoiDong, "khoá tài khoản"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Quan tri he thong");
        }

        @Test
        @DisplayName("Quản trị hệ thống giữ được cả hai trục")
        void quanTriGiuCaHai() {
            MemberScope admin = quanTriHeThong();

            assertThatCode(() -> guard.requireClanWide(admin, "duyệt nội dung"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> guard.requireSystemAdmin(admin, "khoá tài khoản"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Trưởng chi không phải vai toàn dòng họ dù xếp trên Thành viên")
        void truongChiKhongToanDongHo() {
            assertThatThrownBy(() -> guard.requireClanWide(truongChi(CHI_GIAP), "cấp vai trò"))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("Tộc trưởng theo huyết thống mà chỉ có vai Thành viên thì vẫn không ghi được")
        void tocTruongTheoHuyetThongKhongTuDongCoQuyen() {
            // Chuc danh dong toc (Toc truong, Truong chi — theo huyet thong/dich ton) nam o
            // `branch.head_person_id`, KHONG suy ra vai he thong. Nguoi nay dung la truong toc cua
            // Chi Giap, nhung tai khoan chi mang vai MEMBER.
            UUID tocTruongPersonId = UUID.randomUUID();
            MemberScope tocTruongNhungChiLaThanhVien = thanhVien(tocTruongPersonId);

            assertThatThrownBy(() ->
                    guard.requireWriteAccess(tocTruongNhungChiLaThanhVien, CHI_GIAP))
                    .isInstanceOf(ForbiddenException.class);
            // Nhung ho so cua chinh minh thi van sua duoc — day la loi rieng, khong phai loi ghi.
            assertThatCode(() -> guard.requireSelfOrWriteAccess(
                    tocTruongNhungChiLaThanhVien, CHI_GIAP, tocTruongPersonId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Vai BRANCH_HEAD không đòi người đó phải là Trưởng chi theo huyết thống")
        void vaiKyThuatKhongDoiHuyetThong() {
            // Chieu nguoc lai: mot nguoi khong phai dich ton van co the duoc Hoi dong giao vai
            // BRANCH_HEAD tren Chi Giap. Hai du kien doc lap.
            assertThatCode(() -> guard.requireWriteAccess(truongChi(CHI_GIAP), NGANH_TRUONG))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Hồ sơ của chính mình")
    class HoSoCuaMinh {

        @Test
        @DisplayName("Chính chủ sửa được hồ sơ mình dù không có phạm vi nào")
        void chinhChu() {
            UUID toi = UUID.randomUUID();

            assertThatCode(() -> guard.requireSelfOrWriteAccess(thanhVien(toi), CHI_AT, toi))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Hồ sơ người khác thì quay về luật ghi thường")
        void hoSoNguoiKhac() {
            assertThatThrownBy(() -> guard.requireSelfOrWriteAccess(
                    thanhVien(UUID.randomUUID()), CHI_GIAP, UUID.randomUUID()))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("Tài khoản đã khởi tạo")
    class TaiKhoanDaKhoiTao {

        @Test
        @DisplayName("Khách và token chưa có app_user đều bị ACCOUNT_NOT_PROVISIONED")
        void chuaKhoiTao() {
            assertThatThrownBy(() -> guard.requireProvisionedAccount(MemberScope.guest()))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(ex -> ((ForbiddenException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.ACCOUNT_NOT_PROVISIONED);

            assertThatThrownBy(() ->
                    guard.requireProvisionedAccount(MemberScope.unlinked(RoleCode.MEMBER)))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(ex -> ((ForbiddenException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.ACCOUNT_NOT_PROVISIONED);
        }

        @Test
        @DisplayName("Tài khoản có app_user thì đi qua")
        void daCoTaiKhoan() {
            assertThatCode(() -> guard.requireProvisionedAccount(thanhVien(UUID.randomUUID())))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Mã lỗi giữ nguyên văn hợp đồng với frontend")
    void maLoiOnDinh() {
        // Giao dien phan nhanh xu ly theo `code`, nen mot ma la lam hong client ma khong ai thay
        // loi bien dich.
        assertThat(MembershipProblemCodes.FORBIDDEN).isEqualTo("FORBIDDEN");
        assertThat(MembershipProblemCodes.BRANCH_SCOPE_VIOLATION).isEqualTo("BRANCH_SCOPE_VIOLATION");
        assertThat(MembershipProblemCodes.NOT_FOUND).isEqualTo("NOT_FOUND");
        assertThat(MembershipProblemCodes.VALIDATION_FAILED).isEqualTo("VALIDATION_FAILED");
    }
}
