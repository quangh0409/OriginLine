/**
 * Bản chiếu JPA của bảng {@code post} và {@code honour} (V17), cùng hai adapter hiện thực port.
 *
 * <p>Luật "ai thấy gì" và phạm vi chi/ngành được dịch thành <b>native query</b> ở đây, dùng toán
 * tử {@code ltree[] @> ltree} của Postgres — không nạp hết rồi lọc trong Java. Xem javadoc của
 * {@code PostJpaRepository}.</p>
 */
package vn.giapha.content.infrastructure.jpa;
