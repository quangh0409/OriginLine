package vn.giapha.membership.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.port.BranchLookupPort;
import vn.giapha.shared.vo.BranchPath;

/**
 * Bản giả của {@code BranchLookupPort}: hai bảng tra cứu chi/ngành mà phân quyền theo phạm vi cần.
 *
 * <p>Giữ riêng {@link #deleted} vì bản thật lọc {@code b.is_deleted = FALSE}. Một chi đã xoá mềm
 * phải phân giải thành "không có path", và khi ấy chỉ vai toàn dòng họ mới đụng được — nếu bản giả
 * bỏ qua điều này thì luật "chi rỗng không phải chi công cộng" sẽ không có test nào canh.</p>
 */
public final class StubBranchLookup implements BranchLookupPort {

    private final Map<UUID, BranchPath> branchPaths = new LinkedHashMap<>();
    private final Map<UUID, UUID> personBranches = new LinkedHashMap<>();
    private final Map<UUID, Boolean> deleted = new LinkedHashMap<>();

    /** Khai báo một chi/ngành với đường dẫn {@code ltree} của nó. */
    public UUID branch(UUID branchId, String ltreePath) {
        branchPaths.put(branchId, BranchPath.of(ltreePath));
        deleted.put(branchId, Boolean.FALSE);
        return branchId;
    }

    /** Gắn một nhân khẩu vào chi chính của người ấy. */
    public UUID person(UUID personId, UUID branchId) {
        personBranches.put(personId, branchId);
        return personId;
    }

    /** Xoá mềm một chi — path không còn phân giải được. */
    public void softDelete(UUID branchId) {
        deleted.put(branchId, Boolean.TRUE);
    }

    @Override
    public Optional<BranchPath> pathOfBranch(UUID branchId) {
        if (branchId == null || Boolean.TRUE.equals(deleted.get(branchId))) {
            return Optional.empty();
        }
        return Optional.ofNullable(branchPaths.get(branchId));
    }

    @Override
    public Optional<BranchPath> branchOfPerson(UUID personId) {
        return branchIdOfPerson(personId).flatMap(this::pathOfBranch);
    }

    @Override
    public Optional<UUID> branchIdOfPerson(UUID personId) {
        if (personId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(personBranches.get(personId));
    }

    @Override
    public boolean branchExists(UUID branchId) {
        return branchId != null && branchPaths.containsKey(branchId)
                && !Boolean.TRUE.equals(deleted.get(branchId));
    }
}
