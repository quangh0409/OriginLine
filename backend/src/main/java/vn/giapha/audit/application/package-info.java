/**
 * Tầng <b>application</b> của context {@code audit}: use case service điều phối domain + port,
 * ranh giới {@code @Transactional}, DTO vào/ra.
 *
 * <p>Chỉ được phụ thuộc xuống {@code domain}; không biết gì về HTTP, JPA hay Cypher.</p>
 *
 * <p><b>Đây là mặt tiền công khai của context</b> ({@code @NamedInterface}): context khác gọi
 * {@code AuditTrailService} chứ không tự viết {@code INSERT INTO audit_log}. Ba thứ phải giống
 * hệt nhau ở mọi context — che dữ liệu Tầng 3, phân giải người thực hiện, và điền dấu vết HTTP —
 * nên chúng nằm ở service này.</p>
 */
@org.springframework.modulith.NamedInterface("application")
package vn.giapha.audit.application;
