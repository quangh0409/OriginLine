/**
 * Bounded context <b>membership</b> — tài khoản, vai trò, phạm vi chi/ngành, luồng duyệt đính chính.
 *
 * <p>Khái niệm lõi: AppUser, Role, BranchAssignment, ChangeRequest. Map keycloak_sub → app_user → person; phân quyền soi ltree chứ không chỉ soi role.</p>
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. Context khác chỉ được gọi qua application service public
 * của context này hoặc qua domain event — không bao giờ chạm thẳng repository.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Membership — Tài khoản &amp; phân quyền")
package vn.giapha.membership;
