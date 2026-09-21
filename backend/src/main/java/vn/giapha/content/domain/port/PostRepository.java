package vn.giapha.content.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.content.domain.ContentStatus;
import vn.giapha.content.domain.Post;
import vn.giapha.shared.vo.BranchPath;

/**
 * Lưu trữ bài viết.
 *
 * <h2>Lọc phạm vi nằm trong SQL, không nằm trong Java</h2>
 * {@link #search} nhận {@code scopes} + {@code clanWide} và phải dịch chúng thành toán tử
 * {@code ltree} của Postgres. Hai lý do, và lý do thứ hai là lý do thật: nạp hết rồi lọc thì tốn,
 * và nạp hết thì <b>chỉ cần một chỗ quên lọc</b> là Trưởng chi Giáp nhìn thấy hàng đợi chờ duyệt
 * của Chi Ất. Đây là đúng khuôn {@code ChangeRequestRepository.pendingInScope} đã đặt.
 *
 * <p><b>Danh sách phạm vi rỗng nghĩa là không có phạm vi nào</b>, tuyệt đối không được diễn giải
 * ngược thành "thấy tất".</p>
 */
public interface PostRepository {

    Optional<Post> byId(UUID id);

    Post save(Post post);

    /**
     * Tìm bài theo trạng thái, đã áp <b>luật ai thấy gì</b> ngay trong câu truy vấn.
     *
     * <p>Luật ấy (design/07-checklist §2, hợp đồng REST của đợt này):</p>
     * <ul>
     *   <li>{@code PUBLISHED} — mọi thành viên đã có tài khoản;</li>
     *   <li>{@code DRAFT} — <b>chỉ chính tác giả</b>;</li>
     *   <li>{@code PENDING} — tác giả, và người duyệt <i>trong phạm vi</i>;</li>
     *   <li>{@code WITHDRAWN} — tác giả, và người duyệt trong phạm vi.</li>
     * </ul>
     *
     * @param status     lọc theo trạng thái; {@code null} = mọi trạng thái mà người gọi được thấy
     * @param callerUserId {@code app_user.id} của người gọi — căn cứ "bài của tôi"
     * @param scopes     các chi được giao duyệt; rỗng = không duyệt được chi nào
     * @param clanWide   phạm vi toàn dòng họ: bỏ qua {@code scopes}
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    List<Post> search(ContentStatus status, UUID callerUserId, List<BranchPath> scopes,
                      boolean clanWide, boolean mine, int limit, int offset);

    /** Đếm cho phân trang — <b>phải dùng cùng một mệnh đề WHERE</b> với {@link #search}. */
    long count(ContentStatus status, UUID callerUserId, List<BranchPath> scopes,
                boolean clanWide, boolean mine);

    /** Bài đã đăng, mới nhất trước — nguồn của trang chủ. Không phụ thuộc phạm vi chi. */
    List<Post> feed(int limit);

    /** Số bài đang chờ duyệt trong phạm vi — cho badge trên giao diện. */
    long countPendingInScope(List<BranchPath> scopes, boolean clanWide);
}
