package vn.giapha.genealogy.api.rest.public_.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;
import vn.giapha.genealogy.application.view.TreeDirection;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.RelType;

/**
 * Phả đồ phẳng cho Khách vãng lai — <b>chỉ gồm người đã khuất</b>.
 *
 * <h2>Quyết định thiết kế: cây của Khách là cây CÓ LỖ, không phải cây được vá</h2>
 * Một cụ đã khuất có con còn sống, người con ấy lại có con đã khuất. Ba cách xử lý cạnh:
 * <ol>
 *   <li><b>Chèn node ẩn danh</b> ("một người còn sống") để giữ đường nối — <b>bị loại</b>. BA v2
 *       §10 nói Khách không thấy người còn sống, và {@code PrivacyTierService} nói rõ: không phải
 *       "thấy node ẩn danh", mà là <i>không tồn tại trong phản hồi</i>. Node ẩn danh vẫn tiết lộ
 *       có bao nhiêu người sống và họ đứng ở đâu trong phả hệ.</li>
 *   <li><b>Rút gọn đường đi</b>, nối thẳng cụ với cháu — <b>bị loại</b>. Nó bịa ra một quan hệ
 *       cha–con không có thật; với một cuốn gia phả thì đó là làm sai phả hệ, tệ hơn cả thiếu.</li>
 *   <li><b>Bỏ hẳn người sống và mọi cạnh chạm vào họ</b> — <b>đã chọn</b>. Người cháu vẫn xuất hiện
 *       đúng đời của mình nhưng {@code parentIds} rỗng; phả đồ công khai vì thế có thể gồm nhiều
 *       mảnh rời. Không bịa dữ liệu, không tiết lộ ai bị giấu.</li>
 * </ol>
 * {@code meta.guestFiltered} luôn {@code true} để giao diện hiện một câu giải thích chung ("một số
 * thành viên còn sống không hiển thị với khách") — <b>một hằng số, không phải phép đếm</b>, nên nó
 * không nói gì về số người bị ẩn.
 *
 * @param nodes node đã sắp theo đời rồi tới tên
 * @param edges chỉ những cạnh mà <b>cả hai đầu</b> đều có trong {@code nodes}
 */
public record PublicTreeDto(UUID rootId, List<PublicTreeNodeDto> nodes, List<PublicTreeEdgeDto> edges,
                            PublicTreeMetaDto meta) {

    /**
     * @param depth      khoảng cách đời so với gốc; {@code 0} là gốc, âm là đời trên
     * @param parentIds  cha/mẹ <b>có mặt trong chính projection này</b> — rỗng không có nghĩa là
     *                   mồ côi, rất có thể cha/mẹ còn sống nên đã bị lọc
     * @param expandable còn có thể mở rộng tiếp hay không, suy ra <b>chỉ từ tham số yêu cầu</b>
     *                   (node nằm ở rìa độ sâu, hoặc bị cắt vì chạm {@code maxNodes}).
     *                   Cố ý <b>không</b> dùng số con thật: số ấy gồm cả người còn sống, để lộ ra
     *                   là gián tiếp nói "cụ này còn mấy người con mà bạn không được thấy". Hệ quả
     *                   chấp nhận được: cờ này có thể {@code true} ở một node thực ra không còn
     *                   con nào — bấm mở rộng chỉ tốn một lượt gọi trả về rỗng.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PublicTreeNodeDto(UUID id, PublicPersonSummaryDto person, int depth,
                                    List<UUID> parentIds, List<UUID> spouseIds, boolean expandable) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PublicTreeEdgeDto(String id, UUID source, UUID target, RelType relType,
                                    HeirKind heirKind, Integer spouseOrder) {
    }

    /**
     * @param truncated     đã chạm trần {@code maxNodes} nên cây bị cắt — giao diện phải xử lý
     * @param guestFiltered luôn {@code true}: đây là bản đã lọc cho Khách, không phải cây đầy đủ
     */
    public record PublicTreeMetaDto(int depth, TreeDirection direction, int nodeCount, int edgeCount,
                                    boolean truncated, boolean guestFiltered) {
    }
}
