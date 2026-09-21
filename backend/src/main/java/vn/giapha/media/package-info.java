/**
 * Bounded context <b>media</b> — kho đối tượng, ảnh/video của bài viết, ảnh chân dung nhân khẩu.
 *
 * <h2>Vì sao là một context riêng, không phải {@code shared.storage}</h2>
 * Vì thứ được thêm vào đợt này <b>không phải một tiện ích</b>. {@code shared} là nơi của value
 * object và của mã mà mọi context đều có quyền phụ thuộc vào mà không ai phải hỏi — một
 * {@code BranchPath}, một {@code DomainException}. Nếu đặt kho tệp ở đó thì mọi module đều tự phát
 * được URL đã ký, và phép kiểm quyền duy nhất bảo vệ ảnh chân dung của người còn sống sẽ nằm
 * trong <i>trí nhớ của người viết mã</i> chứ không trong kiểu.
 *
 * <p>Thứ ta đang thêm có đầy đủ hình dạng của một context: một vòng đời có trạng thái
 * ({@code PENDING → READY → PURGED}), một máy quyết định ai xem được gì, một hàng đợi duyệt
 * (báo gỡ) với phép so {@code ltree}, một công việc đêm, ba bảng, và một bộ trần có người ký tên.
 * Cái nằm ở {@code shared} chỉ có thể là {@code ObjectStoragePort} — nhưng port ấy vô nghĩa nếu
 * tách khỏi những luật quanh nó, nên nó ở lại đây, và lớp <b>duy nhất</b> trong cả cây nguồn được
 * phép nhắc tới SDK MinIO là hiện thực của nó.
 *
 * <h2>Ba quyết định của chủ dự án, và chỗ mỗi quyết định được thi hành</h2>
 * <ol>
 *   <li><b>Ảnh trong bài đi theo quyền của BÀI</b>, không theo bộ lọc nhóm trường của từng người
 *       có mặt trong ảnh — thi hành ở {@code content.infrastructure.media.PostMediaAccessAdapter}.
 *       Nửa còn lại của nó là {@link vn.giapha.media.application.MediaReportService} (đường báo
 *       gỡ); hai thứ ấy là một cặp và phải được xét lại cùng lúc.</li>
 *   <li><b>Video nhận tệp gốc, không chuyển mã</b> — không ffmpeg, không ảnh bìa sinh phía máy
 *       chủ. Trần thời lượng vẫn thật, vì
 *       {@link vn.giapha.media.domain.VideoHeaderProbe} đọc nó từ header container.</li>
 *   <li><b>Ảnh chân dung đi qua nhóm trường {@code birthDetailAndPhoto} đã có</b> — không luật
 *       riêng tư mới; thi hành ở {@code genealogy.infrastructure.media.PersonAvatarAccessAdapter}.</li>
 * </ol>
 *
 * <h2>Phụ thuộc: context này là LÁ</h2>
 * {@code content → media} và {@code genealogy → media}; {@code media → membership}
 * ({@code application}) + {@code audit}. {@code media} <b>không</b> gọi ngược sang {@code content}
 * hay {@code genealogy} ở cấp Java — nó hỏi qua hai cổng đảo phụ thuộc
 * ({@code PostMediaAccessPort}, {@code PersonAvatarAccessPort}) và báo bằng một domain event. Nhờ
 * vậy đồ thị vẫn là DAG.
 *
 * <p>Bốn lớp Hexagonal, phụ thuộc một chiều {@code api → application → domain};
 * {@code infrastructure} hiện thực các port do {@code domain} khai. {@code domain} là POJO thuần.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Media — Kho tệp, ảnh &amp; video")
package vn.giapha.media;
