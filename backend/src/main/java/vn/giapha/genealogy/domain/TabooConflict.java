package vn.giapha.genealogy.domain;

import java.util.UUID;
import vn.giapha.shared.domain.ValueObject;

/**
 * Một bậc trên có <b>tên húy</b> trùng với tên đang định đặt (FR-1.6).
 *
 * <p>Kỵ húy là tục kiêng gọi tên thật của người trên; trùng tên húy bậc trên bị xem là thất kính.
 * Đây là <b>cảnh báo, không phải lệnh cấm</b> — dòng họ có quyền quyết định ghi đè, và việc ghi đè
 * được lưu vào {@code audit_log}.</p>
 *
 * @param ancestorPersonId    nhân khẩu bậc trên bị trùng
 * @param ancestorDisplayName tên hiển thị của bậc trên
 * @param ancestorGeneration  đời thứ của bậc trên (Thuỷ tổ = 1); có thể null
 * @param tabooName           tên húy bị trùng
 * @param matchedNameType     lớp tên của bên gây va chạm
 * @param matchKind           mức độ trùng
 * @param relationHint        gợi ý quan hệ, chỉ để hiển thị — đừng phân tích chuỗi này
 */
public record TabooConflict(UUID ancestorPersonId,
                            String ancestorDisplayName,
                            Integer ancestorGeneration,
                            String tabooName,
                            NameType matchedNameType,
                            TabooMatchKind matchKind,
                            String relationHint) implements ValueObject {
}
