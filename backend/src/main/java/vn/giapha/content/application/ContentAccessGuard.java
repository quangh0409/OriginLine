package vn.giapha.content.application;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.content.domain.port.BranchLocatorPort;
import vn.giapha.membership.application.BranchScopeGuard;
import vn.giapha.membership.application.MemberScopeService;
import vn.giapha.membership.application.MemberScopeView;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.vo.BranchPath;

/**
 * Cổng phân quyền của context {@code content}: <b>vai trò × phạm vi {@code ltree}</b>, cộng một
 * luật riêng của đợt này ("chưa vào phả thì chưa viết bài").
 *
 * <h2>Lớp này KHÔNG chứa luật phân quyền — nó chỉ phân giải chi đích rồi giao lại</h2>
 * Toàn bộ phép so vai × phạm vi nằm ở {@code membership.BranchScopeGuard}, một bản duy nhất cho cả
 * hệ thống. Việc duy nhất còn lại ở đây là câu hỏi mà {@code membership} không trả lời được: <i>chi
 * đích của bản ghi này là chi nào</i> — với bài viết là {@code post.branch_id} đã chụp, với vinh
 * danh là chi <b>hiện tại</b> của nhân khẩu được vinh danh.
 *
 * <p>Đây là khác biệt đáng nói so với {@code dataimport.api.support.ImportScopeGuard}: lớp ấy phải
 * <b>chép lại</b> phép so vì {@code BranchScopeGuard} khi đó chỉ nhận {@code MemberScope} (kiểu
 * của {@code membership.domain}, không được công bố). Đợt này đã thêm chồng hàm nhận
 * {@code MemberScopeView}, nên bản chép thứ ba không cần ra đời.</p>
 *
 * <h2>Vì sao phép kiểm không nằm ở {@code @PreAuthorize}</h2>
 * Quyền duyệt phụ thuộc <i>chi của từng bản ghi</i>, mà controller chưa nạp bản ghi thì chưa biết
 * chi ấy là gì. Một {@code @PreAuthorize("hasRole('BRANCH_HEAD')")} sẽ cho Trưởng chi Ất đi qua
 * cửa rồi mới chặn ở trong — và tệ hơn, nó tạo cảm giác đã kiểm quyền xong.
 */
@Component
public class ContentAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(ContentAccessGuard.class);

    private final MemberScopeService scopes;
    private final BranchScopeGuard guard;
    private final BranchLocatorPort branches;

    public ContentAccessGuard(MemberScopeService scopes, BranchScopeGuard guard,
                              BranchLocatorPort branches) {
        this.scopes = scopes;
        this.guard = guard;
        this.branches = branches;
    }

    /**
     * Phạm vi của người gọi, dựng <b>một lần</b> cho mỗi use case rồi truyền đi.
     *
     * <p>Hỏi lại giữa chừng vừa tốn thêm vài câu SELECT sang {@code membership}, vừa mở đường cho
     * hai bản ghi trong cùng một phản hồi bị xét theo hai ngữ cảnh khác nhau.</p>
     */
    public MemberScopeView caller() {
        return scopes.currentScope();
    }

    /** Người gọi đã có tài khoản trong hệ thống. Khách vãng lai không thấy gì ở context này. */
    public MemberScopeView requireProvisioned() {
        MemberScopeView caller = caller();
        guard.requireProvisionedAccount(caller);
        return caller;
    }

    /**
     * Nhân khẩu của người viết — <b>cửa chốt của quyết định "chưa vào phả thì chưa viết bài"</b>.
     *
     * <p>Quyết định đã chốt của chủ dự án (design/07-checklist §2): "viết bài là tiếng nói của
     * người trong họ". Một tài khoản vừa đăng ký bằng mã mời dòng họ đã xem được phả đồ nhưng
     * {@code person_id} còn rỗng — người ấy chưa được ai xác nhận là người trong họ, nên chưa viết
     * bài được. Cột {@code post.author_person_id NOT NULL} của V17 là lưới cuối cho cùng luật này;
     * chỗ đúng để trả lời người dùng thì ở đây, vì chỉ ở đây mới nói được <i>vì sao</i>.</p>
     *
     * @return {@code person.id} của người viết, không bao giờ {@code null}
     * @throws ForbiddenException {@code AUTHOR_NOT_IN_PHA}
     */
    public UUID requireAuthorPerson(MemberScopeView caller) {
        if (caller.personId() == null) {
            log.info("Tu choi viet bai: app_user {} chua duoc gan nhan khau nao", caller.appUserId());
            throw new ForbiddenException(ContentProblemCodes.AUTHOR_NOT_IN_PHA,
                    "Tai khoan chua duoc duyet vao pha nen chua dang bai duoc."
                            + " Hay tu nhan minh tren pha do va cho Truong chi duyet.");
        }
        return caller.personId();
    }

    /**
     * Bắt buộc quyền duyệt trên bản ghi thuộc chi {@code target}.
     *
     * <p>{@code target == null} (bản ghi chưa gắn chi nào) thì chỉ vai toàn dòng họ đi tiếp được —
     * luật "chi rỗng không phải chi công cộng" của {@code BranchScopeGuard}. Dữ liệu thiếu phải làm
     * quyền <b>hẹp lại</b>, không bao giờ được nới ra.</p>
     */
    public void requireReviewer(MemberScopeView caller, BranchPath target) {
        guard.requireReviewAccess(caller, target);
    }

    /** Phiên bản trả boolean — cho những lối cần <i>lọc</i> thay vì <i>chặn</i>. */
    public boolean canReview(MemberScopeView caller, BranchPath target) {
        return guard.canReview(caller, target);
    }

    /** {@code ltree} path của một chi; {@code null} khi chi không tồn tại hoặc đã xoá mềm. */
    public BranchPath pathOfBranch(UUID branchId) {
        return branchId == null ? null : branches.pathOfBranch(branchId).orElse(null);
    }

    /** Chi <b>hiện tại</b> của một nhân khẩu; {@code null} khi chưa gắn chi nào. */
    public BranchPath branchOfPerson(UUID personId) {
        return personId == null ? null : branches.branchOfPerson(personId).orElse(null);
    }

    /** Khoá chi chính của một nhân khẩu — để chụp vào {@code post.branch_id} lúc tạo nháp. */
    public UUID branchIdOfPerson(UUID personId) {
        return personId == null ? null : branches.branchIdOfPerson(personId).orElse(null);
    }

    /** Tên hiển thị của một chi. Dữ liệu công khai của dòng họ, không phải dữ liệu cá nhân. */
    public String branchName(UUID branchId) {
        return branchId == null ? null : branches.nameOfBranch(branchId).orElse(null);
    }
}
