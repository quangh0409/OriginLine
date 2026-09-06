package vn.giapha.membership.api.rest.dto;

import java.time.LocalDate;
import java.util.UUID;
import vn.giapha.membership.domain.BranchAssignment;

/**
 * Một dòng phân công vai trò trả ra API.
 *
 * <p>{@code branchPath} là {@code null} khi phạm vi là toàn dòng họ. Giao diện phải hiển thị điều
 * đó thành chữ ("toàn dòng họ") chứ không để trống — một ô trống ở cột phạm vi trông giống hệt một
 * lỗi tải dữ liệu.</p>
 */
public record BranchAssignmentDto(UUID id, UUID appUserId, String role, UUID branchId,
                                  String branchPath, LocalDate validFrom, LocalDate validTo,
                                  UUID grantedBy, String note) {

    public static BranchAssignmentDto from(BranchAssignment assignment) {
        return new BranchAssignmentDto(assignment.id(), assignment.appUserId(),
                assignment.role().name(), assignment.branchId(),
                assignment.branchPath() == null ? null : assignment.branchPath().value(),
                assignment.validFrom(), assignment.validTo(), assignment.grantedBy(),
                assignment.note());
    }
}
