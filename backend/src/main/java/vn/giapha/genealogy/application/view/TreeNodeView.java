package vn.giapha.genealogy.application.view;

import java.util.List;
import java.util.UUID;

/**
 * Một node trên phả đồ.
 *
 * @param depth              khoảng cách đời so với gốc: {@code 0} là gốc, dương là đời dưới,
 *                           <b>âm là đời trên</b>
 * @param parentIds          cha/mẹ <b>có mặt trong projection này</b> (không phải toàn bộ cha mẹ)
 * @param spouseIds          vợ/chồng có mặt trong projection này
 * @param childCount         tổng số con, kể cả phần chưa nạp — để giao diện hiện "còn 12 người con"
 * @param hasMoreDescendants {@code true} khi còn con cháu chưa nạp vì chạm {@code depth} hoặc
 *                           {@code maxNodes}
 */
public record TreeNodeView(UUID id,
                           PersonSummaryView person,
                           int depth,
                           List<UUID> parentIds,
                           List<UUID> spouseIds,
                           Integer childCount,
                           boolean hasMoreDescendants,
                           List<PersonBadge> badges) {
}
