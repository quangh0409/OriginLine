/**
 * Bản chiếu JPA của các bảng lõi phả hệ. <b>Nơi duy nhất</b> trong context {@code genealogy} được
 * mang annotation {@code jakarta.persistence}.
 *
 * <p>Lưu ý xuyên suốt: đồ thị Apache AGE <b>không</b> đi qua JPA — kiểu {@code agtype} không map
 * được. Cypher chạy bằng {@code JdbcTemplate} ở {@code genealogy.infrastructure.graph}, trên cùng
 * một {@code DataSource}, nên hai bên vẫn nằm gọn trong một transaction.</p>
 */
package vn.giapha.genealogy.infrastructure.jpa;
