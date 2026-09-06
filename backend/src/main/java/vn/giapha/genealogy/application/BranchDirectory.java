package vn.giapha.genealogy.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import vn.giapha.genealogy.application.view.BranchRef;
import vn.giapha.genealogy.domain.Branch;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.shared.vo.BranchPath;

/**
 * Bảng tra chi/ngành nạp <b>một lượt</b> cho cả một phản hồi.
 *
 * <p>Lý do tồn tại rất cụ thể: phân quyền và phân tầng riêng tư đều hỏi "nhân khẩu này thuộc chi
 * nào" cho <i>từng</i> người. Trên một projection 500 node mà mỗi lần hỏi là một truy vấn thì
 * chính bộ lọc bảo vệ dữ liệu lại trở thành thứ làm sập trang phả đồ. Nạp trước theo lô, tra
 * trong bộ nhớ.</p>
 */
public final class BranchDirectory {

    private static final BranchDirectory EMPTY = new BranchDirectory(Map.of());

    private final Map<UUID, Branch> byId;

    private BranchDirectory(Map<UUID, Branch> byId) {
        this.byId = byId;
    }

    public static BranchDirectory empty() {
        return EMPTY;
    }

    /** Nạp đúng những chi được nhắc tới; id {@code null} hoặc trùng bị bỏ qua. */
    public static BranchDirectory load(BranchRepository branches, Collection<UUID> branchIds) {
        if (branches == null || branchIds == null || branchIds.isEmpty()) {
            return EMPTY;
        }
        Collection<UUID> distinct = branchIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinct.isEmpty()) {
            return EMPTY;
        }
        Map<UUID, Branch> map = new LinkedHashMap<>();
        for (Branch branch : branches.byIds(distinct)) {
            map.put(branch.id(), branch);
        }
        return new BranchDirectory(map);
    }

    public static BranchDirectory of(Branch branch) {
        return branch == null ? EMPTY : new BranchDirectory(Map.of(branch.id(), branch));
    }

    /** Đường dẫn {@code ltree} của một chi; {@code null} khi nhân khẩu chưa gắn chi nào. */
    public BranchPath pathOf(UUID branchId) {
        Branch branch = branchId == null ? null : byId.get(branchId);
        return branch == null ? null : branch.path();
    }

    public BranchRef refOf(UUID branchId) {
        return branchId == null ? null : BranchRef.of(byId.get(branchId));
    }

    /**
     * Nhân khẩu đang giữ chức <b>Trưởng chi</b> của chi này.
     *
     * <p>Đây là chức danh dòng tộc theo huyết thống/đích tôn, hoàn toàn tách khỏi vai kỹ thuật
     * {@code BRANCH_HEAD} trong JWT: người giữ chức chưa chắc có tài khoản, và người có vai chưa
     * chắc giữ chức.</p>
     */
    public UUID headPersonOf(UUID branchId) {
        Branch branch = branchId == null ? null : byId.get(branchId);
        return branch == null ? null : branch.headPersonId();
    }
}
