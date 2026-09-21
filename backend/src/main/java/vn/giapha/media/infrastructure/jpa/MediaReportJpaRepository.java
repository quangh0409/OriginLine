package vn.giapha.media.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data cho {@code media_report}. */
public interface MediaReportJpaRepository extends JpaRepository<MediaReportJpaEntity, UUID> {

    Optional<MediaReportJpaEntity> findByMediaIdAndReportedByAndStatus(
            UUID mediaId, UUID reportedBy, String status);

    List<MediaReportJpaEntity> findByStatusOrderByCreatedAtDesc(String status, Limit limit);
}
