package vn.giapha.media.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Tệp này thuộc về cái gì. POJO thuần.
 *
 * <p><b>Một tệp, một chủ</b> — ép bằng {@code ux_media_link_one_owner} của V19. Xem khối ghi chú
 * 19.2 ở đó cho lý do: cho một tệp gắn vào hai bài sẽ làm "gỡ khỏi bài A" hoá ra vẫn còn ở bài B,
 * tức là một lỗ ngay trên đường gỡ — thứ vừa được dựng lên để làm quyết định "ảnh đi theo quyền
 * của bài" an toàn.</p>
 *
 * @param position thứ tự hiển thị trong bài; ảnh chân dung luôn {@code 0}
 */
public record MediaLink(UUID id, UUID mediaId, MediaOwnerType ownerType, UUID ownerId,
                        int position, Instant createdAt) {

    public static MediaLink of(UUID mediaId, MediaOwnerType ownerType, UUID ownerId, int position) {
        return new MediaLink(UUID.randomUUID(), mediaId, ownerType, ownerId, position, null);
    }
}
