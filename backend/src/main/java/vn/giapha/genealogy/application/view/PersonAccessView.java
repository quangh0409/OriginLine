package vn.giapha.genealogy.application.view;

import vn.giapha.genealogy.application.CallerRole;
import vn.giapha.genealogy.application.VisibleTier;

/**
 * Ngữ cảnh <b>quyền của người gọi</b> với một hồ sơ, để giao diện render đúng affordance thay vì
 * bấm thử rồi ăn 403.
 *
 * <p>Cố ý chỉ nói về <i>người gọi làm được gì</i>, không nói <i>dữ liệu nào đang bị giấu</i> —
 * nói điều thứ hai là tự tay tiết lộ thứ mà việc lọc đang cố giấu.</p>
 */
public record PersonAccessView(VisibleTier visibleTier, boolean canEdit, boolean canDelete,
                               boolean canRequestCorrection, boolean isSelf, CallerRole callerRole) {
}
