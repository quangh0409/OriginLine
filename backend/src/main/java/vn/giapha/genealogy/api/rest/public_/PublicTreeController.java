package vn.giapha.genealogy.api.rest.public_;

import jakarta.validation.constraints.Min;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.genealogy.api.rest.public_.dto.PublicTreeDto;
import vn.giapha.genealogy.application.DefaultTreeRootResolver;
import vn.giapha.genealogy.application.GenealogyProblemCodes;
import vn.giapha.genealogy.application.PrivacyTierService;
import vn.giapha.genealogy.application.TreeProjectionService;
import vn.giapha.genealogy.application.command.TreeQuery;
import vn.giapha.genealogy.application.view.TreeDirection;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Phả đồ công khai — {@code GET /api/v1/public/tree}.
 *
 * <h2>Cây của Khách là cây có lỗ</h2>
 * Người còn sống bị bỏ hẳn cùng mọi cạnh chạm vào họ, nên một người cháu đã khuất có thể xuất hiện
 * với {@code parentIds} rỗng vì cha mẹ còn sống. Không chèn node ẩn danh, không nối tắt qua đời bị
 * giấu — lý do đầy đủ nằm ở javadoc của {@link PublicTreeDto}. Giao diện <b>phải</b> vẽ được cây
 * gồm nhiều mảnh rời; cạnh thiếu ở đây không phải lỗi dữ liệu.
 *
 * <h2>Chống quét cả phả</h2>
 * <ul>
 *   <li><b>{@code rootId} tuỳ chọn, và thiếu nó thì gốc là Thuỷ tổ</b>. Trước đây tham số này bắt
 *       buộc, với lý do "không có endpoint nào liệt kê gốc". Lý do ấy không còn đứng được: nó làm
 *       trang phả đồ công khai — cửa vào của cả nửa "cổng thông tin dòng họ" — trả {@code 400} cho
 *       mọi Khách, trong khi thứ duy nhất nó giấu là <b>một</b> định danh của <b>một</b> người đã
 *       khuất, vốn đã tra ra được bằng {@code /public/persons/search}. Chi phí quét cả phả thì
 *       không đổi một chút nào: hai cái trần dưới đây và {@link PublicRateLimitFilter} vẫn tính
 *       tiền từng lượt gọi y như cũ.</li>
 *   <li><b>Trần thấp hơn hẳn bản thành viên</b>: {@code depth} tối đa
 *       {@code giapha.public-portal.max-tree-depth} (mặc định 4, bản thành viên là 10) và số node
 *       tối đa {@code max-tree-nodes} (mặc định 200, bản thành viên là 2000). Muốn kéo cả một chi
 *       về máy thì phải trả bằng rất nhiều lượt gọi — và mỗi lượt đều bị
 *       {@link PublicRateLimitFilter} tính tiền.</li>
 *   <li><b>Không có {@code includeDeleted}</b> và không có tham số nào mở rộng phạm vi.</li>
 * </ul>
 *
 * <p>{@code includeSpouses} được giữ vì vợ/chồng là một phần của phả hệ; với Khách thì chỉ những
 * vợ/chồng <b>đã khuất</b> hiện ra, đúng như mọi node khác.</p>
 */
@RestController
@RequestMapping("/api/v1/public/tree")
@Validated
public class PublicTreeController {

    private static final Logger log = LoggerFactory.getLogger(PublicTreeController.class);

    private final TreeProjectionService treeProjection;
    private final DefaultTreeRootResolver defaultRoot;
    private final PrivacyTierService privacy;
    private final PublicGuestScope guestScope;
    private final PublicVisibilityGuard guard;
    private final PublicPortalProperties properties;

    public PublicTreeController(TreeProjectionService treeProjection,
                                DefaultTreeRootResolver defaultRoot, PrivacyTierService privacy,
                                PublicGuestScope guestScope, PublicVisibilityGuard guard,
                                PublicPortalProperties properties) {
        this.treeProjection = treeProjection;
        this.defaultRoot = defaultRoot;
        this.privacy = privacy;
        this.guestScope = guestScope;
        this.guard = guard;
        this.properties = properties;
    }

    @GetMapping
    public ResponseEntity<PublicTreeDto> tree(
            @RequestParam(required = false) UUID rootId,
            @RequestParam(required = false) @Min(0) Integer depth,
            @RequestParam(defaultValue = "DESCENDANTS") TreeDirection direction,
            @RequestParam(defaultValue = "true") boolean includeSpouses) {

        int requestedDepth = depth == null ? properties.getDefaultTreeDepth() : depth;
        if (requestedDepth > properties.getMaxTreeDepth()) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "Pha do cong khai gioi han do sau toi da %d doi; hay mo rong tung nhanh"
                            + " hoac dang nhap de xem day du", properties.getMaxTreeDepth()));
        }

        // CA HAI buoc deu chay TRONG asGuest. Phan giai goc ma dung ngu canh that cua mot thanh
        // vien lo mang token toi day se chon ra goc cua CHI ho - tuc cung mot URL cong khai tra ve
        // hai cay khac nhau tuy token, dung dieu ma PublicGuestScope sinh ra de chan.
        PublicTreeDto body = guestScope.asGuest(() -> {
            UUID root = rootId != null ? rootId : defaultRoot.resolve(privacy.caller())
                    .orElseThrow(() -> new NotFoundException(GenealogyProblemCodes.NOT_FOUND,
                            "Chua co nhan khau da khuat nao de lam goc pha do cong khai"));
            return guard.tree(treeProjection.project(new TreeQuery(root, requestedDepth, direction,
                    includeSpouses, false, properties.getMaxTreeNodes())), requestedDepth);
        });

        log.debug("GET /api/v1/public/tree rootId={} depth={} -> {} node cong khai",
                rootId == null ? "(goc mac dinh)" : rootId, requestedDepth, body.nodes().size());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(properties.getCacheSeconds(), TimeUnit.SECONDS)
                        .cachePublic())
                .body(body);
    }
}
