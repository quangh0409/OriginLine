/**
 * Domain event của context {@code media} — <b>lối ngược duy nhất</b> mà kiến trúc cho phép.
 *
 * <p>Mọi mũi tên phụ thuộc ở cấp Java chạy <i>về phía</i> {@code media}
 * ({@code content → media}, {@code genealogy → media}). Khi {@code media} cần báo cho một context
 * khác rằng có chuyện xảy ra, nó không gọi ngược — nó phát sự kiện. Đúng một sự kiện ở đợt này,
 * và nó tồn tại vì một triệu chứng cụ thể: xem
 * {@link vn.giapha.media.domain.event.PersonAvatarRemovedEvent}.</p>
 */
@org.springframework.modulith.NamedInterface("events")
package vn.giapha.media.domain.event;
