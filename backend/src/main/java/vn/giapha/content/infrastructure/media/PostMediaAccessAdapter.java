package vn.giapha.content.infrastructure.media;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.content.application.ContentAccessGuard;
import vn.giapha.content.domain.ContentStatus;
import vn.giapha.content.domain.Post;
import vn.giapha.content.domain.port.PostRepository;
import vn.giapha.media.domain.port.PostMediaAccessPort;
import vn.giapha.membership.application.MemberScopeView;
import vn.giapha.shared.vo.BranchPath;

/**
 * {@code content} trả lời cho {@code media}: ai xem/sửa được tệp đính kèm của một bài.
 *
 * <h2>Đây là chỗ QUYẾT ĐỊNH SỐ 1 CỦA CHỦ DỰ ÁN được thi hành</h2>
 * <b>Ảnh trong bài đi theo quyền của BÀI</b>, không theo bộ lọc nhóm trường của từng người có mặt
 * trong ảnh. Vì vậy {@link #canView} trả lời <i>đúng</i> câu "người này có đọc được bài ấy không"
 * và không hỏi thêm gì nữa — không tra {@code person}, không gọi {@code PrivacyTierService},
 * không nhìn vào nội dung tấm ảnh. Một bài đã đăng thì mọi thành viên xem được ảnh trong đó, kể cả
 * khi trong ảnh có người đang để {@code birthDetailAndPhoto} = {@code PRIVATE}.
 *
 * <p>Cái giá của quyết định ấy được trả bằng <b>đường báo gỡ</b> ({@code MediaReportService}).
 * Hai thứ là một cặp: nếu một ngày đường gỡ bị bỏ, dòng {@link #canView} dưới đây phải được xét
 * lại cùng lúc.</p>
 *
 * <h2>Chiều phụ thuộc</h2>
 * Lớp này nằm ở {@code content} và hiện thực một giao diện của {@code media}, nên mũi tên vẫn là
 * {@code content → media}. Không có chu trình. Cùng cách chữa mà {@code ClaimScreeningPort} đã
 * dùng giữa {@code genealogy} và {@code membership}.
 */
@Component
public class PostMediaAccessAdapter implements PostMediaAccessPort {

    private static final Logger log = LoggerFactory.getLogger(PostMediaAccessAdapter.class);

    private final PostRepository posts;
    private final ContentAccessGuard access;

    public PostMediaAccessAdapter(PostRepository posts, ContentAccessGuard access) {
        this.posts = posts;
        this.access = access;
    }

    /**
     * Bản sao <b>bằng Java</b> của mệnh đề {@code PostJpaRepository.VISIBLE_TO_CALLER}.
     *
     * <p>Chép một luật ra hai nơi là thứ dự án này vẫn cấm, nên phải nói rõ vì sao ở đây nó là lựa
     * chọn đúng: mệnh đề kia là một đoạn SQL chạy trên <i>một tập</i> bài, dùng phép {@code @>} của
     * {@code ltree} ngay trong câu truy vấn để trang chủ không phải nạp bài của chi khác vào bộ
     * nhớ. Nó không gọi lại được cho <i>một</i> khoá mà không viết thêm một câu truy vấn thứ hai
     * với đúng hình dạng ấy. Bản ở đây gọi {@code ContentAccessGuard.canReview}, tức vẫn dùng
     * <b>cùng một</b> {@code BranchScopeGuard}; thứ được chép lại chỉ là bốn nhánh trạng thái, và
     * {@code PostMediaVisibilityTest} ghim rằng bốn nhánh ấy trả lời giống hệt câu SQL.</p>
     *
     * <ul>
     *   <li>{@code PUBLISHED} — mọi thành viên;</li>
     *   <li>bài của chính mình — luôn thấy;</li>
     *   <li>{@code PENDING}/{@code WITHDRAWN} — người duyệt trong phạm vi {@code ltree};</li>
     *   <li>{@code DRAFT} của người khác — <b>không ai</b>, kể cả Hội đồng.</li>
     * </ul>
     */
    @Override
    public boolean canView(UUID postId) {
        Optional<Post> found = posts.byId(postId);
        if (found.isEmpty()) {
            return false;
        }
        Post post = found.get();
        MemberScopeView caller = access.caller();
        if (caller.appUserId() == null) {
            // Khach vang lai. Bucket rieng tu va khong co loi doc an danh nao — BA v2 §10.
            return false;
        }
        if (post.status() == ContentStatus.PUBLISHED || post.isAuthor(caller.appUserId())) {
            return true;
        }
        if (post.status() == ContentStatus.DRAFT) {
            return false;
        }
        return access.canReview(caller, access.pathOfBranch(post.branchId()));
    }

    /**
     * Gắn/gỡ tệp: <b>chỉ tác giả, chỉ khi bài đang ở {@code DRAFT}</b>.
     *
     * <p>Hẹp hơn {@link #canView} rất nhiều, và có lý do: luật "bài đã đăng thì không sửa" của
     * {@code Post.edit} nói rằng chữ ký duyệt của Trưởng chi phải có nghĩa. Cho phép đổi ảnh sau
     * khi duyệt là một lối vòng qua đúng luật ấy — người duyệt đọc một bài có ba tấm ảnh lễ giỗ
     * rồi bấm duyệt, tác giả thay ba tấm khác vào, và chữ ký vẫn còn đó.</p>
     *
     * <p>Đây cũng là chỗ chặn ca <b>người ngoài phạm vi gắn tệp vào bài của chi khác</b>: không
     * phải tác giả thì không gắn được, dù có vai gì.</p>
     */
    @Override
    public boolean canAttach(UUID postId) {
        Optional<Post> found = posts.byId(postId);
        if (found.isEmpty()) {
            return false;
        }
        Post post = found.get();
        MemberScopeView caller = access.caller();
        boolean allowed = post.isAuthor(caller.appUserId()) && post.status() == ContentStatus.DRAFT;
        if (!allowed) {
            log.debug("Tu choi gan tep vao bai {}: tac gia={} trang thai={}",
                    postId, post.isAuthor(caller.appUserId()), post.status());
        }
        return allowed;
    }

    /**
     * Chi của bài — <b>giá trị đã chụp lúc tạo nháp</b> ({@code post.branch_id}), không phải chi
     * hiện tại của tác giả.
     *
     * <p>Đó là ngữ nghĩa mà V17 đã chọn cho hàng đợi duyệt bài, và đơn báo gỡ một tấm ảnh trong
     * bài phải đi theo <i>cùng</i> hàng đợi ấy — nếu không, một bài nằm ở hàng đợi chi Giáp mà đơn
     * báo ảnh của nó lại nằm ở hàng đợi chi Ất.</p>
     */
    @Override
    public BranchPath branchOf(UUID postId) {
        return posts.byId(postId).map(p -> access.pathOfBranch(p.branchId())).orElse(null);
    }
}
