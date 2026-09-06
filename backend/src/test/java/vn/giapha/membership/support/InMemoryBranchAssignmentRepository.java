package vn.giapha.membership.support;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.BranchAssignment;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.membership.domain.port.BranchAssignmentRepository;

/**
 * Bản trong bộ nhớ của {@code branch_assignment} — <b>nguồn chân lý của chiều phân quyền thứ
 * hai</b>.
 *
 * <p>{@link #activeFor} lọc hiệu lực <b>ngay tại đây</b>, đúng như hợp đồng của cổng: bản thật lọc
 * trong câu SQL, và nếu bản giả trả cả nhiệm kỳ đã hết thì test sẽ không bao giờ phát hiện được
 * việc quên lọc.</p>
 */
public final class InMemoryBranchAssignmentRepository implements BranchAssignmentRepository {

    private final Map<UUID, BranchAssignment> rows = new LinkedHashMap<>();

    public BranchAssignment seed(BranchAssignment assignment) {
        rows.put(assignment.id(), assignment);
        return assignment;
    }

    @Override
    public List<BranchAssignment> activeFor(UUID appUserId, LocalDate on) {
        List<BranchAssignment> found = new ArrayList<>();
        for (BranchAssignment assignment : rows.values()) {
            if (assignment.appUserId().equals(appUserId) && assignment.isActiveOn(on)) {
                found.add(assignment);
            }
        }
        return found;
    }

    @Override
    public List<BranchAssignment> allFor(UUID appUserId) {
        List<BranchAssignment> found = new ArrayList<>();
        for (BranchAssignment assignment : rows.values()) {
            if (assignment.appUserId().equals(appUserId)) {
                found.add(assignment);
            }
        }
        return found;
    }

    @Override
    public Optional<BranchAssignment> byId(UUID id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public List<BranchAssignment> byRoleAndBranch(RoleCode role, UUID branchId, LocalDate on) {
        List<BranchAssignment> found = new ArrayList<>();
        for (BranchAssignment assignment : rows.values()) {
            if (assignment.role() == role
                    && Objects.equals(assignment.branchId(), branchId)
                    && assignment.isActiveOn(on)) {
                found.add(assignment);
            }
        }
        return found;
    }

    @Override
    public BranchAssignment save(BranchAssignment assignment) {
        rows.put(assignment.id(), assignment);
        return assignment;
    }

    @Override
    public void delete(UUID assignmentId) {
        rows.remove(assignmentId);
    }

    public int size() {
        return rows.size();
    }
}
