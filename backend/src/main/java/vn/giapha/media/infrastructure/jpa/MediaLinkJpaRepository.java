package vn.giapha.media.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data cho {@code media_link}. */
public interface MediaLinkJpaRepository extends JpaRepository<MediaLinkJpaEntity, UUID> {

    List<MediaLinkJpaEntity> findByOwnerTypeAndOwnerIdOrderByPosition(String ownerType, UUID ownerId);

    List<MediaLinkJpaEntity> findByOwnerTypeAndOwnerIdInOrderByOwnerIdAscPositionAsc(
            String ownerType, List<UUID> ownerIds);

    Optional<MediaLinkJpaEntity> findByMediaId(UUID mediaId);

    /**
     * {@code flushAutomatically} là bắt buộc ở đây, không phải trang trí.
     *
     * <p>{@code MediaLinkService} xoá cả tập rồi chèn lại ngay trong một giao dịch. Không flush
     * trước khi chạy câu {@code DELETE} hàng loạt thì các hàng mới chèn còn nằm trong persistence
     * context sẽ bị ghi <i>sau</i> lệnh xoá và mất sạch; {@code clearAutomatically} thì cần để
     * những hàng đã xoá không còn được đọc lại từ bộ đệm cấp một.</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM MediaLinkJpaEntity l WHERE l.ownerType = :ownerType AND l.ownerId = :ownerId")
    int deleteByOwner(@Param("ownerType") String ownerType, @Param("ownerId") UUID ownerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM MediaLinkJpaEntity l WHERE l.mediaId = :mediaId")
    int deleteByMediaId(@Param("mediaId") UUID mediaId);
}
