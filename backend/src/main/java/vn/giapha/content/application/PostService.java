package vn.giapha.content.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.content.application.command.CreatePostCommand;
import vn.giapha.content.application.command.PostQuery;
import vn.giapha.content.application.command.ReviewPostCommand;
import vn.giapha.content.application.command.UpdatePostCommand;
import vn.giapha.content.domain.ContentStatus;
import vn.giapha.content.domain.Post;
import vn.giapha.content.domain.port.PostRepository;
import vn.giapha.media.application.MediaLinkService;
import vn.giapha.media.application.view.MediaAssetView;
import vn.giapha.membership.application.MemberScopeView;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.BranchPath;

/**
 * Luồng <b>soạn → gửi duyệt → đăng</b> của bài viết (design/07-checklist §2).
 *
 * <h2>Đây là bản sao có chủ ý của {@code membership.ChangeRequestService}, không phải bản thứ hai</h2>
 * Cùng hình dạng: người đề nghị gửi, người có thẩm quyền <i>trong phạm vi</i> quyết, aggregate
 * không tự kiểm quyền, {@code BranchScopeGuard} so {@code ltree}, mọi mũi tên đều để lại một dòng
 * {@code audit_log}. Chuỗi kiểm quyền cũng giống hệt:
 * <ol>
 *   <li>xác định <b>chi đích</b> — với bài viết là {@code post.branch_id} đã chụp lúc tạo nháp;</li>
 *   <li>phân giải nó thành một {@link BranchPath} thật;</li>
 *   <li>giao cho {@code BranchScopeGuard.requireReviewAccess} so bằng ngữ nghĩa {@code @>} của
 *       {@code ltree}.</li>
 * </ol>
 * Một Trưởng chi của {@code goc.chi_giap} duyệt được bài của {@code goc.chi_giap.nganh_truong}
 * nhưng <b>không</b> duyệt được bài của {@code goc.chi_at} — dù token của cả hai giống hệt nhau.
 *
 * <h2>Hai luật riêng của đợt này</h2>
 * <ul>
 *   <li><b>Chưa vào phả thì chưa viết bài.</b> {@code app_user.person_id IS NULL} ⇒ {@code 403}
 *       ở {@link #create}. Quyết định đã chốt của chủ dự án: "viết bài là tiếng nói của người trong
 *       họ".</li>
 *   <li><b>Tên tác giả đi qua bộ lọc riêng tư của người đọc.</b> Không một câu SQL nào trong
 *       context này đọc bảng {@code person} để lấy tên — xem {@link PersonLens}.</li>
 * </ul>
 *
 * <h2>Chi đích không phân giải được thì siết, không mở</h2>
 * Tác giả chưa gắn chi nào ⇒ {@code branchId} rỗng ⇒ chỉ vai toàn dòng họ duyệt được. Coi "không
 * có chi" là "thuộc mọi chi" sẽ biến mỗi bản ghi thiếu dữ liệu thành một lỗ hổng phân quyền.
 */
@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private static final String ENTITY = "Post";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_FEED_SIZE = 50;

    private final PostRepository posts;
    private final ContentAccessGuard access;
    private final AuthorDirectory authors;
    private final ReviewerDirectory reviewers;
    private final AuditTrailService audit;
    private final MediaLinkService media;

    public PostService(PostRepository posts, ContentAccessGuard access, AuthorDirectory authors,
                       ReviewerDirectory reviewers, AuditTrailService audit,
                       MediaLinkService media) {
        this.posts = posts;
        this.access = access;
        this.authors = authors;
        this.reviewers = reviewers;
        this.audit = audit;
        this.media = media;
    }

    /**
     * Đặt <b>cả danh sách</b> tệp đính kèm của một bài, đúng thứ tự.
     *
     * <p>Phép kiểm quyền không nằm ở đây mà ở {@code media}, hỏi ngược lại
     * {@code PostMediaAccessAdapter.canAttach}: <b>chỉ tác giả, chỉ khi bài đang ở
     * {@code DRAFT}</b>. Một bản sao của phép kiểm ấy ở lớp này sẽ là bản luật thứ hai, và nó sẽ
     * lệch — đúng thứ mà mọi javadoc trong context này đã cảnh báo.</p>
     *
     * <p>Không đòi {@code If-Match}: gắn tệp <b>không đụng</b> tới {@code post.version}. Hai người
     * cùng sửa thân bài là một xung đột thật; một người sửa thân bài còn người kia đổi thứ tự ảnh
     * thì không — và bắt client lấy ETag trước mỗi lần kéo-thả ảnh là một vòng HTTP thừa cho mỗi
     * thao tác.</p>
     */
    @Transactional
    public PostView setMedia(UUID postId, List<UUID> mediaIds) {
        MemberScopeView caller = access.requireProvisioned();
        Post post = load(postId);
        media.attachToPost(post.id(), mediaIds);
        audit.record(ENTITY, post.id().toString(), AuditAction.UPDATE, null,
                Map.of("mediaCount", mediaIds == null ? 0 : mediaIds.size()),
                List.of("media"), null);
        log.info("Bai viet {} duoc dat {} tep dinh kem boi app_user {}",
                post.id(), mediaIds == null ? 0 : mediaIds.size(), caller.appUserId());
        return view(post, caller);
    }

    // =====================================================================================
    // Ghi
    // =====================================================================================

    /**
     * Tạo bản nháp.
     *
     * <p>Chi của bài được <b>chụp</b> tại đây từ chi chính của tác giả, đúng như
     * {@code ChangeRequestService} chụp {@code target_branch_id} lúc gửi. Nhờ vậy hàng đợi duyệt
     * lọc được bằng một câu {@code ltree} duy nhất thay vì phải nối sang {@code person} cho từng
     * dòng — và nhờ vậy bài vẫn thuộc về chi của tác giả <i>lúc viết</i>, kể cả khi sau này họ
     * chuyển chi.</p>
     */
    @Transactional
    public PostView create(CreatePostCommand command) {
        MemberScopeView caller = access.requireProvisioned();
        UUID authorPersonId = access.requireAuthorPerson(caller);
        UUID branchId = access.branchIdOfPerson(authorPersonId);

        Post post;
        try {
            post = Post.draft(UUID.randomUUID(), command.title(), command.body(),
                    authorPersonId, caller.appUserId(), branchId);
        } catch (IllegalArgumentException ex) {
            throw new DomainException(ContentProblemCodes.VALIDATION_FAILED, ex.getMessage(), ex);
        }
        Post saved = posts.save(post);

        audit.record(ENTITY, saved.id().toString(), AuditAction.CREATE, null,
                saved.auditSnapshot(), List.of("title", "body"), null);
        log.info("Bai viet {} duoc tao nhap boi app_user {} (chi {})",
                saved.id(), caller.appUserId(), branchId);
        return view(saved, caller);
    }

    /** Sửa bản nháp. Chỉ tác giả, chỉ khi bài đang ở {@code DRAFT}. */
    @Transactional
    public PostView update(UpdatePostCommand command) {
        MemberScopeView caller = access.requireProvisioned();
        Post post = load(command.postId());
        requireAuthor(caller, post, "sua bai viet");
        requireVersion(post, command.expectedVersion());

        try {
            post.edit(command.title(), command.body());
        } catch (IllegalStateException ex) {
            throw new ContentConflictException(ContentProblemCodes.CONTENT_CLOSED, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new DomainException(ContentProblemCodes.VALIDATION_FAILED, ex.getMessage(), ex);
        }
        Post saved = posts.save(post);

        audit.record(ENTITY, saved.id().toString(), AuditAction.UPDATE, null,
                saved.auditSnapshot(), changedFields(command), null);
        return view(saved, caller);
    }

    /** Gửi duyệt. {@code DRAFT → PENDING}. */
    @Transactional
    public PostView submit(UUID postId) {
        MemberScopeView caller = access.requireProvisioned();
        Post post = load(postId);
        requireAuthor(caller, post, "gui duyet bai viet");

        try {
            post.submit();
        } catch (IllegalStateException ex) {
            throw new ContentConflictException(ContentProblemCodes.CONTENT_CLOSED, ex.getMessage(), ex);
        }
        Post saved = posts.save(post);

        audit.record(ENTITY, saved.id().toString(), AuditAction.UPDATE, null,
                saved.auditSnapshot(), List.of("status"), "Gui duyet");
        log.info("Bai viet {} chuyen sang PENDING", saved.id());
        return view(saved, caller);
    }

    /**
     * Duyệt hoặc trả lại. <b>Đây là cửa kiểm phạm vi.</b>
     *
     * <p>Chuỗi kiểm nằm trọn trong {@link #requireReviewer}: chi đích → {@link BranchPath} →
     * {@code BranchScopeGuard}. Nới lỏng ở đây — ví dụ "Trưởng chi nào cũng đọc được bài cả họ nên
     * duyệt cũng được" — là mở đúng lối mà bước duyệt sinh ra để đóng.</p>
     */
    @Transactional
    public PostView review(ReviewPostCommand command) {
        MemberScopeView caller = access.requireProvisioned();
        Post post = load(command.postId());
        requireReviewer(caller, post);

        Instant now = Instant.now();
        try {
            if (command.approve()) {
                post.approve(caller.appUserId(), now);
            } else {
                post.sendBack(caller.appUserId(), command.note(), now);
            }
        } catch (IllegalStateException ex) {
            throw selfReviewOrClosed(ex);
        } catch (IllegalArgumentException ex) {
            throw new DomainException(ContentProblemCodes.VALIDATION_FAILED, ex.getMessage(), ex);
        }
        Post saved = posts.save(post);

        audit.record(ENTITY, saved.id().toString(),
                command.approve() ? AuditAction.APPROVE : AuditAction.REJECT, null,
                saved.auditSnapshot(), List.of("status", "reviewedBy", "reviewedAt"),
                command.note());
        log.info("Bai viet {} -> {} boi app_user {}", saved.id(), saved.status(),
                caller.appUserId());
        return view(saved, caller);
    }

    /**
     * Gỡ bài — <b>xoá mềm, luôn luôn</b>.
     *
     * <p>Ai được gỡ: tác giả (bỏ bản nháp của mình, hoặc rút bài mình đã đăng) và người duyệt
     * trong phạm vi. Không có ai khác, và không có lệnh xoá nào.</p>
     */
    @Transactional
    public PostView withdraw(UUID postId, String reason) {
        MemberScopeView caller = access.requireProvisioned();
        Post post = load(postId);
        if (!post.isAuthor(caller.appUserId())) {
            requireReviewer(caller, post);
        }

        try {
            post.withdraw(caller.appUserId(), reason, Instant.now());
        } catch (IllegalStateException ex) {
            throw new ContentConflictException(ContentProblemCodes.CONTENT_CLOSED, ex.getMessage(), ex);
        }
        Post saved = posts.save(post);

        // Go bai => cat moi lien ket tep. Bai o lai (xoa mem tuyet doi), tep thanh MO COI va
        // duong don mang di sau an han — do la ranh gioi da ghi o V19: luat xoa mem noi ve NODE
        // PHA HE, con NÐ 13/2023 doi du lieu bi go phai that su bien mat.
        media.detachFromPost(saved.id());

        audit.record(ENTITY, saved.id().toString(), AuditAction.SOFT_DELETE, null,
                saved.auditSnapshot(), List.of("status"), reason);
        log.info("Bai viet {} bi go boi app_user {}; tep dinh kem da thanh mo coi",
                saved.id(), caller.appUserId());
        return view(saved, caller);
    }

    // =====================================================================================
    // Đọc
    // =====================================================================================

    /**
     * Danh sách bài, <b>đã áp luật ai thấy gì ngay trong SQL</b>.
     *
     * <p>Lọc ở CSDL chứ không ở Java: bản nháp của người khác không bao giờ đi vào bộ nhớ tiến
     * trình, nên không có chỗ nào để quên lọc. Cùng lý do với
     * {@code ChangeRequestJpaRepository.PENDING_IN_SCOPE}.</p>
     */
    @Transactional(readOnly = true)
    public ContentPage<PostView> search(PostQuery query) {
        MemberScopeView caller = access.requireProvisioned();
        int size = clamp(query.size(), DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
        int page = Math.max(query.page(), 0);

        List<Post> rows = posts.search(query.status(), caller.appUserId(),
                caller.managedBranches(), caller.clanWide(), query.mine(), size, page * size);
        long total = posts.count(query.status(), caller.appUserId(),
                caller.managedBranches(), caller.clanWide(), query.mine());
        return new ContentPage<>(views(rows, caller), page, size, total);
    }

    /** Trang chủ: bài đã đăng, mới nhất trước. Không phụ thuộc phạm vi chi. */
    @Transactional(readOnly = true)
    public List<PostView> feed(int limit) {
        MemberScopeView caller = access.requireProvisioned();
        return views(posts.feed(clamp(limit, 10, MAX_FEED_SIZE)), caller);
    }

    /**
     * Một bài cụ thể.
     *
     * <p>Bài không được phép thấy trả {@code 404}, không phải {@code 403} — trả 403 là tự xác nhận
     * bài đó có thật, và với một bản nháp thì "có thật" đã là thông tin.</p>
     */
    @Transactional(readOnly = true)
    public PostView byId(UUID postId) {
        MemberScopeView caller = access.requireProvisioned();
        Post post = load(postId);
        if (!isVisibleTo(caller, post)) {
            throw NotFoundException.of(ENTITY, postId);
        }
        return view(post, caller);
    }

    /** Số bài chờ duyệt trong phạm vi — cho badge trên giao diện. */
    @Transactional(readOnly = true)
    public long countPendingForReview() {
        MemberScopeView caller = access.caller();
        if (caller.appUserId() == null) {
            return 0L;
        }
        return posts.countPendingInScope(caller.managedBranches(), caller.clanWide());
    }

    // =====================================================================================
    // Nội bộ
    // =====================================================================================

    private Post load(UUID postId) {
        if (postId == null) {
            throw NotFoundException.of(ENTITY, null);
        }
        return posts.byId(postId).orElseThrow(() -> NotFoundException.of(ENTITY, postId));
    }

    private void requireAuthor(MemberScopeView caller, Post post, String what) {
        if (!post.isAuthor(caller.appUserId())) {
            throw new ForbiddenException(ContentProblemCodes.FORBIDDEN,
                    "Chi tac gia moi duoc " + what);
        }
    }

    /**
     * Cửa kiểm quyền duyệt — phương thức mà <b>mọi</b> lối duyệt bắt buộc đi qua.
     *
     * <p>Chi đích lấy từ {@code post.branch_id}; không phân giải được thì {@code target} là
     * {@code null} và chỉ vai toàn dòng họ đi tiếp.</p>
     */
    private void requireReviewer(MemberScopeView caller, Post post) {
        access.requireReviewer(caller, access.pathOfBranch(post.branchId()));
    }

    /**
     * Người gọi có được thấy bài này không — <b>phải khớp từng dòng</b> với mệnh đề WHERE của
     * {@code PostRepository.search}.
     *
     * <p>Hai luật khác nhau cho cùng câu hỏi là cách một danh sách hiện ra bài mà bấm vào thì
     * {@code 404}, hoặc tệ hơn, ngược lại.</p>
     */
    private boolean isVisibleTo(MemberScopeView caller, Post post) {
        if (post.status() == ContentStatus.PUBLISHED) {
            return true;
        }
        if (post.isAuthor(caller.appUserId())) {
            return true;
        }
        if (post.status() == ContentStatus.DRAFT) {
            // Ban nhap CHI chinh tac gia thay — ke ca Hoi dong. Mot ban nhap chua gui la thu chua
            // ai duoc moc doc.
            return false;
        }
        return access.canReview(caller, access.pathOfBranch(post.branchId()));
    }

    /** Một lượt nạp tên tác giả cho cả danh sách, không một lượt cho mỗi bài. */
    private List<PostView> views(List<Post> rows, MemberScopeView caller) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, UUID> nhanKhauCuaNguoiDuyet = reviewers.personIdsOf(
                rows.stream().map(Post::reviewedBy).toList());

        Set<UUID> canHoi = new LinkedHashSet<>();
        rows.forEach(post -> canHoi.add(post.authorPersonId()));
        canHoi.addAll(nhanKhauCuaNguoiDuyet.values());
        PersonLens lens = authors.load(canHoi);

        // MOT luot cho ca trang, khong mot luot cho moi bai: trang chu 10 bai x 2 cau = 20 luot
        // truy van, va NFR-1 dat ngan sach 2000 ms toi the nguoi dau tien.
        Map<UUID, List<MediaAssetView>> tepCuaBai =
                media.listForPosts(rows.stream().map(Post::id).toList());

        List<PostView> views = new ArrayList<>(rows.size());
        for (Post post : rows) {
            views.add(toView(post, caller, lens, nhanKhauCuaNguoiDuyet,
                    tepCuaBai.getOrDefault(post.id(), List.of())));
        }
        return views;
    }

    private PostView view(Post post, MemberScopeView caller) {
        Map<UUID, UUID> nhanKhauCuaNguoiDuyet = reviewers.personIdsOf(
                java.util.Collections.singletonList(post.reviewedBy()));
        Set<UUID> canHoi = new LinkedHashSet<>();
        canHoi.add(post.authorPersonId());
        canHoi.addAll(nhanKhauCuaNguoiDuyet.values());
        return toView(post, caller, authors.load(canHoi), nhanKhauCuaNguoiDuyet,
                media.listForPost(post.id()));
    }

    /**
     * Ten nguoi duyet di qua DUNG mot bo loc voi ten tac gia.
     *
     * <p>Checklist §2 doi ghi ro "ai duyet va luc nao", nhung cot {@code reviewed_by} la mot khoa
     * TAI KHOAN — no khong hien len man hinh duoc. Duong dung la
     * {@code app_user → person → PersonDisclosureService}, tuc van qua bo loc rieng tu cua nguoi
     * doc. Doc thang bang {@code person} cho nhanh la dung cai bay ma
     * {@code RelationshipSummaryLoader} da chan o man "Quan he": ten mot nguoi con song ro ra qua
     * mot ho so cong khai — o day la qua chan bai viet ma ho vua duyet.</p>
     *
     * <p>Khong tra duoc ten thi tra {@code null}, KHONG bia mot chuoi thay the.</p>
     */
    private PostView toView(Post post, MemberScopeView caller, PersonLens lens,
                            Map<UUID, UUID> nhanKhauCuaNguoiDuyet, List<MediaAssetView> tep) {
        BranchPath path = access.pathOfBranch(post.branchId());
        boolean canReview = post.status() == ContentStatus.PENDING
                && !post.isAuthor(caller.appUserId())
                && access.canReview(caller, path);
        boolean canEdit = post.isAuthor(caller.appUserId())
                && post.status() == ContentStatus.DRAFT;
        String tenNguoiDuyet = lens.displayName(nhanKhauCuaNguoiDuyet.get(post.reviewedBy()));
        return PostView.of(post, lens.displayName(post.authorPersonId()), tenNguoiDuyet,
                access.branchName(post.branchId()), canReview, canEdit, tep);
    }

    /**
     * Khoá lạc quan.
     *
     * <p>{@code expectedVersion} rỗng là lỗi của <i>lớp HTTP</i> (thiếu {@code If-Match}) và đã bị
     * chặn ở controller; nếu lọt tới đây thì vẫn chặn, vì một lối vào khác (GraphQL, job) cũng
     * không được ghi mù.</p>
     */
    private void requireVersion(Post post, Long expectedVersion) {
        if (expectedVersion == null) {
            throw new ContentPreconditionException(
                    "Thieu phien ban ky vong; hay lay ETag tu lan doc gan nhat truoc khi sua");
        }
        if (post.version() != expectedVersion) {
            throw new ContentConflictException(ContentProblemCodes.OPTIMISTIC_LOCK_CONFLICT,
                    "Bai viet da bi nguoi khac sua (phien ban " + post.version()
                            + ", ban gui " + expectedVersion + "); hay tai lai roi thu lai");
        }
    }

    /**
     * Phân biệt "tự duyệt" với "trạng thái đã đóng".
     *
     * <p>Cả hai đều là {@link IllegalStateException} từ aggregate, nhưng chúng dẫn tới hai hành
     * động khác hẳn của người dùng: một bên là "nhờ người khác duyệt", bên kia là "tải lại trang".
     * Đây là đúng cách {@code ChangeRequestService.review} phân nhánh.</p>
     */
    private DomainException selfReviewOrClosed(IllegalStateException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("tu duyet")) {
            return new ForbiddenException(ContentProblemCodes.SELF_REVIEW_FORBIDDEN, message);
        }
        return new ContentConflictException(ContentProblemCodes.CONTENT_CLOSED, message, ex);
    }

    private static List<String> changedFields(UpdatePostCommand command) {
        List<String> fields = new ArrayList<>();
        if (command.title() != null) {
            fields.add("title");
        }
        if (command.body() != null) {
            fields.add("body");
        }
        return fields;
    }

    private static int clamp(int value, int fallback, int max) {
        if (value <= 0) {
            return fallback;
        }
        return Math.min(value, max);
    }
}
