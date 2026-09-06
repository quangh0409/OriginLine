/**
 * Bounded context <b>calendar</b> — quy đổi Âm–Dương theo thuật toán Hồ Ngọc Đức tại GMT+7, tiết khí, can chi.
 *
 * <p>Khái niệm lõi: LunarConverter, LunarDate, SolarTerm. Zero-dependency, đóng gói như thư viện dùng chung cho nhắc giỗ và sao hạn.</p>
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. Context khác chỉ được gọi qua application service public
 * của context này hoặc qua domain event — không bao giờ chạm thẳng repository.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Calendar — Lịch âm")
package vn.giapha.calendar;
