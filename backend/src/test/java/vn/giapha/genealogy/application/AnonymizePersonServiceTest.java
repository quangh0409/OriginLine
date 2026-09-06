package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.domain.FieldChange;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonFixtures;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.PrivacyLevel;
import vn.giapha.genealogy.domain.ProfileEdit;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * <b>Ẩn danh hoá theo yêu cầu hợp pháp</b> (Nghị định 13/2023) ở tầng use case.
 *
 * <p>Nghĩa vụ pháp lý được thực hiện bằng cách xoá dữ liệu Tầng 3 và <b>giữ lại node phả hệ</b>:
 * một yêu cầu xoá của <i>một</i> cá nhân không được phép phá hỏng dữ liệu của <i>cả dòng họ</i>,
 * trong đó có những người đã khuất mà quyền riêng tư không còn áp dụng.
 *
 * <p>Chỉ <b>chính chủ thể</b> hoặc {@code ADMIN} được gọi — Trưởng chi và Hội đồng thì không: ẩn
 * danh hoá hộ người khác là một quyết định về dữ liệu cá nhân, không phải thao tác quản trị phả hệ.
 */
class AnonymizePersonServiceTest {

    private GenealogyServiceFixture fx;
    private Person cha;
    private Person toi;
    private Person con;

    @BeforeEach
    void dungHo() {
        fx = new GenealogyServiceFixture();
        toi = Person.create(PersonId.newId(), Gender.FEMALE, true, List.of(
                PersonName.of(NameType.THUONG_GOI, "Nguyễn Thị Lan", true),
                PersonName.of(NameType.HUY, "Nguyễn Thị Lan Anh", false)));
        toi.placeInGeneration(6);
        toi.applyProfileEdit(ProfileEdit.builder()
                .birth(FieldChange.set(PersonFixtures.ngaySinh(1985, 4, 17)))
                .currentPlaceFull(FieldChange.set("số 12 ngõ 30 phố Hàng Bột, Hà Nội"))
                .occupation(FieldChange.set("Giáo viên"))
                .biography(FieldChange.set("tiểu sử riêng tư"))
                .avatarKey(FieldChange.set("portraits/lan.jpg"))
                .contact(FieldChange.set(PersonFixtures.lienHe()))
                .attributes(FieldChange.set(Map.of("soCMND", "001188000123")))
                .build());
        fx.seed(toi, fx.chiGiap);

        cha = fx.seed(PersonFixtures.nam("Người Cha"), fx.chiGiap);
        con = fx.seed(PersonFixtures.nam("Người Con"), fx.chiGiap);
        fx.seedParent(cha, toi, false);
        fx.seedParent(toi, con, false);
    }

    @AfterEach
    void dongPhien() {
        fx.dangXuat();
    }

    @Test
    @DisplayName("Chính chủ ẩn danh hoá: dữ liệu Tầng 3 biến mất, node phả hệ ở lại")
    void chinhChuAnDanhHoa() {
        fx.dangNhapThanhVien(toi.rawId(), GenealogyServiceFixture.P_CHI_GIAP);

        fx.anonymize.anonymize(toi.rawId(), "yêu cầu bằng văn bản ngày 01/09/2026");

        Person sau = fx.persons.byId(PersonId.of(toi.rawId())).orElseThrow();
        assertThat(sau.contact().isEmpty()).isTrue();
        assertThat(sau.currentPlaceFull()).isNull();
        assertThat(sau.occupation()).isNull();
        assertThat(sau.biography()).isNull();
        assertThat(sau.avatarKey()).isNull();
        assertThat(sau.attributes()).isEmpty();
        assertThat(sau.isAnonymized()).isTrue();
        assertThat(sau.privacyLevel()).isEqualTo(PrivacyLevel.RESTRICTED);

        assertThat(sau.isDeleted()).as("an danh hoa KHONG phai xoa mem").isFalse();
        assertThat(sau.displayName()).isEqualTo("Nguyễn Thị Lan");
        assertThat(sau.generation()).isEqualTo(6);
        assertThat(sau.primaryBranchId()).isEqualTo(fx.chiGiap.id());
    }

    @Test
    @DisplayName("Cạnh quan hệ hai bên KHÔNG bị đụng tới — cây vẫn nối được ông với cháu")
    void canhQuanHeKhongBiDungToi() {
        fx.dangNhapThanhVien(toi.rawId(), GenealogyServiceFixture.P_CHI_GIAP);

        fx.anonymize.anonymize(toi.rawId(), null);

        assertThat(fx.relationships.all()).hasSize(2);
        assertThat(fx.relationships.byPerson(toi.rawId())).hasSize(2);
        assertThat(fx.graph.nodeExists(toi.rawId())).isTrue();
        assertThat(fx.graph.lenhGhi)
                .as("use case an danh hoa khong duoc phat bat ky lenh ghi do thi nao")
                .isEmpty();
        assertThat(fx.graph.descendants(cha.rawId(), 5))
                .extracting(vn.giapha.genealogy.domain.GraphNodeRef::personId)
                .contains(con.rawId());
    }

    @Test
    @DisplayName("Quản trị hệ thống ẩn danh hoá thay mặt chủ thể khi có yêu cầu bằng văn bản")
    void adminAnDanhHoaThayMatChuThe() {
        fx.dangNhapAdmin();

        fx.anonymize.anonymize(toi.rawId(), "công văn số 12");

        assertThat(fx.persons.byId(PersonId.of(toi.rawId())).orElseThrow().isAnonymized()).isTrue();
    }

    @Test
    @DisplayName("Trưởng Chi KHÔNG được ẩn danh hoá người khác dù người đó trong chi mình")
    void truongChiKhongDuocAnDanhHoaNguoiKhac() {
        fx.dangNhapTruongChi(GenealogyServiceFixture.P_CHI_GIAP);

        assertThatThrownBy(() -> fx.anonymize.anonymize(toi.rawId(), null))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.FORBIDDEN);

        assertThat(fx.persons.byId(PersonId.of(toi.rawId())).orElseThrow().isAnonymized()).isFalse();
    }

    @Test
    @DisplayName("Hội đồng Tộc biểu cũng KHÔNG được ẩn danh hoá người khác")
    void hoiDongCungKhongDuocAnDanhHoaNguoiKhac() {
        fx.dangNhapHoiDong();

        assertThatThrownBy(() -> fx.anonymize.anonymize(toi.rawId(), null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Nhật ký ghi việc ĐÃ ẩn danh hoá, không chép lại thứ vừa bị xoá")
    void nhatKyKhongChepLaiThuVuaBiXoa() {
        fx.dangNhapAdmin();

        fx.anonymize.anonymize(toi.rawId(), "yêu cầu hợp pháp");

        assertThat(fx.audit.hanhDong()).containsExactly(AuditPort.Action.ANONYMIZE);
        String noiDung = String.valueOf(fx.audit.cuoiCung().before())
                + fx.audit.cuoiCung().after();
        assertThat(noiDung)
                .as("chep du lieu Tang 3 vao audit_log la giu lai dung cai ma nguoi ta vua yeu cau xoa")
                .doesNotContain("0912345678")
                .doesNotContain("nguoidung@example.com")
                .doesNotContain("Hàng Bột")
                .doesNotContain("001188000123")
                .doesNotContain("portraits/lan.jpg");
        assertThat(fx.audit.cuoiCung().changedFields()).contains("contact", "attributes", "birth");
        assertThat(fx.treeCache.soLanEvictAll).isEqualTo(1);
    }

    @Test
    @DisplayName("Chỉ còn tên chính; các lớp tên còn lại biến mất")
    void chiConTenChinh() {
        fx.dangNhapAdmin();

        fx.anonymize.anonymize(toi.rawId(), null);

        Person sau = fx.persons.byId(PersonId.of(toi.rawId())).orElseThrow();
        assertThat(sau.names()).hasSize(1);
        assertThat(sau.tabooNames()).isEmpty();
    }
}
