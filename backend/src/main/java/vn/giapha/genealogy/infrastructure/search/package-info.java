/**
 * Adapter <b>tra cứu tên</b> chạy trên full-text search của PostgreSQL: tìm kiếm nhân khẩu (FR-4.4)
 * và cảnh báo kỵ húy (FR-1.6).
 *
 * <p>Cả hai đều so trên cột không dấu {@code person_name.name_unaccented} và các chỉ mục của
 * {@code V6__search.sql}. Định nghĩa "bỏ dấu" chỉ tồn tại ở một nơi — hàm {@code vn_unaccent} trong
 * CSDL. Đừng viết thêm một bản bỏ dấu bằng Java để so sánh: hai bản lệch nhau thì cảnh báo kỵ húy
 * lúc có lúc không, mà không có cách nào tái hiện.</p>
 *
 * <p>Dùng {@code NamedParameterJdbcTemplate} thay vì JPA vì đây là truy vấn đọc dùng những thứ
 * Hibernate không mô hình hoá được: {@code tsvector}, toán tử trigram, {@code ltree <@},
 * {@code DISTINCT ON}.</p>
 */
package vn.giapha.genealogy.infrastructure.search;
