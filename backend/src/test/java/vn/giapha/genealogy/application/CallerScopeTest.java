package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.BranchPath;

/**
 * Danh tính và <b>phạm vi chi/ngành</b> của người gọi — nửa thứ hai của phân quyền.
 *
 * <p>Vai trò trong JWT mới là một nửa; nửa còn lại là phạm vi {@code ltree}. Có vai
 * {@code BRANCH_HEAD} không đồng nghĩa được đụng tới mọi người trong họ. Quan trọng nhất:
 * <b>danh sách chi được giao rỗng nghĩa là không có phạm vi nào</b>, tuyệt đối không phải toàn
 * quyền.
 */
class CallerScopeTest {

    private static final BranchPath GOC = BranchPath.of("goc");
    private static final BranchPath CHI_GIAP = BranchPath.of("goc.chi_giap");
    private static final BranchPath NGANH_TRUONG = BranchPath.of("goc.chi_giap.nganh_truong");
    private static final BranchPath CHI_AT = BranchPath.of("goc.chi_at");

    @Test
    @DisplayName("Chi được giao chứa cây con của nó — đúng ngữ nghĩa toán tử @> của ltree")
    void chiDuocGiaoChuaCayCon() {
        CallerContext caller = new CallerContext(CallerRole.BRANCH_HEAD, UUID.randomUUID(),
                List.of(CHI_GIAP), CHI_GIAP);

        assertThat(caller.managesBranch(CHI_GIAP)).isTrue();
        assertThat(caller.managesBranch(NGANH_TRUONG)).isTrue();
        assertThat(caller.managesBranch(CHI_AT)).isFalse();
        assertThat(caller.managesBranch(GOC))
                .as("Truong chi khong quan tri duoc chi cha cua minh")
                .isFalse();
    }

    @Test
    @DisplayName("Danh sách chi được giao rỗng nghĩa là KHÔNG có phạm vi nào, không phải toàn quyền")
    void danhSachChiRongNghiaLaKhongCoPhamVi() {
        CallerContext caller = new CallerContext(CallerRole.BRANCH_HEAD, UUID.randomUUID(),
                List.of(), null);

        assertThat(caller.managesBranch(CHI_GIAP)).isFalse();
        assertThat(caller.managesBranch(GOC)).isFalse();
        assertThat(caller.managesBranch(null)).isFalse();
    }

    @Test
    @DisplayName("Cùng chi được xét HAI CHIỀU: chi nhà chứa chi kia hoặc ngược lại")
    void cungChiXetHaiChieu() {
        CallerContext oGocChi = new CallerContext(CallerRole.MEMBER, UUID.randomUUID(),
                List.of(), CHI_GIAP);
        CallerContext oCanhNho = new CallerContext(CallerRole.MEMBER, UUID.randomUUID(),
                List.of(), NGANH_TRUONG);

        assertThat(oGocChi.sharesBranchWith(NGANH_TRUONG)).isTrue();
        assertThat(oCanhNho.sharesBranchWith(CHI_GIAP))
                .as("thanh vien canh nho van coi la cung chi voi nguoi dung o goc chi minh")
                .isTrue();
        assertThat(oGocChi.sharesBranchWith(CHI_AT)).isFalse();
    }

    @Test
    @DisplayName("Không có chi nhà thì không bao giờ 'cùng chi' với ai")
    void khongCoChiNhaThiKhongCungChiVoiAi() {
        CallerContext caller = new CallerContext(CallerRole.MEMBER, UUID.randomUUID(), List.of(), null);

        assertThat(caller.sharesBranchWith(CHI_GIAP)).isFalse();
        assertThat(caller.sharesBranchWith(null)).isFalse();
    }

    @Test
    @DisplayName("Khách vãng lai không có nhân khẩu, không có phạm vi, không phải chính chủ của ai")
    void khachVangLaiKhongCoGiCa() {
        CallerContext khach = CallerContext.guest();

        assertThat(khach.isGuest()).isTrue();
        assertThat(khach.isAdmin()).isFalse();
        assertThat(khach.personId()).isNull();
        assertThat(khach.managedBranches()).isEmpty();
        assertThat(khach.homeBranch()).isNull();
        assertThat(khach.isSelf(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("isSelf chỉ đúng khi tài khoản đã map được sang một nhân khẩu")
    void isSelfChiDungKhiDaMapNhanKhau() {
        UUID toi = UUID.randomUUID();
        CallerContext coMap = new CallerContext(CallerRole.MEMBER, toi, List.of(), null);
        CallerContext chuaMap = new CallerContext(CallerRole.MEMBER, null, List.of(), null);

        assertThat(coMap.isSelf(toi)).isTrue();
        assertThat(coMap.isSelf(UUID.randomUUID())).isFalse();
        assertThat(chuaMap.isSelf(toi)).isFalse();
        assertThat(chuaMap.isSelf(null)).isFalse();
    }

    @Test
    @DisplayName("Vai được phân giải là vai RỘNG NHẤT mà token có")
    void vaiDuocPhanGiaiLaVaiRongNhat() {
        assertThat(CallerRole.from(Set.of("MEMBER", "ADMIN"))).isEqualTo(CallerRole.ADMIN);
        assertThat(CallerRole.from(Set.of("MEMBER", "BRANCH_HEAD", "COUNCIL")))
                .isEqualTo(CallerRole.COUNCIL);
        assertThat(CallerRole.from(Set.of("MEMBER", "BRANCH_HEAD"))).isEqualTo(CallerRole.BRANCH_HEAD);
        assertThat(CallerRole.from(Set.of("MEMBER"))).isEqualTo(CallerRole.MEMBER);
    }

    @Test
    @DisplayName("Token không có vai nào quy về MEMBER, KHÔNG quy về GUEST và cũng không quy về ADMIN")
    void tokenKhongCoVaiQuyVeMember() {
        assertThat(CallerRole.from(Set.of())).isEqualTo(CallerRole.MEMBER);
        assertThat(CallerRole.from(null)).isEqualTo(CallerRole.MEMBER);
        assertThat(CallerRole.from(Set.of("mot_vai_la_hoac"))).isEqualTo(CallerRole.MEMBER);
    }

    @Test
    @DisplayName("Chỉ ADMIN và COUNCIL có phạm vi toàn dòng họ")
    void chiAdminVaCouncilCoPhamViToanDongHo() {
        assertThat(CallerRole.ADMIN.isClanWide()).isTrue();
        assertThat(CallerRole.COUNCIL.isClanWide()).isTrue();
        assertThat(CallerRole.BRANCH_HEAD.isClanWide()).isFalse();
        assertThat(CallerRole.MEMBER.isClanWide()).isFalse();
        assertThat(CallerRole.GUEST.isClanWide()).isFalse();
    }

    @Test
    @DisplayName("Danh sách chi được giao là bản sao bất biến — không sửa được từ ngoài")
    void danhSachChiLaBanSaoBatBien() {
        List<BranchPath> nguon = new java.util.ArrayList<>(List.of(CHI_GIAP));
        CallerContext caller = new CallerContext(CallerRole.BRANCH_HEAD, UUID.randomUUID(),
                nguon, CHI_GIAP);

        nguon.add(CHI_AT);

        assertThat(caller.managedBranches()).containsExactly(CHI_GIAP);
        assertThat(caller.managesBranch(CHI_AT)).isFalse();
    }
}
