package vn.giapha.dataimport.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Một vấn đề bộ kiểm tìm thấy trong một lô.
 *
 * <h2>Ba thứ bắt buộc phải có, và lý do</h2>
 * <ul>
 *   <li>{@link #rowNo()} — người nhập sửa trên chính tệp Excel của họ, nên "dòng 137" là đơn vị
 *       duy nhất họ dùng được. Một thông báo không có số dòng, trên tệp 400 dòng, là vô dụng.</li>
 *   <li>{@link #message()} — một câu tiếng Việt hoàn chỉnh, không phải mã lỗi. Người đọc là Trưởng
 *       chi, không phải lập trình viên.</li>
 *   <li>{@link #context()} — cùng nội dung ấy ở dạng máy đọc được: chuỗi mã của vòng lặp, danh
 *       sách mã gợi ý, điểm nghi trùng. Giao diện dựng nút nhảy tới dòng đó từ đây.</li>
 * </ul>
 *
 * @param sheet trang trong tệp: {@code NHAN_KHAU} hoặc {@code HON_PHOI}
 * @param rowNo số dòng trong trang đó, đúng như Excel đánh; {@code null} khi lỗi thuộc về cả lô
 * @param field tên cột tiếng Việt, để giao diện tô đúng ô
 */
public record ImportIssue(IssueCode code,
                          String sheet,
                          Integer rowNo,
                          String field,
                          String message,
                          Map<String, Object> context) {

    public static final String SHEET_NHAN_KHAU = "NHAN_KHAU";
    public static final String SHEET_HON_PHOI = "HON_PHOI";
    public static final String SHEET_LO = "LO";

    /**
     * Thứ tự tất định để so sánh hai lần chạy bộ kiểm trên cùng một tệp.
     *
     * <p>Đây không phải chuyện thẩm mỹ: nghiệm thu đòi hai lần chạy phải cho <b>danh sách giống
     * hệt nhau</b>. Nếu thứ tự phụ thuộc vào thứ tự duyệt của một {@code HashMap} thì bài kiểm ấy
     * lúc xanh lúc đỏ và không ai truy ra được vì sao.</p>
     */
    public static final Comparator<ImportIssue> TAT_DINH = Comparator
            .comparing(ImportIssue::sheet)
            .thenComparing(i -> i.rowNo() == null ? Integer.MAX_VALUE : i.rowNo())
            .thenComparing(i -> i.code().name())
            .thenComparing(i -> i.field() == null ? "" : i.field())
            .thenComparing(ImportIssue::message);

    public ImportIssue {
        Objects.requireNonNull(code, "IssueCode khong duoc null");
        Objects.requireNonNull(message, "ImportIssue.message khong duoc null");
        sheet = sheet == null ? SHEET_NHAN_KHAU : sheet;
        // Map.copyOf nem NPE khi co gia tri null, va context rat hay mang mot truong chua co gia
        // tri (nam sinh chua biet, nguoi bi nghi chua duoc ghi). Loc null truoc roi moi copyOf,
        // thay vi de mot cai bay no lam do ca bo kiem giua chung.
        context = context == null ? Map.of() : Map.copyOf(withoutNulls(context));
    }

    private static Map<String, Object> withoutNulls(Map<String, Object> src) {
        Map<String, Object> copy = new LinkedHashMap<>();
        src.forEach((k, v) -> {
            if (v != null) {
                copy.put(k, v);
            }
        });
        return copy;
    }

    public IssueSeverity severity() {
        return code.severity();
    }

    public boolean chan() {
        return code.chan();
    }

    public static ImportIssue nhanKhau(IssueCode code, int rowNo, String field, String message,
                                       Map<String, Object> context) {
        return new ImportIssue(code, SHEET_NHAN_KHAU, rowNo, field, message, context);
    }

    public static ImportIssue honPhoi(IssueCode code, int rowNo, String field, String message,
                                      Map<String, Object> context) {
        return new ImportIssue(code, SHEET_HON_PHOI, rowNo, field, message, context);
    }

    /** Vấn đề của cả lô, không gắn với dòng nào. */
    public static ImportIssue caLo(IssueCode code, String message, Map<String, Object> context) {
        return new ImportIssue(code, SHEET_LO, null, null, message, context);
    }
}
