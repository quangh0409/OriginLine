/**
 * <b>Port</b> — các interface do tầng domain khai báo và tầng {@code infrastructure} hiện thực
 * (kiến trúc Hexagonal, chiều phụ thuộc luôn hướng vào trong).
 *
 * <p>Không interface nào ở đây được nhắc tới JPA, JDBC, Cypher, Redis hay HTTP. Nhờ vậy adapter
 * đồ thị có thể đổi từ Apache AGE sang thứ khác mà domain không biết — đúng điều kiện mà BA v2 §12
 * đặt ra khi giữ Neo4j làm phương án dự phòng.</p>
 */
package vn.giapha.genealogy.domain.port;
