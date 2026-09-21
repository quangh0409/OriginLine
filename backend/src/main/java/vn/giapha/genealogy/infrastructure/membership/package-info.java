/**
 * Hai adapter hiện thực cổng mà {@code membership} khai báo cho luồng <b>"tự nhận mình trong
 * phả"</b>: {@code ClaimScreeningPort} (dò trùng/kỵ húy trước khi Trưởng chi duyệt) và
 * {@code ClaimPersonWriterPort} (ghi nhân khẩu mới khi lá đơn "Tôi chưa có trong phả" được duyệt).
 * Cả hai chỉ là bộ <b>dịch kiểu</b> — không một quyết định nghiệp vụ nào ở đây.
 *
 * <h2>Vì sao chúng nằm ở {@code genealogy} chứ không ở {@code membership}</h2>
 * {@code genealogy} <i>đã</i> phụ thuộc {@code membership} ở hai chỗ có lý do chính đáng —
 * {@code infrastructure.security.CallerIdentityJdbcAdapter} gọi {@code MemberScopeService}, và
 * {@code application.ChangeRequestApplier} nghe {@code ChangeRequestApprovedEvent}. Hệ quả:
 * <b>mọi</b> cạnh theo chiều {@code membership → genealogy} tạo ra một chu trình. Bản dựng đầu đặt
 * hai adapter ở {@code membership.infrastructure.genealogy} và {@code ModularityTests} đỏ ngay với
 * {@code "Cycle detected: Slice genealogy -> Slice membership -> Slice genealogy"}.
 *
 * <p>Lối thoát là <b>đảo phụ thuộc</b>: {@code membership} khai báo cổng, module khác hiện thực.
 * Đặt phần hiện thực ở đây là chỗ rẻ nhất — cạnh {@code genealogy → membership} vốn đã có nên
 * <b>không thêm một phụ thuộc nào</b>, và nó khớp đúng khuôn {@code infrastructure.security} đã
 * lập. (Một module cầu nối riêng, {@code vn.giapha.onboarding}, từng tồn tại chỉ vì đợt làm việc
 * sinh ra nó không được phép sửa {@code genealogy/**}; nó đã bị xoá.)</p>
 *
 * <h2>Vì sao hai lối tắt thông thường đều KHÔNG dùng được</h2>
 * <ol>
 *   <li><b>Đọc thẳng bảng của {@code genealogy} bằng SQL</b>, như
 *       {@code membership.infrastructure.invite.InviteeLookupJdbcAdapter} vẫn làm. Lối ấy hợp lệ
 *       cho việc <i>tra một cái tên</i>, nhưng ở đây nó có nghĩa là viết <b>bộ chấm điểm nghi trùng
 *       thứ hai</b> — triệu chứng sẽ là màn duyệt đơn bảo trùng còn màn thêm người bảo không,
 *       không ai truy ra được vì sao.</li>
 *   <li><b>Tự chèn nhân khẩu và quan hệ bằng SQL.</b> Phá thẳng bất biến nặng nhất của hệ thống:
 *       cạnh AGE và dòng {@code relationship} phải ghi trong cùng một transaction
 *       ({@code V2__core.sql} §2.4, ghim bởi {@code GraphRelationalConsistencyIT}).</li>
 * </ol>
 *
 * <h2>Điều tuyệt đối KHÔNG được làm ở đây</h2>
 * Thêm bất cứ thứ gì không phải phép dịch kiểu. Không luật nghiệp vụ, không phép kiểm quyền, không
 * bảng, không endpoint. Một gói "tiện tay" là cách một cây cầu biến thành một khu phố: lần sau ai
 * đó cần gọi chéo hai context sẽ thấy ở đây một chỗ trống và đặt logic vào, rồi ranh giới mà
 * {@code ModularityTests} canh sẽ đi vòng qua đúng chỗ này.
 */
package vn.giapha.genealogy.infrastructure.membership;
