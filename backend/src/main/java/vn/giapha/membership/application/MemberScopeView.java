package vn.giapha.membership.application;

import java.util.List;
import java.util.UUID;
import vn.giapha.membership.domain.MemberScope;
import vn.giapha.shared.vo.BranchPath;

/**
 * Phạm vi phân quyền của một người gọi, ở dạng phẳng để context khác tiêu thụ.
 *
 * <p><b>Đây là kiểu trả về công khai của {@link MemberScopeService}</b> — hợp đồng giữa
 * {@code membership} và phần còn lại của hệ thống. Cố ý là một record bất biến, không phải aggregate
 * của domain: bên gọi chỉ đọc, và một object có phương thức đổi trạng thái sẽ mời gọi việc "sửa
 * quyền tại chỗ" ở tận đâu đó trong một service khác.</p>
 *
 * <p>Đủ đúng ba thứ mà {@code CallerIdentityPort} bên {@code genealogy} cần:
 * {@link #personId()}, {@link #managedBranches()}, {@link #homeBranch()}.</p>
 *
 * @param appUserId       {@code app_user.id}; {@code null} với khách hoặc tài khoản chưa tạo
 * @param personId        nhân khẩu tương ứng; {@code null} khi chưa ghép vào cây
 * @param role            vai <b>rộng nhất</b> mà token có, dạng chuỗi ({@code ADMIN}…{@code GUEST})
 * @param clanWide        phạm vi toàn dòng họ (vai toàn cục <i>và</i> có phân công tương ứng)
 * @param managedBranches các chi được giao quản trị; <b>rỗng = không có phạm vi nào</b>
 * @param homeBranch      chi chính của người gọi — căn cứ xét "cùng chi" cho Tầng 2
 */
public record MemberScopeView(UUID appUserId, UUID personId, String role, boolean clanWide,
                              List<BranchPath> managedBranches, BranchPath homeBranch) {

    public MemberScopeView {
        managedBranches = managedBranches == null ? List.of() : List.copyOf(managedBranches);
    }

    public static MemberScopeView from(MemberScope scope) {
        return new MemberScopeView(scope.appUserId(), scope.personId(), scope.role().name(),
                scope.isClanWide(), scope.managedBranches(), scope.homeBranch());
    }

    /** Khách vãng lai — không tài khoản, không nhân khẩu, không phạm vi. */
    public static MemberScopeView guest() {
        return from(MemberScope.guest());
    }

    public boolean isGuest() {
        return "GUEST".equals(role);
    }

    /** {@code target} nằm trong (hoặc chính là) một chi được giao — ngữ nghĩa {@code @>} của ltree. */
    public boolean managesBranch(BranchPath target) {
        if (target == null) {
            return false;
        }
        return managedBranches.stream().anyMatch(scope -> scope.isAncestorOf(target));
    }
}
