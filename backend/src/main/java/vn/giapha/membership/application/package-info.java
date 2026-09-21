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
 * <p>Và từ V16, hai <b>cổng SPI</b> mà một module khác hiện thực:</p>
 * <ul>
 *   <li>{@code ClaimScreeningPort} — dò trùng cho đơn "tôi chưa có trong phả";</li>
 *   <li>{@code ClaimPersonWriterPort} — ghi nhân khẩu mới khi Trưởng chi duyệt đơn ấy.</li>
 * </ul>
 *
 * <p><b>Hai cổng này nằm ở {@code application} chứ không ở {@code domain.port}, và đó là hệ quả
 * của hình dạng đồ thị module chứ không phải một sơ suất.</b> {@code genealogy} <i>đã</i> phụ
 * thuộc vào {@code membership}, nên chiều ngược lại tạo chu trình — đã kiểm chứng bằng
 * {@code ModularityTests}, không phải suy đoán. Lối thoát duy nhất là đảo phụ thuộc, và bên hiện
 * thực cần <i>nhìn thấy</i> cổng; chỉ gói này mang {@code @NamedInterface}. Đặt cổng ở
 * {@code domain.port} rồi gắn nhãn cho nó là đưa siêu dữ liệu framework vào tầng domain, đúng thứ
 * mà kỷ luật "domain là POJO thuần" của dự án cấm.</p>
 *
 * <p>Vai trò trong Keycloak chỉ là <b>một nửa</b> phân quyền; nửa còn lại nằm ở
 * {@code branch_assignment} và không suy ra được từ token. Gom cả hai vào một chỗ để luật chỉ có
 * một bản, thay vì mỗi context tự tra bảng rồi tự diễn giải.</p>
 */
@org.springframework.modulith.NamedInterface("application")
package vn.giapha.membership.application;
