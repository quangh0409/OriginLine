package vn.giapha.clan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Hồ sơ dòng họ — luật của domain")
class ClanProfileTest {

    private static ClanProfileContent noiDung(String nhan, ClanRegion vung) {
        return new ClanProfileContent("Họ Nguyễn Đình", "Nguyễn Đình", RootBranchLabel.of(nhan),
                vung, new AncestralHall("Từ đường họ Nguyễn Đình", "Đông Ngạc, Hà Nội"),
                "Nguyễn Đình Công", "Khai canh làng Đông Ngạc", new GioTo(12, 3));
    }

    @Nested
    @DisplayName("Nhãn gốc ltree")
    class NhanGoc {

        @ParameterizedTest
        @ValueSource(strings = {"ho_nguyen_dinh", "goc", "a1_b2", "x"})
        @DisplayName("một nhãn chữ thường không dấu thì nhận")
        void nhanHopLe(String nhan) {
            assertThat(RootBranchLabel.of(" " + nhan + " ").value()).isEqualTo(nhan);
        }

        @ParameterizedTest
        @ValueSource(strings = {"ho.nguyen", "Ho_Nguyen", "họ_nguyễn", "ho-nguyen", "ho nguyen", ""})
        @DisplayName("nhiều nhãn, chữ hoa, có dấu, gạch ngang, khoảng trắng hay rỗng thì từ chối")
        void nhanSai(String nhan) {
            assertThatThrownBy(() -> RootBranchLabel.of(nhan))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("dài hơn 64 ký tự thì từ chối — cùng trần với branch.slug")
        void nhanQuaDai() {
            assertThatThrownBy(() -> RootBranchLabel.of("a".repeat(65)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(RootBranchLabel.of("a".repeat(64)).value()).hasSize(64);
        }
    }

    @Nested
    @DisplayName("Nội dung hồ sơ")
    class NoiDung {

        @Test
        @DisplayName("tên bắt buộc, cắt khoảng trắng, tối đa 120 ký tự")
        void tenBatBuoc() {
            assertThatThrownBy(() -> new ClanProfileContent("   ", null, RootBranchLabel.of("goc"),
                    ClanRegion.BAC, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ClanProfileContent("x".repeat(121), null,
                    RootBranchLabel.of("goc"), ClanRegion.BAC, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(new ClanProfileContent("  Họ Lê  ", "  ", RootBranchLabel.of("ho_le"),
                    ClanRegion.NAM, null, null, null, null))
                    .satisfies(c -> {
                        assertThat(c.name()).isEqualTo("Họ Lê");
                        assertThat(c.shortName()).as("ô trống = không có giá trị").isNull();
                    });
        }

        @Test
        @DisplayName("ghi chú lập họ tối đa 2000 ký tự")
        void ghiChuToiDa() {
            assertThatThrownBy(() -> new ClanProfileContent("Họ Lê", null, RootBranchLabel.of("ho_le"),
                    ClanRegion.NAM, null, null, "x".repeat(2001), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("từ đường có địa chỉ mà không có tên thì từ chối")
        void tuDuongCanTen() {
            assertThatThrownBy(() -> new AncestralHall(" ", "Đông Ngạc"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("giỗ tổ: ngày 1..30, tháng 1..12")
        void gioTo() {
            assertThatThrownBy(() -> new GioTo(0, 3)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new GioTo(31, 3)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new GioTo(10, 13)).isInstanceOf(IllegalArgumentException.class);
            assertThat(new GioTo(30, 12)).isNotNull();
        }

        @Test
        @DisplayName("vùng miền chỉ nhận BAC/TRUNG/NAM, không phân biệt hoa thường")
        void vungMien() {
            assertThat(ClanRegion.parse(" trung ")).isEqualTo(ClanRegion.TRUNG);
            assertThatThrownBy(() -> ClanRegion.parse("TAY")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ClanRegion.parse(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("changedFrom liệt kê đúng những trường đổi, theo tên trường của hợp đồng")
        void truongDoi() {
            ClanProfileContent cu = noiDung("ho_nguyen_dinh", ClanRegion.BAC);
            ClanProfileContent moi = new ClanProfileContent("Họ Nguyễn Đình", "Nguyễn Đình",
                    RootBranchLabel.of("ho_nguyen_dinh"), ClanRegion.TRUNG, null,
                    "Nguyễn Đình Công", "Khai canh làng Đông Ngạc", new GioTo(12, 3));
            assertThat(moi.changedFrom(cu))
                    .containsExactly("region", "ancestralHall.name", "ancestralHall.address");
            assertThat(cu.changedFrom(null)).contains("name", "rootBranchPath", "region");
            assertThat(cu.changedFrom(cu)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Khoá nhãn gốc")
    class KhoaNhanGoc {

        @Test
        @DisplayName("phả đã có người (kể cả đã xoá mềm) thì không đổi được nhãn gốc")
        void coNguoiThiKhoa() {
            ClanProfile hoSo = ClanProfile.create(UUID.randomUUID(),
                    noiDung("ho_nguyen_dinh", ClanRegion.BAC));
            assertThatThrownBy(() -> hoSo.revise(noiDung("ho_nguyen", ClanRegion.BAC), 1))
                    .isInstanceOf(ClanProfile.RootLockedException.class);
            assertThat(hoSo.content().root().value())
                    .as("tu choi thi khong doi gi").isEqualTo("ho_nguyen_dinh");
        }

        @Test
        @DisplayName("phả chưa có ai thì đổi được nhãn gốc")
        void chuaCoNguoiThiDoiDuoc() {
            ClanProfile hoSo = ClanProfile.create(UUID.randomUUID(),
                    noiDung("ho_nguyen_dinh", ClanRegion.BAC));
            hoSo.revise(noiDung("ho_nguyen", ClanRegion.BAC), 0);
            assertThat(hoSo.content().root().value()).isEqualTo("ho_nguyen");
        }

        @Test
        @DisplayName("giữ nguyên nhãn gốc thì sửa được mọi thứ khác dù phả đã có 1.500 người")
        void giuNhanThiSuaTuDo() {
            ClanProfile hoSo = ClanProfile.create(UUID.randomUUID(),
                    noiDung("ho_nguyen_dinh", ClanRegion.BAC));
            hoSo.revise(noiDung("ho_nguyen_dinh", ClanRegion.TRUNG), 1500);
            assertThat(hoSo.content().region()).isEqualTo(ClanRegion.TRUNG);
        }
    }
}
