package vn.giapha.dataimport.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Cổng cảnh báo <b>kỵ húy</b> (FR-1.6): tên huý định đặt trùng tên huý của một bậc trên.
 *
 * <p>Kỵ húy là tục kiêng gọi tên thật của bậc trên; đặt trùng tên huý của một cụ bị xem là thất
 * kính. Hệ thống <b>cảnh báo chứ không cấm</b> — thẩm quyền quyết định thuộc về dòng họ, không
 * thuộc về phần mềm. Vì vậy đây là cảnh báo, và mỗi lần ghi đè phải vào audit_log.</p>
 *
 * <p>Như {@link DuplicateScanPort}, cổng này <b>không chứa thuật toán</b>: phép dò kỵ húy đã tồn
 * tại ở {@code genealogy}, adapter chỉ dịch lời gọi.</p>
 */
public interface TabooScanPort {

    /** @return chỉ các dòng có va chạm; dòng không va chạm không xuất hiện trong kết quả */
    List<KetQua> scan(List<UngVien> rows);

    /**
     * @param tabooName tên huý định đặt. Chỉ lớp tên HUY mới phạm huý — quét cả các lớp tên khác
     *        chỉ tạo ra một biển cảnh báo giả khiến người dùng bấm vẫn-ghi theo phản xạ.
     * @param generation đời thứ dự kiến, để xác định ai là bậc trên; null thì quét toàn họ
     */
    record UngVien(String ref, String tabooName, Integer generation, UUID excludePersonId) {
    }

    /** @param ancestorPersonId bậc trên mang tên huý ấy */
    record KetQua(String ref, String tabooName, UUID ancestorPersonId, String ancestorName,
                  Integer ancestorGeneration) {
    }
}
