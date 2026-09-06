package vn.giapha.membership.application.command;

import java.time.LocalDate;
import java.util.UUID;
import vn.giapha.membership.domain.RoleCode;

/**
 * Cấp một vai trò cho tài khoản, kèm phạm vi chi/ngành.
 *
 * @param appUserId tài khoản nhận vai
 * @param role      vai trò kỹ thuật
 * @param branchId  chi được giao. {@code null} = phạm vi toàn dòng họ và <b>chỉ</b> hợp lệ với
 *                  {@code ADMIN}/{@code COUNCIL}; một {@code BRANCH_HEAD} không kèm chi là một
 *                  Trưởng chi có quyền trên cả họ, tức là không còn là Trưởng chi nữa
 * @param validFrom hiệu lực từ; {@code null} = ngay lập tức
 * @param validTo   hết hiệu lực; {@code null} = vô thời hạn
 * @param note      lý do cấp, ví dụ số nghị quyết của Hội đồng Tộc biểu
 */
public record GrantRoleCommand(UUID appUserId, RoleCode role, UUID branchId,
                               LocalDate validFrom, LocalDate validTo, String note) {
}
