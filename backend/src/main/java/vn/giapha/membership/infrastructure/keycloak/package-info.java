/**
 * Adapter sang <b>Keycloak Admin REST API</b> — nơi duy nhất trong toàn hệ thống tạo được một tài
 * khoản đăng nhập.
 *
 * <p>Gói này hiện thực {@code membership.domain.port.IdentityProviderPort} và không ai ngoài
 * {@code membership} biết tới nó. Nếu một ngày realm được thay bằng nhà cung cấp danh tính khác,
 * hoặc Keycloak mọc thêm endpoint trả thẳng action token (hôm nay không có — xem
 * {@code SetPasswordTokenCodec}), thì chỉ gói này đổi.</p>
 *
 * <p>Bí mật của tài khoản dịch vụ <b>chỉ</b> đến từ biến môi trường
 * {@code GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET}; xem {@code infra/keycloak-admin-dev.example.txt}.</p>
 */
package vn.giapha.membership.infrastructure.keycloak;
