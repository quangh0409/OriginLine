/**
 * Kênh Web Push (VAPID / RFC 8292 + mã hoá payload RFC 8291), dựng bằng JCE có sẵn trong JDK.
 *
 * <p><b>Khoá riêng VAPID chỉ đến từ biến môi trường</b> — không ở repo, không ở cơ sở dữ liệu, không
 * ở log. Xem {@code WebPushProperties}.</p>
 *
 * <p>Chưa cấu hình khoá thì cả kênh tự tắt và ứng dụng vẫn chạy bình thường: thông báo in-app không
 * phụ thuộc vào nó.</p>
 */
package vn.giapha.notification.infrastructure.webpush;
