package vn.giapha.membership.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import vn.giapha.shared.vo.BranchPath;

/**
 * Ảnh chụp <b>đầy đủ hai chiều</b> phân quyền của một người gọi: vai trò kỹ thuật <i>và</i> phạm vi
 * chi/ngành theo {@code ltree}.
 *
 * <h2>Vì sao đây là kiểu trung tâm của W6</h2>
 * Role trong Keycloak chỉ là <b>một nửa</b> bài toán. Nửa còn lại nằm ở {@code branch_assignment},
 * backend tự tra, và không suy ra được từ token. Gộp cả hai vào một value object khiến "quên kiểm
 * phạm vi" trở thành một việc phải làm có chủ ý chứ không phải một sơ suất: mọi API kiểm quyền của
 * context này đều nhận {@code MemberScope}, không nhận {@code RoleCode} trần.
 *
 * <h2>Dựng một lần cho một request</h2>
 * Một lượt {@code GET /tree} lọc riêng tư trên hàng trăm nhân khẩu. Hỏi lại {@code SecurityContext}
 * và {@code branch_assignment} ở mỗi lần lọc thì vừa chậm vừa mở đường cho hai nhân khẩu trong cùng
 * một phản hồi bị xét theo hai ngữ cảnh khác nhau.
 *
 * <h2>Danh sách phạm vi rỗng nghĩa là KHÔNG có phạm vi nào</h2>
 * Tuyệt đối không được diễn giải ngược thành "không giới hạn". Đây là lỗi kinh điển của phân quyền
 * theo scope và là lý do {@link #managedBranches} không bao giờ được trả {@code null}.
 */
public final class MemberScope {

    private static final MemberScope GUEST =
            new MemberScope(null, null, RoleCode.GUEST, List.of(), null, false);

    private final UUID appUserId;
    private final UUID personId;
    private final RoleCode role;
    private final List<BranchPath> managedBranches;
    private final BranchPath homeBranch;
    private final boolean clanWideAssignment;

    public MemberScope(UUID appUserId, UUID personId, RoleCode role,
                       List<BranchPath> managedBranches, BranchPath homeBranch,
                       boolean clanWideAssignment) {
        this.appUserId = appUserId;
        this.personId = personId;
        this.role = Objects.requireNonNull(role, "MemberScope.role khong duoc null");
        this.managedBranches = managedBranches == null ? List.of() : List.copyOf(managedBranches);
        this.homeBranch = homeBranch;
        this.clanWideAssignment = clanWideAssignment;
    }

    /** Khách vãng lai: không token, không tài khoản, không nhân khẩu, không phạm vi. */
    public static MemberScope guest() {
        return GUEST;
    }

    /**
     * Người đã đăng nhập nhưng chưa có dòng {@code app_user} nào — token hợp lệ, tài khoản chưa
     * được tạo hoặc chưa được duyệt. Đối xử như {@link RoleCode#MEMBER} không phạm vi.
     */
    public static MemberScope unlinked(RoleCode role) {
        return new MemberScope(null, null, role == null ? RoleCode.MEMBER : role,
                List.of(), null, false);
    }

    public UUID appUserId() {
        return appUserId;
    }

    public UUID personId() {
        return personId;
    }

    public RoleCode role() {
        return role;
    }

    /** Các chi được giao quản trị. Rỗng = không có phạm vi nào, <b>không</b> phải toàn quyền. */
    public List<BranchPath> managedBranches() {
        return managedBranches;
    }

    /** Chi chính của nhân khẩu ứng với người gọi — căn cứ xét "cùng chi" cho Tầng 2. */
    public BranchPath homeBranch() {
        return homeBranch;
    }

    public boolean isGuest() {
        return role == RoleCode.GUEST;
    }

    /**
     * Phạm vi toàn dòng họ.
     *
     * <p>Đòi <b>cả hai</b>: vai trong token phải là {@code ADMIN}/{@code COUNCIL}, <i>và</i> phải
     * có một dòng {@code branch_assignment} không gắn chi. Cố ý không chỉ tin vào token: một realm
     * Keycloak bị cấu hình lỏng tay, hoặc một client được gán vai mặc định, sẽ phát ra token
     * {@code ADMIN} cho người mà dòng họ chưa bao giờ trao quyền đó.</p>
     *
     * <p>Ngoại lệ đúng một chỗ: {@link RoleCode#ADMIN} là vai <b>kỹ thuật</b> — người vận hành hệ
     * thống phải vào được ngay cả khi bảng phân công còn trống, nếu không thì không ai cấp được
     * dòng phân công đầu tiên.</p>
     */
    public boolean isClanWide() {
        if (role == RoleCode.ADMIN) {
            return true;
        }
        return role == RoleCode.COUNCIL && clanWideAssignment;
    }

    public boolean isSystemAdmin() {
        return role == RoleCode.ADMIN;
    }

    /** {@code true} nếu hồ sơ đang xét chính là người đang đăng nhập. */
    public boolean isSelf(UUID targetPersonId) {
        return personId != null && targetPersonId != null && personId.equals(targetPersonId);
    }

    /**
     * {@code target} nằm trong (hoặc chính là) một chi được giao — đúng ngữ nghĩa {@code @>} của
     * {@code ltree}.
     *
     * <p>{@code target == null} luôn trả {@code false}: nhân khẩu chưa gắn chi chỉ vai toàn dòng
     * họ mới được đụng.</p>
     */
    public boolean managesBranch(BranchPath target) {
        if (target == null) {
            return false;
        }
        for (BranchPath scope : managedBranches) {
            if (scope.isAncestorOf(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Quyền ghi trên dữ liệu thuộc chi {@code target}: toàn dòng họ, hoặc Trưởng chi đúng phạm vi.
     *
     * <p>{@link RoleCode#MEMBER} luôn {@code false} ở đây — thành viên sửa hồ sơ của chính mình
     * qua lối riêng, còn muốn sửa người khác thì gửi {@code ChangeRequest}.</p>
     */
    public boolean canWriteOn(BranchPath target) {
        if (isClanWide()) {
            return true;
        }
        return role == RoleCode.BRANCH_HEAD && managesBranch(target);
    }

    /**
     * Quyền <b>duyệt</b> một yêu cầu đính chính nhắm vào chi {@code target}.
     *
     * <p>Cùng luật với {@link #canWriteOn}: duyệt là ghi, chỉ khác ở chỗ có thêm một người đứng
     * giữa. Trưởng chi duyệt được đúng nhánh mình, ngoài nhánh là 403.</p>
     */
    public boolean canReview(BranchPath target) {
        return role.canReview() && canWriteOn(target);
    }

    /**
     * Người gọi và {@code target} cùng một nhánh phả hệ, xét <b>hai chiều</b>: chi nhà chứa chi
     * kia, hoặc ngược lại.
     *
     * <p>Chiều ngược cố ý được tính: một thành viên của cành nhỏ vẫn coi là "cùng chi" với người
     * đứng ở gốc chi mình. Chặn chiều đó chỉ tạo ra một loại vô hình khó hiểu cho người dùng mà
     * không bảo vệ thêm được gì.</p>
     */
    public boolean sharesBranchWith(BranchPath target) {
        if (target == null || homeBranch == null) {
            return false;
        }
        return homeBranch.isAncestorOf(target) || target.isAncestorOf(homeBranch);
    }

    /** Dựng lại phạm vi từ danh sách phân công còn hiệu lực. */
    public static MemberScope from(UUID appUserId, UUID personId, RoleCode tokenRole,
                                   List<BranchAssignment> assignments, BranchPath homeBranch) {
        RoleCode effective = tokenRole == null ? RoleCode.MEMBER : tokenRole;
        List<BranchPath> managed = new ArrayList<>();
        boolean clanWide = false;
        if (assignments != null) {
            for (BranchAssignment assignment : assignments) {
                if (assignment.coversEverything()) {
                    clanWide = clanWide || assignment.role().isClanWide();
                } else if (assignment.branchPath() != null && assignment.role().canReview()) {
                    // Chi phan cong mang vai QUAN TRI moi tao ra pham vi ghi. Mot dong
                    // (MEMBER x chi) chi noi nguoi do sinh hoat o chi nao, khong cho quyen gi.
                    managed.add(assignment.branchPath());
                }
            }
        }
        return new MemberScope(appUserId, personId, effective, managed, homeBranch, clanWide);
    }
}
