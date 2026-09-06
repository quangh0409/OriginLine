package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.vo.BranchPath;

/**
 * <b>Phân quyền theo phạm vi chi/ngành</b> — chiều dễ bỏ sót nhất của RBAC dự án này.
 *
 * <p>Trưởng Chi chỉ thao tác được trên cây con nằm dưới path được giao. Test quan trọng nhất của
 * lớp này là ca "đủ vai nhưng sai phạm vi": phải là {@code 403 BRANCH_SCOPE_VIOLATION}, không phải
 * cho qua, và cũng không phải {@code FORBIDDEN} chung chung — giao diện phân nhánh xử lý theo mã.
 */
class GenealogyAccessGuardTest {

    private static final BranchPath CHI_GIAP = BranchPath.of("goc.chi_giap");
    private static final BranchPath NGANH_TRUONG = BranchPath.of("goc.chi_giap.nganh_truong");
    private static final BranchPath CHI_AT = BranchPath.of("goc.chi_at");

    private final GenealogyAccessGuard guard = new GenealogyAccessGuard();

    private CallerContext truongChiGiap() {
        return new CallerContext(CallerRole.BRANCH_HEAD, UUID.randomUUID(),
                List.of(CHI_GIAP), CHI_GIAP);
    }

    @Test
    @DisplayName("Trưởng Chi KHÔNG sửa được nhân khẩu của chi khác — 403 BRANCH_SCOPE_VIOLATION")
    void truongChiKhongSuaDuocChiKhac() {
        assertThatThrownBy(() -> guard.requireWriteAccess(truongChiGiap(), CHI_AT))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo(GenealogyProblemCodes.BRANCH_SCOPE_VIOLATION);
    }

    @Test
    @DisplayName("Trưởng Chi sửa được trong chi mình và toàn bộ cây con bên dưới")
    void truongChiSuaDuocTrongPhamViDuocGiao() {
        assertThatCode(() -> guard.requireWriteAccess(truongChiGiap(), CHI_GIAP))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.requireWriteAccess(truongChiGiap(), NGANH_TRUONG))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Nhân khẩu chưa gắn chi chỉ ADMIN/COUNCIL được đụng tới")
    void nhanKhauChuaGanChiChiClanWideDuocDung() {
        assertThatThrownBy(() -> guard.requireWriteAccess(truongChiGiap(), null))
                .as("coi 'khong co chi' la 'thuoc moi chi' se bien moi ban ghi thieu du lieu "
                        + "thanh mot lo hong phan quyen")
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo(GenealogyProblemCodes.BRANCH_SCOPE_VIOLATION);

        assertThatCode(() -> guard.requireWriteAccess(clanWide(CallerRole.COUNCIL), null))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.requireWriteAccess(clanWide(CallerRole.ADMIN), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Thành viên thường không sửa được dữ liệu phả hệ — 403 FORBIDDEN")
    void thanhVienThuongKhongSuaDuocDuLieuPhaHe() {
        CallerContext thanhVien = new CallerContext(CallerRole.MEMBER, UUID.randomUUID(),
                List.of(), CHI_GIAP);

        assertThatThrownBy(() -> guard.requireWriteAccess(thanhVien, CHI_GIAP))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo(GenealogyProblemCodes.FORBIDDEN);
    }

    @Test
    @DisplayName("Khách vãng lai không ghi được gì")
    void khachKhongGhiDuocGi() {
        assertThatThrownBy(() -> guard.requireWriteAccess(CallerContext.guest(), CHI_GIAP))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Chính chủ sửa được hồ sơ của mình dù không có vai quản trị nào")
    void chinhChuSuaDuocHoSoCuaMinh() {
        UUID toi = UUID.randomUUID();
        CallerContext thanhVien = new CallerContext(CallerRole.MEMBER, toi, List.of(), CHI_AT);

        assertThatCode(() -> guard.requireProfileWriteAccess(thanhVien, CHI_GIAP, toi))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Thành viên không sửa được hồ sơ người khác kể cả cùng chi")
    void thanhVienKhongSuaDuocHoSoNguoiKhac() {
        CallerContext thanhVien = new CallerContext(CallerRole.MEMBER, UUID.randomUUID(),
                List.of(), CHI_GIAP);

        assertThatThrownBy(() -> guard.requireProfileWriteAccess(thanhVien, CHI_GIAP, UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Chuyển chi đòi quyền ở CẢ chi cũ lẫn chi mới")
    void chuyenChiDoiQuyenOCaHaiChi() {
        CallerContext truongChi = truongChiGiap();

        assertThatThrownBy(() -> guard.requireMoveAccess(truongChi, CHI_AT, CHI_GIAP))
                .as("chi kiem chi dich thi mot Truong chi keo duoc nguoi cua chi khac ve chi minh "
                        + "roi tu cap quyen cho chinh minh")
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> guard.requireMoveAccess(truongChi, CHI_GIAP, CHI_AT))
                .isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> guard.requireMoveAccess(truongChi, CHI_GIAP, NGANH_TRUONG))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Khôi phục bản ghi đã xoá mềm chỉ dành cho Hội đồng và Quản trị hệ thống")
    void khoiPhucChiDanhChoClanWide() {
        assertThatCode(() -> guard.requireClanWide(clanWide(CallerRole.ADMIN), "khoi phuc"))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.requireClanWide(clanWide(CallerRole.COUNCIL), "khoi phuc"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> guard.requireClanWide(truongChiGiap(), "khoi phuc"))
                .as("Truong chi khoi phuc lai nguoi ma Hoi dong vua cho xoa la lat quyet dinh cap tren")
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo(GenealogyProblemCodes.FORBIDDEN);
    }

    @Test
    @DisplayName("Thông điệp 403 không tiết lộ dữ liệu của nhân khẩu bị chặn")
    void thongDiep403KhongTietLoDuLieu() {
        ForbiddenException ex = (ForbiddenException) org.assertj.core.api.Assertions
                .catchThrowable(() -> guard.requireWriteAccess(truongChiGiap(), CHI_AT));

        assertThat(ex).isNotNull();
        assertThat(ex.getMessage())
                .doesNotContain(CHI_AT.value())
                .doesNotContain("chi_at");
    }

    private CallerContext clanWide(CallerRole role) {
        return new CallerContext(role, UUID.randomUUID(), List.of(), null);
    }
}
