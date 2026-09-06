/**
 * Tầng <b>infrastructure</b> của context {@code calendar}: adapter hiện thực port của domain —
 * entity JPA + repository Spring Data, adapter Apache AGE (Cypher qua {@code JdbcTemplate}),
 * client MinIO/RabbitMQ/Redis, mapper domain ↔ entity.
 *
 * <p>Đây là nơi <b>duy nhất</b> được phép mang annotation JPA.</p>
 */
package vn.giapha.calendar.infrastructure;
