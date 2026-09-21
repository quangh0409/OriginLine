package vn.giapha.media.application;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import vn.giapha.media.domain.MediaOwnerType;
import vn.giapha.media.domain.MediaProblemCodes;
import vn.giapha.media.domain.port.MediaOwnerAccessPort;
import vn.giapha.media.domain.port.PersonAvatarAccessPort;
import vn.giapha.media.domain.port.PostMediaAccessPort;
import vn.giapha.membership.application.BranchScopeGuard;
import vn.giapha.membership.application.MemberScopeService;
import vn.giapha.membership.application.MemberScopeView;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.vo.BranchPath;

/**
 * Cổng phân quyền của context {@code media}.
 *
 * <h2>Lớp này KHÔNG chứa một luật phân quyền nào — nó chỉ định tuyến câu hỏi</h2>
 * Cùng khuôn {@code content.ContentAccessGuard}: phép so vai × phạm vi {@code ltree} nằm ở
 * {@code membership.BranchScopeGuard}, một bản duy nhất cho cả hệ thống. Thứ riêng ở đây là việc
 * <b>chọn đúng người để hỏi</b>: quyền trên một tệp luôn là quyền trên <i>bản ghi mang tệp ấy</i>,
 * và bản ghi ấy thuộc về một context khác.
 *
 * <h2>Mặc định ĐÓNG khi không có ai trả lời</h2>
 * {@link ObjectProvider} chứ không phải tiêm bắt buộc, vì hai cổng chủ sở hữu do {@code content}
 * và {@code genealogy} hiện thực — và một bài kiểm lát cắt hẹp có thể nạp {@code media} mà không
 * nạp hai context ấy. Khi vắng, {@link #canView} trả {@code false} và {@link #requireAttach} ném
 * {@code 403}. <b>Không bao giờ</b> "không biết thì cho qua": đó chính là hình dạng của lỗ hổng mà
 * luật "chi rỗng không phải chi công cộng" đã dựng lên để chặn.
 */
@Component
public class MediaAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(MediaAccessGuard.class);

    private final MemberScopeService scopes;
    private final BranchScopeGuard guard;
    private final ObjectProvider<PostMediaAccessPort> postAccess;
    private final ObjectProvider<PersonAvatarAccessPort> avatarAccess;

    public MediaAccessGuard(MemberScopeService scopes, BranchScopeGuard guard,
                            ObjectProvider<PostMediaAccessPort> postAccess,
                            ObjectProvider<PersonAvatarAccessPort> avatarAccess) {
        this.scopes = scopes;
        this.guard = guard;
        this.postAccess = postAccess;
        this.avatarAccess = avatarAccess;
    }

    public MemberScopeView caller() {
        return scopes.currentScope();
    }

    /**
     * Người gọi đã có tài khoản trong hệ thống.
     *
     * <p><b>Khách vãng lai không tải lên được gì</b>, và cũng không xem được tệp nào — bucket để
     * private và mọi lối đọc đều đi qua {@link #canView}. Đây là hệ quả trực tiếp của BA v2 §10:
     * khách không thấy người còn sống, nên càng không thấy ảnh của họ.</p>
     */
    public MemberScopeView requireProvisioned() {
        MemberScopeView caller = caller();
        guard.requireProvisionedAccount(caller);
        return caller;
    }

    // =====================================================================================
    // Định tuyến câu hỏi sang module sở hữu bản ghi
    // =====================================================================================

    /** Người gọi hiện tại có xem được tệp gắn vào bản ghi này không. Vắng cổng ⇒ {@code false}. */
    public boolean canView(MediaOwnerType ownerType, UUID ownerId) {
        MediaOwnerAccessPort port = portFor(ownerType);
        if (port == null) {
            log.warn("Khong co MediaOwnerAccessPort cho {} — tu choi xem tep cua ban ghi {}."
                    + " Day la mac dinh dong, khong phai loi cau hinh im lang.", ownerType, ownerId);
            return false;
        }
        return port.canView(ownerId);
    }

    /**
     * Bắt buộc quyền <b>gắn/gỡ</b> tệp trên bản ghi này.
     *
     * <p>Đây là chỗ chặn ca "người ngoài phạm vi gắn tệp vào bài của chi khác". Phép kiểm thật
     * nằm ở hiện thực của {@code content}: chỉ tác giả, chỉ khi bài còn ở {@code DRAFT}.</p>
     */
    public void requireAttach(MediaOwnerType ownerType, UUID ownerId) {
        MediaOwnerAccessPort port = portFor(ownerType);
        if (port == null || !port.canAttach(ownerId)) {
            log.info("Tu choi gan/go tep tren {} {} — nguoi goi khong co quyen ghi tren ban ghi ay",
                    ownerType, ownerId);
            throw new ForbiddenException(MediaProblemCodes.FORBIDDEN,
                    "Ban khong co quyen thay doi tep dinh kem cua ban ghi nay.");
        }
    }

    /**
     * Bắt buộc quyền <b>duyệt</b> một đơn báo gỡ, theo chi của bản ghi mang tệp.
     *
     * <p>Chi ấy do chính module sở hữu trả lời ({@code branchOf}) — {@code media} không tra được,
     * vì {@code post.branch_id} là ảnh chụp lúc tạo nháp còn chi của một nhân khẩu là giá trị hiện
     * tại. Hai ngữ nghĩa khác nhau và mỗi context giữ ngữ nghĩa của mình.</p>
     */
    public void requireReviewer(MemberScopeView caller, MediaOwnerType ownerType, UUID ownerId) {
        MediaOwnerAccessPort port = portFor(ownerType);
        BranchPath target = port == null ? null : port.branchOf(ownerId);
        guard.requireReviewAccess(caller, target);
    }

    /**
     * Người gọi có duyệt được đơn này không — bản trả {@code boolean}, để <i>lọc</i> hàng đợi thay
     * vì <i>chặn</i>.
     */
    public boolean canReview(MemberScopeView caller, MediaOwnerType ownerType, UUID ownerId) {
        MediaOwnerAccessPort port = portFor(ownerType);
        BranchPath target = port == null ? null : port.branchOf(ownerId);
        return guard.canReview(caller, target);
    }

    private MediaOwnerAccessPort portFor(MediaOwnerType ownerType) {
        return switch (ownerType) {
            case POST -> postAccess.getIfAvailable();
            case PERSON_AVATAR -> avatarAccess.getIfAvailable();
        };
    }
}
