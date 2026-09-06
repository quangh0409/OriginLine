package vn.giapha.genealogy.application;

/**
 * Mã lỗi máy đọc trả ra ở thuộc tính {@code code} của RFC 7807.
 *
 * <p><b>Danh sách đóng.</b> Giá trị ở đây phải khớp từng ký tự với enum {@code ProblemCode} trong
 * {@code contracts/openapi.yaml}: giao diện phân nhánh xử lý theo {@code code} chứ không theo
 * {@code detail}, nên bịa thêm một mã mới là làm hỏng client mà không ai thấy lỗi biên dịch.
 * GraphQL dùng đúng tập mã này trong {@code errors[].extensions.code}.</p>
 *
 * <p>Cố ý <b>không</b> dùng các factory sẵn có của {@code shared.exception} (chúng sinh mã dạng
 * {@code person.not-found}): mã của contract mới là thứ client đọc.</p>
 */
public final class GenealogyProblemCodes {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String FORBIDDEN = "FORBIDDEN";

    /** Đủ vai trò nhưng đối tượng nằm ngoài phạm vi {@code ltree} được giao. */
    public static final String BRANCH_SCOPE_VIOLATION = "BRANCH_SCOPE_VIOLATION";

    public static final String NOT_FOUND = "NOT_FOUND";

    /** Trùng tên húy bậc trên - ghi đè được bằng {@code confirmTabooOverride}. */
    public static final String KY_HUY_CONFLICT = "KY_HUY_CONFLICT";

    public static final String DUPLICATE_PERSON_SUSPECTED = "DUPLICATE_PERSON_SUSPECTED";

    /** Quan hệ cha-con sẽ tạo chu trình: A là tổ tiên của chính A. */
    public static final String RELATIONSHIP_CYCLE = "RELATIONSHIP_CYCLE";

    public static final String INVALID_RELATIONSHIP = "INVALID_RELATIONSHIP";
    public static final String PERSON_ALREADY_DELETED = "PERSON_ALREADY_DELETED";
    public static final String OPTIMISTIC_LOCK_CONFLICT = "OPTIMISTIC_LOCK_CONFLICT";
    public static final String PRECONDITION_REQUIRED = "PRECONDITION_REQUIRED";
    public static final String DEPTH_LIMIT_EXCEEDED = "DEPTH_LIMIT_EXCEEDED";

    private GenealogyProblemCodes() {
    }
}
