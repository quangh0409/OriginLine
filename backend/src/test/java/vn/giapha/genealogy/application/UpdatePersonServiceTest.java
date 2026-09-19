package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.GenealogyTestDoubles.FakeTabooNamePort;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonFixtures;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.PrivacyFieldGroup;
import vn.giapha.genealogy.domain.ShareScope;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;
import vn.giapha.shared.vo.PersonId;

/**
 * Use case <b>sửa một phần hồ sơ nhân khẩu</b>.
 *
 * <p>Điểm được soi kỹ nhất là <b>khoá lạc quan</b>: gia phả là dữ liệu nhiều người cùng biên tập,
 * nên chuyện hai người mở cùng một form không phải trường hợp hiếm. {@code expectedVersion} đến từ
 * {@code ETag} của lần đọc trước; lệch thì trả 409 để người dùng nạp lại thay vì âm thầm ghi đè
 * công của nhau.
 */
class UpdatePersonServiceTest {

    private GenealogyServiceFixture fx;
    private Person nguoi;

    @BeforeEach
    void dungHo() {
        fx = new GenealogyServiceFixture();
        fx.dangNhapAdmin();
        nguoi = fx.seed(PersonFixtures.nam("Nguyễn Văn Cả"), fx.chiGiap);
        nguoi.placeInGeneration(4);
        fx.persons.seed(nguoi);
    }

    @AfterEach
    void dongPhien() {
        fx.dangXuat();
    }

    @Test
    @DisplayName("Sửa hồ sơ ghi bản mới, ghi nhật ký UPDATE và dọn cache cây")
    void suaHoSoGhiNhatKyVaDonCache() {
        PersonView view = fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .ngheNghiep("Thầy đồ").build());

        assertThat(view.occupation()).isEqualTo("Thầy đồ");
        assertThat(fx.audit.hanhDong()).containsExactly(AuditPort.Action.UPDATE);
        assertThat(fx.audit.cuoiCung().changedFields()).containsExactly("occupation");
        assertThat(fx.treeCache.soLanEvictAll).isEqualTo(1);
    }

    @Test
    @DisplayName("Không có gì đổi thì không ghi, không nhật ký, không dọn cache")
    void khongCoGiDoiThiKhongGhi() {
        int truoc = fx.persons.soLanSave;

        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId()).build());

        assertThat(fx.persons.soLanSave).isEqualTo(truoc);
        assertThat(fx.audit.dong).isEmpty();
        assertThat(fx.treeCache.soLanEvictAll).isZero();
    }

    @Test
    @DisplayName("Phiên bản lệch trả 409 OPTIMISTIC_LOCK_CONFLICT, không ghi đè")
    void phienBanLechTra409() {
        assertThatThrownBy(() -> fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .phienBan(99L).ngheNghiep("Thầy đồ").build()))
                .isInstanceOf(GenealogyConflictException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.OPTIMISTIC_LOCK_CONFLICT);

        assertThat(fx.persons.byId(PersonId.of(nguoi.rawId())).orElseThrow().occupation()).isNull();
    }

    @Test
    @DisplayName("Không gửi phiên bản thì bỏ qua kiểm khoá lạc quan (kiểm ở tầng api)")
    void khongGuiPhienBanThiBoQuaKiemKhoa() {
        PersonView view = fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .ngheNghiep("Thầy đồ").build());

        assertThat(view.occupation()).isEqualTo("Thầy đồ");
    }

    @Test
    @DisplayName("HỒI QUY — phiên bản trả về sau PATCH phải là phiên bản ĐÃ GHI, không phải bản cũ")
    void phienBanTraVeSauPatchPhaiLaPhienBanDaGhi() {
        long phienBanTruoc = fx.persons.versionOf(nguoi.rawId());

        PersonView view = fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .phienBan(phienBanTruoc).ngheNghiep("Thầy đồ").build());

        assertThat(view.version())
                .as("ETag cua phan hoi PATCH sinh tu version nay; tra lai ban cu thi ETag tre mot "
                        + "nhip va lan PATCH ke tiep bi 409 gia")
                .isEqualTo(fx.persons.versionOf(nguoi.rawId()));
    }

    @Test
    @DisplayName("HỒI QUY — hai lần PATCH liên tiếp dùng ETag vừa nhận không được sinh 409 giả")
    void haiLanPatchLienTiepKhongDuocSinh409Gia() {
        PersonView lanMot = fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .phienBan(fx.persons.versionOf(nguoi.rawId()))
                .ngheNghiep("Thầy đồ").build());

        org.assertj.core.api.Assertions.assertThatCode(
                        () -> fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                                .phienBan(lanMot.version())
                                .nguyenQuan("Bắc Ninh").build()))
                .as("khong ai sua ban ghi giua hai lan PATCH nen lan hai phai thanh cong")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Thay thế danh sách tên có kiểm kỵ húy; chưa xác nhận thì không ghi gì")
    void thayTheTenCoKiemKyHuy() {
        fx.tabooPort.vaChamVoi("Nguyễn Văn Tuân", FakeTabooNamePort.cuTo("Nguyễn Văn Tuân", 2));
        int truoc = fx.persons.soLanSave;

        assertThatThrownBy(() -> fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .ten(List.of(PersonName.of(NameType.HUY, "Nguyễn Văn Tuân", true))).build()))
                .isInstanceOf(TabooNameConflictException.class);

        assertThat(fx.persons.soLanSave).isEqualTo(truoc);
        assertThat(fx.persons.byId(PersonId.of(nguoi.rawId())).orElseThrow().displayName())
                .isEqualTo("Nguyễn Văn Cả");
    }

    @Test
    @DisplayName("Có xác nhận kỵ húy thì tên được thay thế toàn bộ")
    void coXacNhanKyHuyThiThayTheTen() {
        fx.tabooPort.vaChamVoi("Nguyễn Văn Tuân", FakeTabooNamePort.cuTo("Nguyễn Văn Tuân", 2));

        PersonView view = fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .ten(List.of(PersonName.of(NameType.HUY, "Nguyễn Văn Tuân", true)))
                .xacNhanKyHuy().build());

        assertThat(view.displayName()).isEqualTo("Nguyễn Văn Tuân");
        assertThat(fx.audit.cuoiCung().changedFields()).contains("names");
    }

    @Test
    @DisplayName("Báo mất: gửi kèm alive=false thì ngày mất được ghi")
    void baoMatGuiKemAliveFalse() {
        PersonView view = fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .conSong(false)
                .ngayMat(vn.giapha.genealogy.domain.LifeDate.ofLunar(LunarDate.of(2024, 8, 15)))
                .build());

        assertThat(view.alive()).isFalse();
        assertThat(view.death().lunar()).isEqualTo(LunarDate.of(2024, 8, 15));
        assertThat(fx.audit.cuoiCung().changedFields()).contains("isAlive", "death");
    }

    @Test
    @DisplayName("Đính chính còn sống thì ngày mất bị gỡ, không tồn tại đồng thời")
    void dinhChinhConSongThiGoNgayMat() {
        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .conSong(false)
                .ngayMat(vn.giapha.genealogy.domain.LifeDate.ofLunar(LunarDate.of(2024, 8, 15)))
                .build());

        PersonView view = fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .conSong(true).build());

        assertThat(view.alive()).isTrue();
        assertThat(view.death()).isNull();
    }

    @Test
    @DisplayName("Chỉ chính chủ và Quản trị hệ thống được đổi mức riêng tư — Hội đồng thì không")
    void chiChinhChuVaAdminDuocDoiMucRiengTu() {
        fx.dangXuat();
        fx.dangNhapHoiDong();

        assertThatThrownBy(() -> fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .mucRiengTu(PrivacyFieldGroup.CONTACT, ShareScope.CLAN).build()))
                .as("ban dong thuan la y chi cua nguoi duoc ghi trong gia pha, khong phai cong cu quan tri")
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.FORBIDDEN);
    }

    @Test
    @DisplayName("Chính chủ tự đổi được mức riêng tư của mình")
    void chinhChuTuDoiDuocMucRiengTu() {
        fx.dangXuat();
        fx.dangNhapThanhVien(nguoi.rawId(), GenealogyServiceFixture.P_CHI_GIAP);

        PersonView view = fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .mucRiengTu(PrivacyFieldGroup.OCCUPATION, ShareScope.CLAN).build());

        assertThat(view.privacyConsent().scopeOf(PrivacyFieldGroup.OCCUPATION))
                .isEqualTo(ShareScope.CLAN);
        assertThat(view.privacyConsent().scopeOf(PrivacyFieldGroup.CONTACT))
                .as("hop nhat chu khong thay the: nhom khong duoc nhac toi giu nguyen muc kin")
                .isEqualTo(ShareScope.PRIVATE);
    }

    @Test
    @DisplayName("Trưởng Chi không sửa được hồ sơ nhân khẩu thuộc chi khác")
    void truongChiKhongSuaDuocHoSoChiKhac() {
        fx.dangXuat();
        fx.dangNhapTruongChi(GenealogyServiceFixture.P_CHI_AT);

        assertThatThrownBy(() -> fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .ngheNghiep("Thầy đồ").build()))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.BRANCH_SCOPE_VIOLATION);
    }

    @Test
    @DisplayName("Cờ isDeleted trong body được uỷ thác sang use case xoá mềm / khôi phục")
    void coIsDeletedDuocUyThacSangUseCaseRieng() {
        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId()).daXoa(true).build());

        assertThat(fx.persons.byId(PersonId.of(nguoi.rawId())).orElseThrow().isDeleted()).isTrue();
        assertThat(fx.audit.hanhDong()).contains(AuditPort.Action.SOFT_DELETE);
        assertThat(fx.graph.nodeDaDongBo)
                .as("dinh do thi phai duoc dong bo co is_deleted, KHONG bi xoa khoi do thi")
                .contains(nguoi.rawId() + ":deleted=true");

        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId()).daXoa(false).build());

        assertThat(fx.persons.byId(PersonId.of(nguoi.rawId())).orElseThrow().isDeleted()).isFalse();
        assertThat(fx.audit.hanhDong()).contains(AuditPort.Action.RESTORE);
    }

    @Test
    @DisplayName("Đổi chi trong body được uỷ thác sang use case chuyển chi, có kiểm quyền hai đầu")
    void doiChiDuocUyThacSangUseCaseChuyenChi() {
        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId()).chi(fx.chiAt.id()).build());

        assertThat(fx.persons.byId(PersonId.of(nguoi.rawId())).orElseThrow().primaryBranchId())
                .isEqualTo(fx.chiAt.id());
        assertThat(fx.audit.hanhDong()).contains(AuditPort.Action.MOVE_BRANCH);
    }

    @Test
    @DisplayName("Đổi giới tính đồng bộ lại đỉnh đồ thị")
    void doiGioiTinhDongBoDinhDoThi() {
        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .gioiTinh(Gender.FEMALE).build());

        assertThat(fx.graph.nodeDaDongBo).contains(nguoi.rawId() + ":deleted=false");
    }

    @Test
    @DisplayName("Xoá trắng một trường bằng clearFields ghi đúng vào changed_fields")
    void xoaTrangMotTruongGhiDungChangedFields() {
        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId()).ngheNghiep("Thầy đồ").build());

        PersonView view = fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .xoaTrangNgheNghiep().build());

        assertThat(view.occupation()).isNull();
        assertThat(fx.audit.cuoiCung().changedFields()).containsExactly("occupation");
    }

    @Test
    @DisplayName("Nhật ký giữ ảnh trước và sau, cả hai đều không chứa dữ liệu Tầng 3")
    void nhatKyGiuAnhTruocSauKhongCoTang3() {
        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .lienHe(PersonFixtures.lienHe()).build());

        var ban = fx.audit.cuoiCung();
        assertThat(ban.before()).isNotNull();
        assertThat(ban.after()).isNotNull();
        assertThat(String.valueOf(ban.before()) + ban.after())
                .doesNotContain("0912345678")
                .doesNotContain("nguoidung@example.com");
        assertThat(ban.changedFields()).containsExactly("contact");
    }
}
