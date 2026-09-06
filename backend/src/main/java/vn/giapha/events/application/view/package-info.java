/**
 * Mô hình đọc (read model) mà tầng application của {@code events} trả ra ngoài.
 *
 * <p>Tách khỏi {@code domain} vì chúng phục vụ hiển thị, không mang bất biến nghiệp vụ: chúng chứa
 * cả giá trị <b>suy ra</b> như ngày dương của lần giỗ sắp tới hay số ngày còn lại — những thứ không
 * có trong cơ sở dữ liệu và không được phép lưu.</p>
 */
package vn.giapha.events.application.view;
