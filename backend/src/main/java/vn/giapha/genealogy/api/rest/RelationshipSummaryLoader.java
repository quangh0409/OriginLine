package vn.giapha.genealogy.api.rest;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.giapha.genealogy.api.rest.dto.PersonSummaryDto;
import vn.giapha.genealogy.application.PersonQueryService;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.application.view.RelationshipView;

/**
 * Nạp tóm tắt của những nhân khẩu ở <b>đầu kia</b> các cạnh quan hệ trong một hồ sơ.
 *
 * <h2>Vì sao việc này nằm ở tầng api chứ không ở application</h2>
 * Tầng application đã trả đủ dữ liệu đúng (danh sách cạnh đã lọc, và một API công khai để nạp hồ sơ
 * đã lọc theo lô). Việc gộp hai thứ đó thành hình dạng mà contract REST yêu cầu là chuyện của tầng
 * api - đúng chiều phụ thuộc {@code api → application → domain}.
 *
 * <h2>Bất biến bảo mật</h2>
 * Tóm tắt <b>bắt buộc</b> đi qua {@link PersonQueryService#visibleByIds}, tức qua đúng
 * {@code PrivacyTierService} và đúng ngữ cảnh người gọi hiện tại. Đọc thẳng kho nhân khẩu ở đây sẽ
 * biến màn "Quan hệ" thành lỗ rò: chỉ cần một người còn sống là con của một cụ đã khuất (hồ sơ công
 * khai) là tên người sống ấy lộ ra cho bất kỳ ai mở hồ sơ cụ.
 *
 * <p>Một lượt nạp theo lô cho cả hồ sơ, không phải một lượt cho mỗi cạnh - số cạnh của một người có
 * thể lên tới hàng chục khi gia đình đông con.</p>
 */
@Component
public class RelationshipSummaryLoader {

    private final PersonQueryService personQuery;

    public RelationshipSummaryLoader(PersonQueryService personQuery) {
        this.personQuery = personQuery;
    }

    /**
     * Tóm tắt của mọi đầu kia trong {@code view.relationships()}, tra theo id.
     *
     * @return map rỗng khi hồ sơ không có cạnh nào; id nào người gọi không được thấy thì đơn giản là
     *         không có mặt trong map, và cạnh tương ứng sẽ ra JSON không có {@code otherPerson}
     */
    public Map<UUID, PersonSummaryDto> forSubject(PersonView view) {
        if (view == null || view.relationships() == null || view.relationships().isEmpty()) {
            return Map.of();
        }
        Set<UUID> otherIds = new LinkedHashSet<>();
        for (RelationshipView rel : view.relationships()) {
            if (view.id() != null && view.id().equals(rel.fromPersonId())) {
                otherIds.add(rel.toPersonId());
            } else if (view.id() != null && view.id().equals(rel.toPersonId())) {
                otherIds.add(rel.fromPersonId());
            }
        }
        otherIds.remove(null);
        otherIds.remove(view.id());
        if (otherIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, PersonSummaryDto> summaries = new LinkedHashMap<>();
        for (PersonView other : personQuery.visibleByIds(otherIds)) {
            summaries.put(other.id(), GenealogyDtoMapper.toSummary(other));
        }
        return summaries;
    }
}
