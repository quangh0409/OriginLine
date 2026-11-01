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
                            BranchPath homeBranch, boolean linkedToTree) {

    public CallerContext {
        Objects.requireNonNull(role, "CallerContext.role khong duoc null");
        managedBranches = managedBranches == null ? List.of() : List.copyOf(managedBranches);
    }

    /**
     * Dạng thường dùng: "đã vào phả" suy thẳng từ việc có nhân khẩu hay không.
     *
     * <p>Chỉ {@code DirectoryAudience} mới cần dạng năm tham số — nó cố ý xoá {@code personId}
     * (để danh bạ không cho chính chủ đặc quyền) nhưng phải giữ lại sự thật "người này đã vào
     * phả", vì mức "Cả họ xem" dựa vào sự thật ấy chứ không dựa vào việc có token.</p>
     */
    public CallerContext(CallerRole role, UUID personId, List<BranchPath> managedBranches,
                         BranchPath homeBranch) {
        this(role, personId, managedBranches, homeBranch,
                role != CallerRole.GUEST && personId != null);
    }

    /** Khách vãng lai: không token, không nhân khẩu, không phạm vi. */
    public static CallerContext guest() {
        return new CallerContext(CallerRole.GUEST, null, List.of(), null, false);
    }

    public boolean isGuest() {
        return role == CallerRole.GUEST;
    }

    public boolean isAdmin() {
        return role == CallerRole.ADMIN;
    }

    /**
     * Người gọi <b>đã vào phả</b>: tài khoản đã được duyệt và gắn với một nhân khẩu.
     *
     * <p>Đây là ranh giới mà mức chia sẻ "Cả họ xem" / "Cùng chi xem" dựa vào — không phải "đã
     * đăng nhập". Một tài khoản đăng ký bằng mã mời dòng họ (V16) nhưng chưa được Trưởng chi
     * duyệt có token, không phải Khách, nhưng chưa là người trong họ; xem
     * {@link PersonVisibility#allows}.</p>
     *
     * <p>Bản đầu (26/09) suy cờ này từ {@code personId != null || homeBranch != null} để danh bạ
     * (đã xoá {@code personId}) vẫn đúng. {@code PrivacyTierServiceTest} bắt ngay: một ca dựng
     * "chưa duyệt" bằng {@code personId = null} nhưng vẫn kèm chi nhà — hình dạng không có thật
     * ngoài đời, nhưng cho thấy cờ suy từ dữ liệu phụ là cờ dễ suy sai. Nay nó là một trường
     * tường minh, và chỗ duy nhất đặt nó khác với "có nhân khẩu" là {@code DirectoryAudience}.</p>
     */
    public boolean isLinkedToTree() {
        return linkedToTree;
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
