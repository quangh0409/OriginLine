package vn.giapha.membership.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Bản chiếu bảng {@code change_request}.
 *
 * <p>{@code payload} là {@code jsonb}: {@code @JdbcTypeCode(SqlTypes.JSON)} để Hibernate 6 gửi đúng
 * kiểu cho driver, {@code @Convert} lo phần chuyển đổi Java ⇄ chuỗi JSON.</p>
 *
 * <p>{@code created_at} là {@code insertable = false, updatable = false}: giá trị do
 * {@code DEFAULT now()} của CSDL đặt, không phải do đồng hồ của JVM. Nhiều instance thì đồng hồ
 * lệch nhau, còn thứ tự trong hàng đợi duyệt thì phải nhất quán.</p>
 */
@Entity
@Table(name = "change_request")
public class ChangeRequestJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "request_type", nullable = false, length = 24)
    private String requestType;

    @Column(name = "person_id")
    private UUID personId;

    @Column(name = "target_branch_id")
    private UUID targetBranchId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = MembershipJsonbConverter.class)
    @Column(name = "payload", nullable = false)
    private Map<String, Object> payload;

    @Column(name = "reason")
    private String reason;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ChangeRequestJpaEntity() {
    }

    public ChangeRequestJpaEntity(UUID id, String requestType, UUID requestedBy) {
        this.id = id;
        this.requestType = requestType;
        this.requestedBy = requestedBy;
    }

    public UUID getId() {
        return id;
    }

    public String getRequestType() {
        return requestType;
    }

    public UUID getPersonId() {
        return personId;
    }

    public void setPersonId(UUID personId) {
        this.personId = personId;
    }

    public UUID getTargetBranchId() {
        return targetBranchId;
    }

    public void setTargetBranchId(UUID targetBranchId) {
        this.targetBranchId = targetBranchId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(UUID reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
