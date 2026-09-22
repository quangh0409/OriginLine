/**
 * Adapter của luồng <b>cấp lại liên kết đặt mật khẩu</b> (Trưởng chi/Hội đồng cấp cho một thành
 * viên trong phạm vi của mình).
 *
 * <p>Đúng một lớp: kho lưu {@code password_reset_grant} bằng JDBC. Lý do không dùng JPA nằm ở
 * javadoc của lớp ấy — phép canh "một lần" phải là một lệnh ghi có điều kiện, và thứ tự
 * thu-hồi-rồi-ghi không được phép bị Hibernate sắp xếp lại.</p>
 *
 * <p>Phần Keycloak của luồng này nằm ở {@code ..infrastructure.keycloak.KeycloakPasswordResetAdapter},
 * cạnh adapter danh tính kia.</p>
 */
package vn.giapha.membership.infrastructure.password;
