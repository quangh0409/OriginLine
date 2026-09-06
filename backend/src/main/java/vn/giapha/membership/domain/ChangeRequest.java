package vn.giapha.membership.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Đề nghị sửa dữ liệu phả hệ do một thành viên gửi, chờ Trưởng chi hoặc Hội đồng Tộc biểu quyết.
 *
 * <h2>Vì sao luồng này tồn tại</h2>
 * Thành viên biết rõ nhất về nhánh nhà mình nhưng không được ghi thẳng vào cây — một sai sót về
 * đời thứ hoặc về quan hệ cha–con lan ra toàn bộ phả đồ phía dưới. Đề nghị + duyệt giữ được cả hai:
 * kiến thức của người trong cuộc và trách nhiệm của người có thẩm quyền.
 *
 * <h2>Quyền duyệt soi phạm vi, không chỉ soi vai</h2>
 * Aggregate này cố ý <b>không</b> tự kiểm quyền — nó không biết {@code ltree}. Việc so path là của
 * {@code BranchScopeGuard}, và {@link #approve}/{@link #reject} chỉ nhận {@code reviewerId} <i>sau
 * khi</i> phép so đó đã qua. Nhốt luật phạm vi vào một chỗ duy nhất thì không có lối vòng nào.
 *
 * <h2>Trạng thái cuối là cuối</h2>
 * {@code ck_change_request_reviewed} của V5 đòi mọi bản ghi đã xử lý phải có {@code reviewer_id} và
 * {@code reviewed_at}. Ở đây điều đó được bảo đảm bằng cấu trúc chứ không bằng kỷ luật: hai trường
 * ấy chỉ được gán trong đúng hai phương thức chuyển trạng thái.
 *
 * <p>POJO thuần — bản chiếu JPA nằm ở {@code membership.infrastructure.jpa}.</p>
 */
public final class ChangeRequest {

    private final UUID id;
    private final ChangeRequestType type;
    private final UUID personId;
    private final UUID targetBranchId;
    private final Map<String, Object> payload;
    private final String reason;
    private final UUID requestedBy;
    private final Instant createdAt;
    private final long version;

    private ChangeRequestStatus status;
    private UUID reviewerId;
    private String reviewNote;
    private Instant reviewedAt;

    public ChangeRequest(UUID id, ChangeRequestType type, UUID personId, UUID targetBranchId,
                         Map<String, Object> payload, String reason, UUID requestedBy,
                         ChangeRequestStatus status, UUID reviewerId, String reviewNote,
                         Instant reviewedAt, Instant createdAt, long version) {
        this.id = Objects.requireNonNull(id, "ChangeRequest.id khong duoc null");
        this.type = Objects.requireNonNull(type, "ChangeRequest.type khong duoc null");
        this.personId = personId;
        this.targetBranchId = targetBranchId;
        this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        this.reason = reason;
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy khong duoc null");
        this.status = status == null ? ChangeRequestStatus.PENDING : status;
        this.reviewerId = reviewerId;
        this.reviewNote = reviewNote;
        this.reviewedAt = reviewedAt;
        this.createdAt = createdAt;
        this.version = version;
        if (type.requiresExistingPerson() && personId == null) {
            throw new IllegalArgumentException(
                    "Yeu cau loai " + type + " phai tro toi mot nhan khau co san");
        }
    }

    /** Yêu cầu mới, luôn ở {@link ChangeRequestStatus#PENDING}. */
    public static ChangeRequest submit(UUID id, ChangeRequestType type, UUID personId,
                                       UUID targetBranchId, Map<String, Object> payload,
                                       String reason, UUID requestedBy) {
        return new ChangeRequest(id, type, personId, targetBranchId, payload, reason, requestedBy,
                ChangeRequestStatus.PENDING, null, null, null, null, 0L);
    }

    public UUID id() {
        return id;
    }

    public ChangeRequestType type() {
        return type;
    }

    public UUID personId() {
        return personId;
    }

    /** Chi mà yêu cầu này nhắm tới — <b>căn cứ để so phạm vi khi duyệt</b>. */
    public UUID targetBranchId() {
        return targetBranchId;
    }

    public Map<String, Object> payload() {
        // KHONG dung Map.copyOf: no nem NPE khi map chua gia tri null, ma "xoa mot truong"
        // (dat null) la mot de nghi dinh chinh hoan toan hop le — vi du bo ngay mat ghi nham.
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public String reason() {
        return reason;
    }

    public UUID requestedBy() {
        return requestedBy;
    }

    public ChangeRequestStatus status() {
        return status;
    }

    public UUID reviewerId() {
        return reviewerId;
    }

    public String reviewNote() {
        return reviewNote;
    }

    public Instant reviewedAt() {
        return reviewedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public long version() {
        return version;
    }

    /**
     * Duyệt.
     *
     * @throws IllegalStateException nếu yêu cầu đã được xử lý, hoặc người duyệt chính là người gửi
     */
    public void approve(UUID reviewer, String note, Instant at) {
        transition(ChangeRequestStatus.APPROVED, reviewer, note, at);
    }

    /** Từ chối. Lý do là <b>bắt buộc</b> — người gửi có quyền biết vì sao. */
    public void reject(UUID reviewer, String note, Instant at) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("Tu choi yeu cau dinh chinh phai kem ly do");
        }
        transition(ChangeRequestStatus.REJECTED, reviewer, note, at);
    }

    /**
     * Người gửi tự rút lại. Không đi qua {@link #transition} vì không cần luật "không tự duyệt":
     * rút yêu cầu của chính mình là việc hoàn toàn hợp lệ.
     */
    public void cancel(UUID by, Instant at) {
        requireOpen();
        if (!requestedBy.equals(by)) {
            throw new IllegalStateException("Chi nguoi gui moi duoc rut lai yeu cau dinh chinh");
        }
        this.status = ChangeRequestStatus.CANCELLED;
        this.reviewerId = by;
        this.reviewedAt = at == null ? Instant.now() : at;
    }

    /**
     * <b>Không ai được tự duyệt yêu cầu của chính mình.</b>
     *
     * <p>Một Trưởng chi vẫn ghi thẳng được trong nhánh mình mà không cần qua đây; nếu người ấy lại
     * duyệt được chính đề nghị mình gửi thì luồng duyệt chỉ còn là một bước bấm thêm, và bản ghi
     * {@code audit_log} sẽ ghi lại một cuộc "phê duyệt" không có ai kiểm tra ai.</p>
     */
    private void transition(ChangeRequestStatus next, UUID reviewer, String note, Instant at) {
        requireOpen();
        Objects.requireNonNull(reviewer, "reviewerId khong duoc null khi xu ly yeu cau");
        if (reviewer.equals(requestedBy)) {
            throw new IllegalStateException(
                    "Khong duoc tu duyet yeu cau dinh chinh do chinh minh gui");
        }
        this.status = next;
        this.reviewerId = reviewer;
        this.reviewNote = note;
        this.reviewedAt = at == null ? Instant.now() : at;
    }

    private void requireOpen() {
        if (status.isFinal()) {
            throw new IllegalStateException(
                    "Yeu cau dinh chinh da o trang thai cuoi (" + status + "), khong xu ly lai duoc");
        }
    }

    /**
     * Ảnh chụp cho {@code audit_log}.
     *
     * <p><b>Không</b> chép {@code payload} vào đây: nội dung đề nghị có thể chứa số điện thoại hay
     * địa chỉ của một người còn sống, và {@code audit_log} là bảng chỉ ghi thêm — lọt vào là không
     * gỡ ra được. Chỉ ghi <i>tên</i> các trường được đề nghị sửa, đúng tinh thần "ghi ten truong bi
     * xoa, KHONG ghi gia tri" của V5.</p>
     */
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", id.toString());
        snapshot.put("type", type.name());
        snapshot.put("status", status.name());
        snapshot.put("personId", personId == null ? null : personId.toString());
        snapshot.put("targetBranchId", targetBranchId == null ? null : targetBranchId.toString());
        snapshot.put("requestedBy", requestedBy.toString());
        snapshot.put("reviewerId", reviewerId == null ? null : reviewerId.toString());
        snapshot.put("payloadFields", payload.keySet().stream().sorted().toList());
        return snapshot;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ChangeRequest request && id.equals(request.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
