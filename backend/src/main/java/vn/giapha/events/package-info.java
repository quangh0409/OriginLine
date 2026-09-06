/**
 * Bounded context <b>events</b> — giỗ/lễ, sinh lịch nhắc trước 7/3/1 ngày; death_lunar là nguồn chân lý của ngày giỗ.
 *
 * <p>Khái niệm lõi: Event, EventType, ReminderPlan, ReminderJob.</p>
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. Context khác chỉ được gọi qua application service public
 * của context này hoặc qua domain event — không bao giờ chạm thẳng repository.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Events — Sự kiện &amp; giỗ chạp")
package vn.giapha.events;
