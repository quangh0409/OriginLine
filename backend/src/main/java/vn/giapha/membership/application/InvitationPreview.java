package vn.giapha.membership.application;

import java.time.Instant;
import java.util.UUID;
import vn.giapha.shared.vo.BranchPath;

/**
 * Nội dung màn "Lời mời này dành cho ai" — thứ <b>người chưa đăng nhập</b> nhìn thấy.
 *
 * <h2>Đây là bề mặt rò rỉ duy nhất của cả luồng</h2>
 * Mỗi trường ở đây là một mẩu dữ liệu mà người nhặt được tờ phiếu cũng đọc được. Đừng thêm trường
 * thứ n+1 mà không nêu được lý do:
 *
 * <ul>
 *   <li>{@link Invitee#displayName()} — Tầng 1 của một người đang sống. Ngoại lệ có chủ ý; không có
 *       nó thì nút "Không phải tôi" mất nghĩa và cả luồng vô nghĩa (design 06 §5.3).</li>
 *   <li>{@link Invitee#generation()}, khối chi — để người nhận nhận ra "họ nhà mình". Khối chi
 *       <b>không</b> mở thêm gì: {@code PublicPersonDto} của cổng công khai vốn đã chở nguyên một
 *       {@code BranchRef} cho Khách.</li>
 *   <li>{@link Inviter#displayName()} — <b>lời mời đến từ một con người, không từ một hệ thống.</b>
 *       Một tin nhắn từ đầu số lạ kèm đường dẫn đúng là hình dạng của tin lừa đảo mà người lớn tuổi
 *       được dặn phải xoá; tên người mời là thứ phân biệt.</li>
 *   <li>{@link Inviter#clanTitle()} — chức danh <b>dòng tộc</b> ("Tộc trưởng", "Trưởng Chi Giáp"),
 *       suy từ {@code branch.head_person_id}. <b>Không</b> phải vai kỹ thuật: không ai trong họ tự
 *       giới thiệu mình là {@code BRANCH_HEAD}.</li>
 * </ul>
 *
 * <h2>Hai thứ cố ý KHÔNG có</h2>
 * <ol>
 *   <li><b>Khoá nhân khẩu.</b> Nó là khoá tra cứu ở mọi endpoint khác, tức một khoá nối bền vững
 *       trao cho người chưa xác thực — trong khi màn hình chẳng dùng tới nó. Lệnh <i>nhận</i> mới
 *       trả {@code personId}, và lúc ấy người gọi đã có token.</li>
 *   <li><b>Danh xưng với người mời</b> ("Con dâu ông Nguyễn Văn Bốn"). Giai đoạn 1 không tính được
 *       nó ở đây — xem {@code InviteeLookupPort} để biết ba lý do. Trường tương ứng ở API
 *       <b>vắng mặt</b>, không phải {@code null} rỗng nghĩa.</li>
 * </ol>
 *
 * <p>Và không có năm sinh, nghề nghiệp, nơi ở, điện thoại hay ảnh.</p>
 */
public record InvitationPreview(String clanName, Inviter inviter, Invitee invitee,
                                Instant expiresAt) {

    /**
     * Người phát lời mời.
     *
     * <p>{@link #displayName()} là tên <b>trần</b>, không kính ngữ. Kính ngữ ("ông", "bà", "cụ")
     * phụ thuộc quan hệ họ hàng giữa người mời và người đọc, tức thuộc bộ luật danh xưng — thứ mà
     * endpoint này cố ý không gọi tới. Giao diện <b>không được</b> tự thêm kính ngữ: đoán sai một
     * chữ "ông" cho một người phụ nữ là đúng loại lỗi mà quy tắc "quan hệ là việc của máy chủ" sinh
     * ra để chặn. Sự tôn kính trên màn này do {@link #clanTitle()} gánh.</p>
     */
    public record Inviter(String displayName, String clanTitle) {
    }

    /**
     * Người được mời — mức tối thiểu để tự nhận ra mình, không hơn.
     *
     * <p>{@code branch} có thể {@code null} với nhân khẩu chưa gắn chi.</p>
     */
    public record Invitee(String displayName, Integer generation, Branch branch) {
    }

    /** Khối chi/ngành, khớp đúng {@code BranchRef} đã có trong hợp đồng. */
    public record Branch(UUID id, String name, BranchPath path, String region) {
    }
}
