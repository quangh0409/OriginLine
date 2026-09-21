package vn.giapha.content.domain;

import java.time.Instant;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Một bản ghi <b>vinh danh</b> gắn với nhân khẩu: đỗ đạt · chức tước · thành tích · khen thưởng.
 *
 * <h2>Không phải một loại bài viết — quyết định đã chốt (design/07-checklist §2)</h2>
 * Khác biệt không nằm ở cách hiển thị mà ở <b>việc tra cứu được</b>: vì mỗi bản ghi trỏ đích danh
 * một {@code person_id}, hệ thống trả lời được "chi nào có bao nhiêu người đỗ đạt" và "các cụ đỗ
 * tiến sĩ trong họ" bằng một câu truy vấn. Một bài viết tiêu đề "Họ ta có ba tiến sĩ" thì không.
 *
 * <h2>Chi/ngành KHÔNG được chụp lại ở đây, và đó là khác biệt có chủ ý với {@link Post}</h2>
 * {@code Post} giữ {@code branchId} chụp lúc tạo, vì bài viết là tiếng nói của người viết <i>tại
 * thời điểm viết</i>. Vinh danh thì ngược lại: nó là dữ liệu <b>về một con người</b>, và chi của nó
 * phải là chi <i>hiện tại</i> của người ấy. Chụp lại thì sau một lần chuyển chi, câu "chi nào có
 * bao nhiêu người đỗ đạt" <b>sai mà không ai thấy</b> — bảng vẫn đủ hàng, tổng vẫn khớp, chỉ phân
 * bổ theo chi là sai. Vì thế aggregate này không có trường chi nào cả; nó được suy ở tầng truy vấn.
 *
 * <h2>Vinh danh cũng phải duyệt</h2>
 * "Một danh hiệu tự khai mà lên thẳng trang chủ là chuyện khác hẳn một bài viết" (§2). Nên bản ghi
 * sinh ra ở {@link ContentStatus#PENDING}, không có giai đoạn nháp: bốn ô điền xong trong một phút,
 * thêm trạng thái nháp chỉ tạo một hàng đợi thứ hai mà không ai mở.
 *
 * <p>POJO thuần — bản chiếu JPA nằm ở {@code content.infrastructure.jpa}.</p>
 */
public final class Honour {

    public static final int MAX_TITLE_LENGTH = 250;
    public static final int MAX_ISSUER_LENGTH = 250;
    public static final int MAX_DESCRIPTION_LENGTH = 4_000;

    /** Cận dưới của {@code ck_honour_year}. Không sổ phả giấy nào chép năm nhỏ hơn mà còn đọc được. */
    public static final int MIN_YEAR = 1000;

    /** Cận trên của {@code ck_honour_year}. */
    public static final int MAX_YEAR = 2200;

    private final UUID id;
    private final UUID personId;
    private final UUID createdBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    private HonourKind kind;
    private String title;
    private Integer year;
    private String issuer;
    private String description;
    private ContentStatus status;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String rejectReason;
    private boolean deleted;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public Honour(UUID id, UUID personId, HonourKind kind, String title, Integer year,
                  String issuer, String description, ContentStatus status, UUID createdBy,
                  UUID reviewedBy, Instant reviewedAt, String rejectReason, boolean deleted,
                  Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "Honour.id khong duoc null");
        this.personId = Objects.requireNonNull(personId,
                "Honour.personId khong duoc null — vinh danh khong ton tai roi khoi mot nhan khau");
        this.kind = Objects.requireNonNull(kind, "Honour.kind khong duoc null");
        this.title = requireTitle(title);
        this.year = rangeCheckYear(year);
        this.issuer = truncateCheck(issuer, MAX_ISSUER_LENGTH, "Noi cap");
        this.description = truncateCheck(description, MAX_DESCRIPTION_LENGTH, "Mo ta");
        this.status = status == null ? ContentStatus.PENDING : status;
        this.createdBy = Objects.requireNonNull(createdBy, "Honour.createdBy khong duoc null");
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.rejectReason = blankToNull(rejectReason);
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
        if (this.status == ContentStatus.DRAFT) {
            throw new IllegalArgumentException(
                    "Vinh danh khong co trang thai DRAFT; xem ContentStatus");
        }
    }

    /** Bản ghi mới — luôn ở {@link ContentStatus#PENDING}. */
    public static Honour submit(UUID id, UUID personId, HonourKind kind, String title,
                                Integer year, String issuer, String description, UUID createdBy) {
        return new Honour(id, personId, kind, title, requireFreshYear(year), issuer, description,
                ContentStatus.PENDING, createdBy, null, null, null, false, null, null, 0L);
    }

    public UUID id() {
        return id;
    }

    public UUID personId() {
        return personId;
    }

    public HonourKind kind() {
        return kind;
    }

    public String title() {
        return title;
    }

    public Integer year() {
        return year;
    }

    public String issuer() {
        return issuer;
    }

    public String description() {
        return description;
    }

    public ContentStatus status() {
        return status;
    }

    public UUID createdBy() {
        return createdBy;
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

    public boolean isDeleted() {
        return deleted;
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

    // -------------------------------------------------------------------------------------
    // Chuyển trạng thái
    // -------------------------------------------------------------------------------------

    /**
     * Sửa nội dung.
     *
     * <p>Sửa một bản ghi <b>đã duyệt</b> đưa nó <b>về lại {@link ContentStatus#PENDING}</b> và xoá
     * chữ ký duyệt cũ. Đây là điểm khác {@link Post#edit}, vốn chặn hẳn việc sửa sau khi đăng, và
     * khác vì một lý do cụ thể: một bài viết sửa xong là một bài khác, còn một vinh danh thì thường
     * chỉ được <i>bổ sung</i> (thêm năm, thêm nơi cấp) và bắt người dùng xoá rồi tạo lại sẽ làm mất
     * lịch sử của chính bản ghi ấy. Nhưng nội dung đã đổi thì chữ ký duyệt cũ không còn nói về thứ
     * đang hiển thị, nên nó <b>phải</b> mất theo — nếu không thì "sửa nhẹ sau khi được duyệt" thành
     * lối đăng thẳng lên trang chủ mà không ai đọc.</p>
     *
     * <p>Tham số {@code null} nghĩa là giữ nguyên (ngữ nghĩa PATCH của cả sản phẩm).</p>
     */
    public void edit(HonourKind newKind, String newTitle, Integer newYear, String newIssuer,
                     String newDescription) {
        requireNotDeleted();
        if (status.isFinal()) {
            throw new IllegalStateException(
                    "Vinh danh da o trang thai " + status + ", khong sua lai duoc");
        }
        if (newKind != null) {
            this.kind = newKind;
        }
        if (newTitle != null) {
            this.title = requireTitle(newTitle);
        }
        if (newYear != null) {
            this.year = requireFreshYear(newYear);
        }
        if (newIssuer != null) {
            this.issuer = truncateCheck(blankToNull(newIssuer), MAX_ISSUER_LENGTH, "Noi cap");
        }
        if (newDescription != null) {
            this.description = truncateCheck(blankToNull(newDescription),
                    MAX_DESCRIPTION_LENGTH, "Mo ta");
        }
        if (status == ContentStatus.PUBLISHED) {
            this.status = ContentStatus.PENDING;
            this.reviewedBy = null;
            this.reviewedAt = null;
        }
    }

    /** Duyệt. {@code PENDING → PUBLISHED}. */
    public void approve(UUID reviewerUserId, Instant at) {
        requirePending();
        Objects.requireNonNull(reviewerUserId, "reviewerUserId khong duoc null khi duyet vinh danh");
        requireNotSelfReview(reviewerUserId);
        this.status = ContentStatus.PUBLISHED;
        this.reviewedBy = reviewerUserId;
        this.reviewedAt = at == null ? Instant.now() : at;
        this.rejectReason = null;
    }

    /**
     * Từ chối. {@code PENDING → WITHDRAWN}, lý do bắt buộc.
     *
     * <p>Khác {@link Post#sendBack}: bài viết trả về {@code DRAFT} để sửa rồi gửi lại, còn một vinh
     * danh bị từ chối thường là bị từ chối vì <i>không có thật</i> hoặc <i>không thuộc về người
     * đó</i> — hai thứ sửa câu chữ không cứu được. Cần khai lại thì tạo bản ghi mới, và khi ấy lần
     * từ chối cũ vẫn còn đó để người duyệt sau đọc được.</p>
     */
    public void reject(UUID reviewerUserId, String reason, Instant at) {
        requirePending();
        Objects.requireNonNull(reviewerUserId, "reviewerUserId khong duoc null khi tu choi vinh danh");
        requireNotSelfReview(reviewerUserId);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Tu choi vinh danh phai kem ly do");
        }
        this.status = ContentStatus.WITHDRAWN;
        this.reviewedBy = reviewerUserId;
        this.reviewedAt = at == null ? Instant.now() : at;
        this.rejectReason = reason.trim();
    }

    /**
     * <b>Xoá mềm.</b> Cột {@code status} giữ nguyên.
     *
     * <p>Trộn "đã gỡ" vào {@code status} sẽ xoá mất thông tin bản ghi ấy từng được duyệt hay chưa —
     * và đó đúng là câu người ta hỏi khi tra lại một vinh danh bị gỡ.</p>
     */
    public void softDelete() {
        requireNotDeleted();
        this.deleted = true;
    }

    /**
     * Ảnh chụp cho {@code audit_log}.
     *
     * <h2>KHÔNG có {@code title}, {@code issuer} hay {@code description}</h2>
     * Từ V17 vinh danh là <b>nhóm trường riêng tư thứ sáu</b>: với người còn sống, "Thạc sĩ, Đại
     * học Y Hà Nội, 2019" là dữ liệu cá nhân mà chính chủ có quyền đóng lại. {@code audit_log} là
     * bảng chỉ ghi thêm — chép ba trường ấy vào đó nghĩa là một người bấm "riêng tư" trên giao diện
     * vẫn còn nguyên bản sao trong nhật ký, tức quyền họ vừa dùng không có thật.
     *
     * <p>Ghi lại <i>cấu trúc</i>: loại, có năm hay không, có nơi cấp hay không. Đủ để trả lời "ai
     * đổi cái gì, lúc nào" mà không chở một mẩu dữ liệu Tầng 3 nào. {@code year} <b>cũng</b> bị
     * loại: một năm đỗ đạt gần như luôn suy ra được năm sinh trong khoảng vài năm.</p>
     */
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", id.toString());
        snapshot.put("personId", personId.toString());
        snapshot.put("kind", kind.name());
        snapshot.put("status", status.name());
        snapshot.put("deleted", deleted);
        snapshot.put("createdBy", createdBy.toString());
        snapshot.put("reviewedBy", reviewedBy == null ? null : reviewedBy.toString());
        snapshot.put("reviewedAt", reviewedAt == null ? null : reviewedAt.toString());
        snapshot.put("hasYear", year != null);
        snapshot.put("hasIssuer", issuer != null);
        snapshot.put("hasDescription", description != null);
        return snapshot;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Honour honour && id.equals(honour.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Không chở tiêu đề, nơi cấp hay mô tả — an toàn để ghi log. */
    @Override
    public String toString() {
        return "Honour[" + id + ", " + kind + ", " + status + "]";
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ
    // -------------------------------------------------------------------------------------

    private void requirePending() {
        requireNotDeleted();
        if (status != ContentStatus.PENDING) {
            throw new IllegalStateException(
                    "Chi duyet duoc vinh danh o trang thai PENDING; ban ghi nay dang o " + status);
        }
    }

    private void requireNotDeleted() {
        if (deleted) {
            throw new IllegalStateException("Vinh danh da bi go, khong thao tac lai duoc");
        }
    }

    /**
     * <b>Không ai tự duyệt bản ghi của chính mình.</b> Cùng luật với {@link Post}, và vì đúng câu
     * mà §2 dùng để đòi bước duyệt này: "một danh hiệu <i>tự khai</i> mà lên thẳng trang chủ".
     *
     * <p>Lưu ý phép so là {@code createdBy} (tài khoản đã khai), <b>không</b> phải {@code personId}
     * (người được vinh danh). Ca cần chặn không chỉ là "tự khai cho mình" mà cả "tự khai cho con
     * mình rồi tự duyệt" — cả hai đều là một người vừa khai vừa xác nhận.</p>
     */
    private void requireNotSelfReview(UUID reviewerUserId) {
        if (reviewerUserId.equals(createdBy)) {
            throw new IllegalStateException("Khong duoc tu duyet vinh danh do chinh minh khai");
        }
    }

    private static String requireTitle(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tieu de vinh danh khong duoc rong");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Tieu de vinh danh toi da " + MAX_TITLE_LENGTH
                    + " ky tu");
        }
        return trimmed;
    }

    /**
     * Năm phải nằm trong {@code ck_honour_year} — <b>đúng luật của CSDL, không hơn</b>.
     *
     * <p>Dùng ở constructor, tức cả trên đường <i>nạp lại</i> từ CSDL. Vì thế nó không được chặt
     * hơn {@code CHECK}: một phép kiểm chặt hơn ở đây nghĩa là có những hàng <b>ghi xuống được
     * nhưng đọc lên không được</b> — loại lỗi chỉ lộ ra khi ai đó mở đúng bản ghi cũ ấy, thường là
     * lâu sau khi người viết luật mới đã quên.</p>
     */
    private static Integer rangeCheckYear(Integer value) {
        if (value == null) {
            return null;
        }
        if (value < MIN_YEAR || value > MAX_YEAR) {
            throw new IllegalArgumentException("Nam vinh danh phai nam trong khoang "
                    + MIN_YEAR + "-" + MAX_YEAR + " (nhan duoc " + value + ")");
        }
        return value;
    }

    /**
     * Năm cho một bản ghi <b>mới nhập</b>: thêm chặn tương lai xa.
     *
     * <p>Chỉ dùng ở {@link #submit} và {@link #edit}, không dùng ở constructor — xem
     * {@link #rangeCheckYear}. Kiểm ở tầng domain thay vì để {@code CHECK} bắt vì một năm gõ nhầm
     * ({@code 20219}) phải ra {@code 422} có câu chữ, không phải một lỗi ràng buộc kéo đổ cả
     * transaction đang ghi audit.</p>
     *
     * <p>Không chặn năm <i>sau</i>: một quyết định khen thưởng ký cuối năm nay cho năm sau là
     * chuyện có thật, và chặn nó chỉ tạo ra một ca người dùng không hiểu.</p>
     */
    private static Integer requireFreshYear(Integer value) {
        Integer checked = rangeCheckYear(value);
        if (checked != null && checked > Year.now().getValue() + 1) {
            throw new IllegalArgumentException("Nam vinh danh khong duoc xa hon nam sau");
        }
        return checked;
    }

    private static String truncateCheck(String value, int max, String what) {
        String trimmed = blankToNull(value);
        if (trimmed != null && trimmed.length() > max) {
            throw new IllegalArgumentException(what + " toi da " + max + " ky tu");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
