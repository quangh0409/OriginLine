/**
 * Tầng <b>application</b> của context {@code membership}: use case service điều phối domain + port,
 * ranh giới {@code @Transactional}, DTO vào/ra, và nơi phát domain event.
 *
 * <p>Chỉ được phụ thuộc xuống {@code domain}; không biết gì về HTTP, JPA hay Cypher.</p>
 *
 * <p><b>Đây là mặt tiền công khai của context</b> ({@code @NamedInterface}). Hai lớp quan trọng
 * nhất với phần còn lại của hệ thống:</p>
 * <ul>
 *   <li>{@code MemberScopeService} — trả lời "người đang gọi là ai, ứng với nhân khẩu nào, được
 *       đụng vào những chi nào". Đây là chỗ mà {@code CallerIdentityJdbcAdapter} bên
 *       {@code genealogy} phải gọi tới thay cho SQL tạm của W2.</li>
 *   <li>{@code BranchScopeGuard} — kiểm quyền hai chiều (vai trò × phạm vi {@code ltree}).</li>
 * </ul>
 *
 * <p>Vai trò trong Keycloak chỉ là <b>một nửa</b> phân quyền; nửa còn lại nằm ở
 * {@code branch_assignment} và không suy ra được từ token. Gom cả hai vào một chỗ để luật chỉ có
 * một bản, thay vì mỗi context tự tra bảng rồi tự diễn giải.</p>
 */
@org.springframework.modulith.NamedInterface("application")
package vn.giapha.membership.application;
