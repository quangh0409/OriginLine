/**
 * Bounded context <b>events</b> — giỗ/lễ, sinh lịch nhắc trước 7/3/1 ngày; death_lunar là nguồn chân lý của ngày giỗ.
 *
 * <p>Khái niệm lõi: Event, EventType, ReminderPlan, ReminderJob.</p>
 *
 * <h2>Lối ghi (tạo/sửa/xoá mềm việc họ)</h2>
 * <p>Trưởng cành/chi/họ cấu hình lễ Tết, chạp mả, họp họ, khánh thành qua
 * {@code EventCommandService}. Ba điều không được tách rời khỏi lượt ghi ấy, và cả ba nằm trong
 * <b>một transaction</b>: kiểm quyền theo {@code ltree} (qua {@code membership.BranchScopeGuard} —
 * không có bản chép thứ hai), ghi {@code audit_log}, và dựng lại {@code reminder_job} khi ngày hoặc
 * phạm vi đổi. Bỏ việc thứ ba thì cả chi vẫn được nhắc theo ngày cũ, không lỗi và không log.</p>
 *
 * <p><b>Phạm vi sự kiện quyết định ai được nhắc.</b> Cờ {@code is_clan_level} và
 * {@code target_branch_id} chảy từ thân yêu cầu HTTP tới tận
 * {@code notification.RecipientDirectory.membersOfBranch}, nơi chúng thành một phép so
 * {@code ltree}. Nhắc nhầm cả họ cho việc của một chi là cách nhanh nhất để người ta tắt thông
 * báo.</p>
 *
 * <p><b>Phép quy đổi âm–dương chỉ có một bản:</b> {@code OccurrenceResolver} — kể cả chính sách
 * tháng nhuận biến mất và tháng thiếu. Giải lần hai ở lối ghi là cách chắc chắn nhất để màn lịch và
 * thông báo nhắc trả lời khác nhau về cùng một ngày.</p>
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. Context khác chỉ được gọi qua application service public
 * của context này hoặc qua domain event — không bao giờ chạm thẳng repository.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Events — Sự kiện &amp; giỗ chạp")
package vn.giapha.events;
