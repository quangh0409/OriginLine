/**
 * <b>Cổng thông tin công khai cho Khách vãng lai</b> — {@code /api/v1/public/**} (BA v2 §10).
 *
 * <h2>Vì sao có gói này</h2>
 * {@code SecurityConfig} đã mở {@code GET /api/v1/public/**} cho mọi người từ W0, nhưng suốt Giai
 * đoạn 1 <b>không controller nào ánh xạ vào đó</b>: khách vào xem một cụ đã khuất vẫn nhận
 * {@code 401}, trong khi BA v2 §10 nói người đã khuất là dữ liệu công khai <i>kể cả với Khách</i>.
 * Nửa "cổng thông tin dòng họ" của sản phẩm vì thế đang đóng. Gói này mở đúng nửa đó và không hơn.
 *
 * <h2>Bốn lớp phòng thủ, cố ý chồng lên nhau</h2>
 * <ol>
 *   <li><b>Ngữ cảnh Khách bị ép cứng</b> — {@link vn.giapha.genealogy.api.rest.public_.PublicGuestScope}
 *       xoá {@code SecurityContext} quanh mỗi lượt gọi, nên {@code PrivacyTierService.caller()}
 *       luôn trả {@code CallerContext.guest()} <i>dù người gọi có mang token gì đi nữa</i>. Nhờ
 *       vậy phản hồi công khai là <b>một bản duy nhất cho mọi người</b> — điều kiện tiên quyết để
 *       được phép đặt {@code Cache-Control: public}.</li>
 *   <li><b>Lọc ngay trong SQL</b> — tìm kiếm công khai ghim {@code isAlive = false} xuống tận
 *       {@code PersonSearchPort}, người còn sống không rời khỏi CSDL.</li>
 *   <li><b>{@code canSee} của tầng ứng dụng</b> — cả ba service được gọi ở đây
 *       ({@code PersonQueryService}, {@code PersonSearchService}, {@code TreeProjectionService})
 *       đều đi qua {@code PrivacyTierService.canSee} trước khi dựng view.</li>
 *   <li><b>Chốt chặn ở biên API</b> — {@link vn.giapha.genealogy.api.rest.public_.PublicVisibilityGuard}
 *       tự kiểm tra lại {@code alive} trên <b>từng</b> bản ghi sắp rời khỏi tiến trình.</li>
 * </ol>
 *
 * <p><b>Vì sao lớp 4 tồn tại dù lớp 1–3 đã đủ.</b> {@code PrivacyTierService.tierFor()} trả
 * {@code T1} cho Khách nhìn người còn sống <i>thay vì từ chối</i>; hiện an toàn chỉ vì mọi lối vào
 * đều gọi {@code canSee} trước. Đó là một bất biến <b>không được kiểu dữ liệu bảo vệ</b> — quên
 * một lần là rò rỉ im lặng. Gói này mở một loạt lối vào mới nên không dựa vào bất biến ấy: DTO
 * công khai <b>không có trường nào</b> để chứa dữ liệu Tầng 2/Tầng 3, và mọi phép dựng DTO đều đi
 * qua guard.</p>
 *
 * <h2>Cố ý KHÔNG mở cho Khách</h2>
 * <ul>
 *   <li>Mọi thao tác ghi — {@code SecurityConfig} chỉ mở {@code GET}, và ở đây chỉ có {@code @GetMapping}.</li>
 *   <li>GraphQL: client tự chọn độ sâu là công cụ quét phả hoàn hảo.</li>
 *   <li>Giỗ/sự kiện, thông báo, yêu cầu đính chính, nhật ký kiểm toán, phân công chi.</li>
 *   <li>Khối liên hệ, địa chỉ đầy đủ, {@code attributes} tự do, {@code privacyLevel},
 *       {@code version}, dấu vết kiểm toán — kể cả của người đã khuất.</li>
 *   <li>Bản ghi đã xoá mềm: {@code includeDeleted} thậm chí không phải là tham số ở đây.</li>
 * </ul>
 */
package vn.giapha.genealogy.api.rest.public_;
