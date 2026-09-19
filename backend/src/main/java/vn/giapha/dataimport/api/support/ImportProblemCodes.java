package vn.giapha.dataimport.api.support;

/**
 * Mã lỗi máy đọc trả ở thuộc tính {@code code} của RFC 7807 cho nhóm {@code /api/v1/import/**}.
 *
 * <h2>Dùng lại trước, đặt mới sau</h2>
 * Hai mã dưới đây <b>không</b> mang tiền tố {@code IMP_} vì chúng đã có trong
 * {@code contracts/openapi.yaml} từ W6 và giao diện đã phân nhánh theo chúng:
 * {@code BRANCH_SCOPE_VIOLATION} và {@code ACCOUNT_NOT_PROVISIONED}. Đặt thêm
 * {@code IMP_BRANCH_SCOPE_VIOLATION} cho cùng một tình huống là nhân đôi ngữ nghĩa — client sẽ
 * phải nhớ hai mã cho một câu trả lời, và sớm muộn sẽ quên một mã.
 *
 * <h2>Mã nào là mã của tệp, mã nào là mã của lô</h2>
 * Nhóm <b>từ chối ở cửa</b> ({@code IMP_BAD_FORMAT}, {@code IMP_FILE_TOO_LARGE},
 * {@code IMP_TOO_MANY_ROWS}, {@code IMP_MISSING_SHEET}, {@code IMP_MISSING_COLUMN},
 * {@code IMP_UNSAFE_FILE}, {@code IMP_CORRUPT_FILE}, {@code IMP_ALREADY_COMMITTED}) do
 * {@code domain.ImportRejectedException} phát ra — <b>chưa có dòng nào vào khu vực chờ</b>, nên
 * giao diện phải hiện một câu xin lỗi chứ không phải bảng lỗi.
 *
 * <p>Nhóm <b>cổng duyệt</b> ({@code IMP_BLOCKING_ISSUES_PRESENT},
 * {@code IMP_WARNINGS_NOT_ACKNOWLEDGED}, {@code IMP_DUPLICATES_UNDECIDED},
 * {@code IMP_BATCH_CLOSED}) nói rằng lô đọc được, dữ liệu đã nằm ở khu vực chờ, nhưng chưa đủ điều
 * kiện ghi. Giao diện hiện bảng đối soát.</p>
 */
public final class ImportProblemCodes {

    // --- Đã có trong hợp đồng từ W6 — KHÔNG đặt bản sao mang tiền tố IMP_ ---

    /** Đủ vai nhưng lô thuộc chi nằm ngoài phạm vi {@code ltree} được giao. */
    public static final String BRANCH_SCOPE_VIOLATION = "BRANCH_SCOPE_VIOLATION";

    /** Token Keycloak hợp lệ nhưng chưa có dòng {@code app_user} tương ứng. */
    public static final String ACCOUNT_NOT_PROVISIONED = "ACCOUNT_NOT_PROVISIONED";

    public static final String FORBIDDEN = "FORBIDDEN";

    // --- Từ chối ở cửa: các hằng của ImportRejectedException, nhắc lại để đọc được ở tầng api ---

    /**
     * Đã có một lô {@code COMMITTED} của cùng chi mang đúng mã băm này.
     *
     * <p><b>Giao diện đang gọi nó là {@code IMP_DUPLICATE_FILE}</b> — tên ấy sai theo nghĩa hẹp và
     * sai theo nghĩa rộng: chốt này không nói "tệp trùng" mà nói "tệp này <i>đã được ghi vào phả</i>
     * rồi". Tải lại một tệp giống hệt tệp mới chỉ <i>kiểm</i> chứ chưa ghi thì <b>không</b> bị chặn,
     * và đó là bước 4 của quy trình đối soát.</p>
     */
    public static final String ALREADY_COMMITTED = "IMP_ALREADY_COMMITTED";

    // --- Cổng duyệt ---

    /** Bấm duyệt khi còn lỗi chặn. Backend chặn lại dù giao diện đã ẩn nút. */
    public static final String BLOCKING_ISSUES_PRESENT = "IMP_BLOCKING_ISSUES_PRESENT";

    /** Còn cảnh báo mà người nhập chưa tick "tôi đã xem hết phần cần xem lại". */
    public static final String WARNINGS_NOT_ACKNOWLEDGED = "IMP_WARNINGS_NOT_ACKNOWLEDGED";

    /** Còn cặp nghi trùng chưa ai quyết — máy nghi ngờ, người quyết định. */
    public static final String DUPLICATES_UNDECIDED = "IMP_DUPLICATES_UNDECIDED";

    /**
     * Lô đã chốt ({@code COMMITTED} / {@code SUPERSEDED}) — gửi lô mới, đừng sửa lô cũ.
     *
     * <p>{@code SUPERSEDED} nằm trong nhóm này là cố ý: khi Trưởng chi tải lên lần thứ hai, lô cũ
     * chuyển {@code SUPERSEDED} ngay trong giao dịch ấy. Cho phép duyệt một lô đã bị thay thế là
     * mời gọi hai tab trình duyệt ghi hai phiên bản khác nhau của cùng một chi.</p>
     */
    public static final String BATCH_CLOSED = "IMP_BATCH_CLOSED";

    private ImportProblemCodes() {
    }
}
