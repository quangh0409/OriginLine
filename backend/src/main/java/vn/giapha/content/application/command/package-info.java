/**
 * <b>Command</b> của context {@code content}: tham số đầu vào của use case, ở dạng record bất biến.
 *
 * <p>Tách khỏi DTO của tầng {@code api} để hợp đồng HTTP đổi được mà use case không đổi — và để
 * một lối vào khác (GraphQL, job nền) dùng lại đúng use case ấy mà không phải dựng một
 * {@code @RequestBody} giả.</p>
 */
package vn.giapha.content.application.command;
