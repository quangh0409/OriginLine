/**
 * Mô hình đọc (read model) mà tầng application của {@code notification} trả ra ngoài.
 *
 * <p>Tách khỏi {@code domain} vì chúng phục vụ hiển thị: {@code unreadCount} cho badge chuông, cờ
 * "thiết bị này" cho màn hình cài đặt — không phải bất biến nghiệp vụ.</p>
 */
package vn.giapha.notification.application.view;
