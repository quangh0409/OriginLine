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
 * <h2>Hai loại mã mời, và chúng KHÔNG phải biến thể của nhau</h2>
 * <ul>
 *   <li><b>Mã mời cá nhân</b> ({@code invitation}, V15) — trỏ đích danh một nhân khẩu
 *       ({@code person_id NOT NULL}), dùng <b>một lần</b>, <b>không cần duyệt</b> vì Trưởng chi đã
 *       chỉ đích danh khi phát. Dành cho <i>các cụ lớn tuổi</i>: họ sẽ không tự đăng ký rồi tự tìm
 *       mình trên phả đồ, Trưởng chi làm hộ từ đầu tới cuối.</li>
 *   <li><b>Mã mời dòng họ</b> ({@code clan_invite_code}, V16) — cấp cho <b>cả họ</b>, dùng
 *       <b>nhiều lần</b>, chỉ mở hai cửa: đăng ký tài khoản và xem phả đồ. Dành cho <i>người trẻ
 *       và người ở xa</i>. Dùng xong <b>vẫn phải tự nhận mình</b> rồi chờ duyệt.</li>
 * </ul>
 *
 * <p>Vì mã dòng họ cấp cho cả họ nên <b>mã mời mới là ranh giới an toàn thật sự, không phải bước
 * duyệt</b>: ai cầm được mã là xem được danh sách người đang sống của dòng họ, và mã ấy <i>sẽ</i>
 * lan. Bốn chốt bắt buộc đi kèm — có hạn · thu hồi được · <b>đếm lượt dùng</b> · giới hạn tần suất
 * — nằm ở {@code ClanInviteService}. Chốt thứ ba là chốt dễ bỏ qua nhất và quan trọng nhất: không
 * có bộ đếm thì mã rò ra và mọi thứ trông vẫn bình thường.</p>
 *
 * <h2>Tự nhận mình trong phả — {@code PersonClaimService}</h2>
 * <p>Hai loại đơn: nhận một nhân khẩu <b>đã có</b>, và xin được <b>thêm vào phả</b> (con dâu mới
 * về, cháu mới sinh, nhánh ở xa nhiều đời). Loại thứ hai là <b>lối ghi vào phả</b>, không phải một
 * biểu mẫu liên hệ — nó cho một người chưa được duyệt khởi tạo việc thêm người vào gia phả, nên nó
 * mang năm ràng buộc riêng, ghi ở javadoc của service.</p>
 *
 * <p>Việc gắn {@code app_user ↔ person} đi qua <b>đúng một đường</b> cho cả hai luồng:
 * {@code InvitationLinker}. Hai phép kiểm trong đó — "tài khoản đã gắn người khác chưa" và "nhân
 * khẩu đã có tài khoản khác chưa" — có một phép là <i>đường đua</i>, chỉ đúng khi chạy cùng
 * transaction với lần ghi. Một bản chép ở nơi khác sẽ quên đúng chi tiết ấy.</p>
 *
 * <h2>Phụ thuộc với {@code genealogy} chỉ đi MỘT CHIỀU, và chiều đó là genealogy → membership</h2>
 * {@code genealogy.infrastructure.security.CallerIdentityJdbcAdapter} gọi
 * {@code MemberScopeService}, và {@code genealogy.application.ChangeRequestApplier} nghe
 * {@code membership.domain.event}. Hệ quả: <b>mọi</b> phụ thuộc {@code membership → genealogy} tạo
 * ra một chu trình và làm {@code ModularityTests} đỏ. Luồng duyệt đơn cần hai thứ của
 * {@code genealogy} (bộ dò trùng và đường ghi vào phả), nên nó <b>đảo phụ thuộc</b>: cổng
 * {@code ClaimScreeningPort} / {@code ClaimPersonWriterPort} khai ở {@code application}, hiện thực
 * nằm ở {@code genealogy.infrastructure.membership} — nơi cạnh phụ thuộc vốn đã có, nên đặt ở đó
 * không thêm một cạnh nào. {@code membership} không biết gì về chỗ ấy.
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. Context khác chỉ được gọi qua application service public
 * của context này hoặc qua domain event — không bao giờ chạm thẳng repository.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Membership — Tài khoản &amp; phân quyền")
package vn.giapha.membership;
