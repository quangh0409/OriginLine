package vn.giapha.shared.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bay da biet: nhan cua kieu ltree khong nhan dau tieng Viet. Bo test nay khoa lai quy uoc
 * "path sinh tu slug khong dau, ten hien thi giu o cot rieng".
 */
class BranchPathTest {

    @Test
    @DisplayName("Ten chi/nganh tieng Viet duoc chuyen thanh nhan ltree hop le")
    void sinhNhanLtreeTuTenTiengViet() {
        assertThat(BranchPath.label("Ngành Trưởng")).isEqualTo("nganh_truong");
        assertThat(BranchPath.label("Chi Đức")).isEqualTo("chi_duc");
        assertThat(BranchPath.label("Cành Hai - Nhánh Út")).isEqualTo("canh_hai_nhanh_ut");
    }

    @Test
    @DisplayName("Path chua dau tieng Viet bi tu choi ngay tu constructor")
    void tuChoiPathCoDauTiengViet() {
        assertThatThrownBy(() -> BranchPath.of("goc.chi_Đức"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BranchPath.of("goc..chi"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Quan he to tien - co so cua phan quyen theo pham vi chi/nganh")
    void kiemTraQuanHeToTien() {
        BranchPath goc = BranchPath.of("goc");
        BranchPath chi = goc.child("Chi Giáp");
        BranchPath nganh = chi.child("Ngành Trưởng");

        assertThat(chi.value()).isEqualTo("goc.chi_giap");
        assertThat(goc.isAncestorOf(nganh)).isTrue();
        assertThat(chi.isAncestorOf(nganh)).isTrue();
        assertThat(nganh.isAncestorOf(chi)).isFalse();
        // Khong duoc nham tien to chuoi voi tien to path: "goc.chi_giap2" khong nam duoi "goc.chi_giap"
        assertThat(chi.isAncestorOf(BranchPath.of("goc.chi_giap2"))).isFalse();
        assertThat(nganh.depth()).isEqualTo(3);
        assertThat(nganh.leaf()).isEqualTo("nganh_truong");
    }
}
