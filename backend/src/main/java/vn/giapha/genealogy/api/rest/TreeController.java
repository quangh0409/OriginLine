package vn.giapha.genealogy.api.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.genealogy.api.rest.dto.TreeProjectionDto;
import vn.giapha.genealogy.application.CallerContext;
import vn.giapha.genealogy.application.DefaultTreeRootResolver;
import vn.giapha.genealogy.application.GenealogyProblemCodes;
import vn.giapha.genealogy.application.PrivacyTierService;
import vn.giapha.genealogy.application.TreeProjectionService;
import vn.giapha.genealogy.application.command.TreeQuery;
import vn.giapha.genealogy.application.view.TreeDirection;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.BranchPath;
import java.util.concurrent.TimeUnit;

/**
 * REST cho phả đồ - {@code /api/v1/tree}.
 *
 * <h2>Lazy theo độ sâu</h2>
 * Gọi lần đầu với {@code depth} nhỏ (mặc định 3). Node nào còn con chưa nạp sẽ mang
 * {@code hasMoreDescendants = true} kèm {@code childCount}; giao diện hiện nút mở rộng rồi gọi lại
 * cho riêng node đó và <b>ghép</b> vào đồ thị đang có. Đây là cách duy nhất để canvas chịu được
 * một dòng họ hàng nghìn người mà không nạp cả cây trong một nhịp.
 *
 * <h2>Hai trần, và {@code meta.truncated}</h2>
 * {@code depth} tối đa 10, {@code maxNodes} tối đa 2000. Chạm trần không phải lỗi - phản hồi vẫn
 * {@code 200} nhưng {@code meta.truncated = true} và {@code truncatedNodeIds} chỉ ra chỗ bị cắt.
 * Giao diện <b>phải</b> xử lý cờ này; bỏ qua nó thì cây trông "đủ" trong khi đang thiếu người.
 *
 * <p><b>Cây có lỗ là hợp lệ.</b> Cạnh chỉ xuất hiện khi cả hai đầu đều hiển thị được, nên với
 * Khách - người không được thấy bất kỳ ai còn sống - phả đồ có thể đứt đoạn giữa các đời.</p>
 *
 * <h2>{@code rootId} là tuỳ chọn</h2>
 * Người dùng mở trang phả đồ mà chưa chọn ai thì không có {@code rootId} để gửi, và bắt buộc tham
 * số ấy nghĩa là <b>mọi vai đều nhận {@code 400}</b> ở đúng màn hình chính của sản phẩm. Thiếu nó,
 * máy chủ tự chọn gốc theo <b>vai và phạm vi chi/ngành</b> của người gọi — xem
 * {@link DefaultTreeRootResolver} để biết bốn vai ra bốn gốc khác nhau và vì sao.
 *
 * <h2>{@code ETag} buộc vào người gọi, và {@code Vary: Authorization}</h2>
 * Nội dung của endpoint này phụ thuộc người gọi (bộ lọc riêng tư, và cả gốc mặc định), nên
 * hai cơ chế đệm đều phải biết điều đó. Xem {@link #etagOf} cho vân tay người gọi. Còn
 * {@code Cache-Control: private} thì chỉ chặn proxy dùng chung — nó <b>không</b> chặn bộ nhớ đệm
 * của chính trình duyệt khi hai người nối tiếp nhau trên một máy (máy tính nhà thờ họ), và
 * đó là việc của {@code Vary}.
 *
 * <p><b>Khung xương cây trong Redis thì ngược lại: cố ý không mang vai.</b> Nó chỉ gồm id và độ
 * sâu nên giống nhau với mọi người gọi; thứ bảo vệ riêng tư là bước lọc chạy sau nó và luôn chạy
 * tươi. Trộn vai vào khoá đó vừa nhân bản cache vừa tạo ảo giác rằng riêng tư đã được cache lo.
 * Xem {@code TreeProjectionService.cacheKey}.</p>
 *
 * <p><b>Gửi {@code rootId} thì hành vi không đổi một ly.</b> Phép phân giải gốc mặc định không hề
 * chạy trong ca đó; {@code meta}, {@code ETag} và mọi tham số còn lại giữ nguyên ngữ nghĩa cũ.</p>
 */
@RestController
@RequestMapping("/api/v1/tree")
@Validated
public class TreeController {

    private static final Logger log = LoggerFactory.getLogger(TreeController.class);

    private final TreeProjectionService treeProjection;
    private final DefaultTreeRootResolver defaultRoot;
    private final PrivacyTierService privacy;
    private final ObjectMapper objectMapper;

    public TreeController(TreeProjectionService treeProjection, DefaultTreeRootResolver defaultRoot,
                          PrivacyTierService privacy, ObjectMapper objectMapper) {
        this.treeProjection = treeProjection;
        this.defaultRoot = defaultRoot;
        this.privacy = privacy;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<TreeProjectionDto> tree(
            @RequestParam(required = false) UUID rootId,
            @RequestParam(defaultValue = "3") @Min(0) @Max(TreeQuery.MAX_DEPTH) int depth,
            @RequestParam(defaultValue = "DESCENDANTS") TreeDirection direction,
            @RequestParam(defaultValue = "true") boolean includeSpouses,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "500") @Min(1) @Max(TreeQuery.MAX_NODES) int maxNodes,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        CallerContext caller = privacy.caller();
        UUID root = rootId != null ? rootId : defaultRootOrFail(caller);
        TreeProjectionDto projection = GenealogyDtoMapper.toDto(treeProjection.project(
                new TreeQuery(root, depth, direction, includeSpouses, includeDeleted, maxNodes)));
        String etag = etagOf(projection, caller);

        if (etag != null && etag.equals(normalize(ifNoneMatch))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .varyBy(HttpHeaders.AUTHORIZATION)
                    .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePrivate())
                    .build();
        }
        log.debug("GET /api/v1/tree rootId={}{} depth={} -> {} node", root,
                rootId == null ? " (goc mac dinh)" : "", depth, projection.nodes().size());
        return ResponseEntity.ok()
                .eTag(etag)
                // Vary: Authorization - thieu header nay thi bo nho dem CUA CHINH TRINH DUYET coi
                // URL la khoa duy nhat. Tren mot may dung chung (may tinh nha tho ho), nguoi thu
                // hai dang nhap trong vong 60 giay duoc phuc vu LAI than phan hoi cua nguoi thu
                // nhat ma khong he goi lai may chu. `private` chi chan proxy dung chung, khong
                // chan duoc chuyen nay.
                .varyBy(HttpHeaders.AUTHORIZATION)
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePrivate())
                .body(projection);
    }

    /**
     * Gốc mặc định khi client không gửi {@code rootId}.
     *
     * <p><b>Lối gọi cũ không đổi một ly:</b> có {@code rootId} thì đường đi y như trước, và phép
     * phân giải này thậm chí không chạy. Đây là một nhánh <i>thêm</i> cho ca không có tham số, chứ
     * không phải một giá trị mặc định gắn vào tham số — luật chọn gốc phụ thuộc vai và phạm vi chi
     * của người gọi nên không có hằng số nào diễn đạt nổi. Xem {@link DefaultTreeRootResolver}.</p>
     *
     * <p>Phả rỗng (hoặc không có ai người gọi được biết là tồn tại) thì {@code 404} chứ không phải
     * một cây rỗng {@code 200}: cây rỗng nói dối rằng dòng họ không có ai, và giao diện sẽ vẽ ra
     * một canvas trắng trông hệt như lỗi tải.</p>
     */
    private UUID defaultRootOrFail(CallerContext caller) {
        return defaultRoot.resolve(caller).orElseThrow(() -> new NotFoundException(
                GenealogyProblemCodes.NOT_FOUND,
                "Chua co nhan khau nao de lam goc pha do"));
    }

    /**
     * ETag là vân tay của <b>nội dung phản hồi cộng với người gọi</b>.
     *
     * <h2>Vì sao băm nội dung thôi thì chưa đủ</h2>
     * Băm nội dung đã lọc là đúng nhưng chỉ bảo đảm một chiều: <i>nội dung khác ⇒ ETag khác</i>.
     * Chiều ngược lại - <i>ETag giống ⇒ được phép trả 304</i> - thì không: hai người gọi khác
     * nhau có thể tình cờ nhận đúng một cây (thường gặp: một nhánh toàn người đã khuất, nơi bộ
     * lọc riêng tư không cắt gì cả), rồi một hôm dữ liệu đổi và hai cây tách ra. Lúc ấy
     * {@code If-None-Match} của người này vẫn khớp vân tay của người kia.
     *
     * <p>Trộn vân tay người gọi vào làm cho <b>ETag giống ⇒ cùng người gọi và cùng nội dung</b>.
     * Đây là điều kiện duy nhất khiến {@code 304} không bao giờ trả cho người khác.</p>
     *
     * <h2>Đưa cái gì vào vân tay, và vì sao {@code personId} là bắt buộc</h2>
     * "Vai + phạm vi chi" <b>không đủ</b>: {@code PersonVisibility} mở các nhóm trường cho
     * <i>chính chủ</i> ({@code vis.self()}), nên hai Thành viên cùng vai cùng chi vẫn nhận hai cây
     * khác nhau ở đúng một node - node của chính họ. Bỏ {@code personId} ra là chấp nhận rò rỉ đúng
     * cái node nhạy cảm nhất.
     *
     * <p>Chi phí gần như bằng không: ETag không phải cache phía máy chủ mà chỉ là <i>validator</i>.
     * Cùng một người gọi lặp lại vẫn nhận {@code 304} như cũ; chỉ khi <b>người khác</b> dùng lại
     * cùng một mục cache của trình duyệt mới tốn thêm một lượt tải đầy đủ - đúng thứ ta muốn.</p>
     *
     * <p>Không lấy từ phiên bản bản ghi gốc: một projection phụ thuộc hàng trăm nhân khẩu, nên
     * "gốc chưa đổi" không hề đồng nghĩa "cây chưa đổi".</p>
     */
    private String etagOf(TreeProjectionDto projection, CallerContext caller) {
        try {
            // Băm bản KHÔNG có generatedAt/fromCache: hai trường đó đổi ở mỗi lần gọi, để nguyên
            // thì ETag không bao giờ trùng và 304 vĩnh viễn không xảy ra.
            TreeProjectionDto.TreeMetaDto meta = projection.meta();
            TreeProjectionDto stable = new TreeProjectionDto(projection.rootId(), projection.nodes(),
                    projection.edges(), new TreeProjectionDto.TreeMetaDto(meta.depth(),
                            meta.direction(), meta.nodeCount(), meta.edgeCount(), meta.truncated(),
                            meta.truncatedNodeIds(), null, false));
            byte[] payload = objectMapper.writeValueAsBytes(stable);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(callerFingerprint(caller).getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(payload);
            return "\"" + HexFormat.of().formatHex(hash, 0, 16) + "\"";
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            log.warn("Khong tinh duoc ETag cho pha do, bo qua cache co dieu kien", ex);
            return null;
        }
    }

    /**
     * Vân tay người gọi - <b>không</b> chứa {@code keycloak_sub}.
     *
     * <p>{@code personId} đã đủ để tách hai người dùng khác nhau và nó chính là thứ mà bộ lọc riêng
     * tư thật sự đọc. Hai tài khoản chưa được ghép vào phả ({@code personId == null}) cùng vai cùng
     * phạm vi thì nhận đúng một cây, nên gom chung là đúng chứ không phải sơ hở.</p>
     *
     * <p>Chỉ băm, không phát ra ngoài: giá trị này đi qua SHA-256 cùng payload nên đường dẫn
     * {@code ltree} và {@code personId} không xuất hiện trong header {@code ETag}.</p>
     */
    private String callerFingerprint(CallerContext caller) {
        String branches = caller.managedBranches().stream()
                .map(BranchPath::value)
                .sorted()
                .collect(Collectors.joining(","));
        return "caller:v1|" + caller.role()
                + "|" + caller.personId()
                + "|" + (caller.homeBranch() == null ? "" : caller.homeBranch().value())
                + "|" + branches + "|";
    }

    private String normalize(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        String value = headerValue.trim();
        return value.startsWith("W/") ? value.substring(2) : value;
    }
}
