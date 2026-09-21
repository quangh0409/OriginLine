package vn.giapha.events.application;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventSubject;
import vn.giapha.events.domain.port.EventSubjectPort;
import vn.giapha.membership.application.BranchScopeGuard;
import vn.giapha.membership.application.MemberScopeService;
import vn.giapha.membership.application.MemberScopeView;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.BranchPath;

/**
 * Ai được cấu hình việc họ, và ở chi nào.
 *
 * <h2>Không có phép kiểm thứ hai ở đây</h2>
 * Lớp này <b>không</b> tự so vai hay tự duyệt danh sách chi. Nó làm đúng một việc mà
 * {@code membership} không làm được: đổi {@code target_branch_id} của sự kiện thành {@link BranchPath},
 * rồi giao cho {@link BranchScopeGuard} — nơi luật "vai × phạm vi {@code ltree}" có <b>một bản duy
 * nhất</b>. Một bản chép thứ hai của luật ấy là một bản sẽ lệch, và triệu chứng của nó là Trưởng chi
 * Ất sửa được việc của chi Bính ở đúng một endpoint mà không ai để ý.
 *
 * <h2>Việc của cả họ đòi thẩm quyền của cả họ</h2>
 * {@code clanWide = true} không nằm dưới chi nào, nên không có {@code BranchPath} để so. Nó đòi
 * {@link BranchScopeGuard#requireClanWide}: chỉ Hội đồng Tộc biểu và Quản trị hệ thống. Cho Trưởng
 * chi đặt một sự kiện cấp dòng họ là cho họ gửi thông báo tới toàn bộ dòng họ — đúng thứ mà phạm vi
 * chi sinh ra để chặn.
 *
 * <h2>Đổi phạm vi thì kiểm CẢ HAI đầu</h2>
 * Chuyển một sự kiện từ Chi Ất sang Chi Bính là hai việc: lấy nó ra khỏi Chi Ất, và đặt nó vào Chi
 * Bính. Chỉ kiểm chi đích thì Trưởng chi Bính cướp được sự kiện của Chi Ất; chỉ kiểm chi nguồn thì
 * Trưởng chi Ất đẩy được thông báo sang Chi Bính. Xem {@link #requireCanMove}.
 */
@Component
public class EventScopeGuard {

    private static final Logger log = LoggerFactory.getLogger(EventScopeGuard.class);

    private final MemberScopeService scopes;
    private final BranchScopeGuard branchScope;
    private final EventSubjectPort subjects;

    public EventScopeGuard(MemberScopeService scopes, BranchScopeGuard branchScope,
                           EventSubjectPort subjects) {
        this.scopes = scopes;
        this.branchScope = branchScope;
        this.subjects = subjects;
    }

    public MemberScopeView caller() {
        return scopes.currentScope();
    }

    /** Người gọi phải có tài khoản thật trong hệ thống — khách không cấu hình việc họ. */
    public MemberScopeView requireProvisioned() {
        MemberScopeView caller = caller();
        branchScope.requireProvisionedAccount(caller);
        return caller;
    }

    /**
     * Bắt buộc quyền ghi trên một phạm vi sự kiện.
     *
     * @param clanWide việc của cả dòng họ
     * @param branchId chi/ngành đích khi không phải việc của cả họ
     */
    public void requireWriteAccess(MemberScopeView caller, boolean clanWide, UUID branchId) {
        if (clanWide) {
            branchScope.requireClanWide(caller, "tao va sua su kien cap dong ho");
            return;
        }
        branchScope.requireWriteAccess(caller, pathOf(branchId));
    }

    /** Quyền ghi trên một sự kiện <b>đã có</b>, xét theo phạm vi hiện tại của nó. */
    public void requireWriteAccess(MemberScopeView caller, Event event) {
        requireWriteAccess(caller, event.isClanLevel(), event.targetBranchId());
    }

    /**
     * Đổi phạm vi: phải được ghi ở <b>cả</b> phạm vi cũ lẫn phạm vi mới.
     *
     * <p>Kiểm phạm vi cũ trước, để người gọi nhận đúng câu trả lời "bạn không có quyền trên sự kiện
     * này" thay vì "chi đích không hợp lệ".</p>
     */
    public void requireCanMove(MemberScopeView caller, Event current, boolean newClanWide,
                               UUID newBranchId) {
        requireWriteAccess(caller, current);
        if (newClanWide == current.isClanLevel()
                && java.util.Objects.equals(newBranchId, current.targetBranchId())) {
            return;
        }
        log.debug("app_user {} chuyen su kien {} tu (clanLevel={}, chi={}) sang (clanLevel={}, chi={})",
                caller.appUserId(), current.id(), current.isClanLevel(), current.targetBranchId(),
                newClanWide, newBranchId);
        requireWriteAccess(caller, newClanWide, newBranchId);
    }

    /**
     * {@code target_branch_id} → {@link BranchPath}.
     *
     * <p>Chi không tồn tại (hoặc đã xoá mềm) trả <b>404</b>, không phải 403: trả 403 cho một khoá
     * bịa ra là xác nhận cho người hỏi biết khoá nào có thật.</p>
     */
    public BranchPath pathOf(UUID branchId) {
        if (branchId == null) {
            // Ở đây null KHÔNG phải "không giới hạn" mà là "chưa gắn chi" — BranchScopeGuard chỉ cho
            // vai toàn dòng họ đụng vào. Trả null nguyên vẹn để nó xử theo luật của nó.
            return null;
        }
        EventSubject.BranchSnapshot branch = subjects.findBranch(branchId)
                .orElseThrow(() -> NotFoundException.of("Branch", branchId));
        return BranchPath.of(branch.path());
    }
}
