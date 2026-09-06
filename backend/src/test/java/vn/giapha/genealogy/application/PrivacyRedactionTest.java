package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.GenealogyTestDoubles.FakeCallerIdentity;
import vn.giapha.genealogy.application.view.PersonSummaryView;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.Branch;
import vn.giapha.genealogy.domain.BranchKind;
import vn.giapha.genealogy.domain.DatePrecision;
import vn.giapha.genealogy.domain.FieldChange;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonFixtures;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.PrivacyLevel;
import vn.giapha.genealogy.domain.ProfileEdit;
import vn.giapha.shared.vo.BranchPath;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * Nội dung thực sự đi ra khỏi bộ lọc phân tầng riêng tư.
 *
 * <p>Khác với {@code PrivacyTierServiceTest} (chỉ hỏi "tầng nào"), lớp này soi <b>từng trường</b>
 * của {@code PersonView} / {@code PersonSummaryView}: trường bị ẩn phải là {@code null} và không
 * được phân biệt với trường không có dữ liệu.
 */
class PrivacyRedactionTest {

    private static final BranchPath CHI_GIAP = BranchPath.of("goc.chi_giap");
    private static final BranchPath CHI_AT = BranchPath.of("goc.chi_at");

    private final FakeCallerIdentity identity = new FakeCallerIdentity();
    private final PrivacyTierService privacy = new PrivacyTierService(identity);

    private final Branch chiGiap = Branch.create(UUID.randomUUID(), "Chi Giáp", CHI_GIAP,
            null, BranchKind.CHI);
    private final BranchDirectory danhBaChi = BranchDirectory.of(chiGiap);

    private Person hoSoDayDu(boolean conSong) {
        Person person = Person.create(PersonId.newId(), Gender.FEMALE, conSong, List.of(
                PersonName.of(NameType.THUONG_GOI, "Nguyễn Thị Lan", true),
                PersonName.of(NameType.HUY, "Nguyễn Thị Lan Anh", false),
                PersonName.of(NameType.PHAP_DANH, "Diệu Tâm", false)));
        person.moveToBranch(chiGiap.id());
        person.placeInGeneration(6);
        person.applyProfileEdit(ProfileEdit.builder()
                .birth(FieldChange.set(PersonFixtures.ngaySinh(1960, 4, 17)))
                .nativePlace(FieldChange.set("Bắc Ninh"))
                .currentPlaceProvince(FieldChange.set("Hà Nội"))
                .currentPlaceFull(FieldChange.set("số 12 ngõ 30 phố Hàng Bột, Hà Nội"))
                .occupation(FieldChange.set("Giáo viên"))
                .biography(FieldChange.set("Tiểu sử chi tiết..."))
                .avatarKey(FieldChange.set("portraits/lan.jpg"))
                .contact(FieldChange.set(PersonFixtures.lienHe()))
                .attributes(FieldChange.set(Map.of("hocVi", "Cử nhân")))
                .build());
        if (!conSong) {
            person.markDeceased(PersonFixtures.ngaySinh(2010, 1, 2));
        }
        return person;
    }

    private CallerContext vai(CallerRole role, BranchPath chiNha, BranchPath... quanTri) {
        return new CallerContext(role, UUID.randomUUID(), List.of(quanTri), chiNha);
    }

    @Test
    @DisplayName("Tầng 1: chỉ tên chính, không nghề nghiệp, không năm sinh, không liên hệ")
    void tang1ChiTraTenChinh() {
        PersonView view = privacy.toView(hoSoDayDu(true), vai(CallerRole.MEMBER, CHI_AT), danhBaChi);

        assertThat(view.names())
                .as("ten huy la can cu canh bao ky huy cho nguoi khac; pho no ra la mat y nghia "
                        + "cua tuc kieng ten")
                .singleElement()
                .extracting(PersonName::fullName).isEqualTo("Nguyễn Thị Lan");
        assertThat(view.displayName()).isEqualTo("Nguyễn Thị Lan");
        assertThat(view.generation()).isEqualTo(6);
        assertThat(view.nativePlace()).isNull();
        assertThat(view.occupation()).isNull();
        assertThat(view.currentPlaceProvince()).isNull();
        assertThat(view.birth()).isNull();
        assertThat(view.contact()).isNull();
        assertThat(view.biography()).isNull();
        assertThat(view.avatarKey()).isNull();
        assertThat(view.attributes()).isNull();
        assertThat(view.currentPlaceFull()).isNull();
    }

    @Test
    @DisplayName("Tầng 2: thêm nghề, tỉnh, nguyên quán và CHỈ năm sinh — không phải ngày đầy đủ")
    void tang2ChiChoNamSinh() {
        PersonView view = privacy.toView(hoSoDayDu(true), vai(CallerRole.MEMBER, CHI_GIAP), danhBaChi);

        assertThat(view.occupation()).isEqualTo("Giáo viên");
        assertThat(view.currentPlaceProvince()).isEqualTo("Hà Nội");
        assertThat(view.nativePlace()).isEqualTo("Bắc Ninh");
        assertThat(view.birth()).isNotNull();
        assertThat(view.birth().precision())
                .as("ngay sinh day du la du lieu Tang 3")
                .isEqualTo(DatePrecision.YEAR);
        assertThat(view.birth().year()).hasValue(1960);
        assertThat(view.birth().solar().getMonthValue()).isEqualTo(1);
        assertThat(view.contact()).isNull();
        assertThat(view.currentPlaceFull()).isNull();
        assertThat(view.biography()).isNull();
        assertThat(view.avatarKey()).isNull();
    }

    @Test
    @DisplayName("Tầng 3 (chính chủ): đầy đủ liên hệ, địa chỉ, ngày sinh, ảnh và tiểu sử")
    void tang3ChoChinhChu() {
        Person person = hoSoDayDu(true);
        CallerContext chinhChu = new CallerContext(CallerRole.MEMBER, person.rawId(),
                List.of(), CHI_AT);

        PersonView view = privacy.toView(person, chinhChu, danhBaChi);

        assertThat(view.contact()).isNotNull();
        assertThat(view.contact().phone()).isEqualTo("0912345678");
        assertThat(view.currentPlaceFull()).isEqualTo("số 12 ngõ 30 phố Hàng Bột, Hà Nội");
        assertThat(view.birth().precision()).isEqualTo(DatePrecision.DAY);
        assertThat(view.birth().solar().getDayOfMonth()).isEqualTo(17);
        assertThat(view.avatarKey()).isEqualTo("portraits/lan.jpg");
        assertThat(view.biography()).isEqualTo("Tiểu sử chi tiết...");
        assertThat(view.attributes()).containsEntry("hocVi", "Cử nhân");
        assertThat(view.names()).hasSize(3);
    }

    @Test
    @DisplayName("Mức riêng tư chỉ hiện với chính chủ và Quản trị hệ thống")
    void mucRiengTuChiHienVoiChinhChuVaAdmin() {
        Person person = hoSoDayDu(true);
        person.choosePrivacyLevel(PrivacyLevel.CLAN_OPT_IN);
        CallerContext chinhChu = new CallerContext(CallerRole.MEMBER, person.rawId(),
                List.of(), CHI_GIAP);

        assertThat(privacy.toView(person, chinhChu, danhBaChi).privacyLevel())
                .isEqualTo(PrivacyLevel.CLAN_OPT_IN);
        assertThat(privacy.toView(person, vai(CallerRole.ADMIN, null), danhBaChi).privacyLevel())
                .isEqualTo(PrivacyLevel.CLAN_OPT_IN);
        assertThat(privacy.toView(person, vai(CallerRole.COUNCIL, null), danhBaChi).privacyLevel())
                .isNull();
        assertThat(privacy.toView(person, vai(CallerRole.MEMBER, CHI_GIAP), danhBaChi).privacyLevel())
                .isNull();
    }

    @Test
    @DisplayName("Cờ xoá mềm chỉ hiện với Hội đồng và Quản trị hệ thống, với vai khác là null")
    void coXoaMemChiHienVoiClanWide() {
        Person person = hoSoDayDu(false);

        assertThat(privacy.toView(person, vai(CallerRole.ADMIN, null), danhBaChi).deleted())
                .isEqualTo(Boolean.FALSE);
        assertThat(privacy.toView(person, vai(CallerRole.MEMBER, CHI_GIAP), danhBaChi).deleted())
                .isNull();
        assertThat(privacy.toView(person, CallerContext.guest(), danhBaChi).deleted()).isNull();
    }

    @Test
    @DisplayName("Người đã khuất: dữ liệu phả hệ công khai với Khách")
    void nguoiDaKhuatCongKhaiDuLieuPhaHe() {
        PersonView view = privacy.toView(hoSoDayDu(false), CallerContext.guest(), danhBaChi);

        assertThat(view.displayName()).isEqualTo("Nguyễn Thị Lan");
        assertThat(view.generation()).isEqualTo(6);
        assertThat(view.names()).hasSize(3);
        assertThat(view.death()).isNotNull();
        assertThat(view.primaryBranch()).isNotNull();
        assertThat(view.primaryBranch().path()).isEqualTo("goc.chi_giap");
    }

    @Test
    @DisplayName("Khách KHÔNG được nhận khối liên hệ trong hồ sơ người đã khuất")
    void khachKhongNhanKhoiLienHeCuaNguoiDaKhuat() {
        PersonView view = privacy.toView(hoSoDayDu(false), CallerContext.guest(), danhBaChi);

        assertThat(view.contact())
                .as("so dien thoai ghi trong ho so mot cu da mat tren thuc te la so cua nguoi than "
                        + "dang song — do la du lieu ca nhan cua NGUOI KHAC")
                .isNull();
    }

    @Test
    @DisplayName("Thành viên thường KHÔNG được nhận khối liên hệ trong hồ sơ người đã khuất")
    void thanhVienKhongNhanKhoiLienHeCuaNguoiDaKhuat() {
        PersonView view = privacy.toView(hoSoDayDu(false), vai(CallerRole.MEMBER, CHI_AT), danhBaChi);

        assertThat(view.contact()).isNull();
    }

    @Test
    @DisplayName("Hội đồng và chính chủ vẫn xem được khối liên hệ của người đã khuất")
    void clanWideXemDuocLienHeCuaNguoiDaKhuat() {
        PersonView view = privacy.toView(hoSoDayDu(false), vai(CallerRole.COUNCIL, null), danhBaChi);

        assertThat(view.contact()).isNotNull();
    }

    @Test
    @DisplayName("Dạng gọn cho phả đồ chỉ mang dữ liệu Tầng 1 khi người gọi ở Tầng 1")
    void dangGonChiMangTang1() {
        PersonSummaryView summary = privacy.toSummary(hoSoDayDu(true),
                vai(CallerRole.MEMBER, CHI_AT), danhBaChi);

        assertThat(summary.displayName()).isEqualTo("Nguyễn Thị Lan");
        assertThat(summary.generation()).isEqualTo(6);
        assertThat(summary.birthYear()).isNull();
        assertThat(summary.nativePlace()).isNull();
        assertThat(summary.avatarKey()).isNull();
        assertThat(summary.matchedNameType()).isNull();
    }

    @Test
    @DisplayName("Dạng gọn ở Tầng 2 có năm sinh và nguyên quán nhưng vẫn không có ảnh")
    void dangGonTang2CoNamSinhKhongCoAnh() {
        PersonSummaryView summary = privacy.toSummary(hoSoDayDu(true),
                vai(CallerRole.MEMBER, CHI_GIAP), danhBaChi);

        assertThat(summary.birthYear()).isEqualTo(1960);
        assertThat(summary.nativePlace()).isEqualTo("Bắc Ninh");
        assertThat(summary.avatarKey()).isNull();
    }

    @Test
    @DisplayName("Năm mất là dữ liệu công khai — người đã khuất không còn quyền riêng tư")
    void namMatLaDuLieuCongKhai() {
        PersonSummaryView summary = privacy.toSummary(hoSoDayDu(false), CallerContext.guest(),
                danhBaChi);

        assertThat(summary.deathYear()).isEqualTo(2010);
        assertThat(summary.alive()).isFalse();
    }

    @Test
    @DisplayName("Quyền của người gọi nói về NGƯỜI GỌI, không nói dữ liệu nào đang bị giấu")
    void quyenCuaNguoiGoiKhongTietLoDuLieuBiGiau() {
        Person person = hoSoDayDu(true);

        PersonView cuaThanhVien = privacy.toView(person, vai(CallerRole.MEMBER, CHI_AT), danhBaChi);
        PersonView cuaTruongChi = privacy.toView(person,
                vai(CallerRole.BRANCH_HEAD, null, CHI_GIAP), danhBaChi);

        assertThat(cuaThanhVien.access().canEdit()).isFalse();
        assertThat(cuaThanhVien.access().canDelete()).isFalse();
        assertThat(cuaThanhVien.access().isSelf()).isFalse();
        assertThat(cuaThanhVien.access().visibleTier()).isEqualTo(VisibleTier.T1);

        assertThat(cuaTruongChi.access().canEdit()).isTrue();
        assertThat(cuaTruongChi.access().canDelete()).isTrue();
        assertThat(cuaTruongChi.access().callerRole()).isEqualTo(CallerRole.BRANCH_HEAD);
    }

    @Test
    @DisplayName("Trưởng chi NGOÀI phạm vi không được quyền sửa trên hồ sơ")
    void truongChiNgoaiPhamViKhongCoQuyenSua() {
        PersonView view = privacy.toView(hoSoDayDu(true),
                vai(CallerRole.BRANCH_HEAD, null, CHI_AT), danhBaChi);

        assertThat(view.access().canEdit()).isFalse();
        assertThat(view.access().canDelete()).isFalse();
    }

    @Test
    @DisplayName("Nhân khẩu chưa gắn chi không làm vỡ bộ lọc, chỉ là primaryBranch = null")
    void nhanKhauChuaGanChiKhongLamVoBoLoc() {
        Person person = PersonFixtures.nam("Nguyễn Văn Chưa Gắn Chi");

        PersonView view = privacy.toView(person, vai(CallerRole.MEMBER, CHI_GIAP),
                BranchDirectory.empty());

        assertThat(view.primaryBranch()).isNull();
        assertThat(view.access().visibleTier()).isEqualTo(VisibleTier.T1);
    }
}
