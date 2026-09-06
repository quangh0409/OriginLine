package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.command.MoveBranchCommand;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonFixtures;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.PersonId;

/**
 * Chuyển một nhân khẩu sang chi/ngành khác.
 *
 * <p>Chi/ngành là <b>phạm vi phân quyền hạng nhất</b>, nên đổi chi của một người là đổi luôn danh
 * sách người được sửa hồ sơ đó. Vì vậy người gọi phải có quyền ở <b>cả chi cũ lẫn chi mới</b>; chỉ
 * kiểm chi đích là mở đúng vòng lách kinh điển của phân quyền theo phạm vi.
 */
class MoveBranchServiceTest {

    private GenealogyServiceFixture fx;
    private Person nguoi;
    private Person con;

    @BeforeEach
    void dungHo() {
        fx = new GenealogyServiceFixture();
        fx.dangNhapAdmin();
        nguoi = fx.seed(PersonFixtures.nam("Nguyễn Văn Cả"), fx.chiGiap);
        con = fx.seed(PersonFixtures.nu("Nguyễn Thị Con"), fx.chiGiap);
        fx.seedParent(nguoi, con, false);
    }

    @AfterEach
    void dongPhien() {
        fx.dangXuat();
    }

    @Test
    @DisplayName("Chuyển chi ghi nhật ký MOVE_BRANCH và dọn cache cây")
    void chuyenChiGhiNhatKyVaDonCache() {
        fx.moveBranch.move(new MoveBranchCommand(nguoi.rawId(), fx.chiAt.id(), "sắp xếp lại chi"));

        assertThat(fx.persons.byId(PersonId.of(nguoi.rawId())).orElseThrow().primaryBranchId())
                .isEqualTo(fx.chiAt.id());
        assertThat(fx.audit.hanhDong()).containsExactly(AuditPort.Action.MOVE_BRANCH);
        assertThat(fx.audit.cuoiCung().changedFields()).containsExactly("primaryBranchId");
        assertThat(fx.treeCache.soLanEvictAll).isEqualTo(1);
    }

    @Test
    @DisplayName("Chuyển chi KHÔNG đụng tới cạnh quan hệ — huyết thống không đổi theo giấy tờ")
    void chuyenChiKhongDungToiCanhQuanHe() {
        fx.moveBranch.move(new MoveBranchCommand(nguoi.rawId(), fx.chiAt.id(), null));

        assertThat(fx.relationships.all()).hasSize(1);
        assertThat(fx.graph.lenhGhi).isEmpty();
        assertThat(fx.persons.byId(PersonId.of(con.rawId())).orElseThrow().primaryBranchId())
                .as("chuyen mot nguoi khong keo theo ca cay con")
                .isEqualTo(fx.chiGiap.id());
    }

    @Test
    @DisplayName("Trưởng Chi chỉ có quyền ở chi ĐÍCH thì không kéo được người của chi khác về")
    void chiCoQuyenOChiDichThiKhongKeoDuocNguoiVe() {
        fx.dangXuat();
        fx.dangNhapTruongChi(GenealogyServiceFixture.P_CHI_AT);

        assertThatThrownBy(() -> fx.moveBranch.move(
                new MoveBranchCommand(nguoi.rawId(), fx.chiAt.id(), null)))
                .as("neu khong chan thi mot Truong chi keo nguoi ve chi minh roi tu cap quyen sua ho")
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.BRANCH_SCOPE_VIOLATION);

        assertThat(fx.persons.byId(PersonId.of(nguoi.rawId())).orElseThrow().primaryBranchId())
                .isEqualTo(fx.chiGiap.id());
    }

    @Test
    @DisplayName("Trưởng Chi chỉ có quyền ở chi CŨ cũng không đẩy được người sang chi khác")
    void chiCoQuyenOChiCuThiKhongDayDuocNguoiDi() {
        fx.dangXuat();
        fx.dangNhapTruongChi(GenealogyServiceFixture.P_CHI_GIAP);

        assertThatThrownBy(() -> fx.moveBranch.move(
                new MoveBranchCommand(nguoi.rawId(), fx.chiAt.id(), null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Có quyền ở cả hai chi thì chuyển được")
    void coQuyenOCaHaiChiThiChuyenDuoc() {
        fx.dangXuat();
        fx.dangNhapTruongChi(GenealogyServiceFixture.P_GOC);

        fx.moveBranch.move(new MoveBranchCommand(nguoi.rawId(), fx.chiAt.id(), null));

        assertThat(fx.persons.byId(PersonId.of(nguoi.rawId())).orElseThrow().primaryBranchId())
                .isEqualTo(fx.chiAt.id());
    }

    @Test
    @DisplayName("Chi đích không tồn tại trả 404")
    void chiDichKhongTonTaiTra404() {
        assertThatThrownBy(() -> fx.moveBranch.move(
                new MoveBranchCommand(nguoi.rawId(), UUID.randomUUID(), null)))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.NOT_FOUND);
    }

    @Test
    @DisplayName("Nhân khẩu không tồn tại trả 404")
    void nhanKhauKhongTonTaiTra404() {
        assertThatThrownBy(() -> fx.moveBranch.move(
                new MoveBranchCommand(UUID.randomUUID(), fx.chiAt.id(), null)))
                .isInstanceOf(NotFoundException.class);
    }
}
