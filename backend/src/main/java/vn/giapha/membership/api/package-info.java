/**
 * Tầng <b>api</b> của context {@code membership}: REST controller ({@code /api/v1}).
 *
 * <p>Chỉ gọi xuống application service; không chứa business logic; lỗi trả về dạng RFC 7807
 * Problem Details qua {@code vn.giapha.shared.api.GlobalExceptionHandler}.</p>
 */
package vn.giapha.membership.api;
