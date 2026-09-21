package vn.giapha.media.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.media.domain.MediaAsset;

/** Cổng lưu trữ quan hệ của {@link MediaAsset}. Hiện thực JPA ở {@code media.infrastructure.jpa}. */
public interface MediaAssetRepository {

    MediaAsset save(MediaAsset asset);

    Optional<MediaAsset> findById(UUID id);

    /**
     * Tra theo khoá đối tượng. {@code media_asset.object_key} là UNIQUE nên phép tra này xác định.
     * Dùng bởi {@code MediaViewService} khi client đưa {@code avatarKey} lấy từ {@code PersonDto}.
     */
    Optional<MediaAsset> findByObjectKey(String objectKey);

    List<MediaAsset> findAllByObjectKeys(List<String> objectKeys);

    /** Nhiều tệp một lượt — dùng cùng {@code MediaLinkRepository.findByOwners} để tránh N+1. */
    List<MediaAsset> findAllByIds(List<UUID> ids);

    /**
     * Đường dọn #1: phiếu {@code PENDING} quá hạn quá {@code MediaLimits.PENDING_GRACE} — người
     * dùng xin URL rồi bỏ ngang.
     */
    List<MediaAsset> findExpiredPending(Instant cutoff, int limit);

    /**
     * Đường dọn #2: tệp {@code READY} <b>không còn hàng {@code media_link}</b> nào trỏ tới và đã
     * quá {@code MediaLimits.ORPHAN_GRACE}. Phép {@code NOT EXISTS} chạy trong SQL: kéo cả bảng về
     * Java rồi lọc là cách một dòng lệnh quản trị làm treo tiến trình web ở lần chạy thứ một trăm.
     */
    List<MediaAsset> findOrphanReady(Instant cutoff, int limit);
}
