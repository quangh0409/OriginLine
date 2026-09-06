package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.command.LinkRelationshipCommand;
import vn.giapha.genealogy.application.view.RelationshipView;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonFixtures;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.PersonId;

/**
 * Nối hai nhân khẩu bằng một cạnh quan hệ — nơi giữ <b>bất biến quan trọng nhất của cả context</b>:
 * cạnh trong đồ thị AGE và dòng {@code relationship} được ghi trong <b>cùng một transaction</b>.
 *
 * <p>Ghi một bên mà thiếu bên kia thì không có gì báo lỗi, nhưng phả đồ và báo cáo sẽ nói hai điều
 * khác nhau — và không ai biết bên nào đúng.
 */
class LinkRelationshipServiceTest {

    private GenealogyServiceFixture fx;
    private Person cha;
    private Person con;
    private Person vo;

    @BeforeEach
    void dungHo() {
        fx = new GenealogyServiceFixture();
        fx.dangNhapAdmin();
        cha = fx.seed(PersonFixtures.nam("Người Cha"), fx.chiGiap);
        cha.placeInGeneration(4);
        fx.persons.seed(cha);
        con = fx.seed(PersonFixtures.nu("Người Con"), fx.chiGiap);
        vo = fx.seed(PersonFixtures.nu("Người Vợ"), fx.chiGiap);
    }

    @AfterEach
    void dongPhien() {
        fx.dangXuat();
    }

    private LinkRelationshipCommand chaCon(Person from, Person to, RelType loai) {
        return new LinkRelationshipCommand(from.rawId(), to.rawId(), loai, null, null, null, null, null);
    }

    @Test
    @DisplayName("Nối cha - con ghi CẢ cạnh đồ thị LẪN bản chiếu trong cùng một lượt")
    void noiChaConGhiCaDoThiLanBanChieu() {
        RelationshipView view = fx.links.link(chaCon(cha, con, RelType.PARENT_BIO));

        assertThat(fx.graph.lenhGhi)
                .as("thieu mot trong hai ben la tao ra du lieu lech ma khong co gi bao")
                .anyMatch(lenh -> lenh.startsWith("linkParent:PARENT_BIO:"));
        assertThat(fx.relationships.all()).singleElement()
                .satisfies(edge -> {
                    assertThat(edge.id()).isEqualTo(view.id());
                    assertThat(edge.fromPersonId()).isEqualTo(cha.rawId());
                    assertThat(edge.toPersonId()).isEqualTo(con.rawId());
                });
        assertThat(fx.audit.hanhDong()).containsExactly(AuditPort.Action.LINK_RELATIONSHIP);
        assertThat(fx.treeCache.soLanEvictAll).isEqualTo(1);
        assertThat(fx.suKien).hasSize(1);
    }

    @Test
    @DisplayName("Con chưa có đời thứ thì được đặt = đời cha + 1")
    void conChuaCoDoiThuThiDuocDatTheoCha() {
        fx.links.link(chaCon(cha, con, RelType.PARENT_BIO));

        assertThat(fx.persons.byId(PersonId.of(con.rawId())).orElseThrow().generation()).isEqualTo(5);
    }

    @Test
    @DisplayName("Con ĐÃ có đời thứ thì không bị đánh số lại — đánh số cả cây con là nghiệp vụ khác")
    void conDaCoDoiThuThiKhongBiDanhSoLai() {
        con.placeInGeneration(9);
        fx.persons.seed(con);

        fx.links.link(chaCon(cha, con, RelType.PARENT_BIO));

        assertThat(fx.persons.byId(PersonId.of(con.rawId())).orElseThrow().generation())
                .as("am tham danh so lai ca nhanh trong mot lenh noi canh la cach chac chan de "
                        + "mot hom nao do ca nhanh cay nhay doi ma khong ai giai thich duoc")
                .isEqualTo(9);
    }

    @Test
    @DisplayName("Cạnh cha - con trùng lặp bị từ chối")
    void canhChaConTrungLapBiTuChoi() {
        fx.links.link(chaCon(cha, con, RelType.PARENT_BIO));

        assertThatThrownBy(() -> fx.links.link(chaCon(cha, con, RelType.PARENT_ADOPT)))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.INVALID_RELATIONSHIP);
    }

    @Test
    @DisplayName("Quan hệ tạo chu trình bị chặn — 409 RELATIONSHIP_CYCLE")
    void quanHeTaoChuTrinhBiChan() {
        fx.links.link(chaCon(cha, con, RelType.PARENT_BIO));

        assertThatThrownBy(() -> fx.links.link(chaCon(con, cha, RelType.PARENT_BIO)))
                .as("chu trinh lam moi phep duyet (LCA, danh xung, pha do) chay vo tan")
                .isInstanceOf(GenealogyConflictException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.RELATIONSHIP_CYCLE);
    }

    @Test
    @DisplayName("Một người không thể có quan hệ với chính mình")
    void khongTuNoiVoiChinhMinh() {
        assertThatThrownBy(() -> fx.links.link(chaCon(cha, cha, RelType.PARENT_BIO)))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.INVALID_RELATIONSHIP);
    }

    @Test
    @DisplayName("Nối với nhân khẩu không tồn tại trả 404")
    void noiVoiNhanKhauKhongTonTaiTra404() {
        LinkRelationshipCommand cmd = new LinkRelationshipCommand(cha.rawId(), UUID.randomUUID(),
                RelType.PARENT_BIO, null, null, null, null, null);

        assertThatThrownBy(() -> fx.links.link(cmd)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Đa thê: hai cạnh SPOUSE khác spouse_order cùng tồn tại")
    void daTheHaiCanhSpouseCungTonTai() {
        Person voHai = fx.seed(PersonFixtures.nu("Vợ Hai"), fx.chiGiap);

        fx.links.link(new LinkRelationshipCommand(cha.rawId(), vo.rawId(), RelType.SPOUSE,
                null, 1, LocalDate.of(1960, 1, 1), null, null));
        fx.links.link(new LinkRelationshipCommand(cha.rawId(), voHai.rawId(), RelType.SPOUSE,
                null, 2, LocalDate.of(1970, 1, 1), null, null));

        assertThat(fx.relationships.all()).hasSize(2)
                .extracting(vn.giapha.genealogy.domain.Relationship::spouseOrder)
                .containsExactly(1, 2);
        assertThat(fx.graph.lenhGhi).anyMatch(lenh -> lenh.contains("linkSpouse") && lenh.endsWith("order=2"));
    }

    @Test
    @DisplayName("spouse_order sai bị dịch thành lỗi nghiệp vụ, không phải 500")
    void spouseOrderSaiThanhLoiNghiepVu() {
        assertThatThrownBy(() -> fx.links.link(new LinkRelationshipCommand(cha.rawId(), vo.rawId(),
                RelType.SPOUSE, null, 0, null, null, null)))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.INVALID_RELATIONSHIP);
    }

    @Test
    @DisplayName("Cạnh HEIR thiếu heirKind bị dịch thành lỗi nghiệp vụ")
    void canhHeirThieuHeirKindThanhLoiNghiepVu() {
        assertThatThrownBy(() -> fx.links.link(new LinkRelationshipCommand(cha.rawId(), con.rawId(),
                RelType.HEIR, null, null, null, null, null)))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.INVALID_RELATIONSHIP);
    }

    @Test
    @DisplayName("Đích tôn được ghi bằng cạnh HEIR vào cả đồ thị lẫn bản chiếu")
    void dichTonGhiBangCanhHeir() {
        fx.links.link(new LinkRelationshipCommand(cha.rawId(), con.rawId(), RelType.HEIR,
                HeirKind.DICH_TON, null, null, null, "đích tôn theo tộc ước"));

        assertThat(fx.relationships.all()).singleElement()
                .satisfies(edge -> {
                    assertThat(edge.relType()).isEqualTo(RelType.HEIR);
                    assertThat(edge.heirKind()).isEqualTo(HeirKind.DICH_TON);
                });
        assertThat(fx.graph.lenhGhi).anyMatch(lenh -> lenh.startsWith("linkHeir:DICH_TON:"));
    }

    @Test
    @DisplayName("Con nuôi là loại cạnh riêng, ghi đúng nhãn ADOPT xuống đồ thị")
    void conNuoiLaLoaiCanhRieng() {
        fx.links.link(chaCon(cha, con, RelType.PARENT_ADOPT));

        assertThat(fx.graph.lenhGhi).anyMatch(lenh -> lenh.startsWith("linkParent:PARENT_ADOPT:"));
        assertThat(fx.relationships.all()).singleElement()
                .extracting(vn.giapha.genealogy.domain.Relationship::relType)
                .isEqualTo(RelType.PARENT_ADOPT);
    }

    @Test
    @DisplayName("Kiểm quyền ở CẢ hai đầu cạnh")
    void kiemQuyenOCaHaiDauCanh() {
        Person nguoiChiAt = fx.seed(PersonFixtures.nam("Người Chi Ất"), fx.chiAt);
        fx.dangXuat();
        fx.dangNhapTruongChi(GenealogyServiceFixture.P_CHI_GIAP);

        assertThatThrownBy(() -> fx.links.link(chaCon(cha, nguoiChiAt, RelType.PARENT_BIO)))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.BRANCH_SCOPE_VIOLATION);

        assertThat(fx.relationships.all()).isEmpty();
        assertThat(fx.graph.lenhGhi).isEmpty();
    }

    @Test
    @DisplayName("Nhật ký của cạnh quan hệ không chứa dữ liệu Tầng 3")
    void nhatKyCanhQuanHeKhongChuaTang3() {
        fx.links.link(chaCon(cha, con, RelType.PARENT_BIO));

        assertThat(fx.audit.cuoiCung().after().keySet())
                .containsExactlyInAnyOrder("id", "fromPersonId", "toPersonId", "relType",
                        "spouseOrder", "heirKind");
    }
}
