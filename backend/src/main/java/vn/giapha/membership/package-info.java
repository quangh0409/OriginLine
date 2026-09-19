/**
 * Bounded context <b>membership</b> — tài khoản, vai trò, phạm vi chi/ngành, luồng duyệt đính chính.
 *
 * <p>Khái niệm lõi: AppUser, Role, BranchAssignment, ChangeRequest, Invitation. Map keycloak_sub → app_user → person; phân quyền soi ltree chứ không chỉ soi role.</p>
 *
 * <h2>Invitation — cửa duy nhất để một người thứ tư vào được hệ thống</h2>
 * <p>Dòng họ <b>mời</b> người vào; không ai tự đăng ký (realm Keycloak đặt
 * {@code registrationAllowed: false} và đó là mặc định vĩnh viễn). Lời mời <b>mang sẵn</b>
 * {@code personId}, nên người được mời không rơi vào trạng thái "chờ duyệt". Xem
 * {@code InvitationService} — nhất là ghi chú về phạm vi chi của Trưởng chi, vốn là <b>giả định
 * của chủ dự án chứ chưa phải phán quyết của Hội đồng Tộc biểu</b>.</p>
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. Context khác chỉ được gọi qua application service public
 * của context này hoặc qua domain event — không bao giờ chạm thẳng repository.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Membership — Tài khoản &amp; phân quyền")
package vn.giapha.membership;
