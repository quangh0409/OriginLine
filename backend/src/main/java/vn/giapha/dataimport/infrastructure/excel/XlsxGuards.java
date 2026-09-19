package vn.giapha.dataimport.infrastructure.excel;

import java.util.Locale;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.util.IOUtils;
import vn.giapha.dataimport.domain.ImportLimits;
import vn.giapha.dataimport.domain.LunarDeathDate;
import vn.giapha.dataimport.domain.ImportRejectedException;

/**
 * Phòng thủ cho việc đọc một tệp <b>không tin cậy</b>.
 *
 * <h2>Trưởng chi không phải kẻ tấn công — nhưng .xlsx là một kho nén chứa XML</h2>
 * Bốn lớp bảo vệ ở đây đều rẻ và đều có một tình huống hỏng cụ thể đằng sau:
 * <ol>
 *   <li><b>Chữ ký tệp.</b> Không tin đuôi tệp, không tin {@code Content-Type} của client. Một tệp
 *       {@code .xls} cũ đổi đuôi thành {@code .xlsx} là chuyện xảy ra hàng ngày khi người dùng đổi
 *       tên tệp trong Explorer, và POI sẽ ném một ngoại lệ khó hiểu thay vì một câu tiếng Việt.</li>
 *   <li><b>Tỉ lệ giải nén.</b> Chặn zip bomb: một tệp 1 MB bung ra 4 GB XML.</li>
 *   <li><b>Trần mảng byte.</b> Chặn một ô văn bản khổng lồ làm POI cấp phát hết heap.</li>
 *   <li><b>Không bao giờ tính lại công thức.</b> Xem {@link #dataFormatter()}.</li>
 * </ol>
 *
 * <p>Quét vi-rút <b>ngoài phạm vi</b> — nêu ra để biết mình đang chấp nhận gì.</p>
 */
public final class XlsxGuards {

    /**
     * Đánh dấu một ô mà Excel đã tự biến thành <b>ngày dương lịch</b>.
     *
     * <p>Đây là cái bẫy tốn thời gian nhất của trang Nhân khẩu: để định dạng ô mặc định thì Excel
     * nuốt {@code 15/8} thành một ngày dương của năm hiện tại và gửi đi một số sê-ri. Nếu ta để
     * {@link DataFormatter} hiển thị nó thành "15/08/2026" thì bộ đọc ngày âm sẽ vui vẻ hiểu là
     * ngày 15 tháng 8 <b>năm âm 2026</b> — một ngày giỗ sai, không lỗi, không log. Gắn dấu ở đây
     * để bộ kiểm nhận ra và bắt người nhập định dạng lại cột, thay vì đoán.</p>
     */
    public static final String DAU_NGAY_DUONG = LunarDeathDate.DAU_NGAY_DUONG;

    private XlsxGuards() {
    }

    /** Đặt các trần toàn cục của POI. Gọi một lần lúc khởi tạo bean đọc tệp. */
    public static void applyGlobalLimits() {
        ZipSecureFile.setMinInflateRatio(ImportLimits.MIN_INFLATE_RATIO);
        IOUtils.setByteArrayMaxOverride(ImportLimits.MAX_BYTE_ARRAY);
    }

    /**
     * Nhận dạng bằng <b>chữ ký tệp</b>, không bằng đuôi tệp.
     *
     * <p>{@code .xlsx} là một kho ZIP nên bốn byte đầu là {@code PK..}. {@code .xls} cũ (BIFF) bắt
     * đầu bằng chữ ký OLE2 {@code D0 CF 11 E0} — nhận ra được để báo một câu rõ ràng thay vì để
     * POI ném ra thứ gì đó.</p>
     */
    public static void requireXlsx(byte[] head, String filename) {
        if (head.length >= 8 && (head[0] & 0xFF) == 0xD0 && (head[1] & 0xFF) == 0xCF
                && (head[2] & 0xFF) == 0x11 && (head[3] & 0xFF) == 0xE0) {
            throw new ImportRejectedException(ImportRejectedException.BAD_FORMAT,
                    "Tệp " + filename + " là định dạng Excel cũ (.xls), dù tên tệp có đuôi gì."
                            + " Mở bằng Excel rồi chọn Lưu thành .xlsx và tải lại.");
        }
        if (head.length < 2 || head[0] != 'P' || head[1] != 'K') {
            throw new ImportRejectedException(ImportRejectedException.BAD_FORMAT,
                    "Tệp " + filename + " không phải tệp Excel .xlsx. Chỉ nhận .xlsx — không nhận"
                            + " .xls, .xlsm (có macro) hay .csv.");
        }
        String ten = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (ten.endsWith(".xlsm")) {
            throw new ImportRejectedException(ImportRejectedException.BAD_FORMAT,
                    "Tệp " + filename + " là .xlsm (bảng tính có macro). Lưu lại thành .xlsx rồi"
                            + " tải lên.");
        }
    }

    /**
     * {@link DataFormatter} có <b>hai</b> sửa đổi so với mặc định.
     *
     * <ul>
     *   <li>Ô định dạng ngày được trả về kèm {@link #DAU_NGAY_DUONG} thay vì một chuỗi ngày trông
     *       như thật — xem javadoc của hằng số đó.</li>
     *   <li>Không {@code FormulaEvaluator} nào được gắn vào: gặp ô công thức thì lấy <b>giá trị đã
     *       lưu sẵn</b>. Chạy bộ tính công thức trên một tệp lạ là mở cửa cho tham chiếu ngoài, hàm
     *       WEBSERVICE, và vòng lặp tính vô tận.</li>
     * </ul>
     */
    public static DataFormatter dataFormatter() {
        return new DataFormatter(Locale.ROOT) {
            @Override
            public String formatRawCellContents(double value, int formatIndex, String formatString,
                                                boolean use1904Windowing) {
                if (DateUtil.isADateFormat(formatIndex, formatString)) {
                    return DAU_NGAY_DUONG + value;
                }
                return super.formatRawCellContents(value, formatIndex, formatString, use1904Windowing);
            }
        };
    }

    /** Cắt ô quá dài — một ô 1 MB không phải dữ liệu gia phả. */
    public static String capCell(String value) {
        if (value == null || value.length() <= ImportLimits.MAX_CELL_CHARS) {
            return value;
        }
        return value.substring(0, ImportLimits.MAX_CELL_CHARS);
    }

    /**
     * Thêm dấu nháy đầu cho chuỗi bắt đầu bằng {@code =} {@code +} {@code -} {@code @}.
     *
     * <p>Dùng khi <b>ghi ngược</b> dữ liệu người dùng vào một tệp Excel (tệp báo lỗi trả về, mẫu
     * điền sẵn). Không có bước này thì ta gửi lại cho chính họ một tệp có công thức chạy được, và
     * chuỗi họ gõ trở thành lệnh khi mở bằng Excel.</p>
     */
    public static String chongChenCongThuc(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char c = value.charAt(0);
        return (c == '=' || c == '+' || c == '-' || c == '@') ? "'" + value : value;
    }
}
