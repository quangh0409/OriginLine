package vn.giapha.dataimport.domain;

/**
 * Giới hạn phòng thủ cho <b>đầu vào không tin cậy</b>.
 *
 * <h2>Trưởng chi không phải kẻ tấn công — nhưng .xlsx là một kho nén chứa XML</h2>
 * Một tệp hỏng, hoặc một tệp được dựng ác ý, không được phép làm sập tiến trình web hay ngốn hết
 * heap. Các con số dưới đây đều có căn cứ chứ không phải chọn cho tròn.
 */
public final class ImportLimits {

    /**
     * Trần kích thước tệp: 10 MB.
     *
     * <p>Một chi 400 người xuất ra khoảng vài trăm KB, nên 10 MB đã là hơn hai mươi lần dư.</p>
     */
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    /**
     * Trần số dòng trang Nhân khẩu: 5.000.
     *
     * <p>Mỗi Trưởng chi nhập 350–400 người, nên đây là hơn mười hai lần dư. Lý do có trần
     * <b>không phải</b> vì máy không chịu nổi, mà vì <b>không ai đối soát nổi một tệp 5.000
     * dòng</b> với cuốn sổ giấy đặt cạnh. Quá ngưỡng thì bắt tách tệp.</p>
     */
    public static final int MAX_PERSON_ROWS = 5_000;

    /** Trần số dòng trang Hôn phối: 2.000. Cùng lý do. */
    public static final int MAX_MARRIAGE_ROWS = 2_000;

    /**
     * Trần độ dài một ô chữ. Ô dài hơn bị cắt và ghi log — một ô 1 MB không phải dữ liệu gia phả.
     */
    public static final int MAX_CELL_CHARS = 4_000;

    /**
     * Tỉ lệ giải nén tối thiểu cho POI: 0,01.
     *
     * <p>Chặn zip bomb — một tệp 1 MB bung ra 4 GB XML. Đây là ngưỡng POI dùng để quyết định từ
     * chối, không phải ngưỡng hiệu năng.</p>
     */
    public static final double MIN_INFLATE_RATIO = 0.01d;

    /** Trần bộ nhớ cho một mảng byte POI cấp phát khi đọc một phần tệp: 64 MB. */
    public static final int MAX_BYTE_ARRAY = 64 * 1024 * 1024;

    /** Trần thời gian phân tích một tệp. Quá thì cắt ngang, đánh FAILED, giữ nguyên tệp gốc để soi. */
    public static final int PARSE_TIMEOUT_SECONDS = 60;

    /**
     * Ngưỡng của {@link IssueCode#IMP_MASS_CREATE_GUARD}: tải lại cho một chi <b>đã có dữ liệu</b>
     * mà quá tỉ lệ này số dòng ra CREATE thì chặn.
     *
     * <p>Đây là con số <b>phán đoán, không phải đo</b>. Dấu hiệu nó bắt là sổ giấy bị đánh số lại
     * — lúc đó không mã nào khớp, cả 400 dòng thành CREATE, và chi ấy có 800 người mà không một
     * luật nào khác bắt được, vì từng dòng một đều hợp lệ. Nhưng một lần nhập bổ sung thật ở năm
     * thứ hai cũng có thể có 40% dòng mới nếu chi đó vừa khai thêm hai đời, nên thông báo phải nói
     * rõ "nếu đây là bổ sung lớn thì xác nhận", và ngưỡng phải chỉnh được mà không phải biên dịch
     * lại.</p>
     */
    public static final double MASS_CREATE_RATIO = 0.30d;

    /** Số mã gợi ý tối đa khi báo "không tìm thấy mã cha" — quá ba thì không còn là gợi ý. */
    public static final int MAX_CODE_SUGGESTIONS = 3;

    /**
     * <b>Cửa sổ gỡ lô</b>: 7 ngày kể từ lúc lô được ghi vào phả.
     *
     * <h2>Vì sao 7 chứ không phải 30 như hoàn tác gộp trùng</h2>
     * Hai nghiệp vụ khác nhau về bản chất. Hoàn tác gộp khôi phục <b>một</b> ảnh chụp của hai
     * người; gỡ lô xoá mềm <b>hàng trăm</b> người và gỡ mọi cạnh của họ. Kế hoạch §13.2 nói thẳng
     * điều này: giá trị thật của lệnh gỡ nằm ở <b>tuần đầu</b>, khi mới nhập xong và chưa ai đụng
     * tới; sau ba tháng nó gần như chắc chắn sẽ từ chối vì người trong họ đã động vào — và từ chối
     * là hành vi đúng, vì rút lại một lô ba tháng tuổi là tai hoạ thứ hai chồng lên tai hoạ thứ
     * nhất.
     *
     * <p>Một cửa sổ dài mà <b>luôn luôn</b> từ chối thì tệ hơn một cửa sổ ngắn nói thật: nó khiến
     * Trưởng chi tin là còn đường lùi trong 30 ngày, rồi đến ngày thứ 25 mới biết là không.</p>
     *
     * <p><b>Điều kiện thật không phải là thời hạn.</b> Thời hạn chỉ là cái chặn cuối; điều kiện
     * quyết định là "chưa ai khác động vào" — xem {@code RollbackImportBatchService}. Một lô 2
     * ngày tuổi mà đã có người treo thêm con vào thì vẫn bị từ chối.</p>
     */
    public static final int ROLLBACK_WINDOW_DAYS = 7;

    private ImportLimits() {
    }
}
