/**
 * Bounded context <b>fund</b> — quỹ họ, sổ vàng công đức, thu–chi minh bạch.
 *
 * <p>Khái niệm lõi: FundAccount, FundTransaction, Donation.</p>
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. Context khác chỉ được gọi qua application service public
 * của context này hoặc qua domain event — không bao giờ chạm thẳng repository.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Fund — Quỹ họ")
package vn.giapha.fund;
