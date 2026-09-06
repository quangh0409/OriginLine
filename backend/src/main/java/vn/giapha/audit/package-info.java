/**
 * Bounded context <b>audit</b> — ghi vết ai đổi gì, khi nào, trước/sau; truy vết truy cập.
 *
 * <p>Khái niệm lõi: AuditLog, AuditInterceptor. Không bao giờ ghi giá trị nhạy cảm (Tier-3) vào log.</p>
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. Context khác chỉ được gọi qua application service public
 * của context này hoặc qua domain event — không bao giờ chạm thẳng repository.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Audit — Nhật ký thay đổi")
package vn.giapha.audit;
