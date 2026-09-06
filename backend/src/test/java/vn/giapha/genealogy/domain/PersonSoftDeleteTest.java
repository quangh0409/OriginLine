package vn.giapha.genealogy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.domain.event.PersonRestoredEvent;
import vn.giapha.genealogy.domain.event.PersonSoftDeletedEvent;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.shared.vo.Gender;

/**
 * <b>Xoá mềm — luật số một của phả hệ (FR-1.5).</b>
 *
 * <p>Không bao giờ được xoá cứng một nhân khẩu: node phải ở lại đồ thị, nếu không thì mọi hậu duệ
 * mất đường nối lên tổ tiên — mất một người thành mất cả một nhánh. Test trong lớp này ghim luật
 * đó ở cả hai mức: hành vi của aggregate, và <b>hình dạng của cổng lưu trữ</b> (kho nhân khẩu
 * không được phép có bất kỳ phương thức xoá nào).</p>
 */
class PersonSoftDeleteTest {

    @Test
    @DisplayName("Xoá mềm chỉ bật cờ: nhân khẩu vẫn còn tên, đời thứ, chi và giới tính")
    void xoaMemChiBatCoChuKhongXoaDuLieu() {
        Person person = PersonFixtures.nam("Nguyễn Văn Cả");
        UUID chi = UUID.randomUUID();
        person.moveToBranch(chi);
        person.placeInGeneration(4);
        person.placeInBirthOrder(1);

        person.softDelete("trùng bản ghi, gộp theo biên bản họp họ 2024");

        assertThat(person.isDeleted()).isTrue();
        assertThat(person.deletedAt()).isNotNull();
        assertThat(person.rawId()).as("định danh không đổi — cạnh trong đồ thị vẫn trỏ tới nó").isNotNull();
        assertThat(person.displayName()).isEqualTo("Nguyễn Văn Cả");
        assertThat(person.names()).hasSize(1);
        assertThat(person.generation()).isEqualTo(4);
        assertThat(person.birthOrder()).isEqualTo(1);
        assertThat(person.primaryBranchId()).isEqualTo(chi);
        assertThat(person.gender()).isEqualTo(Gender.MALE);
    }

    @Test
    @DisplayName("Xoá mềm phát PersonSoftDeletedEvent kèm lý do")
    void xoaMemPhatSuKienKemLyDo() {
        Person person = PersonFixtures.nam("Nguyễn Văn Hai");
        person.clearDomainEvents();

        person.softDelete("nhập trùng");

        assertThat(person.domainEvents())
                .singleElement()
                .isInstanceOfSatisfying(PersonSoftDeletedEvent.class, event -> {
                    assertThat(event.personId()).isEqualTo(person.rawId());
                    assertThat(event.reason()).isEqualTo("nhập trùng");
                });
    }

    @Test
    @DisplayName("Xoá mềm hai lần ném IllegalStateException để tầng api dịch thành 409")
    void xoaMemHaiLanThiXungDot() {
        Person person = PersonFixtures.nam("Nguyễn Văn Ba");
        person.softDelete("lần đầu");

        assertThatThrownBy(() -> person.softDelete("lần hai"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("xoa mem");
    }

    @Test
    @DisplayName("Khôi phục gỡ cờ và phát PersonRestoredEvent")
    void khoiPhucGoCo() {
        Person person = PersonFixtures.nam("Nguyễn Văn Tư");
        person.softDelete("nhầm");
        person.clearDomainEvents();

        person.restore();

        assertThat(person.isDeleted()).isFalse();
        assertThat(person.deletedAt()).isNull();
        assertThat(person.domainEvents()).singleElement().isInstanceOf(PersonRestoredEvent.class);
    }

    @Test
    @DisplayName("Khôi phục một bản ghi chưa bị xoá là thao tác rỗng, không phát sự kiện")
    void khoiPhucBanGhiChuaXoaLaThaoTacRong() {
        Person person = PersonFixtures.nam("Nguyễn Văn Năm");
        person.clearDomainEvents();

        person.restore();

        assertThat(person.isDeleted()).isFalse();
        assertThat(person.domainEvents()).isEmpty();
    }

    @Test
    @DisplayName("Aggregate Person không phơi ra bất kỳ đường xoá cứng nào")
    void aggregateKhongCoDuongXoaCung() {
        assertThat(Person.class.getMethods())
                .filteredOn(method -> tenGoiYXoaCung(method.getName()))
                .as("Person chỉ được có softDelete/anonymize; mọi phương thức tên 'delete*'/'remove*' "
                        + "khác là một đường xoá cứng vừa được mở ra")
                .isEmpty();
    }

    @Test
    @DisplayName("PersonRepository cố ý không có phương thức xoá — xoá là softDelete rồi save")
    void khoNhanKhauKhongCoPhuongThucXoa() {
        assertThat(PersonRepository.class.getMethods())
                .filteredOn(method -> tenGoiYXoaCung(method.getName()))
                .as("thêm delete()/remove() vào kho nhân khẩu là mở đường cắt đứt cây phả hệ")
                .isEmpty();
    }

    private static boolean tenGoiYXoaCung(String tenPhuongThuc) {
        String ten = tenPhuongThuc.toLowerCase(Locale.ROOT);
        if (ten.equals("softdelete") || ten.equals("deletedat") || ten.equals("isdeleted")) {
            return false;
        }
        return ten.startsWith("delete") || ten.startsWith("remove") || ten.startsWith("purge")
                || ten.startsWith("harddelete");
    }

    @Test
    @DisplayName("Xoá mềm không đụng tới danh sách tên — dữ liệu vẫn còn để Hội đồng đối chiếu")
    void xoaMemGiuNguyenCacLopTen() {
        Person person = Person.create(vn.giapha.shared.vo.PersonId.newId(), Gender.MALE, false,
                java.util.List.of(
                        PersonName.of(NameType.HUY, "Nguyễn Đình Trọng", true),
                        PersonName.of(NameType.THUY, "Phúc Trung", false)));

        person.softDelete("gộp bản ghi");

        assertThat(person.names()).hasSize(2);
        assertThat(person.tabooNames()).extracting(PersonName::fullName)
                .containsExactly("Nguyễn Đình Trọng");
    }

    @Test
    @DisplayName("Bộ dựng rehydrate nạp lại được trạng thái đã xoá mềm mà không phát sự kiện")
    void napLaiTrangThaiDaXoaMem() {
        java.time.Instant luc = java.time.Instant.parse("2024-03-01T10:00:00Z");
        Person person = Person.rehydrate(vn.giapha.shared.vo.PersonId.newId())
                .names(java.util.List.of(PersonName.of(NameType.THUONG_GOI, "Nguyễn Văn Sáu", true)))
                .deleted(true, luc)
                .build();

        assertThat(person.isDeleted()).isTrue();
        assertThat(person.deletedAt()).isEqualTo(luc);
        assertThat(person.domainEvents())
                .as("nạp lại từ CSDL không phải một sự kiện nghiệp vụ mới")
                .isEmpty();
    }
}
