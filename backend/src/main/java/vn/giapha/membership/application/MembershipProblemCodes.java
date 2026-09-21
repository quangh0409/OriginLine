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

    // --- Mã mới của luồng MÃ MỜI DÒNG HỌ và ĐƠN TỰ NHẬN (V16) ---

    /**
     * Mã mời dòng họ đã <b>dùng hết trần lượt</b> mà Hội đồng đặt — HTTP 409.
     *
     * <h2>Vì sao ca này có mã riêng, còn "hết hạn"/"đã thu hồi" thì dùng lại mã của luồng cá nhân</h2>
     * {@link #INVITATION_EXPIRED} và {@link #INVITATION_REVOKED} nói đúng điều cần nói và dẫn tới
     * đúng màn hình ấy: "mã hết hạn, hỏi lại người đưa mã". Người dùng không phân biệt được — và
     * không cần phân biệt — mã cá nhân với mã dòng họ; họ chỉ cầm một dãy ký tự. Đẻ thêm hai mã
     * song song là bắt giao diện viết hai nhánh cho cùng một câu trả lời.
     *
     * <p>"Hết lượt" thì khác thật: nó <b>không</b> tồn tại ở mã cá nhân (mã cá nhân một lần là một
     * lần, và ca ấy đã có {@link #INVITATION_ALREADY_USED}), và nó dẫn tới một câu khác hẳn — mã
     * vẫn còn hạn, vẫn chưa bị thu hồi, chỉ là Hội đồng đã đặt trần và trần ấy đầy.</p>
     */
    public static final String CLAN_INVITE_EXHAUSTED = "CLAN_INVITE_EXHAUSTED";

    /**
     * Đơn tự nhận đã được duyệt/từ chối/rút — không xử lý lại. HTTP 409.
     *
     * <p>Cố ý <b>không</b> dùng lại {@link #CHANGE_REQUEST_CLOSED}: hai thực thể khác nhau, hai màn
     * hình khác nhau, và giao diện rẽ nhánh theo {@code code}. Một mã lỗi nói "yêu cầu đính chính
     * đã đóng" xuất hiện trên màn duyệt đơn nhận người là một thông điệp sai.</p>
     */
    public static final String CLAIM_CLOSED = "CLAIM_CLOSED";

    /**
     * Nhân khẩu được nhận <b>không nhận đơn được</b> — HTTP 422.
     *
     * <h2>MỘT mã cho BA lý do, và đó là điểm của nó</h2>
     * Người đã khuất · nhân khẩu đã có tài khoản · nhân khẩu đã xoá mềm. Gộp ba lý do vào một mã là
     * thứ giữ cho màn tự nhận <b>không thành công cụ liệt kê ai đã có tài khoản</b>: gửi thử lần
     * lượt từng ô trên phả đồ rồi đọc mã lỗi sẽ ra danh sách những người <i>chưa</i> đăng ký — tức
     * danh sách để mạo danh, và với 1.500 người thì nó có giá trị thật.
     *
     * <p><b>Vậy vì sao vẫn tách khỏi {@link #VALIDATION_FAILED}?</b> Vì bốn tình huống đang dùng
     * chung một mã dẫn tới <i>bốn hành động tiếp theo khác nhau</i>, và giao diện buộc phải suy ra
     * bằng cách nhìn luồng nào đang chạy — đúng hôm nay, âm thầm sai ngày có phép kiểm thứ năm dùng
     * lại mã ấy. Tách ra không nói thêm gì về <i>người bị nhận</i>; nó chỉ nói cho client biết cái
     * ô người dùng vừa chọn là thứ không dùng được.</p>
     *
     * <p><b>Và vì sao không có endpoint kiểm trước.</b> Bất cứ thứ gì trả lời "node này có nhận đơn
     * không" <i>chính là</i> công cụ liệt kê ấy, chỉ khác chỗ nó rẻ hơn — không tốn một lượt, không
     * để lại một dòng đơn. Lý do này đã được nêu và chấp nhận; đừng thêm lối kiểm trước dù giao
     * diện sẽ dễ hơn.</p>
     */
    public static final String CLAIM_TARGET_UNAVAILABLE = "CLAIM_TARGET_UNAVAILABLE";

    /**
     * Người thân được chỉ ra trong đơn "tôi chưa có trong phả" <b>không dùng để nối được</b> —
     * HTTP 422.
     *
     * <p>Đã xoá mềm, hoặc chưa được gắn vào chi nào. Ca thứ hai đáng nói thẳng: người dùng không
     * sửa được, nhưng Trưởng chi thì sửa được — và một câu mơ hồ ở đây sẽ khiến họ không bao giờ
     * biết cần sửa gì.</p>
     *
     * <p>Tách khỏi {@link #CLAIM_TARGET_UNAVAILABLE} vì hai luồng dẫn tới hai màn hình khác nhau:
     * một bên là "chọn ô khác trên phả đồ", bên kia là "chọn người thân khác".</p>
     */
    public static final String CLAIM_RELATIVE_UNUSABLE = "CLAIM_RELATIVE_UNUSABLE";

    /**
     * Người gửi đang có một đơn chờ duyệt — HTTP 409.
     *
     * <p>Một tài khoản chỉ có một đơn đang chờ ({@code ux_person_claim_open_requester}). Ca này có
     * mã riêng vì hành động tiếp theo rất cụ thể và giao diện <b>làm hộ được</b>: rút đơn cũ
     * ({@code POST /person-claims/&#123;id&#125;/cancel}) rồi gửi lại. Rút <b>không</b> tính vào
     * giới hạn gửi lại.</p>
     */
    public static final String CLAIM_ALREADY_OPEN = "CLAIM_ALREADY_OPEN";

    /**
     * Người gửi đã bị từ chối quá số lần cho phép — HTTP 422.
     *
     * <p>Design 07 §1.4: bị từ chối thì gửi lại được, <b>nhưng có giới hạn số lần</b>. Không giới
     * hạn thì màn này thành cách dò đúng người bằng cách thử lần lượt — gửi đơn nhận ông A, bị từ
     * chối, gửi tiếp ông B, cho tới khi trúng. Lối đi tiếp cho người dùng thật là gọi Trưởng chi,
     * nên thông điệp phải nói ra điều đó chứ không chỉ nói "không được".</p>
     */
    public static final String CLAIM_LIMIT_REACHED = "CLAIM_LIMIT_REACHED";

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

    /**
     * Định danh (email / số điện thoại) tự khai <b>đã có tài khoản</b> trong realm — HTTP 422.
     *
     * <h2>Đây là một phép chặn an ninh, không phải một phép kiểm dữ liệu</h2>
     * Lối đăng ký bằng mã dòng họ <b>không đòi đăng nhập</b>, nên thứ duy nhất người gọi trình ra
     * là một mã mà cả họ đang cầm cộng một chuỗi họ tự gõ. Chuỗi ấy <b>không</b> chứng minh được
     * họ sở hữu địa chỉ thư đó. Nếu địa chỉ ấy đã thuộc về một tài khoản có thật thì mọi thao tác
     * tiếp theo (đúc liên kết đặt mật khẩu, làm tươi hồ sơ) là thao tác trên tài sản của người
     * khác — xem {@code ClanInviteService#register}.
     *
     * <p>Lối đi tiếp cho người dùng thật: <b>đăng nhập rồi nhập lại mã</b>. Nhánh "đã có token"
     * của cùng endpoint xử lý trọn ca ấy, và ở đó danh tính là do Keycloak chứng nhận chứ không
     * phải do người gọi tự khai.</p>
     *
     * <p><b>Nó có là một máy dò tài khoản không?</b> Có, ở mức hẹp nhất còn lại: người gọi biết
     * được "địa chỉ này đã đăng ký". Đổi lại nó được <b>tính vào giới hạn tần suất</b> như một lần
     * thất bại, nên dò cả danh bạ dòng họ là việc không làm được trong một cửa sổ. Trả một phản
     * hồi thành công giả cũng không xoá được tín hiệu ấy — nó chỉ chuyển tín hiệu sang chỗ khác,
     * và đổi lại bằng một lời nói dối với người dùng thật.</p>
     */
    public static final String IDENTITY_ALREADY_REGISTERED = "IDENTITY_ALREADY_REGISTERED";

    private MembershipProblemCodes() {
    }
}
