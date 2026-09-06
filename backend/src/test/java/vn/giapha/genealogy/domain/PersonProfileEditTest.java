package vn.giapha.genealogy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.domain.event.PersonUpdatedEvent;
import vn.giapha.shared.vo.Gender;

/**
 * Ba trạng thái của một trường trong lệnh sửa một phần: <b>vắng mặt = giữ nguyên</b>,
 * <b>có giá trị = ghi đè</b>, <b>{@code clearFields} = xoá trắng</b>.
 *
 * <p>Đây chính là lý do contract chọn {@code clearFields} thay vì gửi {@code null}: qua các lớp
 * JSON client, "gửi null" và "không gửi" lẫn vào nhau quá dễ. Danh sách trường thực sự đổi mà
 * {@code applyProfileEdit} trả về đi thẳng vào {@code audit_log.changed_fields}, nên nó phải
 * chính xác — thừa một tên là nhật ký nói dối.
 */
class PersonProfileEditTest {

    @Test
    @DisplayName("Trường vắng mặt thì giữ nguyên và không xuất hiện trong changed_fields")
    void truongVangMatThiGiuNguyen() {
        Person person = PersonFixtures.nam("Nguyễn Văn A");
        person.applyProfileEdit(ProfileEdit.builder()
                .nativePlace(FieldChange.set("Bắc Ninh"))
                .build());
        person.clearDomainEvents();

        List<String> changed = person.applyProfileEdit(ProfileEdit.builder().build());

        assertThat(changed).isEmpty();
        assertThat(person.nativePlace()).isEqualTo("Bắc Ninh");
        assertThat(person.domainEvents())
                .as("khong co gi doi thi khong duoc phat su kien cap nhat")
                .isEmpty();
    }

    @Test
    @DisplayName("clear() xoá trắng trường về null và được ghi vào changed_fields")
    void clearXoaTrangTruong() {
        Person person = PersonFixtures.nam("Nguyễn Văn A");
        person.applyProfileEdit(ProfileEdit.builder()
                .occupation(FieldChange.set("Thầy đồ"))
                .build());

        List<String> changed = person.applyProfileEdit(ProfileEdit.builder()
                .occupation(FieldChange.clear())
                .build());

        assertThat(changed).containsExactly("occupation");
        assertThat(person.occupation()).isNull();
    }

    @Test
    @DisplayName("Chuỗi trắng được chuẩn hoá về null, chuỗi có giá trị được trim")
    void chuoiTrangThanhNullVaChuoiCoGiaTriDuocTrim() {
        Person person = PersonFixtures.nam("Nguyễn Văn A");

        person.applyProfileEdit(ProfileEdit.builder()
                .nativePlace(FieldChange.set("   Hà Nội  "))
                .biography(FieldChange.set("    "))
                .build());

        assertThat(person.nativePlace()).isEqualTo("Hà Nội");
        assertThat(person.biography()).isNull();
    }

    @Test
    @DisplayName("Ghi đè bằng đúng giá trị cũ không được tính là thay đổi")
    void ghiDeBangGiaTriCuKhongPhaiThayDoi() {
        Person person = PersonFixtures.nam("Nguyễn Văn A");
        person.applyProfileEdit(ProfileEdit.builder()
                .nativePlace(FieldChange.set("Bắc Ninh"))
                .build());

        List<String> changed = person.applyProfileEdit(ProfileEdit.builder()
                .nativePlace(FieldChange.set("Bắc Ninh"))
                .build());

        assertThat(changed).isEmpty();
    }

    @Test
    @DisplayName("Giới tính null được quy về UNKNOWN — gia phả giấy thường không ghi giới tính đời xa")
    void gioiTinhNullQuyVeUnknown() {
        Person person = PersonFixtures.nam("Nguyễn Văn A");

        List<String> changed = person.applyProfileEdit(ProfileEdit.builder()
                .gender(FieldChange.set(null))
                .build());

        assertThat(changed).containsExactly("gender");
        assertThat(person.gender()).isEqualTo(Gender.UNKNOWN);
    }

    @Test
    @DisplayName("attributes được thay thế trọn gói, không trộn với giá trị cũ")
    void attributesDuocThayTheTronGoi() {
        Person person = PersonFixtures.nam("Nguyễn Văn A");
        person.applyProfileEdit(ProfileEdit.builder()
                .attributes(FieldChange.set(Map.of("hocVi", "Cử nhân", "chucVu", "Lý trưởng")))
                .build());

        person.applyProfileEdit(ProfileEdit.builder()
                .attributes(FieldChange.set(Map.of("hocVi", "Tiến sĩ")))
                .build());

        assertThat(person.attributes()).containsExactly(Map.entry("hocVi", "Tiến sĩ"));
    }

    @Test
    @DisplayName("Sự kiện PersonUpdatedEvent liệt kê đúng các trường đã đổi")
    void suKienLietKeDungCacTruongDaDoi() {
        Person person = PersonFixtures.nam("Nguyễn Văn A");
        person.clearDomainEvents();

        person.applyProfileEdit(ProfileEdit.builder()
                .occupation(FieldChange.set("Thầy đồ"))
                .nativePlace(FieldChange.set("Bắc Ninh"))
                .build());

        assertThat(person.domainEvents()).singleElement()
                .isInstanceOfSatisfying(PersonUpdatedEvent.class, event ->
                        assertThat(event.changedFields())
                                .containsExactlyInAnyOrder("nativePlace", "occupation"));
    }

    @Test
    @DisplayName("Khối liên hệ null được quy về ContactInfo.EMPTY chứ không phải null")
    void lienHeNullQuyVeEmpty() {
        Person person = PersonFixtures.nam("Nguyễn Văn A");
        person.applyProfileEdit(ProfileEdit.builder()
                .contact(FieldChange.set(PersonFixtures.lienHe()))
                .build());

        person.applyProfileEdit(ProfileEdit.builder()
                .contact(FieldChange.clear())
                .build());

        assertThat(person.contact()).isEqualTo(ContactInfo.EMPTY);
        assertThat(person.contact().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("ProfileEdit cố ý KHÔNG chứa các trường có hành vi nghiệp vụ riêng")
    void profileEditKhongChuaTruongCoHanhViRieng() {
        assertThat(ProfileEdit.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .as("isAlive/death/names/primaryBranchId/privacyLevel/isDeleted moi thu la mot hanh vi "
                        + "co ten va co quy tac phan quyen rieng; gop vao mot lo la mat luon cac quy tac do")
                .doesNotContain("alive", "isAlive", "death", "names", "primaryBranchId",
                        "privacyLevel", "deleted", "isDeleted");
    }

    @Test
    @DisplayName("attributes trả ra là bản chỉ đọc — không ai sửa được trạng thái aggregate từ ngoài")
    void attributesTraRaLaBanChiDoc() {
        Person person = PersonFixtures.nam("Nguyễn Văn A");
        person.applyProfileEdit(ProfileEdit.builder()
                .attributes(FieldChange.set(Map.of("hocVi", "Cử nhân")))
                .build());

        assertThat(person.attributes()).isUnmodifiable();
        assertThat(person.names()).isUnmodifiable();
    }
}
