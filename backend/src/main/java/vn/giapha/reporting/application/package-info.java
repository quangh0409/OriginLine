/**
 * Tầng <b>application</b> của context {@code reporting}: use case service điều phối domain + port,
 * ranh giới {@code @Transactional}, DTO vào/ra, và nơi phát domain event.
 *
 * <p>Chỉ được phụ thuộc xuống {@code domain}; không biết gì về HTTP, JPA hay Cypher.</p>
 */
package vn.giapha.reporting.application;
