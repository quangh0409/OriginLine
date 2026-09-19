package vn.giapha.dataimport.application.rule;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.PersonRow;

/**
 * Hình dạng của một dòng: có mã không, có tên không, mã có đúng dạng không, và
 * <b>mã có thuộc chi đang nhập không</b>.
 *
 * <h2>Câu hỏi cuối vừa là lỗi dữ liệu vừa là ranh giới phân quyền</h2>
 * Trưởng chi Ất nộp một tệp chứa mã đã đăng ký cho chi Giáp là đang ghi sang chi khác — dù vô
 * tình. Phải chặn ở đây, bằng một câu tiếng Việt, chứ không để nó đi tới lúc ghi rồi mới bật ra
 * một lỗi phân quyền khó hiểu giữa chừng 400 dòng.
 *
 * <h2>Vì sao không kiểm tiền tố chi theo một quy tắc cứng</h2>
 * Lược đồ hiện <b>không có cột tiền tố</b> trên bảng {@code branch}, nên mọi quy tắc cứng ở đây
 * đều là quy tắc ta tự bịa ra và sẽ đá nhau với cách đánh số thật của từng chi. Vì vậy kiểm hai
 * thứ kiểm được thật: <b>hình dạng</b> mã, và <b>quyền sở hữu</b> mã theo dữ liệu đã đăng ký.
 * Tiền tố do hệ thống cấp lúc khai chi là việc của bước khai khung chi/ngành, không phải của
 * đường ống nhập liệu.
 */
@Component
@Order(10)
public class RowShapeRule implements ImportRule {

    /** AT-02-001, AT02001, 001 — rộng rãi với cách đánh số, nghiêm với ký tự lạ. */
    private static final Pattern DANG_MA = Pattern.compile("^[A-Z0-9]{1,12}([-_.][A-Z0-9]{1,12}){0,4}$");

    @Override
    public void apply(ValidationContext ctx) {
        for (PersonRow row : ctx.personRows()) {
            String ma = row.externalCode();
            if (ma == null || ma.isBlank()) {
                ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_MISSING_CODE, row.rowNo(),
                        ImportColumn.MA.tieuDe(),
                        "Dòng này có dữ liệu nhưng bỏ trống cột Mã. Mã là thứ giữ cho lần tải lại"
                                + " sau không sinh ra một người thứ hai, nên không thể để trống.",
                        Map.of()));
                continue;
            }
            if (!DANG_MA.matcher(ma).matches()) {
                ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_BAD_CODE_FORMAT, row.rowNo(),
                        ImportColumn.MA.tieuDe(),
                        "Mã " + ma + " có ký tự không dùng được. Mã chỉ gồm chữ, số và dấu gạch"
                                + " ngang, ví dụ AT-02-001.",
                        Map.of("ma", ma)));
            }
            // Ma da phan giai duoc trong chinh chi nay thi khong the la ma cua chi khac, du bang
            // tra toan cuc co tinh co tra ve mot chi khac (hai chi tu danh so giong nhau).
            UUID chuSoHuu = ctx.resolved().containsKey(ma) ? null : ctx.chiSoHuuMa().get(ma);
            if (chuSoHuu != null && !chuSoHuu.equals(ctx.branchId())) {
                ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_BAD_CODE_FORMAT, row.rowNo(),
                        ImportColumn.MA.tieuDe(),
                        "Mã " + ma + " đã được đăng ký cho một chi khác. Nộp tệp chứa mã của chi"
                                + " khác là ghi sang phần dữ liệu của họ, nên lô này bị chặn.",
                        Map.of("ma", ma, "chiSoHuu", chuSoHuu.toString())));
            }
            if (row.fullName() == null || row.fullName().isBlank()) {
                ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_MISSING_NAME, row.rowNo(),
                        ImportColumn.HO_TEN.tieuDe(),
                        "Dòng " + ma + " chưa có Họ tên.",
                        Map.of("ma", ma)));
            }
        }
    }
}
