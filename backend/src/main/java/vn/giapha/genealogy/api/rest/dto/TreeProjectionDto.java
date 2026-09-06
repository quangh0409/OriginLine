package vn.giapha.genealogy.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import vn.giapha.genealogy.application.view.PersonBadge;
import vn.giapha.genealogy.application.view.TreeDirection;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.RelType;

/**
 * Projection <b>phẳng</b> của một nhánh phả đồ: {@code nodes} + {@code edges}, nạp thẳng vào React
 * Flow. Cần cây lồng theo hình dạng tự chọn thì dùng GraphQL.
 *
 * <p>Giao diện gọi nhiều lần cho nhiều nhánh rồi <b>ghép</b> vào cùng một đồ thị, nên node và cạnh
 * có thể trùng giữa các lần gọi và phải khử trùng theo {@code id}.</p>
 *
 * <p>{@code edges} chỉ chứa cạnh mà <b>cả hai đầu</b> đều nằm trong {@code nodes}. Cạnh có một đầu
 * bị lọc vì phân tầng riêng tư sẽ không xuất hiện - cây có thể <b>đứt đoạn một cách hợp lệ</b>,
 * đặc biệt với Khách.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TreeProjectionDto(UUID rootId, List<TreeNodeDto> nodes, List<TreeEdgeDto> edges,
                                TreeMetaDto meta) {

    /**
     * @param depth khoảng cách đời từ gốc: 0 là gốc, dương là đời dưới, <b>âm là đời trên</b>
     * @param childCount tổng số con kể cả phần chưa nạp, để hiện "còn 12 người con" trên nút mở rộng
     * @param hasMoreDescendants còn con cháu chưa nạp vì chạm {@code depth} hoặc {@code maxNodes}
     * @param badges nhãn nghiệp vụ tính sẵn ở backend (đích tôn, con nuôi, dâu/rể, tuyệt tự...)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TreeNodeDto(UUID id, PersonSummaryDto person, int depth, List<UUID> parentIds,
                              List<UUID> spouseIds, Integer childCount, boolean hasMoreDescendants,
                              List<PersonBadge> badges) {
    }

    /**
     * @param id id cạnh, <b>ổn định giữa các lần gọi</b> để khử trùng khi ghép đồ thị
     * @param validTo có giá trị nghĩa là quan hệ đã kết thúc; canvas nên vẽ nét đứt
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TreeEdgeDto(String id, UUID source, UUID target, RelType relType, HeirKind heirKind,
                              Integer spouseOrder, LocalDate validTo) {
    }

    /**
     * @param truncated projection đã bị cắt vì chạm {@code maxNodes} - giao diện phải xử lý, đừng
     *        coi là đã nhận đủ cây
     * @param fromCache khung xương lấy từ cache hay vừa duyệt lại; hồ sơ thì luôn nạp và lọc tươi
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TreeMetaDto(int depth, TreeDirection direction, int nodeCount, int edgeCount,
                              boolean truncated, List<UUID> truncatedNodeIds, Instant generatedAt,
                              boolean fromCache) {
    }
}
