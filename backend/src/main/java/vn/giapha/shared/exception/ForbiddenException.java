package vn.giapha.shared.exception;

/**
 * Đã xác thực nhưng không đủ thẩm quyền — ánh xạ sang HTTP 403.
 *
 * <p>Dùng cho cả hai chiều phân quyền: role (Admin / Hội đồng Tộc biểu / Trưởng chi / Thành viên)
 * và <b>phạm vi chi–ngành</b> theo {@code ltree}. Không tiết lộ dữ liệu của tài nguyên trong thông
 * điệp lỗi — người sống thuộc chi khác phải trông như không tồn tại với người không có quyền.</p>
 */
public class ForbiddenException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException(String code, String message) {
        super(code, message);
    }

    public static ForbiddenException outOfBranchScope(String branchPath) {
        return new ForbiddenException(
                "BRANCH_SCOPE_VIOLATION",
                "Tai khoan khong co quyen tren pham vi chi/nganh '" + branchPath + "'");
    }
}
