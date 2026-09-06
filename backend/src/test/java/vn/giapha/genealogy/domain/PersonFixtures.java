package vn.giapha.genealogy.domain;

import java.time.LocalDate;
import java.util.List;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;
import vn.giapha.shared.vo.PersonId;

/**
 * Bộ dựng nhân khẩu dùng chung cho toàn bộ test của context {@code genealogy}.
 *
 * <p>Cố ý <b>không</b> có phương thức nào phân biệt nam/nữ ngoài giá trị {@link Gender} truyền
 * vào: con gái và bên ngoại được ghi nhận đầy đủ và bình đẳng với con trai (BA v2 §12), nên ngay
 * cả bộ fixture cũng không được tạo ra một lối đi thuận tiện hơn cho con trai.</p>
 */
public final class PersonFixtures {

    private PersonFixtures() {
    }

    /** Nhân khẩu còn sống, một lớp tên thường gọi. */
    public static Person nguoiSong(String tenThuongGoi, Gender gioiTinh) {
        return Person.create(PersonId.newId(), gioiTinh, true,
                List.of(PersonName.of(NameType.THUONG_GOI, tenThuongGoi, true)));
    }

    public static Person nam(String tenThuongGoi) {
        return nguoiSong(tenThuongGoi, Gender.MALE);
    }

    public static Person nu(String tenThuongGoi) {
        return nguoiSong(tenThuongGoi, Gender.FEMALE);
    }

    /** Nhân khẩu đã khuất, có ngày giỗ âm lịch — nguồn chân lý để context {@code events} nhắc giỗ. */
    public static Person daKhuat(String tenThuongGoi, Gender gioiTinh) {
        Person person = Person.create(PersonId.newId(), gioiTinh, false,
                List.of(PersonName.of(NameType.THUONG_GOI, tenThuongGoi, true)));
        person.markDeceased(LifeDate.ofLunar(LunarDate.of(1975, 3, 10)));
        return person;
    }

    /** Ngày sinh dương lịch đầy đủ — dùng để kiểm việc hạ mức xuống chỉ còn năm ở Tầng 2. */
    public static LifeDate ngaySinh(int nam, int thang, int ngay) {
        return LifeDate.ofSolar(LocalDate.of(nam, thang, ngay));
    }

    /** Đủ tuổi vị thành niên hay chưa được xét theo <b>năm</b> sinh, nên fixture cũng chỉ cần năm. */
    public static LifeDate sinhNam(int nam) {
        return LifeDate.ofSolar(LocalDate.of(nam, 6, 15));
    }

    public static ContactInfo lienHe() {
        return new ContactInfo("0912345678", "nguoidung@example.com", "zalo_abc");
    }
}
