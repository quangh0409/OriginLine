package vn.giapha.content.application;

/**
 * Mã lỗi máy đọc trả ở thuộc tính {@code code} của RFC 7807.
 *
 * <h2>Dùng lại nguyên văn mã đã có, đẻ mã mới chỉ khi nó dẫn tới một hành động khác</h2>
 * Giao diện phân nhánh xử lý theo {@code code} (contracts §3), nên một mã lạ làm hỏng client mà
 * không ai thấy lỗi biên dịch, còn hai mã cho cùng một câu trả lời thì bắt giao diện viết hai
 * nhánh giống hệt nhau. Sáu mã đầu ở đây trùng <b>từng ký tự</b> với
 * {@code GenealogyProblemCodes} / {@code MembershipProblemCodes} — cố ý, vì chúng mang đúng nghĩa
 * ấy. Chúng được nhân bản thay vì {@code import} chéo context: giá trị chuỗi là hợp đồng, và hợp
 * đồng có quyền có nhiều bản sao; một cạnh phụ thuộc giữa hai context chỉ để chia nhau một hằng số
 * thì không.
 */
public final class ContentProblemCodes {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String FORBIDDEN = "FORBIDDEN";

    /** Đủ vai nhưng đối tượng nằm ngoài phạm vi {@code ltree} được giao. */
    public static final String BRANCH_SCOPE_VIOLATION = "BRANCH_SCOPE_VIOLATION";

    public static final String NOT_FOUND = "NOT_FOUND";

    /** Hai người cùng sửa một bản ghi — HTTP 409. */
    public static final String OPTIMISTIC_LOCK_CONFLICT = "OPTIMISTIC_LOCK_CONFLICT";

    /** Thiếu hoặc sai {@code If-Match} khi sửa — HTTP 412. */
    public static final String PRECONDITION_REQUIRED = "PRECONDITION_REQUIRED";

    /** Người duyệt chính là người soạn/khai — HTTP 403. Cùng nghĩa với mã của luồng đính chính. */
    public static final String SELF_REVIEW_FORBIDDEN = "SELF_REVIEW_FORBIDDEN";

    // --- Mã mới của context này ---

    /**
     * Tài khoản <b>chưa được duyệt vào phả</b> ({@code app_user.person_id IS NULL}) — HTTP 403.
     *
     * <h2>Vì sao có mã riêng, không dùng {@link #FORBIDDEN} hay {@code ACCOUNT_NOT_PROVISIONED}</h2>
     * Ba tình huống, ba lối đi tiếp hoàn toàn khác nhau, và giao diện phải nói đúng cái nào:
     * <ul>
     *   <li>{@code ACCOUNT_NOT_PROVISIONED} — chưa có dòng {@code app_user}: "hãy đăng nhập lại";</li>
     *   <li>{@code FORBIDDEN} — sai vai: "bạn không có quyền này";</li>
     *   <li>mã này — <i>đã</i> đăng nhập, <i>đã</i> có tài khoản, chỉ là chưa ai gắn tài khoản ấy
     *       với một ô trên phả đồ. Lối đi tiếp rất cụ thể và giao diện dẫn được: mở màn "tôi là ai
     *       trong phả" rồi chờ Trưởng chi duyệt.</li>
     * </ul>
     *
     * <p>Đây là quyết định đã chốt của chủ dự án, không phải một phép kiểm kỹ thuật: "viết bài là
     * tiếng nói của người trong họ". Một người vừa cầm mã mời dòng họ vào xem phả <b>chưa</b> là
     * người trong họ theo nghĩa ấy.</p>
     */
    public static final String AUTHOR_NOT_IN_PHA = "AUTHOR_NOT_IN_PHA";

    /**
     * Bản ghi đã ở trạng thái cuối, hoặc mũi tên chuyển trạng thái không tồn tại — HTTP 409.
     *
     * <p>Một mã cho cả hai vì cả hai dẫn tới cùng một lối đi: <b>tải lại rồi xem trạng thái hiện
     * tại</b>. Gần như mọi lần gặp mã này là do hai người mở cùng một bài và người kia bấm trước —
     * bấm lại không giúp gì, nhìn lại thì có.</p>
     */
    public static final String CONTENT_CLOSED = "CONTENT_CLOSED";

    private ContentProblemCodes() {
    }
}
