/**
 * Bounded context <b>content</b> — <i>bài viết</i> của dòng họ và <i>vinh danh</i> gắn với nhân
 * khẩu (design/07-checklist §2).
 *
 * <h2>Vì sao hai aggregate ở CHUNG một context, dù vinh danh "không phải một loại bài viết"</h2>
 * Phán quyết ở §2 là về <b>mô hình dữ liệu</b>, và nó được tôn trọng nguyên vẹn: {@code Honour}
 * là bản ghi gắn với nhân khẩu, có bảng riêng, khoá ngoại cứng sang {@code person}, loại vinh
 * danh, năm, nơi cấp — tra cứu và thống kê theo chi được. Nó <b>không</b> là một hàng
 * {@code post} có {@code kind = 'VINH_DANH'}.
 *
 * <p>Nhưng ranh giới <i>context</i> là một câu hỏi khác: hai aggregate này dùng chung
 * <b>đúng một máy trạng thái duyệt</b> ({@link vn.giapha.content.domain.ContentStatus}), đúng một
 * luật phạm vi {@code ltree}, đúng một bộ mã lỗi, đúng một cách ghi {@code audit_log}. Tách làm
 * hai context nghĩa là chép máy trạng thái ấy ra hai bản — đúng thứ mà brief của đợt này cấm
 * ("đừng phát minh máy trạng thái thứ hai"), chỉ khác chỗ bản sao nằm ở một gói khác nên trông
 * như không phải bản sao.
 *
 * <h2>Phụ thuộc: context này là LÁ, không ai phụ thuộc ngược vào nó</h2>
 * {@code content → genealogy} (hai named interface) và {@code content → membership}
 * ({@code application}). Vì {@code genealogy} vốn đã phụ thuộc {@code membership}, đồ thị vẫn là
 * một DAG và <b>không</b> cần đảo phụ thuộc như {@code ClaimScreeningPort} đã phải làm. Điểm khác
 * biệt đáng ghi lại cho người đọc sau: việc đảo phụ thuộc ở {@code membership} không phải một
 * khuôn mẫu chung để bắt chước, nó là <i>cách chữa một chu trình</i>. Không có chu trình thì lối
 * gọi thẳng là lối đúng.
 *
 * <h2>Ba thứ context này KHÔNG tự làm</h2>
 * <ol>
 *   <li><b>Không tự quyết ai xem được gì.</b> Tên tác giả và quyền xem vinh danh của một người
 *       còn sống đều là câu trả lời của {@code genealogy.PersonDisclosureService}. Đọc thẳng bảng
 *       {@code person} để lấy tên cho nhanh là cách tên người còn sống rò ra qua một bài viết công
 *       khai — cùng loại lỗ với cái mà {@code RelationshipSummaryLoader} đã chặn ở màn "Quan hệ".</li>
 *   <li><b>Không tự tra vai trò và phạm vi chi.</b> {@code membership.MemberScopeService} +
 *       {@code BranchScopeGuard} là bản luật duy nhất; lớp {@code ContentAccessGuard} ở đây chỉ
 *       phân giải <i>chi đích</i> rồi giao lại.</li>
 *   <li><b>Không xoá cứng.</b> Gỡ bài là {@code status = WITHDRAWN}; gỡ vinh danh là cờ
 *       {@code is_deleted}. Không có một lệnh {@code DELETE} nào trên hai bảng của V17.</li>
 * </ol>
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do {@code domain}
 * khai báo. {@code domain} ở đây là POJO thuần — không một annotation Spring hay JPA nào.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Content — Bài viết &amp; vinh danh")
package vn.giapha.content;
