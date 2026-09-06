/**
 * Tầng <b>api</b> của context {@code events}: REST controller ({@code /api/v1}) và GraphQL resolver.
 *
 * <p>Chỉ gọi xuống application service; không chứa business logic; lỗi trả về dạng
 * RFC 7807 Problem Details qua {@code vn.giapha.shared.api.GlobalExceptionHandler}.</p>
 */
package vn.giapha.events.api;
