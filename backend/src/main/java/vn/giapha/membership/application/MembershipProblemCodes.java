package vn.giapha.membership.application;

/**
 * Mã lỗi máy đọc trả ở thuộc tính {@code code} của RFC 7807.
 *
 * <p>Dùng lại <b>nguyên văn</b> ba mã đã có trong {@code contracts/openapi.yaml}
 * ({@code FORBIDDEN}, {@code BRANCH_SCOPE_VIOLATION}, {@code NOT_FOUND},
 * {@code VALIDATION_FAILED}) thay vì bịa mã mới: giao diện phân nhánh xử lý theo {@code code}, nên
 * một mã lạ làm hỏng client mà không ai thấy lỗi biên dịch. Bốn mã cuối là mã <b>mới</b> của W6 và
 * phải được bổ sung vào enum {@code ProblemCode} của contract trước khi frontend dựa vào chúng.</p>
 */
public final class MembershipProblemCodes {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String FORBIDDEN = "FORBIDDEN";

    /** Đủ vai trò nhưng đối tượng nằm ngoài phạm vi {@code ltree} được giao. */
    public static final String BRANCH_SCOPE_VIOLATION = "BRANCH_SCOPE_VIOLATION";

    public static final String NOT_FOUND = "NOT_FOUND";

    // --- Mã mới của W6, cần bổ sung vào contracts/openapi.yaml ---

    /** Yêu cầu đính chính đã được duyệt/từ chối/rút — không xử lý lại. */
    public static final String CHANGE_REQUEST_CLOSED = "CHANGE_REQUEST_CLOSED";

    /** Người duyệt chính là người gửi. */
    public static final String SELF_REVIEW_FORBIDDEN = "SELF_REVIEW_FORBIDDEN";

    /** Token hợp lệ nhưng chưa có dòng {@code app_user} tương ứng. */
    public static final String ACCOUNT_NOT_PROVISIONED = "ACCOUNT_NOT_PROVISIONED";

    /** Tài khoản đang bị khoá hoặc chưa được duyệt. */
    public static final String ACCOUNT_NOT_ACTIVE = "ACCOUNT_NOT_ACTIVE";

    /** Phân công vai trò không hợp lệ, ví dụ {@code BRANCH_HEAD} mà không kèm chi. */
    public static final String INVALID_ROLE_ASSIGNMENT = "INVALID_ROLE_ASSIGNMENT";

    private MembershipProblemCodes() {
    }
}
