/**
 * Tiện ích bảo mật dùng chung: người dùng hiện tại lấy từ JWT Keycloak
 * ({@code keycloak_sub → app_user → person}).
 *
 * <p>Giai đoạn 1 chỉ cần định danh + role thô + bộ lọc phân tầng hiển thị.
 * {@code BranchScope} / {@code @RequiresBranch} soi {@code ltree} thuộc W6–Giai đoạn 2,
 * sẽ bổ sung vào đúng package này.</p>
 */
@org.springframework.modulith.NamedInterface("security")
package vn.giapha.shared.security;
