/**
 * <b>Port</b> của context {@code audit}.
 *
 * <p>{@code @NamedInterface} vì {@code AuditActorPort} được <i>hiện thực từ bên ngoài</i>: context
 * {@code membership} sở hữu bảng {@code app_user} nên nó cung cấp adapter. Đảo chiều như vậy giữ
 * phụ thuộc một chiều {@code membership → audit} và tránh vòng lặp — xem javadoc của cổng.</p>
 */
@org.springframework.modulith.NamedInterface("port")
package vn.giapha.audit.domain.port;
