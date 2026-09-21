package vn.giapha.content.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.content.api.rest.dto.AttachMediaRequest;
import vn.giapha.content.api.rest.dto.ContentPageDto;
import vn.giapha.content.api.rest.dto.CreatePostRequest;
import vn.giapha.content.api.rest.dto.PostDto;
import vn.giapha.content.api.rest.dto.ReviewRequest;
import vn.giapha.content.api.rest.dto.UpdatePostRequest;
import vn.giapha.content.api.rest.dto.WithdrawRequest;
import vn.giapha.content.application.PostService;
import vn.giapha.content.application.PostView;
import vn.giapha.content.application.command.CreatePostCommand;
import vn.giapha.content.application.command.PostQuery;
import vn.giapha.content.application.command.ReviewPostCommand;
import vn.giapha.content.application.command.UpdatePostCommand;
import vn.giapha.content.domain.ContentStatus;

/**
 * Bài viết của dòng họ — {@code /api/v1/posts}.
 *
 * <h2>Kiểm quyền nằm ở tầng use case, không ở đây</h2>
 * Không một {@code @PreAuthorize} nào cho việc duyệt: quyền duyệt phụ thuộc <i>chi của từng bài</i>,
 * mà controller chưa nạp bài lên thì chưa biết chi ấy là gì. Một
 * {@code @PreAuthorize("hasRole('BRANCH_HEAD')")} ở đây sẽ cho Trưởng chi Ất đi qua cửa rồi mới bị
 * chặn ở trong — và tệ hơn, nó tạo cảm giác đã kiểm quyền xong. Cửa kiểm thật là
 * {@code PostService} → {@code BranchScopeGuard} → so {@code ltree}.
 *
 * <h2>Mã HTTP</h2>
 * Chưa được duyệt vào phả → {@code 403 AUTHOR_NOT_IN_PHA}. Sai vai → {@code 403 FORBIDDEN}. Đúng
 * vai sai nhánh → {@code 403 BRANCH_SCOPE_VIOLATION}. Tự duyệt bài mình →
 * {@code 403 SELF_REVIEW_FORBIDDEN}. Trạng thái đã đóng → {@code 409 CONTENT_CLOSED}. Thiếu
 * {@code If-Match} → {@code 412}. Phiên bản lệch → {@code 409 OPTIMISTIC_LOCK_CONFLICT}. Không được
 * phép thấy → {@code 404}, <b>không</b> {@code 403}: trả 403 là tự xác nhận bài đó có thật.
 *
 * <h2>Vì sao mọi phản hồi đều {@code no-store} + {@code Vary: Authorization}</h2>
 * {@code ETag} ở đây là <b>khoá lạc quan</b> (giá trị {@code version}), nên nó giống nhau với mọi
 * người gọi — trong khi thân phản hồi thì <i>không</i>: {@code authorDisplayName} đã đi qua bộ lọc
 * riêng tư của người đọc, và {@code canReview} phụ thuộc phạm vi chi của họ. Thiếu hai header này
 * thì máy tính chung ở nhà thờ họ sẽ phục vụ lại thân phản hồi của người trước dưới cùng một
 * {@code ETag}, và lần đọc thứ hai không bao giờ chạm tới tầng ứng dụng để mà ghi log. Đây đúng là
 * cái bẫy mà {@code PersonController.get} đã ghi lại.
 */
@RestController
@RequestMapping("/api/v1/posts")
@Tag(name = "Posts", description = "Bài viết của dòng họ: soạn → gửi duyệt → đăng")
public class PostController {

    private static final Logger log = LoggerFactory.getLogger(PostController.class);

    private final PostService posts;

    public PostController(PostService posts) {
        this.posts = posts;
    }

    /**
     * Danh sách bài. Luật ai thấy gì chạy trong SQL — xem {@code PostJpaRepository}.
     *
     * @param status {@code DRAFT} · {@code PENDING} · {@code PUBLISHED} · {@code WITHDRAWN};
     *               vắng mặt = mọi trạng thái mà người gọi được thấy
     * @param mine   {@code true} = chỉ bài do <b>chính người đang đăng nhập</b> viết — màn "Bài của
     *               tôi". Lọc <b>trong SQL</b>, kết hợp được với {@code status} và với phân trang.
     *               <p>Không có nó thì giao diện buộc phải bắn bốn lượt gọi song song (một cho mỗi
     *               trạng thái) rồi lọc tiếp ở client — và một người viết nhiều sẽ <b>mất bài cũ mà
     *               không có gì báo</b>, vì phân trang của máy chủ đếm trên toàn bộ tập còn phép lọc
     *               của client chỉ thấy trang đầu. Đó là một lỗi im lặng, tức loại đắt nhất.</p>
     */
    @GetMapping
    @Operation(summary = "Danh sách bài viết trong phạm vi người gọi được thấy")
    public ResponseEntity<ContentPageDto<PostDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return noStore(ContentDtoMapper.toPostPage(
                posts.search(new PostQuery(ContentStatus.of(status), mine, page, size))));
    }

    /** Trang chủ: bài đã đăng, mới nhất trước. */
    @GetMapping("/feed")
    @Operation(summary = "Bài đã đăng, mới nhất trước — nguồn của trang chủ")
    public ResponseEntity<List<PostDto>> feed(@RequestParam(defaultValue = "10") int limit) {
        return noStore(posts.feed(limit).stream().map(ContentDtoMapper::toDto).toList());
    }

    /** Số bài chờ duyệt trong phạm vi — cho badge trên chuông thông báo. */
    @GetMapping("/pending/count")
    @Operation(summary = "Đếm bài chờ duyệt trong phạm vi chi/ngành được giao")
    public ResponseEntity<Map<String, Long>> pendingCount() {
        return noStore(Map.of("count", posts.countPendingForReview()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Một bài viết")
    public ResponseEntity<PostDto> byId(@PathVariable UUID id) {
        PostView view = posts.byId(id);
        return ResponseEntity.ok()
                .eTag(IfMatch.etag(view.version()))
                .varyBy(HttpHeaders.AUTHORIZATION)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(ContentDtoMapper.toDto(view));
    }

    /**
     * Tạo bản nháp.
     *
     * <p>Tài khoản chưa được duyệt vào phả nhận {@code 403 AUTHOR_NOT_IN_PHA} — quyết định đã chốt
     * của chủ dự án: "viết bài là tiếng nói của người trong họ".</p>
     */
    @PostMapping
    @Operation(summary = "Tạo bản nháp bài viết")
    public ResponseEntity<PostDto> create(@Valid @RequestBody CreatePostRequest body) {
        PostView created = posts.create(new CreatePostCommand(body.title(), body.body()));
        log.debug("POST /api/v1/posts -> {}", created.id());
        return ResponseEntity.created(URI.create("/api/v1/posts/" + created.id()))
                .eTag(IfMatch.etag(created.version()))
                .body(ContentDtoMapper.toDto(created));
    }

    /**
     * Đặt <b>cả danh sách</b> tệp đính kèm của bài, đúng thứ tự — {@code PUT}, không
     * {@code PATCH}.
     *
     * <h2>Tệp đã được tải lên TRƯỚC, qua {@code /api/v1/media}</h2>
     * Điểm cuối này chỉ nhận <b>khoá</b> của những tệp đã xác nhận. Không có {@code multipart} ở
     * đây, và không nên có: một video 100 MiB đi xuyên Tomcat chiếm trọn một luồng xử lý suốt thời
     * gian tải, còn trần {@code spring.servlet.multipart} 10 MB của dự án thì cố ý khớp với
     * {@code ImportLimits.MAX_FILE_BYTES} và không được nâng.
     *
     * <p>Chỉ tác giả, chỉ khi bài đang ở {@code DRAFT} — kiểm ở {@code media} qua
     * {@code PostMediaAccessAdapter.canAttach}, tức cùng một bản luật với mọi lối khác. Bài đã
     * đăng mà còn đổi được ảnh là một lối vòng qua luật "bài đã đăng thì không sửa", vốn tồn tại
     * để chữ ký duyệt của Trưởng chi có nghĩa.</p>
     *
     * <p>Không đòi {@code If-Match}: gắn tệp không đụng tới {@code post.version}.</p>
     */
    @PutMapping("/{id}/media")
    @Operation(summary = "Đặt danh sách tệp đính kèm của bài (chỉ tác giả, chỉ khi còn nháp)")
    public ResponseEntity<PostDto> setMedia(@PathVariable UUID id,
                                            @Valid @RequestBody AttachMediaRequest body) {
        PostView updated = posts.setMedia(id, body.mediaIds());
        log.info("PUT /api/v1/posts/{}/media -> {} tep", id, body.mediaIds().size());
        return noStore(ContentDtoMapper.toDto(updated));
    }

    /** Sửa bản nháp. Đòi {@code If-Match}. */
    @PatchMapping("/{id}")
    @Operation(summary = "Sửa bản nháp (đòi If-Match)")
    public ResponseEntity<PostDto> update(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdatePostRequest body) {
        PostView updated = posts.update(new UpdatePostCommand(id, body.title(), body.body(),
                IfMatch.parse(ifMatch)));
        return ResponseEntity.ok()
                .eTag(IfMatch.etag(updated.version()))
                .body(ContentDtoMapper.toDto(updated));
    }

    /** Gửi duyệt. {@code DRAFT → PENDING}. */
    @PostMapping("/{id}/submit")
    @Operation(summary = "Gửi bài đi duyệt")
    public ResponseEntity<PostDto> submit(@PathVariable UUID id) {
        PostView view = posts.submit(id);
        return ResponseEntity.ok().eTag(IfMatch.etag(view.version()))
                .body(ContentDtoMapper.toDto(view));
    }

    /** Duyệt hoặc trả lại. Quyền kiểm theo chi của bài, không theo vai suông. */
    @PostMapping("/{id}/review")
    @Operation(summary = "Duyệt bài hoặc trả lại kèm lý do")
    public ResponseEntity<PostDto> review(@PathVariable UUID id,
                                          @Valid @RequestBody ReviewRequest body) {
        PostView view = posts.review(new ReviewPostCommand(id,
                Boolean.TRUE.equals(body.approve()), body.note()));
        return ResponseEntity.ok().eTag(IfMatch.etag(view.version()))
                .body(ContentDtoMapper.toDto(view));
    }

    /**
     * Gỡ bài — <b>xoá mềm</b>. Không có {@code DELETE} nào trên tài nguyên này, và đó là chủ ý:
     * gỡ bài là đổi trạng thái, hàng ở lại để tra được.
     */
    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Gỡ bài (xoá mềm — hàng ở lại, đổi trạng thái)")
    public ResponseEntity<PostDto> withdraw(@PathVariable UUID id,
                                            @RequestBody(required = false) WithdrawRequest body) {
        PostView view = posts.withdraw(id, body == null ? null : body.reason());
        return ResponseEntity.ok().eTag(IfMatch.etag(view.version()))
                .body(ContentDtoMapper.toDto(view));
    }

    /**
     * Thân phản hồi phụ thuộc người gọi — xem javadoc lớp. Dùng cho mọi lối đọc <b>danh sách</b>,
     * vốn không có {@code ETag} riêng.
     */
    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .varyBy(HttpHeaders.AUTHORIZATION)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(body);
    }
}
