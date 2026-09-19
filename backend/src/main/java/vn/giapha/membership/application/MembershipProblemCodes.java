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

    // --- Mã mới của luồng mời (V15) ---

    /**
     * Mã mời đã <b>hết hạn</b> — HTTP 410.
     *
     * <h2>Vì sao ba ca hỏng có ba mã riêng, chứ không một mã chung kèm thuộc tính "lý do"</h2>
     * Bản dựng đầu của luồng này dùng đúng một mã ({@code INVITE_NOT_USABLE}) cho cả bốn ca, với lý
     * do đi ở thuộc tính mở rộng. Lập luận khi ấy: phân biệt "mã không tồn tại" với "mã hết hạn" là
     * biến endpoint tra mã thành máy xác nhận mã tồn tại. Lập luận đó <b>tự mâu thuẫn</b> — thuộc
     * tính "lý do" cũng phân biệt y hệt, chỉ khác chỗ đặt — và nó trả giá bằng việc bắt giao diện
     * rẽ nhánh theo một trường ngoài {@code code}, trong khi cả dự án đã chốt "client phân nhánh
     * theo {@code code}".
     *
     * <p>Cái mà lập luận cũ định bảo vệ thì gần như vô giá trị ở đây: mã do CSPRNG sinh, 50 bit, và
     * có giới hạn tần suất — không ai dò trúng để mà hỏi. Còn cái giá của việc giấu thì có thật: một
     * cụ cầm tờ phiếu thật đã quá hạn sẽ nhận đúng câu "mã không dùng được" và không biết phải làm
     * gì tiếp.</p>
     *
     * <p>Ca thứ tư — mã <b>không khớp</b> lời mời nào — cố ý <b>không</b> có mã riêng: nó dùng lại
     * {@link #NOT_FOUND} sẵn có, vì nó không mang thêm nghĩa nào.</p>
     */
    public static final String INVITATION_EXPIRED = "INVITATION_EXPIRED";

    /** Mã mời <b>đã được dùng</b> — HTTP 409. Bí mật một lần: dùng xong thì chết. */
    public static final String INVITATION_ALREADY_USED = "INVITATION_ALREADY_USED";

    /** Mã mời <b>đã bị thu hồi</b> — HTTP 410. Trưởng chi thu, hoặc người nhận bấm "Không phải tôi". */
    public static final String INVITATION_REVOKED = "INVITATION_REVOKED";

    /**
     * Nhân khẩu được mời <b>đã có tài khoản</b> ({@code ux_app_user_person}).
     *
     * <p>Phát hiện lúc <b>phát</b>, không phải lúc nhận: Trưởng chi phải biết ngay khi bấm, chứ
     * không phải sau khi đã in phiếu, gửi tin nhắn và chờ cụ bà gọi lại bảo không vào được.</p>
     */
    public static final String PERSON_ALREADY_LINKED = "PERSON_ALREADY_LINKED";

    /** Người đang nhận lời mời đã gắn với một nhân khẩu <b>khác</b> rồi. */
    public static final String ACCOUNT_ALREADY_LINKED = "ACCOUNT_ALREADY_LINKED";

    /** Thử mã quá nhiều lần trong một khoảng thời gian — xem {@code InviteThrottle}. */
    public static final String RATE_LIMITED = "RATE_LIMITED";

    // --- Mã mới của lối lập tài khoản Keycloak (cổng danh tính) ---

    /**
     * Không lập được tài khoản đăng nhập: Keycloak không với tới được, hoặc bí mật của client dịch
     * vụ chưa được cấu hình — HTTP <b>503</b>.
     *
     * <p>Cố ý <b>không</b> gộp vào {@code VALIDATION_FAILED}: người dùng không sửa được gì cả, và
     * câu trả lời đúng cho họ là "thử lại sau", không phải "kiểm tra lại dữ liệu bạn nhập". Quan
     * trọng hơn, khi mã này xuất hiện thì <b>mã mời chưa bị đánh dấu đã dùng</b> — bấm lại là được.</p>
     */
    public static final String IDENTITY_PROVIDER_UNAVAILABLE = "IDENTITY_PROVIDER_UNAVAILABLE";

    /**
     * Liên kết đặt mật khẩu sai chữ ký, quá hạn, hoặc đã dùng rồi — HTTP <b>410</b>.
     *
     * <p>Ba lý do dùng chung một mã vì cả ba dẫn tới cùng một lối đi tiếp: xin Trưởng chi phát lại
     * lời mời. Tách ra ba mã chỉ nói cho kẻ dò biết mình đang sai ở đâu.</p>
     */
    public static final String SET_PASSWORD_LINK_INVALID = "SET_PASSWORD_LINK_INVALID";

    private MembershipProblemCodes() {
    }
}
