package vn.giapha.media.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.media.domain.MediaLink;
import vn.giapha.media.domain.MediaOwnerType;

/** Cổng lưu trữ của {@link MediaLink}. */
public interface MediaLinkRepository {

    MediaLink save(MediaLink link);

    /** Các tệp của một chủ sở hữu, <b>đúng thứ tự {@code position}</b>. */
    List<MediaLink> findByOwner(MediaOwnerType ownerType, UUID ownerId);

    /**
     * Các tệp của <b>nhiều</b> chủ sở hữu cùng lúc, đúng thứ tự.
     *
     * <p>Có nó vì trang chủ: 10 bài × (một câu hỏi liên kết + một câu hỏi tệp) là 20 lượt truy vấn
     * cho một màn hình, và NFR-1 đặt ngân sách 2000 ms tới thẻ người đầu tiên. Một câu
     * {@code IN (...)} thay cho vòng lặp là khác biệt giữa hai con số ấy.</p>
     */
    List<MediaLink> findByOwners(MediaOwnerType ownerType, List<UUID> ownerIds);

    /** Chủ sở hữu của một tệp; rỗng nghĩa là tệp đang mồ côi. */
    Optional<MediaLink> findByMediaId(UUID mediaId);

    /** Cắt mọi liên kết của một chủ sở hữu — gỡ bài, đổi ảnh chân dung. */
    int deleteByOwner(MediaOwnerType ownerType, UUID ownerId);

    /** Cắt liên kết của đúng một tệp — gỡ theo đơn báo vi phạm. */
    int deleteByMediaId(UUID mediaId);
}
