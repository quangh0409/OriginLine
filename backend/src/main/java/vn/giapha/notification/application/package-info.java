/**
 * Tầng <b>application</b> của context {@code notification}: use case service điều phối domain + port,
 * ranh giới {@code @Transactional}, DTO vào/ra, và nơi phát domain event.
 *
 * <p>Chỉ được phụ thuộc xuống {@code domain}; không biết gì về HTTP, JPA hay Cypher.</p>
 *
 * <p><b>Đây là mặt tiền công khai của context</b> ({@code @NamedInterface}): context khác gửi thông
 * báo bằng cách gọi {@code NotificationDispatchService}, <b>không</b> chạm vào
 * {@code notification.domain} hay {@code notification.infrastructure}. Cụ thể, {@code events} đẩy
 * lịch nhắc giỗ qua đây; nhờ vậy {@code events} không biết RabbitMQ tồn tại, và việc thêm kênh mới ở
 * Giai đoạn 2 (Zalo ZNS, SMS) không chạm một dòng nào của {@code events}.</p>
 */
@org.springframework.modulith.NamedInterface("application")
package vn.giapha.notification.application;
