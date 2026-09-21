package vn.giapha.media.domain.port;

import java.util.UUID;
import vn.giapha.shared.vo.BranchPath;

/**
 * <b>Cổng đảo phụ thuộc:</b> {@code media} hỏi, module sở hữu bản ghi trả lời.
 *
 * <h2>Vì sao phải đảo, và vì sao ba quyết định của chủ dự án ép nó phải thế</h2>
 * Ba quyết định đều nói cùng một điều: <i>quyền xem một tệp không nằm ở tệp</i>.
 * <ol>
 *   <li><b>Ảnh trong bài đi theo quyền của BÀI</b>, không theo bộ lọc nhóm trường của từng người
 *       có mặt trong ảnh. Câu "người gọi này có xem được bài ấy không" chỉ {@code content} trả lời
 *       được — nó biết trạng thái bài, chi đã chụp, và ai là tác giả.</li>
 *   <li><b>Ảnh chân dung đi qua nhóm trường {@code birthDetailAndPhoto} đã có</b>, không có luật
 *       riêng tư mới. Câu ấy chỉ {@code genealogy} trả lời được — bản luật duy nhất nằm ở
 *       {@code PrivacyTierService}.</li>
 * </ol>
 * Nếu {@code media} tự trả lời, nó phải chép lại cả hai bản luật, và triệu chứng sẽ đúng như cái
 * mà {@code genealogy} đã ghi lại khi {@code dataimport} chép danh sách trường Tầng 1: <b>một màn
 * hình che, màn hình kia không, và cả hai đều "đúng" theo bản luật của mình</b>.
 *
 * <h2>Chiều phụ thuộc, và vì sao nó vẫn là một DAG</h2>
 * {@code content → media} và {@code genealogy → media} (để gắn/gỡ tệp). Nếu {@code media} gọi
 * ngược sang hai context ấy thì có chu trình, và {@code ModularityTests} đỏ. Đảo lại: {@code media}
 * chỉ khai <b>giao diện</b> này; hai hiện thực nằm ở {@code content.infrastructure.media} và
 * {@code genealogy.infrastructure.media}, nên mọi mũi tên vẫn chạy về phía {@code media}. Cùng
 * cách chữa mà {@code membership.ClaimScreeningPort} đã dùng.
 *
 * <h2>Hai giao diện con, KHÔNG một enum đi qua ranh giới</h2>
 * Giao diện gốc này <b>không</b> mang nhãn. Thứ mang nhãn là hai giao diện con
 * {@link PostMediaAccessPort} và {@link PersonAvatarAccessPort} — mỗi loại chủ sở hữu một giao
 * diện riêng, thay vì một giao diện chung kèm một phương thức {@code ownerType()} trả về
 * {@code MediaOwnerType}. Lý do là luật cứng của dự án: <b>không kiểu {@code domain} nào được gắn
 * nhãn</b>, mà một enum trả về từ phương thức của một giao diện đã gắn nhãn thì chính nó cũng
 * phải gắn nhãn. Tách làm hai còn được thêm một thứ: sai sót trong lúc nối dây trở thành lỗi biên
 * dịch chứ không phải một nhánh {@code switch} thiếu ca.
 *
 * <p><b>Mặc định là ĐÓNG.</b> Không có hiện thực nào cho một loại chủ sở hữu thì
 * {@code MediaViewService} trả lời "không xem được", chứ không phải "không biết nên cho qua".
 * Cùng luật với "chi rỗng không phải chi công cộng" của {@code BranchScopeGuard}: dữ liệu thiếu
 * phải làm quyền <b>hẹp lại</b>.</p>
 */
public interface MediaOwnerAccessPort {

    /**
     * Người gọi <b>hiện tại</b> có được xem tệp gắn vào bản ghi {@code ownerId} không.
     *
     * <p>Không nhận tham số người gọi: mỗi hiện thực tự hỏi ngữ cảnh bảo mật của chính context
     * mình ({@code MemberScopeService} / {@code PersonDisclosureService}). Truyền một
     * {@code MemberScopeView} vào đây sẽ buộc {@code media} phải biết kiểu ấy và mời gọi bên gọi
     * tự dựng một phạm vi rộng hơn thật.</p>
     */
    boolean canView(UUID ownerId);

    /**
     * Người gọi hiện tại có được <b>gắn/gỡ</b> tệp trên bản ghi {@code ownerId} không.
     *
     * <p>Hẹp hơn {@link #canView} rất nhiều: với bài viết là "chính tác giả, và bài đang ở
     * {@code DRAFT}"; với nhân khẩu là phép kiểm ghi theo phạm vi {@code ltree} đã có. Đây là chỗ
     * chặn ca "người ngoài phạm vi gắn tệp vào bài của chi khác".</p>
     */
    boolean canAttach(UUID ownerId);

    /**
     * Chi {@code ltree} của bản ghi chủ sở hữu; {@code null} khi nó chưa gắn chi nào.
     *
     * <p>Dùng cho <b>một</b> việc: hàng đợi báo gỡ. Người duyệt một đơn báo gỡ phải có thẩm quyền
     * trong đúng chi của bản ghi mang tấm ảnh ấy — không thì Trưởng chi Ất gỡ được ảnh trong bài
     * của chi Giáp. {@code media} không tự tra được con đường này, vì {@code post.branch_id} là
     * ảnh chụp lúc tạo nháp còn chi của một nhân khẩu là giá trị <i>hiện tại</i>; hai ngữ nghĩa
     * khác nhau, và mỗi context giữ ngữ nghĩa của mình.</p>
     *
     * <p>{@code null} làm quyền <b>hẹp lại</b>: chỉ vai toàn dòng họ xử được, đúng luật "chi rỗng
     * không phải chi công cộng".</p>
     */
    BranchPath branchOf(UUID ownerId);
}
