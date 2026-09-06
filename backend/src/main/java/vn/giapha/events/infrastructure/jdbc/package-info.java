/**
 * Adapter SQL thuần của context {@code events}.
 *
 * <p>Ba lý do một adapter nằm ở đây thay vì ở {@code jpa}: cần {@code INSERT ... ON CONFLICT} để
 * chống trùng, cần {@code FOR UPDATE SKIP LOCKED} để nhiều instance giành việc, hoặc cần đọc bảng
 * của context khác (nợ kiến trúc đã ghi rõ trong javadoc từng lớp).</p>
 */
package vn.giapha.events.infrastructure.jdbc;
