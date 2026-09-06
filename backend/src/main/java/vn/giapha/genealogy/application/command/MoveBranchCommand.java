package vn.giapha.genealogy.application.command;

import java.util.UUID;

/**
 * Chuyển một nhân khẩu sang chi/ngành khác.
 *
 * <p>Người gọi phải có quyền ở <b>cả chi cũ lẫn chi mới</b>. Chỉ kiểm chi đích thì một Trưởng chi
 * có thể kéo người của chi khác về chi mình, tức là tự cấp quyền cho chính mình.</p>
 */
public record MoveBranchCommand(UUID personId, UUID targetBranchId, String note) {
}
