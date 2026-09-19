package vn.giapha.genealogy.application;

import java.util.List;
import java.util.UUID;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.shared.vo.Gender;

/**
 * Một hồ sơ <b>đang định nhập</b>, đưa vào bộ dò trùng.
 *
 * <p>Cố ý tách khỏi {@code AddPersonCommand}: đường ống nhập liệu hàng loạt (400 người một lượt)
 * dùng lại đúng bộ dò này mà không đi qua use case thêm người, nên đầu vào của bộ dò không được
 * ràng vào một lệnh cụ thể nào.</p>
 *
 * <p>Đời thứ và chi phải là giá trị <b>đã suy ra</b> (từ liên kết cha/mẹ), không phải giá trị
 * client gửi lên — chấm điểm bằng đời thứ tự khai là tự bỏ đi phản chứng đáng tin nhất.</p>
 *
 * @param ref mã tham chiếu do bên gọi đặt, để ghép kết quả về đúng dòng đầu vào; có thể null khi
 *        chỉ dò một người
 * @param excludePersonId bỏ qua chính nhân khẩu này (khi đang sửa hồ sơ, tránh tự báo trùng với
 *        chính mình); null khi thêm mới
 */
public record DuplicateProbe(String ref,
                             List<PersonName> names,
                             Gender gender,
                             Integer generation,
                             UUID branchId,
                             LifeDate birth,
                             LifeDate death,
                             String nativePlace,
                             UUID excludePersonId) {

    public DuplicateProbe {
        names = names == null ? List.of() : List.copyOf(names);
    }

    /** Dạng rút gọn cho lối gọi một người: không mã tham chiếu, không loại trừ ai. */
    public static DuplicateProbe of(List<PersonName> names, Gender gender, Integer generation,
                                    UUID branchId, LifeDate birth, LifeDate death, String nativePlace) {
        return new DuplicateProbe(null, names, gender, generation, branchId, birth, death,
                nativePlace, null);
    }
}
