package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonFixtures;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.PersonId;

/**
 * <b>Xoá mềm và khôi phục</b> ở tầng use case (FR-1.5).
 *
 * <p>Điều được ghim mạnh nhất: sau khi xoá mềm, <b>đỉnh trong đồ thị và mọi cạnh quan hệ vẫn còn
 * nguyên</b>; chỉ có cờ {@code is_deleted} trên đỉnh được cập nhật để phép duyệt <i>đi xuyên qua</i>
 * người này mà không nhận họ làm kết quả. Không có và sẽ không bao giờ có đường xoá cứng.
 */
class SoftDeleteAndRestoreServiceTest {

    private GenealogyServiceFixture fx;
    private Person ong;
    private Person cha;
    private Person chau;

    @BeforeEach
    void dungHoBaDoi() {
        fx = new GenealogyServiceFixture();
        fx.dangNhapAdmin();
        ong = fx.seed(PersonFixtures.nam("Ông Nội"), fx.chiGiap);
        cha = fx.seed(PersonFixtures.nam("Người Cha"), fx.chiGiap);
        chau = fx.seed(PersonFixtures.nu("Cháu Gái"), fx.chiGiap);
        fx.seedParent(ong, cha, false);
        fx.seedParent(cha, chau, false);
    }

    @AfterEach
    void dongPhien() {
        fx.dangXuat();
    }

    @Test
    @DisplayName("Xoá mềm giữ nguyên đỉnh đồ thị và toàn bộ cạnh quan hệ — cây không đứt")
    void xoaMemGiuNguyenDinhVaCanh() {
        fx.softDelete.softDelete(cha.rawId(), "trùng bản ghi");

        assertThat(fx.persons.byId(PersonId.of(cha.rawId())).orElseThrow().isDeleted()).isTrue();
        assertThat(fx.graph.nodeExists(cha.rawId()))
                .as("xoa cung nguoi cha se cat dut duong noi giua ong noi va chau gai")
                .isTrue();
        assertThat(fx.graph.lenhGhi).noneMatch(lenh -> lenh.startsWith("unlink:"));
        assertThat(fx.relationships.all()).hasSize(2);
        assertThat(fx.relationships.byPerson(cha.rawId())).hasSize(2);
        assertThat(fx.graph.descendants(ong.rawId(), 5))
                .extracting(vn.giapha.genealogy.domain.GraphNodeRef::personId)
                .contains(chau.rawId());
    }

    @Test
    @DisplayName("Đỉnh đồ thị được đồng bộ cờ is_deleted để phép duyệt đi xuyên qua")
    void dinhDoThiDuocDongBoCoIsDeleted() {
        fx.softDelete.softDelete(cha.rawId(), null);

        assertThat(fx.graph.nodeDaDongBo).containsExactly(cha.rawId() + ":deleted=true");
    }

    @Test
    @DisplayName("Xoá mềm ghi nhật ký SOFT_DELETE kèm lý do và dọn cache cây")
    void xoaMemGhiNhatKyVaDonCache() {
        fx.softDelete.softDelete(cha.rawId(), "gộp theo biên bản họp họ 2024");

        assertThat(fx.audit.hanhDong()).containsExactly(AuditPort.Action.SOFT_DELETE);
        assertThat(fx.audit.cuoiCung().note()).isEqualTo("gộp theo biên bản họp họ 2024");
        assertThat(fx.audit.cuoiCung().changedFields()).containsExactly("isDeleted");
        assertThat(fx.audit.cuoiCung().before()).containsEntry("isDeleted", false);
        assertThat(fx.audit.cuoiCung().after()).containsEntry("isDeleted", true);
        assertThat(fx.treeCache.soLanEvictAll).isEqualTo(1);
    }

    @Test
    @DisplayName("Xoá mềm hai lần trả 409 PERSON_ALREADY_DELETED")
    void xoaMemHaiLanTra409() {
        fx.softDelete.softDelete(cha.rawId(), null);

        assertThatThrownBy(() -> fx.softDelete.softDelete(cha.rawId(), null))
                .isInstanceOf(GenealogyConflictException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.PERSON_ALREADY_DELETED);
    }

    @Test
    @DisplayName("Xoá nhân khẩu không tồn tại trả 404")
    void xoaNhanKhauKhongTonTaiTra404() {
        assertThatThrownBy(() -> fx.softDelete.softDelete(UUID.randomUUID(), null))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.NOT_FOUND);
    }

    @Test
    @DisplayName("Trưởng Chi không xoá được nhân khẩu của chi khác")
    void truongChiKhongXoaDuocChiKhac() {
        fx.dangXuat();
        fx.dangNhapTruongChi(GenealogyServiceFixture.P_CHI_AT);

        assertThatThrownBy(() -> fx.softDelete.softDelete(cha.rawId(), null))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.BRANCH_SCOPE_VIOLATION);

        assertThat(fx.persons.byId(PersonId.of(cha.rawId())).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("Khôi phục chỉ dành cho Hội đồng và Quản trị hệ thống — Trưởng Chi bị 403")
    void khoiPhucChiDanhChoClanWide() {
        fx.softDelete.softDelete(cha.rawId(), null);
        fx.dangXuat();
        fx.dangNhapTruongChi(GenealogyServiceFixture.P_CHI_GIAP);

        assertThatThrownBy(() -> fx.restore.restore(cha.rawId(), null))
                .as("Truong chi khoi phuc lai nguoi ma Hoi dong vua cho xoa la lat quyet dinh cap tren")
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.FORBIDDEN);

        assertThat(fx.persons.byId(PersonId.of(cha.rawId())).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("Khôi phục gỡ cờ, đồng bộ lại đỉnh và ghi nhật ký RESTORE")
    void khoiPhucGoCoVaGhiNhatKy() {
        fx.softDelete.softDelete(cha.rawId(), null);

        fx.restore.restore(cha.rawId(), "nhầm bản ghi");

        assertThat(fx.persons.byId(PersonId.of(cha.rawId())).orElseThrow().isDeleted()).isFalse();
        assertThat(fx.graph.nodeDaDongBo).contains(cha.rawId() + ":deleted=false");
        assertThat(fx.audit.hanhDong()).containsExactly(
                AuditPort.Action.SOFT_DELETE, AuditPort.Action.RESTORE);
        assertThat(fx.treeCache.soLanEvictAll).isEqualTo(2);
    }

    @Test
    @DisplayName("Người đã xoá mềm biến mất khỏi phả đồ với vai thường nhưng vẫn nằm trong kho")
    void nguoiDaXoaMemVanNamTrongKho() {
        fx.softDelete.softDelete(cha.rawId(), null);

        assertThat(fx.persons.byId(PersonId.of(cha.rawId())))
                .as("kho phai tra ca ban ghi da xoa mem — loc la viec cua tang application")
                .isPresent();
        assertThat(fx.privacy.canSee(fx.persons.byId(PersonId.of(cha.rawId())).orElseThrow(),
                new CallerContext(CallerRole.MEMBER, UUID.randomUUID(), java.util.List.of(),
                        GenealogyServiceFixture.P_CHI_GIAP))).isFalse();
    }
}
