/**
 * Adapter đồ thị <b>Apache AGE</b> — Cypher chạy bên trong PostgreSQL.
 *
 * <p>Vì sao dùng {@code JdbcTemplate} chứ không JPA: kiểu {@code agtype} của AGE không map được
 * qua Hibernate. Bù lại, {@code JdbcTemplate} chạy trên <b>cùng một {@code DataSource}</b> nên
 * lệnh ghi cạnh và lệnh ghi bản chiếu {@code relationship} nằm gọn trong một transaction — đúng
 * bất biến số một của W2.</p>
 *
 * <p>Điều kiện tiên quyết ở tầng hạ tầng: mỗi connection phải chạy
 * {@code LOAD 'age'; SET search_path = ag_catalog, "$user", public;}. Việc này đã được đặt ở
 * {@code spring.datasource.hikari.connection-init-sql}. Thiếu nó thì truy vấn Cypher hỏng
 * <b>rời rạc</b> — connection cũ trong pool vẫn chạy, connection mới thì lỗi.</p>
 */
package vn.giapha.genealogy.infrastructure.graph;
