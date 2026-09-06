package vn.giapha.membership.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.membership.api.rest.dto.BranchAssignmentDto;
import vn.giapha.membership.api.rest.dto.GrantRoleDto;
import vn.giapha.membership.application.MembershipProblemCodes;
import vn.giapha.membership.application.RoleAssignmentService;
import vn.giapha.membership.application.command.GrantRoleCommand;
import vn.giapha.membership.domain.BranchAssignment;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.shared.exception.DomainException;

/**
 * Cấp và thu hồi vai trò kèm phạm vi chi/ngành — {@code /api/v1/branch-assignments}.
 *
 * <p>Kiểm quyền nằm ở {@code RoleAssignmentService}, không ở annotation: luật "chỉ {@code ADMIN}
 * mới cấp được {@code ADMIN}" phụ thuộc vào <i>nội dung</i> yêu cầu, thứ mà một biểu thức
 * {@code @PreAuthorize} chạy trước khi bind tham số không nhìn thấy.</p>
 *
 * <p><b>Đây là bảng điều khiển của toàn bộ chiều phân quyền thứ hai.</b> Mọi thao tác ở đây đều để
 * lại vết {@code GRANT_ROLE} / {@code REVOKE_ROLE} trong {@code audit_log}.</p>
 */
@RestController
@RequestMapping("/api/v1/branch-assignments")
@Tag(name = "Branch assignments", description = "Phân công vai trò theo phạm vi chi/ngành")
public class BranchAssignmentController {

    private final RoleAssignmentService roles;

    public BranchAssignmentController(RoleAssignmentService roles) {
        this.roles = roles;
    }

    @PostMapping
    @Operation(summary = "Cấp vai trò cho tài khoản trong một phạm vi chi/ngành")
    public ResponseEntity<BranchAssignmentDto> grant(@Valid @RequestBody GrantRoleDto body) {
        RoleCode role = RoleCode.parse(body.role());
        if (role == null) {
            throw new DomainException(MembershipProblemCodes.INVALID_ROLE_ASSIGNMENT,
                    "Vai tro khong hop le: " + body.role());
        }
        BranchAssignment saved = roles.grant(new GrantRoleCommand(body.appUserId(), role,
                body.branchId(), body.validFrom(), body.validTo(), body.note()));
        return ResponseEntity
                .created(URI.create("/api/v1/branch-assignments/" + saved.id()))
                .body(BranchAssignmentDto.from(saved));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Thu hồi một phân công")
    public ResponseEntity<Void> revoke(@PathVariable UUID id,
                                       @RequestParam(required = false) String reason) {
        roles.revoke(id, reason);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Phân công của một tài khoản")
    public List<BranchAssignmentDto> ofUser(@RequestParam UUID appUserId) {
        return roles.assignmentsOf(appUserId).stream().map(BranchAssignmentDto::from).toList();
    }

    /** Ai đang giữ vai Trưởng Chi/Ngành của một chi — khác với chức danh dòng tộc ở {@code branch}. */
    @GetMapping("/heads")
    @Operation(summary = "Trưởng Chi/Ngành (vai kỹ thuật) của một chi")
    public List<BranchAssignmentDto> headsOf(@RequestParam UUID branchId) {
        return roles.headsOfBranch(branchId).stream().map(BranchAssignmentDto::from).toList();
    }
}
