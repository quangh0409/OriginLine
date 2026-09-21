package vn.giapha.media.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Bản chiếu bảng {@code media_asset} (V19).
 *
 * <p>{@code created_at}/{@code updated_at} là {@code insertable = false, updatable = false} —
 * do {@code DEFAULT now()} và trigger {@code tg_media_asset_touch} đặt, không phải đồng hồ JVM.
 * Cùng lý do với {@code PostJpaEntity}.</p>
 *
 * <p>{@code confirmed_at}/{@code purged_at} thì ngược lại, do ứng dụng đặt: chúng không phải "lúc
 * hàng được ghi" mà là "lúc backend nhìn thấy tệp" và "lúc byte biến mất", hai sự kiện nghiệp vụ
 * chỉ tình cờ trùng với một lượt {@code UPDATE}.</p>
 */
@Entity
@Table(name = "media_asset")
public class MediaAssetJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "object_key", nullable = false, updatable = false, length = 512)
    private String objectKey;

    @Column(name = "bucket", nullable = false, updatable = false, length = 63)
    private String bucket;

    @Column(name = "kind", nullable = false, updatable = false, length = 8)
    private String kind;

    @Column(name = "status", nullable = false, length = 8)
    private String status;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "alt_text", length = 300)
    private String altText;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "ticket_expires_at", nullable = false, updatable = false)
    private Instant ticketExpiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "purged_at")
    private Instant purgedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected MediaAssetJpaEntity() {
    }

    public MediaAssetJpaEntity(UUID id, String objectKey, String bucket, String kind,
                               UUID uploadedBy, Instant ticketExpiresAt) {
        this.id = id;
        this.objectKey = objectKey;
        this.bucket = bucket;
        this.kind = kind;
        this.uploadedBy = uploadedBy;
        this.ticketExpiresAt = ticketExpiresAt;
    }

    public UUID getId() {
        return id;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getBucket() {
        return bucket;
    }

    public String getKind() {
        return kind;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getTicketExpiresAt() {
        return ticketExpiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getPurgedAt() {
        return purgedAt;
    }

    public void setPurgedAt(Instant purgedAt) {
        this.purgedAt = purgedAt;
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
