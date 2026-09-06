package vn.giapha.genealogy.api.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
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
import vn.giapha.genealogy.application.TreeProjectionService;
import vn.giapha.genealogy.application.command.TreeQuery;
import vn.giapha.genealogy.application.view.TreeDirection;
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
 */
@RestController
@RequestMapping("/api/v1/tree")
@Validated
public class TreeController {

    private static final Logger log = LoggerFactory.getLogger(TreeController.class);

    private final TreeProjectionService treeProjection;
    private final ObjectMapper objectMapper;

    public TreeController(TreeProjectionService treeProjection, ObjectMapper objectMapper) {
        this.treeProjection = treeProjection;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<TreeProjectionDto> tree(
            @RequestParam UUID rootId,
            @RequestParam(defaultValue = "3") @Min(0) @Max(TreeQuery.MAX_DEPTH) int depth,
            @RequestParam(defaultValue = "DESCENDANTS") TreeDirection direction,
            @RequestParam(defaultValue = "true") boolean includeSpouses,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "500") @Min(1) @Max(TreeQuery.MAX_NODES) int maxNodes,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        TreeProjectionDto projection = GenealogyDtoMapper.toDto(treeProjection.project(
                new TreeQuery(rootId, depth, direction, includeSpouses, includeDeleted, maxNodes)));
        String etag = etagOf(projection);

        // ETag tính trên NỘI DUNG ĐÃ LỌC chứ không trên khung xương: hai vai khác nhau nhìn cùng
        // một gốc sẽ nhận hai cây khác nhau, dùng chung ETag là mở đường cho client (hoặc proxy)
        // trả lại bản của vai kia.
        if (etag != null && etag.equals(normalize(ifNoneMatch))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        log.debug("GET /api/v1/tree rootId={} depth={} -> {} node", rootId, depth,
                projection.nodes().size());
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePrivate())
                .body(projection);
    }

    /**
     * ETag là vân tay của chính phản hồi.
     *
     * <p>Không lấy từ phiên bản bản ghi gốc: một projection phụ thuộc hàng trăm nhân khẩu, nên
     * "gốc chưa đổi" không hề đồng nghĩa "cây chưa đổi". Băm nội dung là cách duy nhất đúng ở đây,
     * và cũng rẻ vì payload đã dựng xong rồi.</p>
     */
    private String etagOf(TreeProjectionDto projection) {
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
            byte[] hash = digest.digest(payload);
            return "\"" + HexFormat.of().formatHex(hash, 0, 16) + "\"";
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            log.warn("Khong tinh duoc ETag cho pha do, bo qua cache co dieu kien", ex);
            return null;
        }
    }

    private String normalize(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        String value = headerValue.trim();
        return value.startsWith("W/") ? value.substring(2) : value;
    }
}
