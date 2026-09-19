package vn.giapha.dataimport.domain;

import vn.giapha.shared.exception.DomainException;

/**
 * Tệp bị <b>từ chối ngay ở cửa</b>, trước khi có bất kỳ dòng nào vào khu vực chờ.
 *
 * <h2>Khác hẳn với lỗi của bộ kiểm</h2>
 * {@link ImportIssue} nói "tệp đọc được nhưng dữ liệu có vấn đề ở dòng 137". Ngoại lệ này nói
 * "tệp này không đọc được, hoặc không an toàn để đọc" — không có dòng nào, không có số dòng nào,
 * và không có gì để người nhập sửa từng ô. Gộp hai loại vào một chỗ thì giao diện không biết nên
 * hiện bảng lỗi hay hiện một câu xin lỗi.
 */
public class ImportRejectedException extends DomainException {

    private static final long serialVersionUID = 1L;

    /** Tệp không phải .xlsx (đổi đuôi, hoặc là .xls/.xlsm). */
    public static final String BAD_FORMAT = "IMP_BAD_FORMAT";

    /** Vượt trần kích thước. */
    public static final String FILE_TOO_LARGE = "IMP_FILE_TOO_LARGE";

    /** Vượt trần số dòng — bắt tách tệp, vì không ai đối soát nổi một tệp 5.000 dòng. */
    public static final String TOO_MANY_ROWS = "IMP_TOO_MANY_ROWS";

    /** Thiếu hẳn trang Nhân khẩu, hoặc thiếu cột bắt buộc. */
    public static final String MISSING_SHEET = "IMP_MISSING_SHEET";

    public static final String MISSING_COLUMN = "IMP_MISSING_COLUMN";

    /** Tệp chứa DOCTYPE hoặc thực thể ngoài XML, hoặc bung ra quá tỉ lệ cho phép (zip bomb). */
    public static final String UNSAFE_FILE = "IMP_UNSAFE_FILE";

    /** Tệp hỏng, POI không đọc nổi. */
    public static final String CORRUPT_FILE = "IMP_CORRUPT_FILE";

    public ImportRejectedException(String code, String message) {
        super(code, message);
    }

    public ImportRejectedException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public String code() {
        return getCode();
    }
}
