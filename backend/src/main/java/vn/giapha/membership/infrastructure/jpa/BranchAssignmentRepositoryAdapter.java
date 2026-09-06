package vn.giapha.membership.infrastructure.jpa;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import vn.giapha.membership.domain.BranchAssignment;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.membership.domain.port.BranchAssignmentRepository;
import vn.giapha.shared.vo.BranchPath;

/**
 * Hiện thực {@link BranchAssignmentRepository}.
 *
 * <h2>Path hỏng thì bỏ qua, không ném</h2>
 * Một {@code branch.path} không hợp lệ {@code ltree} là lỗi dữ liệu ở nơi khác (V2 đã có
 * {@code ck_branch_slug_ltree_safe} chặn). Để nó làm hỏng cả phép kiểm phân quyền thì người dùng
 * mất quyền truy cập vì một chi <i>khác</i> bị sai dữ liệu. Bỏ qua kèm log WARN giữ nguyên hướng an
 * toàn: thiếu một phạm vi chỉ làm quyền <b>hẹp hơn</b>, không bao giờ rộng hơn.
 *
 * <h2>Vai lạ cũng bị bỏ qua</h2>
 * Cùng lý do, và có thêm một lý do riêng: nếu về sau {@code ck_role_code} được nới ra mà enum chưa
 * kịp theo, thì một vai chưa biết phải được coi là <b>không có quyền gì</b>, chứ không được ném lỗi
 * (làm hỏng cả phiên) và tuyệt đối không được coi là quyền cao nhất.
 */
@Repository
public class BranchAssignmentRepositoryAdapter implements BranchAssignmentRepository {

    private static final Logger log = LoggerFactory.getLogger(BranchAssignmentRepositoryAdapter.class);

    private final BranchAssignmentJpaRepository jpa;
    private final RoleJpaRepository roles;

    public BranchAssignmentRepositoryAdapter(BranchAssignmentJpaRepository jpa,
                                             RoleJpaRepository roles) {
        this.jpa = jpa;
        this.roles = roles;
    }

    @Override
    public List<BranchAssignment> activeFor(UUID appUserId, LocalDate on) {
        if (appUserId == null) {
            return List.of();
        }
        return map(jpa.findActiveFor(appUserId, on == null ? LocalDate.now() : on));
    }

    @Override
    public List<BranchAssignment> allFor(UUID appUserId) {
        return appUserId == null ? List.of() : map(jpa.findAllFor(appUserId));
    }

    @Override
    public Optional<BranchAssignment> byId(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        List<BranchAssignment> rows = map(jpa.findResolvedById(id));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<BranchAssignment> byRoleAndBranch(RoleCode role, UUID branchId, LocalDate on) {
        if (role == null) {
            return List.of();
        }
        return map(jpa.findByRoleAndBranch(role.name(), branchId, on == null ? LocalDate.now() : on));
    }

    @Override
    public BranchAssignment save(BranchAssignment assignment) {
        UUID roleId = roles.findByCode(assignment.role().name())
                .map(RoleJpaEntity::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Danh muc role thieu ma " + assignment.role()
                                + " - V5 phai seed du nam vai truoc khi cap quyen"));
        BranchAssignmentJpaEntity entity = jpa.findById(assignment.id())
                .orElseGet(() -> new BranchAssignmentJpaEntity(assignment.id(),
                        assignment.appUserId(), roleId));
        entity.setRoleId(roleId);
        entity.setBranchId(assignment.branchId());
        entity.setValidFrom(assignment.validFrom());
        entity.setValidTo(assignment.validTo());
        entity.setGrantedBy(assignment.grantedBy());
        entity.setNote(assignment.note());
        jpa.save(entity);
        return assignment;
    }

    @Override
    public void delete(UUID assignmentId) {
        if (assignmentId != null) {
            jpa.deleteById(assignmentId);
        }
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ
    // -------------------------------------------------------------------------------------

    private static List<BranchAssignment> map(List<Object[]> rows) {
        List<BranchAssignment> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            RoleCode role = RoleCode.parse(asString(row[2]));
            if (role == null) {
                log.warn("Bo qua phan cong {} vi vai tro khong nhan dien duoc: {}", row[0], row[2]);
                continue;
            }
            result.add(new BranchAssignment(
                    asUuid(row[0]),
                    asUuid(row[1]),
                    role,
                    asUuid(row[3]),
                    toBranchPath(asString(row[4])),
                    asLocalDate(row[5]),
                    asLocalDate(row[6]),
                    asUuid(row[7]),
                    asString(row[8])));
        }
        return List.copyOf(result);
    }

    private static BranchPath toBranchPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return BranchPath.of(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("Bo qua branch.path khong hop le ltree: {}", raw);
            return null;
        }
    }

    private static UUID asUuid(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static LocalDate asLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }
}
