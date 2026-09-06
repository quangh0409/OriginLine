package vn.giapha.genealogy.domain.port;

import java.util.List;
import vn.giapha.genealogy.domain.TabooConflict;

/**
 * Tra cứu <b>kỵ húy</b> (FR-1.6): tên mới có trùng tên húy của bậc trên nào không.
 *
 * <p>Truy vấn chạy trên cột không dấu {@code person_name.name_unaccented} (generated ở V6) để
 * "Nguyễn Văn Tuân" và "Nguyen Van Tuan" đều bị soi, và trên chỉ mục
 * {@code ix_person_name_huy_unaccented}.</p>
 */
public interface TabooNamePort {

    /**
     * @param candidateFullName tên đang định đặt (có dấu, chưa chuẩn hoá)
     * @param generationOfNewPerson đời thứ dự kiến của người mới; {@code null} khi chưa nối vào
     *        cây — khi đó phạm vi quét là toàn dòng họ vì không xác định được ai là "bậc trên"
     * @param excludePersonId bỏ qua chính nhân khẩu đang sửa (tránh tự báo trùng với mình)
     * @return danh sách va chạm, rỗng nghĩa là không có gì phải cảnh báo
     */
    List<TabooConflict> findConflicts(String candidateFullName, Integer generationOfNewPerson,
                                      java.util.UUID excludePersonId);
}
