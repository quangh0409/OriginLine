package vn.giapha.media.infrastructure.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import vn.giapha.media.domain.MediaAsset;
import vn.giapha.media.domain.MediaKind;
import vn.giapha.media.domain.MediaLink;
import vn.giapha.media.domain.MediaOwnerType;
import vn.giapha.media.domain.MediaReport;
import vn.giapha.media.domain.MediaStatus;
import vn.giapha.media.domain.ReportReason;
import vn.giapha.media.domain.ReportStatus;
import vn.giapha.media.domain.port.MediaAssetRepository;
import vn.giapha.media.domain.port.MediaLinkRepository;
import vn.giapha.media.domain.port.MediaReportRepository;

/**
 * Ba adapter JPA của context {@code media}, gom vào một tệp.
 *
 * <h2>Vì sao gom, khi quy ước của dự án là "một lớp một tệp"</h2>
 * Vì ba lớp này là <b>một</b> trách nhiệm chia làm ba: dịch giữa POJO domain và bản chiếu JPA của
 * ba bảng cùng một migration, không lớp nào có luật riêng, và cả ba sẽ luôn được sửa cùng lúc khi
 * V19 đổi. Tách thành ba tệp chỉ đổi lấy ba khối {@code import} giống hệt nhau và ba javadoc nói
 * cùng một câu. Nếu một trong ba lớn lên tới mức có logic riêng (ví dụ một câu {@code ltree} như
 * {@code PostRepositoryAdapter} đã cần), tách nó ra khi đó — đó mới là lúc việc tách nói lên điều
 * gì.
 */
public final class MediaRepositoryAdapters {

    private MediaRepositoryAdapters() {
    }

    // =====================================================================================
    // media_asset
    // =====================================================================================

    /** Hiện thực {@link MediaAssetRepository}. */
    @Repository
    public static class AssetAdapter implements MediaAssetRepository {

        private final MediaAssetJpaRepository jpa;

        public AssetAdapter(MediaAssetJpaRepository jpa) {
            this.jpa = jpa;
        }

        @Override
        public MediaAsset save(MediaAsset asset) {
            MediaAssetJpaEntity entity = jpa.findById(asset.id())
                    .orElseGet(() -> new MediaAssetJpaEntity(asset.id(), asset.objectKey(),
                            asset.bucket(), asset.kind().name(), asset.uploadedBy(),
                            asset.ticketExpiresAt()));
            entity.setStatus(asset.status().name());
            entity.setContentType(asset.contentType());
            entity.setSizeBytes(asset.sizeBytes());
            entity.setDurationMs(asset.durationMs());
            entity.setAltText(asset.altText());
            entity.setConfirmedAt(asset.confirmedAt());
            entity.setPurgedAt(asset.purgedAt());
            return toDomain(jpa.saveAndFlush(entity));
        }

        @Override
        public Optional<MediaAsset> findById(UUID id) {
            return id == null ? Optional.empty() : jpa.findById(id).map(AssetAdapter::toDomain);
        }

        @Override
        public Optional<MediaAsset> findByObjectKey(String objectKey) {
            return objectKey == null ? Optional.empty()
                    : jpa.findByObjectKey(objectKey).map(AssetAdapter::toDomain);
        }

        @Override
        public List<MediaAsset> findAllByObjectKeys(List<String> objectKeys) {
            if (objectKeys == null || objectKeys.isEmpty()) {
                return List.of();
            }
            return jpa.findByObjectKeyIn(objectKeys).stream().map(AssetAdapter::toDomain).toList();
        }

        @Override
        public List<MediaAsset> findAllByIds(List<UUID> ids) {
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            return jpa.findAllById(ids).stream().map(AssetAdapter::toDomain).toList();
        }

        @Override
        public List<MediaAsset> findExpiredPending(Instant cutoff, int limit) {
            return jpa.findExpiredPending(cutoff, Limit.of(limit)).stream()
                    .map(AssetAdapter::toDomain).toList();
        }

        @Override
        public List<MediaAsset> findOrphanReady(Instant cutoff, int limit) {
            return jpa.findOrphanReady(cutoff, limit).stream()
                    .map(AssetAdapter::toDomain).toList();
        }

        static MediaAsset toDomain(MediaAssetJpaEntity e) {
            return new MediaAsset(e.getId(), e.getObjectKey(), e.getBucket(),
                    MediaKind.valueOf(e.getKind()), MediaStatus.valueOf(e.getStatus()),
                    e.getContentType(), e.getSizeBytes(), e.getDurationMs(), e.getAltText(),
                    e.getUploadedBy(), e.getTicketExpiresAt(), e.getConfirmedAt(), e.getPurgedAt(),
                    e.getCreatedAt(), e.getVersion());
        }
    }

    // =====================================================================================
    // media_link
    // =====================================================================================

    /** Hiện thực {@link MediaLinkRepository}. */
    @Repository
    public static class LinkAdapter implements MediaLinkRepository {

        private final MediaLinkJpaRepository jpa;

        public LinkAdapter(MediaLinkJpaRepository jpa) {
            this.jpa = jpa;
        }

        @Override
        public MediaLink save(MediaLink link) {
            MediaLinkJpaEntity saved = jpa.saveAndFlush(new MediaLinkJpaEntity(
                    link.id(), link.mediaId(), link.ownerType().name(), link.ownerId(),
                    (short) link.position()));
            return toDomain(saved);
        }

        @Override
        public List<MediaLink> findByOwner(MediaOwnerType ownerType, UUID ownerId) {
            if (ownerType == null || ownerId == null) {
                return List.of();
            }
            return jpa.findByOwnerTypeAndOwnerIdOrderByPosition(ownerType.name(), ownerId).stream()
                    .map(LinkAdapter::toDomain).toList();
        }

        @Override
        public List<MediaLink> findByOwners(MediaOwnerType ownerType, List<UUID> ownerIds) {
            if (ownerType == null || ownerIds == null || ownerIds.isEmpty()) {
                return List.of();
            }
            return jpa.findByOwnerTypeAndOwnerIdInOrderByOwnerIdAscPositionAsc(
                            ownerType.name(), ownerIds).stream()
                    .map(LinkAdapter::toDomain).toList();
        }

        @Override
        public Optional<MediaLink> findByMediaId(UUID mediaId) {
            return mediaId == null ? Optional.empty()
                    : jpa.findByMediaId(mediaId).map(LinkAdapter::toDomain);
        }

        @Override
        public int deleteByOwner(MediaOwnerType ownerType, UUID ownerId) {
            return ownerType == null || ownerId == null ? 0
                    : jpa.deleteByOwner(ownerType.name(), ownerId);
        }

        @Override
        public int deleteByMediaId(UUID mediaId) {
            return mediaId == null ? 0 : jpa.deleteByMediaId(mediaId);
        }

        static MediaLink toDomain(MediaLinkJpaEntity e) {
            return new MediaLink(e.getId(), e.getMediaId(),
                    MediaOwnerType.valueOf(e.getOwnerType()), e.getOwnerId(),
                    e.getPosition(), e.getCreatedAt());
        }
    }

    // =====================================================================================
    // media_report
    // =====================================================================================

    /** Hiện thực {@link MediaReportRepository}. */
    @Repository
    public static class ReportAdapter implements MediaReportRepository {

        private final MediaReportJpaRepository jpa;

        public ReportAdapter(MediaReportJpaRepository jpa) {
            this.jpa = jpa;
        }

        @Override
        public MediaReport save(MediaReport report) {
            MediaReportJpaEntity entity = jpa.findById(report.id())
                    .orElseGet(() -> new MediaReportJpaEntity(report.id(), report.mediaId(),
                            report.reason().name(), report.note(), report.reportedBy()));
            entity.setStatus(report.status().name());
            entity.setReviewedBy(report.reviewedBy());
            entity.setReviewedAt(report.reviewedAt());
            entity.setResolutionNote(report.resolutionNote());
            return toDomain(jpa.saveAndFlush(entity));
        }

        @Override
        public Optional<MediaReport> findById(UUID id) {
            return id == null ? Optional.empty() : jpa.findById(id).map(ReportAdapter::toDomain);
        }

        @Override
        public Optional<MediaReport> findOpenBy(UUID mediaId, UUID reportedBy) {
            if (mediaId == null || reportedBy == null) {
                return Optional.empty();
            }
            return jpa.findByMediaIdAndReportedByAndStatus(mediaId, reportedBy,
                    ReportStatus.OPEN.name()).map(ReportAdapter::toDomain);
        }

        @Override
        public List<MediaReport> findByStatus(ReportStatus status, int limit) {
            return jpa.findByStatusOrderByCreatedAtDesc(status.name(), Limit.of(limit)).stream()
                    .map(ReportAdapter::toDomain).toList();
        }

        static MediaReport toDomain(MediaReportJpaEntity e) {
            return new MediaReport(e.getId(), e.getMediaId(), ReportReason.valueOf(e.getReason()),
                    e.getNote(), e.getReportedBy(), ReportStatus.valueOf(e.getStatus()),
                    e.getReviewedBy(), e.getReviewedAt(), e.getResolutionNote(),
                    e.getCreatedAt(), e.getVersion());
        }
    }
}
