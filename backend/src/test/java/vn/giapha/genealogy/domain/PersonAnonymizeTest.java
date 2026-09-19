package vn.giapha.genealogy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.domain.event.PersonAnonymizedEvent;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * <b>Ẩn danh hoá</b> — cách hệ thống thực hiện quyền xoá dữ liệu cá nhân (Nghị định 13/2023).
 *
 * <p>Xoá hẳn một nhân khẩu thì một yêu cầu hợp pháp của <i>một</i> cá nhân sẽ phá hỏng dữ liệu của
 * <i>cả dòng họ</i>. Vì thế nghĩa vụ pháp lý được thực hiện bằng cách xoá sạch dữ liệu Tầng 3 và
 * <b>giữ nguyên node phả hệ</b>: tên chính, đời thứ, giới tính, chi/ngành và mọi cạnh quan hệ ở
 * lại để cây không gãy.
 */
class PersonAnonymizeTest {

    private Person hoSoDayDu() {
        Person person = Person.create(PersonId.newId(), Gender.FEMALE, true, List.of(
                PersonName.of(NameType.THUONG_GOI, "Nguyễn Thị Lan", true),
                PersonName.of(NameType.HUY, "Nguyễn Thị Lan Anh", false),
                PersonName.of(NameType.PHAP_DANH, "Diệu Tâm", false)));
        person.placeInGeneration(6);
        person.placeInBirthOrder(2);
        person.moveToBranch(UUID.randomUUID());
        person.applyProfileEdit(ProfileEdit.builder()
                .birth(FieldChange.set(PersonFixtures.ngaySinh(1988, 4, 17)))
                .nativePlace(FieldChange.set("Bắc Ninh"))
                .currentPlaceProvince(FieldChange.set("Hà Nội"))
                .currentPlaceFull(FieldChange.set("số 12 ngõ 30 phố Hàng Bột, Hà Nội"))
                .occupation(FieldChange.set("Giáo viên"))
                .biography(FieldChange.set("Tốt nghiệp Đại học Sư phạm năm 2010..."))
                .avatarKey(FieldChange.set("portraits/2024/lan.jpg"))
                .contact(FieldChange.set(PersonFixtures.lienHe()))
                .attributes(FieldChange.set(Map.of("soCMND", "001188000123")))
                .build());
        return person;
    }

    @Test
    @DisplayName("Ẩn danh hoá xoá sạch dữ liệu Tầng 3")
    void anDanhHoaXoaSachTang3() {
        Person person = hoSoDayDu();

        person.anonymize();

        assertThat(person.contact()).isEqualTo(ContactInfo.EMPTY);
        assertThat(person.currentPlaceFull()).isNull();
        assertThat(person.currentPlaceProvince()).isNull();
        assertThat(person.occupation()).isNull();
        assertThat(person.biography()).isNull();
        assertThat(person.avatarKey()).isNull();
        assertThat(person.attributes()).isEmpty();
    }

    @Test
    @DisplayName("Node phả hệ ở lại nguyên vẹn: tên chính, đời thứ, thứ tự sinh, giới tính, chi")
    void nodePhaHeOLaiNguyenVen() {
        Person person = hoSoDayDu();
        UUID chi = person.primaryBranchId();

        person.anonymize();

        assertThat(person.isDeleted())
                .as("an danh hoa KHONG phai xoa mem — node van hien thi tren pha do")
                .isFalse();
        assertThat(person.displayName()).isEqualTo("Nguyễn Thị Lan");
        assertThat(person.generation()).isEqualTo(6);
        assertThat(person.birthOrder()).isEqualTo(2);
        assertThat(person.gender()).isEqualTo(Gender.FEMALE);
        assertThat(person.primaryBranchId()).isEqualTo(chi);
        assertThat(person.rawId()).isNotNull();
    }

    @Test
    @DisplayName("Chỉ giữ lại tên chính; tên húy và pháp danh bị gỡ")
    void chiGiuLaiTenChinh() {
        Person person = hoSoDayDu();

        person.anonymize();

        assertThat(person.names()).singleElement()
                .satisfies(name -> {
                    assertThat(name.primary()).isTrue();
                    assertThat(name.fullName()).isEqualTo("Nguyễn Thị Lan");
                });
        assertThat(person.tabooNames()).isEmpty();
    }

    @Test
    @DisplayName("Ngày sinh bị hạ mức xuống chỉ còn năm, không còn ngày-tháng")
    void ngaySinhHaMucXuongChiConNam() {
        Person person = hoSoDayDu();

        person.anonymize();

        assertThat(person.birth()).isNotNull();
        assertThat(person.birth().precision()).isEqualTo(DatePrecision.YEAR);
        assertThat(person.birth().year()).hasValue(1988);
        assertThat(person.birth().solar().getMonthValue())
                .as("ngay-thang phai bien mat; chi con moc nam 01/01")
                .isEqualTo(1);
        assertThat(person.birth().solar().getDayOfMonth()).isEqualTo(1);
    }

    @Test
    @DisplayName("Bản đồng thuận bị đóng hết để dữ liệu nhập lại sau không tự mở trở lại")
    void banDongThuanBiDongHet() {
        Person person = hoSoDayDu();
        person.choosePrivacyConsent(PrivacyLevel.CLAN_OPT_IN.toConsent());

        person.anonymize();

        assertThat(person.privacyConsent().isAllPrivate()).isTrue();
        assertThat(person.isAnonymized()).isTrue();
        assertThat(person.anonymizedAt()).isNotNull();
    }

    @Test
    @DisplayName("Ẩn danh hoá phát PersonAnonymizedEvent")
    void phatSuKienAnDanhHoa() {
        Person person = hoSoDayDu();
        person.clearDomainEvents();

        person.anonymize();

        assertThat(person.domainEvents()).singleElement()
                .isInstanceOfSatisfying(PersonAnonymizedEvent.class, event ->
                        assertThat(event.personId()).isEqualTo(person.rawId()));
    }

    @Test
    @DisplayName("Người không có ngày sinh vẫn ẩn danh hoá được, birth vẫn là null")
    void khongCoNgaySinhVanAnDanhHoaDuoc() {
        Person person = PersonFixtures.nu("Nguyễn Thị Vô Danh");

        person.anonymize();

        assertThat(person.birth()).isNull();
        assertThat(person.isAnonymized()).isTrue();
    }

    @Test
    @DisplayName("Ảnh chụp nhật ký sau khi ẩn danh hoá không còn mẩu dữ liệu Tầng 3 nào")
    void anhChupNhatKyKhongConTang3() {
        Person person = hoSoDayDu();

        person.anonymize();
        Map<String, Object> snapshot = person.auditSnapshot();

        assertThat(snapshot.toString())
                .doesNotContain("0912345678")
                .doesNotContain("nguoidung@example.com")
                .doesNotContain("Hàng Bột")
                .doesNotContain("001188000123");
    }
}
