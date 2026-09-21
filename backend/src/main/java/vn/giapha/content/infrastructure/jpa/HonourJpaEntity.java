package vn.giapha.content.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Bản chiếu bảng {@code honour} (V17).
 *
 * <p><b>Không có cột chi/ngành</b> — chủ ý, xem javadoc của {@code Honour}: chi của một vinh danh
 * luôn là chi <i>hiện tại</i> của nhân khẩu, vì câu hỏi nghiệp vụ là "chi nào có bao nhiêu người
 * đỗ đạt". Thêm một cột ở đây "cho nhanh" là làm con số ấy sai sau lần chuyển chi đầu tiên.</p>
 *
 * <p>{@code year} là {@link Integer}, và cột ở V17 là {@code INTEGER} chứ không {@code SMALLINT} —
 * <b>bắt buộc phải khớp</b>. Hibernate chạy schema-validation lúc khởi động và từ chối cả ứng dụng
 * với "wrong column type encountered in column [year] ... found int2, expecting integer". Hai byte
 * tiết kiệm được không đáng đổi lấy một cột phải nhớ ép kiểu ở mọi lớp.</p>
 */
@Entity
@Table(name = "honour")
public class HonourJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Column(name = "year")
    private Integer year;

    @Column(name = "issuer", length = 250)
    private String issuer;

    @Column(name = "description")
    private String description;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected HonourJpaEntity() {
    }

    public HonourJpaEntity(UUID id, UUID personId, UUID createdBy) {
        this.id = id;
        this.personId = personId;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPersonId() {
        return personId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(UUID reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
