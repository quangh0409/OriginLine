package vn.giapha.membership.domain.port;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.BranchAssignment;
import vn.giapha.membership.domain.RoleCode;

/**
 * Lưu trữ phân công vai trò theo phạm vi chi/ngành — <b>nguồn chân lý của chiều phân quyền thứ
 * hai</b>.
 *
 * <p>Mọi truy vấn ở đây phải trả kèm {@code branch.path} đã phân giải: bên gọi cần một
 * {@code BranchPath} để so {@code ltree}, và bắt nó tự đi tra lần nữa là mời gọi mỗi nơi tự nghĩ
 * ra một cách so khác nhau.</p>
 */
public interface BranchAssignmentRepository {

    /**
     * Các phân công <b>còn hiệu lực</b> của một tài khoản vào ngày {@code on}.
     *
     * <p>Lọc hiệu lực ở tầng truy vấn chứ không ở Java: một nhiệm kỳ đã hết mà vẫn được nạp lên
     * rồi mới lọc là một lần nữa cơ hội để ai đó quên lọc.</p>
     */
    List<BranchAssignment> activeFor(UUID appUserId, LocalDate on);

    /** Toàn bộ phân công của một tài khoản, kể cả đã hết hiệu lực — dùng cho màn hình quản trị. */
    List<BranchAssignment> allFor(UUID appUserId);

    Optional<BranchAssignment> byId(UUID id);

    /** Các tài khoản đang giữ một vai trong phạm vi một chi cụ thể ({@code branchId} có thể null). */
    List<BranchAssignment> byRoleAndBranch(RoleCode role, UUID branchId, LocalDate on);

    BranchAssignment save(BranchAssignment assignment);

    /**
     * Thu hồi = xoá dòng phân công.
     *
     * <p>Đây là <b>ngoại lệ có chủ ý</b> của quy tắc "chỉ xoá mềm": quy tắc ấy bảo vệ các node phả
     * hệ khỏi bị đứt liên kết, còn một dòng cấp quyền thì càng biến mất sớm càng tốt. Vết của việc
     * thu hồi nằm ở {@code audit_log} với hành động {@code REVOKE_ROLE}, không nằm ở một cột cờ mà
     * ai đó có thể quên lọc.</p>
     */
    void delete(UUID assignmentId);
}
