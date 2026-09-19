package vn.giapha.dataimport.domain;

import static vn.giapha.dataimport.domain.IssueSeverity.BLOCKING;
import static vn.giapha.dataimport.domain.IssueSeverity.WARNING;

/**
 * Mã lỗi/cảnh báo của bộ kiểm — <b>mã máy đọc được</b>, đi kèm một câu tiếng Việt do luật sinh ra.
 *
 * <p>Mức nghiêm trọng gắn cứng vào mã, không phải tham số truyền vào: một mã phải luôn nằm cùng
 * một nhóm ở mọi chỗ, nếu không thì cùng một vấn đề lúc chặn lúc không và người nhập mất lòng tin
 * vào bộ kiểm.</p>
 *
 * <h2>Phân nhóm — đọc lý do trước khi đổi mức của một mã</h2>
 * Lỗi chặn là <b>mâu thuẫn nội tại</b> của dữ liệu: chắc chắn sai, và chỉ ra được sai ở đâu.
 * Cảnh báo là <b>nghi ngờ</b>: máy không đủ căn cứ, chỉ người mở sổ ra mới trả lời được.
 */
public enum IssueCode {

    // ---------------------------------------------------------------------------------------
    // Lỗi chặn
    // ---------------------------------------------------------------------------------------

    /** Mã cha/mẹ không có trong tệp, cũng không có trong {@code person_external_ref} của chi. */
    IMP_PARENT_NOT_FOUND(BLOCKING),

    /** Vòng lặp tổ tiên: A là cha của B, B lại là tổ tiên của A (có thể bắc qua nhiều đời). */
    IMP_CYCLE(BLOCKING),

    /** Một dòng khai chính nó là cha/mẹ của chính nó. */
    IMP_SELF_PARENT(BLOCKING),

    /** Năm sinh con nhỏ hơn năm sinh cha/mẹ. */
    IMP_CHILD_BEFORE_PARENT(BLOCKING),

    /** Ngày âm không tồn tại: mùng 30 của tháng thiếu, tháng nhuận của năm không nhuận tháng ấy. */
    IMP_LUNAR_DATE_NOT_EXIST(BLOCKING),

    /** Ô Ngày mất âm không đọc được thành ngày âm. */
    IMP_LUNAR_DATE_UNPARSEABLE(BLOCKING),

    /**
     * Ô Ngày mất âm đến tay server dưới dạng <b>số sê-ri của Excel</b>.
     *
     * <p>Cái bẫy tốn thời gian nhất của cả trang Nhân khẩu: để định dạng mặc định thì Excel tự
     * nuốt {@code 15/8} thành một ngày <b>dương lịch</b> của năm hiện tại và gửi đi một con số.
     * Đoán ngược lại là đoán ngày giỗ — tuyệt đối không. Báo cho người nhập định dạng cột thành
     * Văn bản rồi gõ lại.</p>
     */
    IMP_LUNAR_DATE_IS_SERIAL(BLOCKING),

    /** Hai dòng cùng một Mã trong một tệp. Không có cách đoán đúng dòng nào. */
    IMP_DUP_CODE(BLOCKING),

    /** Dòng có dữ liệu nhưng bỏ trống cột Mã. */
    IMP_MISSING_CODE(BLOCKING),

    /** Dòng có Mã nhưng bỏ trống Họ tên. */
    IMP_MISSING_NAME(BLOCKING),

    /** Mã sai dạng, hoặc tiền tố không thuộc chi đang nhập — vừa là lỗi dữ liệu vừa là ranh giới phân quyền. */
    IMP_BAD_CODE_FORMAT(BLOCKING),

    /** Còn sống = Có nhưng vẫn có Ngày mất âm — trùng đúng ràng buộc {@code ck_person_alive_vs_death}. */
    IMP_ALIVE_WITH_DEATH(BLOCKING),

    /** Đời khai khác Đời của cha cộng một. */
    IMP_GENERATION_MISMATCH(BLOCKING),

    /** Hai người vợ cùng Bậc trên cùng một người chồng — trùng {@code ux_relationship_spouse_order}. */
    IMP_SPOUSE_ORDER_CONFLICT(BLOCKING),

    /** Tải lại cho một chi đã có dữ liệu mà quá nhiều dòng ra hành động CREATE. */
    IMP_MASS_CREATE_GUARD(BLOCKING),

    // ---------------------------------------------------------------------------------------
    // Lỗi chặn phát sinh Ở BƯỚC GHI, không phải ở bộ kiểm
    // ---------------------------------------------------------------------------------------
    // Bốn mã dưới đây do bước ghi vào phả sinh ra, và chúng dừng cả lô TRƯỚC khi một dòng nào
    // được viết. Chúng KHÔNG trùng lặp với bộ kiểm: bộ kiểm bắt mâu thuẫn nội tại của tệp, còn
    // đây là những câu hỏi mà **Hội đồng Tộc biểu chưa trả lời** (kế hoạch §14, ba ca cuối tài
    // liệu). Máy không được phép tự chọn một đáp án rồi ghi vào phả — một đời thứ đoán sai làm
    // lệch danh xưng của cả một cành, và nó chỉ lộ ra khi một cụ cao niên nhìn phả đồ ba tháng
    // sau.

    /**
     * Vòng lặp tổ tiên vẫn còn ở bước ghi.
     *
     * <p>{@code CycleRule} đã chặn ở bộ kiểm, nên gặp lại ở đây nghĩa là <b>bộ kiểm và bước ghi
     * bất đồng</b> — tệp đổi giữa hai bước, hoặc một luật bị vô hiệu. Phải chết rõ ràng chứ không
     * treo: một phép sắp topo trên đồ thị có chu trình sẽ không bao giờ xếp xong.</p>
     */
    IMP_COMMIT_CYCLE(BLOCKING),

    /**
     * Mã tham chiếu không phân giải được <b>tại thời điểm ghi</b>.
     *
     * <p>Bộ kiểm đã soát mã cha/mẹ, nhưng mã ở trang Hôn phối và mã kế tự thì chưa — và giữa lần
     * kiểm với lần bấm ghi có thể đã trôi qua vài ngày. Bỏ qua lặng lẽ một dòng như thế nghĩa là
     * một cặp vợ chồng, hoặc một quan hệ kế tự, biến mất khỏi phả mà không ai biết.</p>
     */
    IMP_COMMIT_REF_NOT_FOUND(BLOCKING),

    /**
     * Dâu/rể có khai <b>Đời</b> — chưa biết đó là đời của chồng hay đời trong họ gốc.
     *
     * <p>Kế hoạch §14 ca 2: lược đồ nhận số nào cũng được, và bốn chi sẽ trả lời bốn kiểu. Không
     * hoà giải được về sau, nên dừng ở đây và để Hội đồng chốt quy ước.</p>
     */
    IMP_UNDECIDED_INLAW_DOI(BLOCKING),

    /**
     * Con nuôi mà cha/mẹ nuôi là người <b>trong họ</b>.
     *
     * <p>Kế hoạch §14 ca 5: đứa trẻ có cả cha ruột lẫn cha nuôi, cả hai đều thật và đều cần ghi,
     * nhưng mẫu Excel chỉ chở được <b>một</b> cặp Mã cha/Mã mẹ. Ghi một người là âm thầm xoá người
     * kia khỏi phả.</p>
     */
    IMP_UNDECIDED_ADOPTION(BLOCKING),

    /**
     * Không rõ còn sống hay đã mất.
     *
     * <p>Kế hoạch §14 ca 9: {@code person.is_alive} là {@code BOOLEAN NOT NULL}, không có giá trị
     * thứ ba. Người tha hương mất liên lạc phải được Hội đồng chốt quy ước ghi thế nào; đoán
     * "còn sống" thì sinh một phả đồ toàn người sống từ đời thứ ba, đoán "đã mất" thì sinh giỗ cho
     * người chưa mất.</p>
     */
    IMP_UNDECIDED_LIFE_STATUS(BLOCKING),

    /**
     * Một nhóm được quyết là "gộp" nhưng <b>không áp dụng được</b>.
     *
     * <p>Ca duy nhất sinh ra mã này: các dòng được quyết là cùng một người lại dính tới <b>hai hồ
     * sơ khác nhau đã có trong phả</b>. Câu trả lời đúng lúc ấy là "hợp nhất hai hồ sơ đã có" —
     * một thao tác của màn quản lý nhân khẩu, có hoàn tác riêng — chứ không phải một phép gộp
     * trong đường nhập liệu.</p>
     *
     * <p>Đường ống <b>không tự chọn một trong hai</b>: chọn bừa ở đây là hợp nhất hai nhánh con
     * cháu vào một node sai, và sau ba mươi ngày thì không hoàn tác được nữa.</p>
     */
    IMP_MERGE_NOT_APPLICABLE(BLOCKING),

    // ---------------------------------------------------------------------------------------
    // Cảnh báo
    // ---------------------------------------------------------------------------------------

    /** Nghi trùng người. Máy chỉ nghi ngờ — anh em ruột trùng tên đệm theo đời là chuyện thường. */
    IMP_SUSPECT_DUPLICATE(WARNING),

    /** Trùng tên huý của bậc trên (FR-1.6). Kỵ húy là cảnh báo cho phép ghi đè có xác nhận. */
    IMP_TABOO_COLLISION(WARNING),

    /**
     * Người đã mất mà trống Ngày mất âm — <b>cảnh báo đáng giá nhất trong bảng này</b>.
     *
     * <p>Người đó sẽ nằm trong phả đồ nhưng <b>không bao giờ được nhắc giỗ</b>, mà nhắc giỗ là lý
     * do dòng họ mở ứng dụng.</p>
     */
    IMP_MISSING_GIO(WARNING),

    /** Ngày mất âm là 30 mà không rõ năm: tháng thiếu không có ngày 30, giỗ sẽ trôi về 29. */
    IMP_GIO_DAY_30(WARNING),

    /** Không cha, không mẹ, không vợ/chồng. Hợp lệ với thuỷ tổ; sai với mọi người khác. */
    IMP_LONE_NODE(WARNING),

    /** Thiếu giới tính. */
    IMP_UNKNOWN_GENDER(WARNING),

    /** Thiếu mẹ — con số này đo được độ đầy đủ của bên ngoại (FR-1.8). */
    IMP_MISSING_MOTHER(WARNING),

    /** Người có trong lần nhập trước, mất khỏi tệp lần này. <b>Không xoá gì cả.</b> */
    IMP_ROW_DISAPPEARED(WARNING),

    /** Mã tỉnh/quốc gia không có trong danh mục {@code place_division}. */
    IMP_UNKNOWN_PLACE_CODE(WARNING),

    /** Có nguyên quán bằng chữ nhưng không có mã tỉnh — dòng này không vào được báo cáo dân số. */
    IMP_MISSING_PLACE_CODE(WARNING),

    /** Mã người được kế tự không tìm thấy. */
    IMP_HEIR_TARGET_NOT_FOUND(WARNING);

    private final IssueSeverity severity;

    IssueCode(IssueSeverity severity) {
        this.severity = severity;
    }

    public IssueSeverity severity() {
        return severity;
    }

    public boolean chan() {
        return severity == BLOCKING;
    }
}
