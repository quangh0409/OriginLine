/**
 * Adapter SQL thuần của context {@code notification}.
 *
 * <p>Toàn bộ cơ chế <b>chống trùng</b> nằm ở đây, và nó dựa vào chỉ mục duy nhất riêng phần của
 * Postgres chứ không vào kỷ luật của tầng Java — xem {@code NotificationLogJdbcAdapter}.</p>
 */
package vn.giapha.notification.infrastructure.jdbc;
