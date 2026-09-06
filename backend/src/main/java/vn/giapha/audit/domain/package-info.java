/**
 * Tầng <b>domain</b> của context {@code audit}: value object của một dòng nhật ký, bộ che dữ liệu
 * Tầng 3, và các <b>port</b> mà tầng infrastructure phải hiện thực.
 *
 * <p><b>Quy tắc bất di bất dịch:</b> POJO thuần — không {@code @Entity}, không {@code @Component},
 * không import {@code org.springframework.*} hay {@code jakarta.persistence.*}. Adapter nằm ở
 * {@code vn.giapha.audit.infrastructure}.</p>
 *
 * <p>Được đánh dấu {@code @NamedInterface} vì {@code AuditTrailService} nhận và trả các kiểu ở
 * đây ({@code AuditEntry}, {@code AuditAction}); context gọi audit phải nhìn thấy chúng.</p>
 */
@org.springframework.modulith.NamedInterface("domain")
package vn.giapha.audit.domain;
