/**
 * Domain event của context {@code membership}.
 *
 * <p>{@code @NamedInterface} vì đây là <b>một trong hai</b> lối giao tiếp hợp lệ giữa các bounded
 * context (lối kia là application service public). {@code genealogy} lắng nghe
 * {@code ChangeRequestApprovedEvent} để áp dụng thay đổi đã được duyệt; {@code notification} lắng
 * nghe cả hai để báo lại cho người gửi.</p>
 */
@org.springframework.modulith.NamedInterface("events")
package vn.giapha.membership.domain.event;
