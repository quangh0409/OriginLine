package vn.giapha.membership.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import vn.giapha.shared.vo.BranchPath;

/**
 * Gán một vai trò cho một tài khoản <b>kèm phạm vi chi/ngành</b>.
 *
 * <h2>Đây là chiều phân quyền thứ hai</h2>
 * Vai trò nằm trong JWT; phạm vi thì không và <b>không thể</b> suy ra từ token. Đây là dòng dữ liệu
 * duy nhất nói Trưởng chi X được đụng vào nhánh nào. Mọi kiểm tra quyền ghi phải so path của
 * <i>đối tượng</i> với {@link #branchPath} bằng ngữ nghĩa {@code @>} của {@code ltree} — tức là
 * "chi này hoặc mọi hậu duệ của nó".
 *
 * <h2>{@code branchPath == null} nghĩa là toàn dòng họ</h2>
 * Đúng theo chú thích cột {@code branch_assignment.branch_id} của V5. Chỉ dùng cho
 * {@link RoleCode#ADMIN} và {@link RoleCode#COUNCIL}; một dòng {@code BRANCH_HEAD} không có
 * {@code branch_id} là dữ liệu sai và bị {@link #coversEverything()} coi là toàn quyền — nên
 * {@code RoleAssignmentService} chặn ngay lúc cấp.
 *
 * <h2>Hiệu lực theo thời gian</h2>
 * {@code valid_from}/{@code valid_to} cho phép ghi nhiệm kỳ. Một phân công hết hạn <b>không</b> bị
 * xoá: giữ lại thì {@code audit_log} tra ngược được "lúc đó ai có quyền", còn xoá đi thì một cuộc
 * kiểm toán ba năm sau không giải thích nổi vì sao thao tác kia hợp lệ.
 */
public final class BranchAssignment {

    private final UUID id;
    private final UUID appUserId;
    private final RoleCode role;
    private final UUID branchId;
    private final BranchPath branchPath;
    private final LocalDate validFrom;
    private final LocalDate validTo;
    private final UUID grantedBy;
    private final String note;

    public BranchAssignment(UUID id, UUID appUserId, RoleCode role, UUID branchId,
                            BranchPath branchPath, LocalDate validFrom, LocalDate validTo,
                            UUID grantedBy, String note) {
        this.id = Objects.requireNonNull(id, "BranchAssignment.id khong duoc null");
        this.appUserId = Objects.requireNonNull(appUserId, "appUserId khong duoc null");
        this.role = Objects.requireNonNull(role, "role khong duoc null");
        this.branchId = branchId;
        this.branchPath = branchPath;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.grantedBy = grantedBy;
        this.note = note;
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("valid_to phai khong som hon valid_from");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID appUserId() {
        return appUserId;
    }

    public RoleCode role() {
        return role;
    }

    public UUID branchId() {
        return branchId;
    }

    public BranchPath branchPath() {
        return branchPath;
    }

    public LocalDate validFrom() {
        return validFrom;
    }

    public LocalDate validTo() {
        return validTo;
    }

    public UUID grantedBy() {
        return grantedBy;
    }

    public String note() {
        return note;
    }

    /** Còn hiệu lực vào ngày {@code on}; hai mốc để trống nghĩa là vô thời hạn. */
    public boolean isActiveOn(LocalDate on) {
        LocalDate day = on == null ? LocalDate.now() : on;
        boolean started = validFrom == null || !day.isBefore(validFrom);
        boolean notEnded = validTo == null || !day.isAfter(validTo);
        return started && notEnded;
    }

    /** Phạm vi toàn dòng họ — không gắn với chi nào. */
    public boolean coversEverything() {
        return branchId == null;
    }

    /**
     * Phân công này có phủ {@code target} không.
     *
     * <p>Phân công toàn dòng họ phủ mọi thứ. Phân công có chi thì phủ đúng chi đó và mọi hậu duệ.
     * {@code target == null} (nhân khẩu chưa gắn chi) <b>chỉ</b> phân công toàn dòng họ mới phủ:
     * coi "không có chi" là "thuộc mọi chi" sẽ biến mỗi bản ghi thiếu dữ liệu thành một lỗ hổng.</p>
     */
    public boolean covers(BranchPath target) {
        if (coversEverything()) {
            return true;
        }
        if (branchPath == null || target == null) {
            return false;
        }
        return branchPath.isAncestorOf(target);
    }
}
