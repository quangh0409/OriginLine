package vn.giapha.content.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.content.api.rest.dto.ContentPageDto;
import vn.giapha.content.api.rest.dto.CreateHonourRequest;
import vn.giapha.content.api.rest.dto.HonourDto;
import vn.giapha.content.api.rest.dto.ReviewRequest;
import vn.giapha.content.api.rest.dto.UpdateHonourRequest;
import vn.giapha.content.application.HonourService;
import vn.giapha.content.application.HonourView;
import vn.giapha.content.application.command.CreateHonourCommand;
import vn.giapha.content.application.command.HonourQuery;
import vn.giapha.content.application.command.ReviewHonourCommand;
import vn.giapha.content.application.command.UpdateHonourCommand;
import vn.giapha.content.domain.ContentStatus;
import vn.giapha.content.domain.HonourKind;

/**
 * Vinh danh — {@code /api/v1/honours}.
 *
 * <h2>Hai bộ lọc, và cả hai đều chạy trước khi JSON được dựng</h2>
 * <ol>
 *   <li><b>Phạm vi &amp; trạng thái</b> — trong SQL, bằng {@code ltree}. Bản ghi chờ duyệt của chi
 *       khác không bao giờ vào bộ nhớ tiến trình.</li>
 *   <li><b>Nhóm trường riêng tư thứ sáu</b> ({@code PrivacyFieldGroup.HONOUR}, V17) — ở
 *       {@code HonourService}, hỏi {@code genealogy} cho từng nhân khẩu. Vinh danh của người còn
 *       sống là dữ liệu cá nhân (Nghị định 13/2023) và <b>mặc định kín</b>: chính chủ tự quyết mở
 *       cho "cùng chi" hay "cả họ". Vinh danh của người đã khuất thì công khai như mọi dữ liệu
 *       người đã khuất.</li>
 * </ol>
 *
 * <p><b>Hệ quả cần biết khi dựng giao diện:</b> duyệt một vinh danh của người còn sống chưa bật
 * công tắc thì nó vẫn không hiện ra với ai ngoài chính chủ. Đó không phải lỗi — quyền công bố nằm
 * ở chủ thể, còn quyền duyệt chỉ nói "bản ghi này có thật".</p>
 *
 * <h2>{@code DELETE} ở đây là XOÁ MỀM</h2>
 * Động từ HTTP là {@code DELETE} vì nó đúng nghĩa với client ("bỏ bản ghi này đi"), nhưng hàng
 * <b>ở lại</b> trong bảng với cờ {@code is_deleted}. Không có lệnh xoá cứng nào trong context này.
 */
@RestController
@RequestMapping("/api/v1/honours")
@Tag(name = "Honours", description = "Vinh danh gắn với nhân khẩu: đỗ đạt · chức tước · thành tích · khen thưởng")
public class HonourController {

    private static final Logger log = LoggerFactory.getLogger(HonourController.class);

    private final HonourService honours;

    public HonourController(HonourService honours) {
        this.honours = honours;
    }

    /**
     * Tra cứu.
     *
     * @param branchId lọc theo chi — tính cả <b>cây con</b>, vì "Chi Giáp có bao nhiêu người đỗ
     *                 đạt" phải gồm các ngành/cành bên dưới
     */
    @GetMapping
    @Operation(summary = "Tra cứu vinh danh theo nhân khẩu / loại / chi")
    public ResponseEntity<ContentPageDto<HonourDto>> list(
            @RequestParam(required = false) UUID personId,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return noStore(ContentDtoMapper.toHonourPage(honours.search(
                new HonourQuery(personId, HonourKind.of(kind), branchId,
                        ContentStatus.of(status), page, size))));
    }

    /** Số bản ghi chờ duyệt trong phạm vi — cho badge trên giao diện. */
    @GetMapping("/pending/count")
    @Operation(summary = "Đếm vinh danh chờ duyệt trong phạm vi được giao")
    public ResponseEntity<Map<String, Long>> pendingCount() {
        return noStore(Map.of("count", honours.countPendingForReview()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Một bản ghi vinh danh")
    public ResponseEntity<HonourDto> byId(@PathVariable UUID id) {
        HonourView view = honours.byId(id);
        return ResponseEntity.ok()
                .eTag(IfMatch.etag(view.version()))
                .varyBy(HttpHeaders.AUTHORIZATION)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(ContentDtoMapper.toDto(view));
    }

    /** Khai một vinh danh. Bản ghi vào thẳng hàng đợi duyệt ({@code PENDING}). */
    @PostMapping
    @Operation(summary = "Khai một vinh danh (vào hàng đợi duyệt)")
    public ResponseEntity<HonourDto> create(@Valid @RequestBody CreateHonourRequest body) {
        HonourView created = honours.create(new CreateHonourCommand(body.personId(), body.kind(),
                body.title(), body.year(), body.issuer(), body.description()));
        log.debug("POST /api/v1/honours -> {}", created.id());
        return ResponseEntity.created(URI.create("/api/v1/honours/" + created.id()))
                .eTag(IfMatch.etag(created.version()))
                .body(ContentDtoMapper.toDto(created));
    }

    /** Sửa. Đòi {@code If-Match}. Sửa một bản ghi đã duyệt đưa nó về lại hàng đợi. */
    @PatchMapping("/{id}")
    @Operation(summary = "Sửa vinh danh (đòi If-Match)")
    public ResponseEntity<HonourDto> update(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateHonourRequest body) {
        HonourView updated = honours.update(new UpdateHonourCommand(id, body.kind(), body.title(),
                body.year(), body.issuer(), body.description(), IfMatch.parse(ifMatch)));
        return ResponseEntity.ok()
                .eTag(IfMatch.etag(updated.version()))
                .body(ContentDtoMapper.toDto(updated));
    }

    /** Duyệt hoặc từ chối. Quyền kiểm theo chi <b>hiện tại</b> của nhân khẩu được vinh danh. */
    @PostMapping("/{id}/review")
    @Operation(summary = "Duyệt hoặc từ chối vinh danh")
    public ResponseEntity<HonourDto> review(@PathVariable UUID id,
                                            @Valid @RequestBody ReviewRequest body) {
        HonourView view = honours.review(new ReviewHonourCommand(id,
                Boolean.TRUE.equals(body.approve()), body.note()));
        return ResponseEntity.ok().eTag(IfMatch.etag(view.version()))
                .body(ContentDtoMapper.toDto(view));
    }

    /** <b>Xoá mềm.</b> Hàng ở lại; cờ {@code is_deleted} bật. */
    @DeleteMapping("/{id}")
    @Operation(summary = "Gỡ vinh danh (xoá mềm)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        honours.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .varyBy(HttpHeaders.AUTHORIZATION)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(body);
    }
}
