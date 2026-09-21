package vn.giapha.media.infrastructure.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data cho {@code media_asset}. */
public interface MediaAssetJpaRepository extends JpaRepository<MediaAssetJpaEntity, UUID> {

    Optional<MediaAssetJpaEntity> findByObjectKey(String objectKey);

    List<MediaAssetJpaEntity> findByObjectKeyIn(List<String> objectKeys);

    /**
     * Phiếu {@code PENDING} đã quá hạn quá ân hạn — "xin URL rồi bỏ ngang".
     *
     * <p>Chạy trên index một phần {@code ix_media_pending_expired}: mệnh đề {@code WHERE} ở đây
     * phải khớp với mệnh đề của index, nếu không planner bỏ index và câu này quét cả bảng lúc 3
     * giờ sáng.</p>
     */
    @Query("""
            SELECT a FROM MediaAssetJpaEntity a
             WHERE a.status = 'PENDING'
               AND a.ticketExpiresAt < :cutoff
             ORDER BY a.ticketExpiresAt
            """)
    List<MediaAssetJpaEntity> findExpiredPending(@Param("cutoff") Instant cutoff, Limit limit);

    /**
     * Tệp {@code READY} <b>không còn liên kết nào</b> và đã quá ân hạn.
     *
     * <p>Câu {@code NOT EXISTS} chạy <b>trong SQL</b>, không ở Java. Kéo cả bảng
     * {@code media_asset} về rồi lọc là cách một lệnh quản trị hoạt động tốt ở lần chạy đầu và làm
     * treo tiến trình web ở lần thứ một trăm — đúng lúc kho đã lớn tới mức cần dọn nhất.</p>
     *
     * <p>Native query vì {@code media_link} <b>cố ý không có thực thể JPA nào mang quan hệ</b> tới
     * {@code media_asset}: liên kết là khoá đa hình ({@code owner_type} + {@code owner_id}, không
     * FK), nên mô hình hoá nó thành {@code @OneToMany} sẽ là một lời nói dối về lược đồ.</p>
     */
    @Query(value = """
            SELECT a.* FROM media_asset a
             WHERE a.status = 'READY'
               AND a.confirmed_at < :cutoff
               AND NOT EXISTS (SELECT 1 FROM media_link l WHERE l.media_id = a.id)
             ORDER BY a.confirmed_at
             LIMIT :max
            """, nativeQuery = true)
    List<MediaAssetJpaEntity> findOrphanReady(@Param("cutoff") Instant cutoff,
                                              @Param("max") int max);
}
