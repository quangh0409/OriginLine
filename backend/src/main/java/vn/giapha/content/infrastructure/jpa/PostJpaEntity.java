package vn.giapha.content.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Bản chiếu bảng {@code post} (V17).
 *
 * <p>{@code created_at} và {@code updated_at} là {@code insertable = false, updatable = false}:
 * giá trị do {@code DEFAULT now()} và trigger {@code tg_post_touch} của CSDL đặt, không phải do
 * đồng hồ của JVM. Nhiều instance thì đồng hồ lệch nhau, còn thứ tự bài trên trang chủ thì phải
 * nhất quán. Cùng lý do với {@code ChangeRequestJpaEntity}.</p>
 *
 * <p>{@code published_at} thì <b>ngược lại</b> — do ứng dụng đặt. Nó không phải "lúc hàng được
 * ghi" mà là "lúc dòng họ quyết định đăng bài này", và hai thứ ấy chỉ tình cờ trùng nhau.</p>
 */
@Entity
@Table(name = "post")
public class PostJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "author_person_id", nullable = false, updatable = false)
    private UUID authorPersonId;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private UUID authorUserId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PostJpaEntity() {
    }

    public PostJpaEntity(UUID id, UUID authorPersonId, UUID authorUserId, UUID branchId) {
        this.id = id;
        this.authorPersonId = authorPersonId;
        this.authorUserId = authorUserId;
        this.branchId = branchId;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getAuthorPersonId() {
        return authorPersonId;
    }

    public UUID getAuthorUserId() {
        return authorUserId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
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
