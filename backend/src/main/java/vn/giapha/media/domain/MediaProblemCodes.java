package vn.giapha.media.domain;

/**
 * Mã lỗi máy đọc trả ở thuộc tính {@code code} của RFC 7807.
 *
 * <p>Cùng quy ước với {@code ContentProblemCodes}: giao diện phân nhánh theo {@code code}, không
 * theo mã HTTP, nên mỗi mã ở đây phải ứng với một <b>lối đi tiếp khác nhau</b> của người dùng. Bốn
 * mã đầu trùng từng ký tự với các context khác — cố ý, vì chúng mang đúng nghĩa ấy.</p>
 *
 * <p>Lớp này nằm ở {@code domain} chứ không ở {@code application} vì {@link MediaSignature} và
 * {@link VideoHeaderProbe} — hai lớp thuần tính toán, không phụ thuộc Spring — cần nó để ném ngoại
 * lệ kèm mã. Đẩy nó lên {@code application} sẽ tạo một mũi tên phụ thuộc ngược chiều.</p>
 */
public final class MediaProblemCodes {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String BRANCH_SCOPE_VIOLATION = "BRANCH_SCOPE_VIOLATION";
    public static final String NOT_FOUND = "NOT_FOUND";

    // --- Mã riêng của context này ---

    /**
     * Những byte đầu của tệp không khớp định dạng nào được nhận — HTTP <b>422</b>.
     *
     * <p>Lối đi tiếp: <i>chọn hoặc xuất lại tệp</i>. Khác hẳn {@link #VALIDATION_FAILED} (sửa
     * JSON) và khác hẳn {@link #MEDIA_TOO_LARGE} (nén hoặc cắt ngắn).</p>
     */
    public static final String MEDIA_BAD_SIGNATURE = "MEDIA_BAD_SIGNATURE";

    /** Vượt trần dung lượng — HTTP <b>413</b>. Lối đi tiếp: nén/xuất lại ở độ phân giải thấp hơn. */
    public static final String MEDIA_TOO_LARGE = "MEDIA_TOO_LARGE";

    /** Vượt trần thời lượng — HTTP <b>422</b>. Lối đi tiếp: cắt ngắn clip. */
    public static final String MEDIA_TOO_LONG = "MEDIA_TOO_LONG";

    /**
     * Không đọc được thời lượng từ header container — HTTP <b>422</b>.
     *
     * <p>Mã riêng vì lối đi tiếp riêng: <i>xuất lại tệp bằng một công cụ khác</i>. Gộp vào
     * {@link #MEDIA_BAD_SIGNATURE} thì giao diện sẽ bảo người dùng "tệp không phải video" trong khi
     * nó đúng là video — một câu sai làm người ta đi tìm nhầm chỗ.</p>
     */
    public static final String MEDIA_DURATION_UNKNOWN = "MEDIA_DURATION_UNKNOWN";

    /**
     * Xác nhận một phiếu mà <b>trên kho không có đối tượng nào</b> — HTTP <b>409</b>.
     *
     * <p>Đây là bất biến lớn nhất của đường này thành mã lỗi: tệp chỉ có thật sau khi backend tự
     * thấy nó. Lối đi tiếp: tải tệp lên URL đã ký <i>rồi mới</i> gọi xác nhận.</p>
     */
    public static final String MEDIA_NOT_UPLOADED = "MEDIA_NOT_UPLOADED";

    /** Phiếu đã quá hạn, hoặc đã xác nhận rồi, hoặc tệp đã bị gỡ — HTTP <b>409</b>. */
    public static final String MEDIA_TICKET_CLOSED = "MEDIA_TICKET_CLOSED";

    /** Gắn quá {@code MediaLimits.MAX_MEDIA_PER_POST} tệp vào một bài — HTTP <b>422</b>. */
    public static final String MEDIA_TOO_MANY = "MEDIA_TOO_MANY";

    /**
     * Gắn một tệp mà người gọi không phải người đã tải nó lên — HTTP <b>403</b>.
     *
     * <p>Chặn một lối lạm dụng cụ thể: đoán khoá của một tệp người khác vừa tải lên rồi gắn nó
     * vào bài của mình.</p>
     */
    public static final String MEDIA_NOT_OWNED = "MEDIA_NOT_OWNED";

    /** Đơn báo gỡ đã đóng — HTTP <b>409</b>. */
    public static final String REPORT_CLOSED = "REPORT_CLOSED";

    /** Người này đã có một đơn đang mở cho đúng tệp ấy — HTTP <b>409</b>. */
    public static final String REPORT_DUPLICATE = "REPORT_DUPLICATE";

    private MediaProblemCodes() {
    }
}
