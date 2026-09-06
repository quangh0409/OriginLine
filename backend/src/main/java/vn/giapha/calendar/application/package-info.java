/**
 * Tầng <b>application</b> của context {@code calendar}: use case service điều phối domain + port,
 * ranh giới {@code @Transactional}, DTO vào/ra, và nơi phát domain event.
 *
 * <p>Chỉ được phụ thuộc xuống {@code domain}; không biết gì về HTTP, JPA hay Cypher.</p>
 *
 * <p><b>Đây là mặt tiền công khai của context</b> ({@code @NamedInterface}): context khác gọi
 * {@code LunarCalendarService} chứ <b>không</b> chạm thẳng {@code calendar.domain}. Thuật toán Hồ
 * Ngọc Đức để mở thì mỗi nơi gọi sẽ tự chọn múi giờ và tự quyết định làm gì khi ngày âm không tồn
 * tại — ba chính sách ấy phải giống hệt nhau ở mọi context, nên chúng nằm ở service này.</p>
 */
@org.springframework.modulith.NamedInterface("application")
package vn.giapha.calendar.application;
