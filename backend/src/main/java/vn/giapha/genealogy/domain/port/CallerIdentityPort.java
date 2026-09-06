package vn.giapha.genealogy.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.shared.vo.BranchPath;

/**
 * Phân giải người gọi hiện tại thành thứ mà phân quyền theo chi cần: <b>nhân khẩu tương ứng</b> và
 * <b>phạm vi chi/ngành</b> ({@code keycloak_sub → app_user → person}).
 *
 * <p>{@code app_user} và {@code branch_assignment} thuộc context {@code membership}. Adapter Giai
 * đoạn 1 từng đọc thẳng hai bảng đó bằng SQL để W2/W7 không phải chờ W6; nay nó gọi
 * {@code MemberScopeService} do {@code membership} công bố, nên chỉ còn một chỗ biết cấu trúc
 * bảng.</p>
 *
 * <p>Cổng này hiện có ba phương thức và {@code PrivacyTierService} gọi cả ba, khiến phạm vi thành
 * viên bị dựng lại ba lần cho mỗi request. Chấp nhận được vì chỉ chạy một lần mỗi request; cách sửa
 * đúng là thu cổng còn <b>một</b> phương thức trả trọn danh tính.</p>
 */
public interface CallerIdentityPort {

    /** Nhân khẩu ứng với tài khoản đang gọi; rỗng với khách hoặc tài khoản chưa map. */
    Optional<UUID> currentPersonId();

    /**
     * Các chi mà người gọi được giao quản trị ({@code BRANCH_HEAD}). Rỗng nghĩa là không có phạm
     * vi nào — <b>không</b> có nghĩa là toàn quyền.
     */
    List<BranchPath> managedBranches();

    /** Chi chính của nhân khẩu ứng với người gọi — dùng để xét "cùng chi" cho Tầng 2. */
    Optional<BranchPath> homeBranch();
}
