package vn.giapha.content.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Một bài viết của dòng họ: soạn nháp → gửi duyệt → Trưởng cành/chi/họ duyệt → lên trang chủ.
 *
 * <h2>Aggregate này KHÔNG tự kiểm quyền — cố ý, và đây là điểm quan trọng nhất</h2>
 * Nó không biết {@code ltree}, không biết vai trò, không biết ai đang đăng nhập. Việc so phạm vi
 * chi là của {@code membership.BranchScopeGuard}, và {@link #approve}/{@link #sendBack} chỉ nhận
 * {@code reviewerId} <b>sau khi</b> phép so ấy đã qua. Đây là đúng khuôn của
 * {@code membership.domain.ChangeRequest} — nhốt luật phạm vi vào một chỗ duy nhất thì không có
 * lối vòng nào, và ở đây lại càng phải thế: bài viết có tới năm mũi tên chuyển trạng thái, tức năm
 * cơ hội để quên kiểm quyền nếu luật ấy nằm rải rác.
 *
 * <h2>Năm mũi tên, và mũi tên thứ năm là mũi tên hay bị quên</h2>
 * <ol>
 *   <li>{@link #submit} — {@code DRAFT → PENDING}, chỉ tác giả;</li>
 *   <li>{@link #approve} — {@code PENDING → PUBLISHED}, người duyệt trong phạm vi;</li>
 *   <li>{@link #sendBack} — {@code PENDING → DRAFT} <b>kèm lý do bắt buộc</b>;</li>
 *   <li>{@link #withdraw} từ {@code PUBLISHED} — gỡ bài đã đăng;</li>
 *   <li>{@link #withdraw} từ {@code DRAFT}/{@code PENDING} — <b>bỏ bản nháp</b>. Hợp đồng REST
 *       không có lệnh xoá bài nào, và xoá mềm là tuyệt đối, nên đây là lối duy nhất để một người
 *       vứt đi bản nháp họ không muốn viết nữa. Không có nó thì "Bài của tôi" của một người viết
 *       nhiều sẽ đầy rác vĩnh viễn và họ sẽ ngừng dùng màn ấy.</li>
 * </ol>
 *
 * <h2>Vì sao "trả lại" đưa về {@code DRAFT} chứ không phải một trạng thái {@code REJECTED}</h2>
 * §2 gọi việc này là "trả lại kèm lý do", không phải "từ chối". Người viết là bác 60 tuổi; đích
 * đến của họ sau khi bị trả lại là <b>sửa rồi gửi lại</b>, và trạng thái duy nhất cho phép sửa là
 * {@code DRAFT}. Một trạng thái {@code REJECTED} riêng chỉ thêm một mũi tên
 * {@code REJECTED → DRAFT} mà không nói thêm điều gì — {@code rejectReason} đã kể đủ câu chuyện, và
 * nó <b>ở lại</b> trên bản nháp để người viết còn đọc được lý do trong lúc sửa.
 *
 * <p>POJO thuần — bản chiếu JPA nằm ở {@code content.infrastructure.jpa}.</p>
 */
public final class Post {

    /** Giới hạn mềm của thân bài. Xem {@link #requireBody}. */
    public static final int MAX_BODY_LENGTH = 200_000;

    public static final int MAX_TITLE_LENGTH = 250;

    private final UUID id;
    private final UUID authorPersonId;
    private final UUID authorUserId;
    private final UUID branchId;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    private String title;
    private String body;
    private ContentStatus status;
    private Instant publishedAt;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String rejectReason;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public Post(UUID id, String title, String body, ContentStatus status,
                UUID authorPersonId, UUID authorUserId, UUID branchId, Instant publishedAt,
                UUID reviewedBy, Instant reviewedAt, String rejectReason,
                Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "Post.id khong duoc null");
        this.title = requireTitle(title);
        this.body = requireBody(body);
        this.status = status == null ? ContentStatus.DRAFT : status;
        this.authorPersonId = Objects.requireNonNull(authorPersonId,
                "Post.authorPersonId khong duoc null — nguoi chua duoc duyet vao pha khong viet bai");
        this.authorUserId = Objects.requireNonNull(authorUserId, "Post.authorUserId khong duoc null");
        this.branchId = branchId;
        this.publishedAt = publishedAt;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.rejectReason = blankToNull(rejectReason);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /**
     * Bản nháp mới.
     *
     * @param authorPersonId nhân khẩu của người viết — <b>bắt buộc</b>. Tầng use case đã chặn từ
     *                       trước với {@code 403}; ở đây là lưới cuối, và nó ném NPE chứ không lặng
     *                       lẽ dựng một bài viết vô chủ
     */
    public static Post draft(UUID id, String title, String body,
                             UUID authorPersonId, UUID authorUserId, UUID branchId) {
        return new Post(id, title, body, ContentStatus.DRAFT,
                authorPersonId, authorUserId, branchId, null, null, null, null, null, null, 0L);
    }

    public UUID id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public ContentStatus status() {
        return status;
    }

    public UUID authorPersonId() {
        return authorPersonId;
    }

    public UUID authorUserId() {
        return authorUserId;
    }

    /** Chi của tác giả, chụp lúc tạo nháp — <b>căn cứ so phạm vi khi duyệt</b>. */
    public UUID branchId() {
        return branchId;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public UUID reviewedBy() {
        return reviewedBy;
    }

    public Instant reviewedAt() {
        return reviewedAt;
    }

    public String rejectReason() {
        return rejectReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    public boolean isAuthor(UUID appUserId) {
        return appUserId != null && authorUserId.equals(appUserId);
    }

    // -------------------------------------------------------------------------------------
    // Chuyển trạng thái
    // -------------------------------------------------------------------------------------

    /**
     * Sửa nội dung. Chỉ được ở {@code DRAFT}.
     *
     * <p><b>Bài đã đăng thì không sửa.</b> Nghe cứng nhưng đúng: "bài lên trang chủ là tiếng nói
     * của cả dòng họ" (§2), và một bài đã được Trưởng chi đọc rồi duyệt mà tác giả còn sửa được thì
     * chữ ký duyệt ấy không nói lên điều gì. Muốn sửa thì gỡ xuống rồi soạn bài mới, hoặc để người
     * duyệt sửa nhẹ <i>trước</i> khi bấm duyệt.</p>
     *
     * <p>Tham số {@code null} nghĩa là <b>giữ nguyên</b> (ngữ nghĩa PATCH của cả sản phẩm).</p>
     *
     * <h2>Không có ảnh bìa ở đợt này</h2>
     * <p>Bài viết chỉ có tiêu đề và thân bài. Backend chưa có SDK S3/MinIO nào, nên không có lối
     * tải ảnh lên — và một trường {@code coverImageKey} mà client tự điền là một trường trỏ vào hư
     * không. Xem khối ghi chú đầu {@code V17__content_post_honour.sql}.</p>
     */
    public void edit(String newTitle, String newBody) {
        if (status != ContentStatus.DRAFT) {
            throw new IllegalStateException(
                    "Chi sua duoc bai o trang thai DRAFT; bai nay dang o " + status);
        }
        if (newTitle != null) {
            this.title = requireTitle(newTitle);
        }
        if (newBody != null) {
            this.body = requireBody(newBody);
        }
    }

    /**
     * Gửi duyệt. {@code DRAFT → PENDING}.
     *
     * <p>Lý do trả lại của lượt trước bị xoá ở đây: nó nói về bản cũ, và để nguyên thì màn duyệt sẽ
     * hiện một lời phê không còn đúng với thứ người duyệt đang đọc.</p>
     */
    public void submit() {
        if (status != ContentStatus.DRAFT) {
            throw new IllegalStateException(
                    "Chi gui duyet duoc bai o trang thai DRAFT; bai nay dang o " + status);
        }
        this.status = ContentStatus.PENDING;
        this.rejectReason = null;
    }

    /** Duyệt và đăng. {@code PENDING → PUBLISHED}. */
    public void approve(UUID reviewerUserId, Instant at) {
        requirePending();
        Objects.requireNonNull(reviewerUserId, "reviewerUserId khong duoc null khi duyet bai");
        requireNotSelfReview(reviewerUserId);
        this.status = ContentStatus.PUBLISHED;
        this.reviewedBy = reviewerUserId;
        this.reviewedAt = at == null ? Instant.now() : at;
        this.publishedAt = this.reviewedAt;
        this.rejectReason = null;
    }

    /**
     * Trả lại cho người viết. {@code PENDING → DRAFT}. <b>Lý do là bắt buộc.</b>
     *
     * <p>Không có lý do thì người viết gửi lại y nguyên và cả hai bên cùng mất thời gian lần thứ
     * hai — đây là cùng luật với {@code ChangeRequest.reject}.</p>
     */
    public void sendBack(UUID reviewerUserId, String reason, Instant at) {
        requirePending();
        Objects.requireNonNull(reviewerUserId, "reviewerUserId khong duoc null khi tra lai bai");
        requireNotSelfReview(reviewerUserId);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Tra lai bai viet phai kem ly do");
        }
        this.status = ContentStatus.DRAFT;
        this.reviewedBy = reviewerUserId;
        this.reviewedAt = at == null ? Instant.now() : at;
        this.rejectReason = reason.trim();
    }

    /**
     * Gỡ. Từ {@code PUBLISHED} (gỡ bài đã đăng) hoặc từ {@code DRAFT}/{@code PENDING} (bỏ nháp).
     *
     * <p><b>Đây là thứ thay cho lệnh xoá</b>: hàng ở lại trong bảng, đồ thị dữ liệu không thủng, và
     * việc "bài này từng lên trang chủ rồi bị gỡ" vẫn tra lại được. Không có phương thức nào trong
     * lớp này xoá một bài.</p>
     */
    public void withdraw(UUID byUserId, String reason, Instant at) {
        if (status.isFinal()) {
            throw new IllegalStateException("Bai viet da o trang thai " + status + ", khong go lai duoc");
        }
        this.status = ContentStatus.WITHDRAWN;
        this.reviewedBy = byUserId;
        this.reviewedAt = at == null ? Instant.now() : at;
        if (reason != null && !reason.isBlank()) {
            this.rejectReason = reason.trim();
        }
    }

    /**
     * Ảnh chụp cho {@code audit_log}.
     *
     * <h2>KHÔNG có {@code title}, KHÔNG có {@code body}</h2>
     * {@code audit_log} là bảng <b>chỉ ghi thêm</b> — lọt vào là không gỡ ra được. Thân một bài
     * viết do người dùng gõ tự do, và một bài kể chuyện họ hàng hoàn toàn có thể chứa số điện
     * thoại, địa chỉ hay ngày sinh của người còn sống. Ở đây chỉ ghi <i>tên các trường</i> bị đổi,
     * đúng tinh thần "ghi ten truong bi xoa, KHONG ghi gia tri" của V5 và đúng cách
     * {@code ChangeRequest.auditSnapshot} xử lý {@code payload}.
     *
     * <p>Hệ quả phải chấp nhận: nhật ký không khôi phục lại được nội dung cũ của một bài. Đó là
     * đánh đổi đúng — {@code audit_log} sinh ra để trả lời "ai đổi cái gì, lúc nào", không phải để
     * làm kho phiên bản.</p>
     */
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", id.toString());
        snapshot.put("status", status.name());
        snapshot.put("authorPersonId", authorPersonId.toString());
        snapshot.put("authorUserId", authorUserId.toString());
        snapshot.put("branchId", branchId == null ? null : branchId.toString());
        snapshot.put("reviewedBy", reviewedBy == null ? null : reviewedBy.toString());
        snapshot.put("reviewedAt", reviewedAt == null ? null : reviewedAt.toString());
        snapshot.put("publishedAt", publishedAt == null ? null : publishedAt.toString());
        snapshot.put("titleLength", title.length());
        snapshot.put("bodyLength", body.length());
        return snapshot;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Post post && id.equals(post.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Không chở tiêu đề hay thân bài — an toàn để ghi log. */
    @Override
    public String toString() {
        return "Post[" + id + ", " + status + "]";
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ
    // -------------------------------------------------------------------------------------

    private void requirePending() {
        if (status != ContentStatus.PENDING) {
            throw new IllegalStateException(
                    "Chi duyet duoc bai o trang thai PENDING; bai nay dang o " + status);
        }
    }

    /**
     * <b>Không ai tự duyệt bài của chính mình.</b>
     *
     * <p>Cùng luật với {@code ChangeRequest.transition}, và ở đây lý do còn thẳng hơn: §2 nói "bài
     * lên trang chủ là tiếng nói của cả dòng họ", nên điều cả luồng duyệt này mua được chính là
     * <i>một người thứ hai đã đọc</i>. Cho tác giả tự bấm duyệt thì bước duyệt chỉ còn là một cú
     * nhấp thêm, và {@code audit_log} ghi lại một "cuộc phê duyệt" không có ai kiểm tra ai.</p>
     *
     * <p><b>Cái giá, nói thẳng:</b> một dòng họ mà Hội đồng chỉ có đúng một người thì người ấy
     * không đăng được bài của chính mình. Đây là câu hỏi cho Hội đồng Tộc biểu, không phải cho lập
     * trình viên — và nới ở đây là quyết định một chiều, vì một khi đã cho tự duyệt thì mọi dòng
     * {@code APPROVE} trong nhật ký mất hết ý nghĩa, kể cả những dòng đã ghi trước đó.</p>
     */
    private void requireNotSelfReview(UUID reviewerUserId) {
        if (reviewerUserId.equals(authorUserId)) {
            throw new IllegalStateException("Khong duoc tu duyet bai viet do chinh minh soan");
        }
    }

    private static String requireTitle(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tieu de bai viet khong duoc rong");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "Tieu de bai viet toi da " + MAX_TITLE_LENGTH + " ky tu");
        }
        return trimmed;
    }

    /**
     * Thân bài không rỗng và không dài quá {@link #MAX_BODY_LENGTH}.
     *
     * <p>Giới hạn ở đây chứ không ở một {@code CHECK} của CSDL: chạm trần phải là một
     * {@code 422} có câu chữ, không phải một lỗi ràng buộc sau khi người ta đã gõ xong.</p>
     */
    /** Chuỗi rỗng hoặc chỉ khoảng trắng đọc thành {@code null} — "không có" chỉ có một biểu diễn. */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String requireBody(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Than bai viet khong duoc rong");
        }
        if (value.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException(
                    "Than bai viet toi da " + MAX_BODY_LENGTH + " ky tu");
        }
        return value;
    }

}
