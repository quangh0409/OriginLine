package vn.giapha.genealogy.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import vn.giapha.shared.vo.BranchPath;

/**
 * Ảnh chụp danh tính người gọi cho <b>một</b> request: vai kỹ thuật, nhân khẩu tương ứng, và
 * phạm vi chi/ngành.
 *
 * <p><b>Vì sao dựng một lần rồi truyền đi.</b> Một lượt {@code GET /tree} lọc riêng tư trên hàng
 * trăm nhân khẩu; nếu mỗi lần lọc lại đi hỏi {@code SecurityContext} và bảng
 * {@code branch_assignment} thì vừa chậm vừa có nguy cơ hai nhân khẩu trong cùng một phản hồi
 * được xét theo hai ngữ cảnh khác nhau.</p>
 *
 * @param role            vai rộng nhất mà token có
 * @param personId        nhân khẩu ứng với tài khoản ({@code keycloak_sub → app_user → person});
 *                        {@code null} với Khách hoặc tài khoản chưa map
 * @param managedBranches các chi được giao quản trị; rỗng nghĩa là <b>không có phạm vi nào</b>,
 *                        tuyệt đối không phải toàn quyền
 * @param homeBranch      chi chính của người gọi — căn cứ xét "cùng chi" cho Tầng 2
 */
public record CallerContext(CallerRole role, UUID personId, List<BranchPath> managedBranches,
                            BranchPath homeBranch) {

    public CallerContext {
        Objects.requireNonNull(role, "CallerContext.role khong duoc null");
        managedBranches = managedBranches == null ? List.of() : List.copyOf(managedBranches);
    }

    /** Khách vãng lai: không token, không nhân khẩu, không phạm vi. */
    public static CallerContext guest() {
        return new CallerContext(CallerRole.GUEST, null, List.of(), null);
    }

    public boolean isGuest() {
        return role == CallerRole.GUEST;
    }

    public boolean isAdmin() {
        return role == CallerRole.ADMIN;
    }

    /** {@code true} nếu hồ sơ đang xét chính là người đang đăng nhập. */
    public boolean isSelf(UUID targetPersonId) {
        return personId != null && personId.equals(targetPersonId);
    }

    /**
     * {@code true} nếu {@code target} nằm trong (hoặc chính là) một chi được giao quản trị —
     * đúng ngữ nghĩa toán tử {@code @>} của {@code ltree}.
     */
    public boolean managesBranch(BranchPath target) {
        if (target == null) {
            return false;
        }
        return managedBranches.stream().anyMatch(scope -> scope.isAncestorOf(target));
    }

    /**
     * {@code true} nếu người gọi và {@code target} thuộc cùng một nhánh phả hệ, xét <b>hai chiều</b>:
     * chi nhà người gọi chứa chi kia, hoặc ngược lại.
     *
     * <p>Chiều ngược cố ý được tính: một thành viên của cành nhỏ vẫn coi là "cùng chi" với người
     * đứng ở gốc chi mình — chặn chiều đó chỉ tạo ra một loại vô hình khó hiểu cho người dùng mà
     * không bảo vệ thêm được gì.</p>
     */
    public boolean sharesBranchWith(BranchPath target) {
        if (target == null || homeBranch == null) {
            return false;
        }
        return homeBranch.isAncestorOf(target) || target.isAncestorOf(homeBranch);
    }
}
