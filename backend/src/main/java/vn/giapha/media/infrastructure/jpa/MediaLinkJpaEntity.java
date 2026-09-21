package vn.giapha.media.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Bản chiếu bảng {@code media_link} (V19).
 *
 * <p><b>Không một quan hệ JPA nào</b> ({@code @ManyToOne} sang {@code MediaAssetJpaEntity},
 * {@code @OneToMany} ngược lại). Cố ý: {@code owner_id} là khoá đa hình không có FK, và mọi lối
 * đọc ở đây đều đi theo <i>chủ sở hữu</i> chứ không theo đồ thị đối tượng. Một {@code @ManyToOne}
 * sẽ mời gọi lazy-load rồi sinh N+1 ngay trên trang chủ, nơi mỗi bài có tới 12 tệp.</p>
 *
 * <p>Không có cột {@code version}: hàng này là một sự kiện gắn kết, không phải một bản ghi được
 * sửa. Đường ghi duy nhất là xoá cả tập rồi chèn lại (xem {@code MediaLinkService}).</p>
 */
@Entity
@Table(name = "media_link")
public class MediaLinkJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "media_id", nullable = false, updatable = false)
    private UUID mediaId;

    @Column(name = "owner_type", nullable = false, updatable = false, length = 16)
    private String ownerType;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "position", nullable = false)
    private short position;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected MediaLinkJpaEntity() {
    }

    public MediaLinkJpaEntity(UUID id, UUID mediaId, String ownerType, UUID ownerId, short position) {
        this.id = id;
        this.mediaId = mediaId;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMediaId() {
        return mediaId;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public short getPosition() {
        return position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
