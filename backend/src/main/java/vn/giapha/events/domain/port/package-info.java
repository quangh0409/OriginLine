/**
 * <b>Port</b> do domain của {@code events} khai báo — hợp đồng ra thế giới bên ngoài, hiện thực nằm
 * ở {@code events.infrastructure}.
 *
 * <p>Giao diện ở đây nói bằng ngôn ngữ nghiệp vụ ("các sự kiện lặp hằng năm", "giành job đến hạn"),
 * không nói bằng ngôn ngữ hạ tầng. Nhờ vậy đổi từ JPA sang SQL thuần — chuyện đã xảy ra với
 * {@code reminder_job} — không chạm một dòng nào ở tầng application.</p>
 */
package vn.giapha.events.domain.port;
