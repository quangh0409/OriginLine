package vn.giapha.genealogy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.BranchPath;

/**
 * <b>Chi / Ngành / Cành / Nhánh</b> — bốn cấp phân nhánh của gia phả Việt, cộng cấp gốc dòng họ.
 *
 * <p>{@code path} là chiều phân quyền hạng nhất, nên hai điều phải luôn đúng: path <b>luôn</b>
 * ghép từ slug không dấu (nhãn {@code ltree} chỉ nhận {@code [A-Za-z0-9_]}), và chức danh
 * <i>Trưởng chi</i> theo huyết thống hoàn toàn tách khỏi vai kỹ thuật {@code BRANCH_HEAD} trong
 * JWT.
 */
class BranchTest {

    @Test
    @DisplayName("Slug của chi được lấy từ nhãn cuối của path, không phải từ tên hiển thị có dấu")
    void slugLayTuNhanCuoiCuaPath() {
        BranchPath path = BranchPath.of("goc.chi_thuong");

        Branch branch = Branch.create(UUID.randomUUID(), "Chi Thượng", path, null, BranchKind.CHI);

        assertThat(branch.name()).isEqualTo("Chi Thượng");
        assertThat(branch.slug()).isEqualTo("chi_thuong");
        assertThat(branch.path().value()).isEqualTo("goc.chi_thuong");
    }

    @Test
    @DisplayName("Tên tiếng Việt có dấu không bao giờ được ghép thẳng vào path ltree")
    void tenCoDauKhongGhepThangVaoPath() {
        assertThatThrownBy(() -> BranchPath.of("goc.Chi Thượng"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ltree");

        assertThat(BranchPath.of("goc").child("Ngành Trưởng").value()).isEqualTo("goc.nganh_truong");
        assertThat(BranchPath.label("Chi Đức")).isEqualTo("chi_duc");
    }

    @Test
    @DisplayName("Chi cha chứa chi con và chứa chính nó — đúng ngữ nghĩa toán tử @> của ltree")
    void chiChaChuaChiCon() {
        Branch goc = Branch.create(UUID.randomUUID(), "Dòng họ Nguyễn",
                BranchPath.of("goc"), null, BranchKind.DONG_HO);
        Branch chi = Branch.create(UUID.randomUUID(), "Chi Giáp",
                BranchPath.of("goc.chi_giap"), goc.id(), BranchKind.CHI);
        Branch nganh = Branch.create(UUID.randomUUID(), "Ngành Trưởng",
                BranchPath.of("goc.chi_giap.nganh_truong"), chi.id(), BranchKind.NGANH);
        Branch chiKhac = Branch.create(UUID.randomUUID(), "Chi Ất",
                BranchPath.of("goc.chi_at"), goc.id(), BranchKind.CHI);

        assertThat(goc.contains(chi)).isTrue();
        assertThat(goc.contains(nganh)).isTrue();
        assertThat(chi.contains(nganh)).isTrue();
        assertThat(chi.contains(chi)).isTrue();
        assertThat(chi.contains(chiKhac)).isFalse();
        assertThat(nganh.contains(chi)).isFalse();
        assertThat(chi.contains(null)).isFalse();
    }

    @Test
    @DisplayName("Tiền tố chuỗi trùng nhau nhưng khác nhãn thì KHÔNG phải quan hệ cha con")
    void tienToTrungNhungKhacNhanThiKhongChua() {
        Branch chiGiap = Branch.create(UUID.randomUUID(), "Chi Giáp",
                BranchPath.of("goc.chi_giap"), null, BranchKind.CHI);
        Branch chiGiapAt = Branch.create(UUID.randomUUID(), "Chi Giáp Ất",
                BranchPath.of("goc.chi_giap_at"), null, BranchKind.CHI);

        assertThat(chiGiap.contains(chiGiapAt))
                .as("chi_giap khong duoc nuot chi_giap_at chi vi trung tien to chuoi")
                .isFalse();
    }

    @Test
    @DisplayName("Bổ nhiệm Trưởng chi là chức danh dòng tộc, không cấp thêm quyền kỹ thuật nào")
    void bonNhiemTruongChiKhongCapQuyenKyThuat() {
        Branch chi = Branch.create(UUID.randomUUID(), "Chi Giáp",
                BranchPath.of("goc.chi_giap"), null, BranchKind.CHI);
        UUID dichTon = UUID.randomUUID();

        chi.appointHead(dichTon);

        assertThat(chi.headPersonId()).isEqualTo(dichTon);
        assertThat(Branch.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .as("Branch khong duoc biet gi ve vai tro ky thuat trong JWT")
                .doesNotContain("role", "roles", "grantRole", "hasRole");
    }

    @Test
    @DisplayName("Loại chi mặc định là CHI khi không nói rõ; tên và path bắt buộc có")
    void loaiChiMacDinhVaCacTruongBatBuoc() {
        Branch branch = Branch.create(UUID.randomUUID(), "Chi Giáp",
                BranchPath.of("goc.chi_giap"), null, null);
        assertThat(branch.kind()).isEqualTo(BranchKind.CHI);

        assertThatThrownBy(() -> Branch.create(UUID.randomUUID(), null,
                BranchPath.of("goc"), null, BranchKind.CHI))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Branch.create(UUID.randomUUID(), "Chi Giáp", null, null, BranchKind.CHI))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Branch.create(null, "Chi Giáp",
                BranchPath.of("goc"), null, BranchKind.CHI))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Năm cấp phân nhánh của gia phả Việt đều có mặt, không bị dịch gộp")
    void namCapPhanNhanhDeuCoMat() {
        assertThat(BranchKind.values()).extracting(Enum::name)
                .containsExactly("DONG_HO", "CHI", "NGANH", "CANH", "NHANH");
    }

    @Test
    @DisplayName("Bộ dựng rehydrate nạp lại đủ vùng miền, năm lập chi và phiên bản")
    void rehydrateNapLaiDuTruong() {
        UUID id = UUID.randomUUID();

        Branch branch = Branch.rehydrate(id)
                .name("Chi Giáp")
                .slug("chi_giap")
                .path(BranchPath.of("goc.chi_giap"))
                .kind(BranchKind.CHI)
                .region(Region.BAC)
                .foundedYear(1802)
                .sortOrder(2)
                .version(7L)
                .build();

        assertThat(branch.id()).isEqualTo(id);
        assertThat(branch.region())
                .as("vung mien la dau vao chon bo quy tac danh xung cap REGION (FR-1.3a)")
                .isEqualTo(Region.BAC);
        assertThat(branch.foundedYear()).isEqualTo(1802);
        assertThat(branch.sortOrder()).isEqualTo(2);
        assertThat(branch.version()).isEqualTo(7L);
        assertThat(branch.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("Hai chi bằng nhau khi cùng id")
    void bangNhauTheoId() {
        UUID id = UUID.randomUUID();
        Branch a = Branch.create(id, "Chi Giáp", BranchPath.of("goc.chi_giap"), null, BranchKind.CHI);
        Branch b = Branch.create(id, "Tên khác hẳn", BranchPath.of("goc.chi_at"), null, BranchKind.NGANH);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
