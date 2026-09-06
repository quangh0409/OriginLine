package vn.giapha.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.BranchPath;

/**
 * Phạm vi chi/ngành — <b>chiều phân quyền hạng nhất</b> của hệ thống.
 *
 * <pre>
 * goc
 *   goc.chi_giap
 *     goc.chi_giap.nganh_truong
 *   goc.chi_at
 * </pre>
 *
 * <p>Luật cần ghim, theo BA v2 §12 và TDD: Trưởng Chi/Ngành thao tác được <b>chỉ</b> trong chi được
 * giao và hậu duệ của nó; danh sách phạm vi rỗng nghĩa là không có phạm vi nào; đối tượng chưa gắn
 * chi thì chỉ vai toàn dòng họ mới đụng.</p>
 */
class MemberScopeTest {

    private static final BranchPath GOC = BranchPath.of("goc");
    private static final BranchPath CHI_GIAP = BranchPath.of("goc.chi_giap");
    private static final BranchPath NGANH_TRUONG = BranchPath.of("goc.chi_giap.nganh_truong");
    private static final BranchPath CHI_AT = BranchPath.of("goc.chi_at");

    private static final UUID APP_USER = UUID.randomUUID();
    private static final UUID PERSON = UUID.randomUUID();

    private static MemberScope branchHeadOf(BranchPath... scopes) {
        return new MemberScope(APP_USER, PERSON, RoleCode.BRANCH_HEAD, List.of(scopes),
                CHI_GIAP, false);
    }

    private static BranchAssignment assignment(RoleCode role, BranchPath path) {
        return new BranchAssignment(UUID.randomUUID(), APP_USER, role, UUID.randomUUID(),
                path, null, null, null, null);
    }

    @Nested
    @DisplayName("Trưởng Chi/Ngành: chỉ trong nhánh được giao")
    class TruongChi {

        @Test
        @DisplayName("Trưởng Chi Giáp KHÔNG sửa được người thuộc Chi Ất")
        void truongChiGiapKhongDungDuocChiAt() {
            MemberScope truongChiGiap = branchHeadOf(CHI_GIAP);

            assertThat(truongChiGiap.canWriteOn(CHI_AT)).isFalse();
            assertThat(truongChiGiap.managesBranch(CHI_AT)).isFalse();
            assertThat(truongChiGiap.canReview(CHI_AT)).isFalse();
        }

        @Test
        @DisplayName("Phạm vi phủ cả hậu duệ: Chi Giáp phủ Ngành Trưởng nằm dưới nó")
        void phamViPhuHauDue() {
            MemberScope truongChiGiap = branchHeadOf(CHI_GIAP);

            assertThat(truongChiGiap.canWriteOn(CHI_GIAP)).isTrue();
            assertThat(truongChiGiap.canWriteOn(NGANH_TRUONG)).isTrue();
        }

        @Test
        @DisplayName("Phạm vi KHÔNG ngược lên trên: Trưởng Ngành Trưởng không với tới Chi Giáp")
        void phamViKhongNguocLenTren() {
            MemberScope truongNganh = new MemberScope(APP_USER, PERSON, RoleCode.BRANCH_HEAD,
                    List.of(NGANH_TRUONG), NGANH_TRUONG, false);

            assertThat(truongNganh.canWriteOn(NGANH_TRUONG)).isTrue();
            // Quan tri mot canh khong dong nghia quan tri ca chi chua no, cang khong phai ca goc.
            assertThat(truongNganh.canWriteOn(CHI_GIAP)).isFalse();
            assertThat(truongNganh.canWriteOn(GOC)).isFalse();
        }

        @Test
        @DisplayName("Giao nhiều chi thì phủ đúng những chi ấy, không phủ chi thứ ba")
        void nhieuPhamVi() {
            MemberScope truongHaiChi = branchHeadOf(CHI_GIAP, CHI_AT);

            assertThat(truongHaiChi.canWriteOn(CHI_GIAP)).isTrue();
            assertThat(truongHaiChi.canWriteOn(CHI_AT)).isTrue();
            assertThat(truongHaiChi.canWriteOn(BranchPath.of("goc.chi_binh"))).isFalse();
        }

        @Test
        @DisplayName("Danh sách phạm vi RỖNG nghĩa là không có phạm vi nào, không phải toàn quyền")
        void phamViRongLaKhongCoGi() {
            // Loi kinh dien cua phan quyen theo scope: coi danh sach rong la "khong gioi han".
            MemberScope khongPhamVi = branchHeadOf();

            assertThat(khongPhamVi.managedBranches()).isEmpty();
            assertThat(khongPhamVi.canWriteOn(GOC)).isFalse();
            assertThat(khongPhamVi.canWriteOn(CHI_GIAP)).isFalse();
            assertThat(khongPhamVi.canWriteOn(CHI_AT)).isFalse();
            assertThat(khongPhamVi.isClanWide()).isFalse();
        }

        @Test
        @DisplayName("Đối tượng chưa gắn chi: Trưởng chi không đụng được")
        void doiTuongChuaGanChi() {
            // Coi "khong co chi" la "thuoc moi chi" se bien moi ban ghi thieu du lieu thanh mot
            // lo hong — ma ban ghi thieu du lieu thi pha he nao cung co.
            assertThat(branchHeadOf(CHI_GIAP).canWriteOn(null)).isFalse();
            assertThat(branchHeadOf(GOC).managesBranch(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Vai toàn dòng họ")
    class ToanDongHo {

        @Test
        @DisplayName("Quản trị hệ thống toàn quyền ngay cả khi bảng phân công còn trống")
        void quanTriKhongCanPhanCong() {
            // Ngoai le co chu y: neu doi phan cong thi khong ai cap duoc dong phan cong dau tien.
            MemberScope admin = new MemberScope(APP_USER, PERSON, RoleCode.ADMIN,
                    List.of(), null, false);

            assertThat(admin.isClanWide()).isTrue();
            assertThat(admin.isSystemAdmin()).isTrue();
            assertThat(admin.canWriteOn(CHI_AT)).isTrue();
            assertThat(admin.canWriteOn(null)).isTrue();
        }

        @Test
        @DisplayName("Hội đồng Tộc biểu cần CẢ vai trong token LẪN dòng phân công toàn dòng họ")
        void hoiDongCanCaHaiVe() {
            MemberScope chiCoToken = new MemberScope(APP_USER, PERSON, RoleCode.COUNCIL,
                    List.of(), null, false);
            MemberScope duCaHai = new MemberScope(APP_USER, PERSON, RoleCode.COUNCIL,
                    List.of(), null, true);

            // Mot realm Keycloak cau hinh long tay se phat ra token COUNCIL cho nguoi ma dong ho
            // chua bao gio trao quyen do. Khong tin mot minh token.
            assertThat(chiCoToken.isClanWide()).isFalse();
            assertThat(chiCoToken.canWriteOn(CHI_GIAP)).isFalse();

            assertThat(duCaHai.isClanWide()).isTrue();
            assertThat(duCaHai.canWriteOn(CHI_AT)).isTrue();
            // Hoi dong Toc bieu KHONG phai quan tri ky thuat.
            assertThat(duCaHai.isSystemAdmin()).isFalse();
        }
    }

    @Nested
    @DisplayName("Thành viên và Khách")
    class ThanhVienVaKhach {

        @Test
        @DisplayName("Thành viên không ghi được lên ai, kể cả trong chi nhà mình")
        void thanhVienKhongGhi() {
            MemberScope member = new MemberScope(APP_USER, PERSON, RoleCode.MEMBER,
                    List.of(), CHI_GIAP, false);

            assertThat(member.canWriteOn(CHI_GIAP)).isFalse();
            assertThat(member.canReview(CHI_GIAP)).isFalse();
            // Nhung van nhan ra ho so cua chinh minh, va van "cung chi" voi nguoi nha.
            assertThat(member.isSelf(PERSON)).isTrue();
            assertThat(member.sharesBranchWith(NGANH_TRUONG)).isTrue();
        }

        @Test
        @DisplayName("Khách: không tài khoản, không nhân khẩu, không phạm vi")
        void khachTrangTay() {
            MemberScope guest = MemberScope.guest();

            assertThat(guest.isGuest()).isTrue();
            assertThat(guest.appUserId()).isNull();
            assertThat(guest.personId()).isNull();
            assertThat(guest.managedBranches()).isEmpty();
            assertThat(guest.homeBranch()).isNull();
            assertThat(guest.isClanWide()).isFalse();
            assertThat(guest.canWriteOn(GOC)).isFalse();
            assertThat(guest.isSelf(PERSON)).isFalse();
        }

        @Test
        @DisplayName("Token hợp lệ mà chưa có app_user: giữ vai, mất sạch phạm vi")
        void chuaCoTaiKhoan() {
            MemberScope unlinked = MemberScope.unlinked(RoleCode.BRANCH_HEAD);

            assertThat(unlinked.role()).isEqualTo(RoleCode.BRANCH_HEAD);
            assertThat(unlinked.appUserId()).isNull();
            assertThat(unlinked.managedBranches()).isEmpty();
            assertThat(unlinked.canWriteOn(CHI_GIAP)).isFalse();
            assertThat(MemberScope.unlinked(null).role()).isEqualTo(RoleCode.MEMBER);
        }
    }

    @Nested
    @DisplayName("Cùng chi — căn cứ cho Tầng 2 của luật riêng tư")
    class CungChi {

        @Test
        @DisplayName("Xét hai chiều: chi nhà chứa chi kia, hoặc ngược lại")
        void haiChieu() {
            MemberScope oGoc = new MemberScope(APP_USER, PERSON, RoleCode.MEMBER,
                    List.of(), GOC, false);
            MemberScope oNganh = new MemberScope(APP_USER, PERSON, RoleCode.MEMBER,
                    List.of(), NGANH_TRUONG, false);

            assertThat(oGoc.sharesBranchWith(NGANH_TRUONG)).isTrue();
            // Chieu nguoc cung tinh: nguoi o canh nho van cung chi voi nguoi dung o goc chi minh.
            assertThat(oNganh.sharesBranchWith(GOC)).isTrue();
        }

        @Test
        @DisplayName("Hai nhánh rời nhau thì không cùng chi")
        void haiNhanhRoiNhau() {
            MemberScope oChiGiap = new MemberScope(APP_USER, PERSON, RoleCode.MEMBER,
                    List.of(), CHI_GIAP, false);

            assertThat(oChiGiap.sharesBranchWith(CHI_AT)).isFalse();
        }

        @Test
        @DisplayName("Chưa có chi nhà thì không cùng chi với ai")
        void chuaCoChiNha() {
            MemberScope chuaGhep = new MemberScope(APP_USER, null, RoleCode.MEMBER,
                    List.of(), null, false);

            assertThat(chuaGhep.sharesBranchWith(CHI_GIAP)).isFalse();
            assertThat(chuaGhep.sharesBranchWith(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Dựng phạm vi từ bảng phân công")
    class DungTuPhanCong {

        @Test
        @DisplayName("Phân công Trưởng chi sinh ra phạm vi; phân công Thành viên thì không")
        void chiPhanCongQuanTriSinhPhamVi() {
            BranchAssignment truongChi = assignment(RoleCode.BRANCH_HEAD, CHI_GIAP);
            // Mot dong (MEMBER x chi) chi noi nguoi do sinh hoat o chi nao — khong cho quyen gi.
            BranchAssignment sinhHoat = assignment(RoleCode.MEMBER, CHI_AT);

            MemberScope scope = MemberScope.from(APP_USER, PERSON, RoleCode.BRANCH_HEAD,
                    List.of(truongChi, sinhHoat), CHI_GIAP);

            assertThat(scope.managedBranches()).containsExactly(CHI_GIAP);
            assertThat(scope.canWriteOn(CHI_AT)).isFalse();
        }

        @Test
        @DisplayName("Phân công không gắn chi + vai toàn cục = phạm vi toàn dòng họ")
        void phanCongKhongGanChi() {
            BranchAssignment hoiDong = new BranchAssignment(UUID.randomUUID(), APP_USER,
                    RoleCode.COUNCIL, null, null, null, null, null, null);

            MemberScope scope = MemberScope.from(APP_USER, PERSON, RoleCode.COUNCIL,
                    List.of(hoiDong), CHI_GIAP);

            assertThat(scope.isClanWide()).isTrue();
            assertThat(scope.canWriteOn(CHI_AT)).isTrue();
        }

        @Test
        @DisplayName("Vai trong token hẹp hơn phân công thì lấy vai hẹp — hướng an toàn")
        void tokenHepHonPhanCong() {
            // Phan cong noi PHAM VI, token noi VAI. Token chi mang MEMBER thi du co dong
            // BRANCH_HEAD trong bang, nguoi do van khong ghi duoc. Fail-closed la dung huong:
            // mot vai vua bi go khoi Keycloak phai co hieu luc ngay lap tuc.
            BranchAssignment truongChi = assignment(RoleCode.BRANCH_HEAD, CHI_GIAP);

            MemberScope scope = MemberScope.from(APP_USER, PERSON, RoleCode.MEMBER,
                    List.of(truongChi), CHI_GIAP);

            assertThat(scope.role()).isEqualTo(RoleCode.MEMBER);
            assertThat(scope.managedBranches()).containsExactly(CHI_GIAP);
            assertThat(scope.canWriteOn(CHI_GIAP)).isFalse();
        }

        @Test
        @DisplayName("Không phân công nào thì mặc định là Thành viên không phạm vi")
        void khongCoPhanCong() {
            MemberScope scope = MemberScope.from(APP_USER, PERSON, null, List.of(), CHI_GIAP);

            assertThat(scope.role()).isEqualTo(RoleCode.MEMBER);
            assertThat(scope.managedBranches()).isEmpty();
            assertThat(scope.isClanWide()).isFalse();
        }

        @Test
        @DisplayName("Danh sách phạm vi trả ra là bản sao bất biến")
        void phamViBatBien() {
            MemberScope scope = branchHeadOf(CHI_GIAP);

            assertThatThrownBy(() -> scope.managedBranches().add(CHI_AT))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
