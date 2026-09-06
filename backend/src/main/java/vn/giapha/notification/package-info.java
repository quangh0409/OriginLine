/**
 * Bounded context <b>notification</b> — gửi thông báo bất đồng bộ qua RabbitMQ có retry + DLQ; consumer idempotent; luồng web không bao giờ chờ gửi.
 *
 * <p>Khái niệm lõi: NotificationRequest, Channel, NotificationLog, port NotificationProvider (MVP: InApp + Web Push; Zalo/SMS chỉ là adapter thêm ở GĐ2).</p>
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. Context khác chỉ được gọi qua application service public
 * của context này hoặc qua domain event — không bao giờ chạm thẳng repository.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Notification — Thông báo")
package vn.giapha.notification;
