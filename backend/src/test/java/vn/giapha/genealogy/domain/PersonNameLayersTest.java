package vn.giapha.genealogy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * <b>Đa lớp tên</b> — điểm khác biệt nền tảng so với mô hình "một cột full_name" của phần mềm
 * phương Tây.
 *
 * <p>Một người Việt truyền thống có nhiều tên: húy (tên thật, kiêng gọi), tự, hiệu, thụy (đặt sau
 * khi mất), thường gọi, pháp danh. Bất biến duy nhất mà aggregate phải giữ là <b>đúng một tên
 * chính</b> — trùng với chỉ mục {@code ux_person_name_primary} của V2, nhưng bắt ở domain thì kết
 * quả là dữ liệu đã đúng chứ không phải một {@code SQLException} lúc flush.
 */
class PersonNameLayersTest {

    @Test
    @DisplayName("Sáu lớp tên của gia phả Việt đều lưu được trên cùng một nhân khẩu")
    void luuDuDuocSauLopTen() {
        Person person = Person.create(PersonId.newId(), Gender.MALE, false, List.of(
                PersonName.of(NameType.HUY, "Nguyễn Đình Trọng", true),
                PersonName.of(NameType.TU, "Tử Kính", false),
                PersonName.of(NameType.HIEU, "Ức Trai", false),
                PersonName.of(NameType.THUY, "Văn Trinh", false),
                PersonName.of(NameType.THUONG_GOI, "Cụ Cả Trọng", false),
                PersonName.of(NameType.PHAP_DANH, "Thích Minh Đức", false)));

        assertThat(person.names()).extracting(PersonName::type)
                .containsExactly(NameType.HUY, NameType.TU, NameType.HIEU, NameType.THUY,
                        NameType.THUONG_GOI, NameType.PHAP_DANH);
    }

    @Test
    @DisplayName("Không tên nào được đánh dấu chính thì tên đầu tiên tự thành tên chính")
    void khongCoTenChinhThiLayTenDauTien() {
        Person person = Person.create(PersonId.newId(), Gender.FEMALE, true, List.of(
                PersonName.of(NameType.THUONG_GOI, "Nguyễn Thị Lan", false),
                PersonName.of(NameType.HUY, "Nguyễn Thị Lan Anh", false)));

        assertThat(person.names()).filteredOn(PersonName::primary).hasSize(1);
        assertThat(person.displayName()).isEqualTo("Nguyễn Thị Lan");
    }

    @Test
    @DisplayName("Nhiều tên cùng đánh dấu chính thì chỉ tên đầu tiên được giữ")
    void nhieuTenChinhThiChiGiuTenDauTien() {
        Person person = Person.create(PersonId.newId(), Gender.MALE, true, List.of(
                PersonName.of(NameType.THUONG_GOI, "Nguyễn Văn A", true),
                PersonName.of(NameType.HUY, "Nguyễn Văn Anh", true),
                PersonName.of(NameType.TU, "Tử An", true)));

        assertThat(person.names()).filteredOn(PersonName::primary)
                .as("bat bien dung mot ten chinh phai dung o moi loi vao danh sach ten")
                .singleElement()
                .extracting(PersonName::fullName).isEqualTo("Nguyễn Văn A");
    }

    @Test
    @DisplayName("Thêm một tên chính mới thì tên chính cũ vẫn được giữ, tên mới bị hạ")
    void themTenChinhMoiThiHaTenMoi() {
        Person person = PersonFixtures.nam("Nguyễn Văn A");

        person.addName(PersonName.of(NameType.HIEU, "Hồng Sơn", true));

        assertThat(person.names()).filteredOn(PersonName::primary).singleElement()
                .extracting(PersonName::fullName).isEqualTo("Nguyễn Văn A");
        assertThat(person.names()).hasSize(2);
    }

    @Test
    @DisplayName("replaceNames thay thế TOÀN BỘ danh sách, không phải cộng thêm")
    void replaceNamesThayTheToanBo() {
        Person person = PersonFixtures.nam("Nguyễn Văn A");

        person.replaceNames(List.of(
                PersonName.of(NameType.HUY, "Nguyễn Văn Ất", true),
                PersonName.of(NameType.THUY, "Phúc Hậu", false)));

        assertThat(person.names()).hasSize(2);
        assertThat(person.displayName()).isEqualTo("Nguyễn Văn Ất");
    }

    @Test
    @DisplayName("Nhân khẩu bắt buộc có ít nhất một lớp tên — tạo mới và thay thế đều bị chặn")
    void batBuocCoItNhatMotLopTen() {
        assertThatThrownBy(() -> Person.create(PersonId.newId(), Gender.MALE, true, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("it nhat mot lop ten");

        Person person = PersonFixtures.nam("Nguyễn Văn A");
        assertThatThrownBy(() -> person.replaceNames(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> person.replaceNames(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Chỉ lớp tên HUY mới là tên húy — các lớp khác trùng nhau không phạm huý")
    void chiLopHuyMoiLaTenHuy() {
        Person person = Person.create(PersonId.newId(), Gender.MALE, false, List.of(
                PersonName.of(NameType.HUY, "Nguyễn Đình Trọng", true),
                PersonName.of(NameType.TU, "Tử Kính", false),
                PersonName.of(NameType.THUY, "Văn Trinh", false)));

        assertThat(person.tabooNames()).extracting(PersonName::fullName)
                .containsExactly("Nguyễn Đình Trọng");
        assertThat(PersonName.of(NameType.TU, "Tử Kính", false).isTabooName()).isFalse();
        assertThat(PersonName.of(NameType.THUY, "Văn Trinh", false).isTabooName()).isFalse();
    }

    @Test
    @DisplayName("PersonName tự trim tên và từ chối tên rỗng")
    void personNameTuChuanHoa() {
        assertThat(PersonName.of(NameType.HUY, "  Nguyễn Văn Tuân  ", true).fullName())
                .isEqualTo("Nguyễn Văn Tuân");

        assertThatThrownBy(() -> PersonName.of(NameType.HUY, "   ", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("khong duoc rong");
        assertThatThrownBy(() -> PersonName.of(NameType.HUY, null, true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PersonName.of(null, "Nguyễn Văn A", true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Hán-Nôm và ghi chú rỗng được chuẩn hoá về null, không lưu chuỗi trắng")
    void hanNomVaGhiChuRongThanhNull() {
        PersonName name = new PersonName(null, NameType.HUY, "Nguyễn Đình Trọng", "  ", true, "");

        assertThat(name.hanNom()).isNull();
        assertThat(name.note()).isNull();
    }

    @Test
    @DisplayName("Chữ Hán-Nôm được giữ nguyên Unicode CJK, không phiên âm")
    void giuNguyenChuHanNom() {
        PersonName name = new PersonName(null, NameType.HUY, "Nguyễn Đình Trọng",
                "阮廷重", true, "chép theo bia mộ 1887");

        assertThat(name.hanNom()).isEqualTo("阮廷重");
        assertThat(name.note()).isEqualTo("chép theo bia mộ 1887");
    }

    @Test
    @DisplayName("Domain KHÔNG tự sinh cột không dấu — đó là generated column của Postgres")
    void domainKhongTuSinhCotKhongDau() {
        assertThat(PersonName.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .as("sinh tay ban khong dau la mo duong cho lech giua full_name va name_unaccented")
                .doesNotContain("nameUnaccented", "unaccented");
    }

    @Test
    @DisplayName("asPrimary và withId trả bản sao mới, giữ nguyên các thuộc tính còn lại")
    void asPrimaryVaWithIdLaBanSao() {
        PersonName goc = new PersonName(null, NameType.HUY, "Nguyễn Đình Trọng", "阮廷重", true, "bia mộ");
        UUID id = UUID.randomUUID();

        PersonName haXuong = goc.asPrimary(false);
        PersonName coId = goc.withId(id);

        assertThat(goc.primary()).isTrue();
        assertThat(haXuong.primary()).isFalse();
        assertThat(haXuong.hanNom()).isEqualTo("阮廷重");
        assertThat(haXuong.note()).isEqualTo("bia mộ");
        assertThat(coId.id()).isEqualTo(id);
        assertThat(coId.fullName()).isEqualTo("Nguyễn Đình Trọng");
    }
}
