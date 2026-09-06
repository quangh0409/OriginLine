/**
 * Bounded context <b>reporting</b> — báo cáo dân số học, tài chính, phân bố địa lý.
 *
 * <p>Khái niệm lõi: read model / projection — chỉ đọc, dựng từ domain event hoặc view.</p>
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. Context khác chỉ được gọi qua application service public
 * của context này hoặc qua domain event — không bao giờ chạm thẳng repository.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Reporting — Báo cáo")
package vn.giapha.reporting;
