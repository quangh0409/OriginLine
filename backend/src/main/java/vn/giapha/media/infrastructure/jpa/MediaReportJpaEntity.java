package vn.giapha.media.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Bản chiếu bảng {@code media_report} (V19).
 *
 * <p>{@code note} là {@code updatable = false}: lời người báo gõ là <b>bằng chứng</b>, không phải
 * một trường soạn thảo. Cho sửa nó nghĩa là một người có thể mềm hoá lời tố của mình sau khi đã
 * có kết luận, và bản ghi mất hết giá trị đối chiếu.</p>
 */
@Entity
@Table(name = "media_report")
public class MediaReportJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "media_id", nullable = false, updatable = false)
    private UUID mediaId;

    @Column(name = "reason", nullable = false, updatable = false, length = 20)
    private String reason;

    @Column(name = "note", updatable = false)
    private String note;

    @Column(name = "reported_by", nullable = false, updatable = false)
    private UUID reportedBy;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected MediaReportJpaEntity() {
    }

    public MediaReportJpaEntity(UUID id, UUID mediaId, String reason, String note, UUID reportedBy) {
        this.id = id;
        this.mediaId = mediaId;
        this.reason = reason;
        this.note = note;
        this.reportedBy = reportedBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMediaId() {
        return mediaId;
    }

    public String getReason() {
        return reason;
    }

    public String getNote() {
        return note;
    }

    public UUID getReportedBy() {
        return reportedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
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
