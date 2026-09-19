package vn.giapha.dataimport.application.rule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.CodeSuggester;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.PersonRow;

/**
 * Mã cha, mã mẹ và mã người được kế tự có trỏ tới đâu không.
 *
 * <h2>Vì sao mã cha không phân giải được là lỗi chặn</h2>
 * Ghi vào phả sẽ tạo ra một người <b>treo ngoài cây</b>: có hồ sơ, có tên, nhưng không hiện trên
 * phả đồ và không ai tìm thấy. Đó là loại hỏng mà người dùng chỉ phát hiện ra vài tháng sau, khi
 * một cụ cao niên hỏi "sao không thấy ông ấy đâu".
 *
 * <h2>Gợi ý mã gần giống — phần đáng giá nhất của luật này</h2>
 * "Không tìm thấy mã AT-04-O03" trên tệp 400 dòng là một câu đố, và người nhập sẽ không giải được,
 * vì sai ở đúng cái ký tự mắt không phân biệt nổi: chữ O thay vì số 0. Thêm một dòng "ý anh là
 * AT-04-003 (Nguyễn Văn Cẩn) phải không?" biến câu đố thành một cú sửa ba giây. Chênh lệch giữa
 * một bộ kiểm dùng được và một bộ kiểm khiến Trưởng chi bỏ cuộc ở lần tải thứ ba nằm ở đúng những
 * chỗ như thế này.
 */
@Component
@Order(30)
public class ParentReferenceRule implements ImportRule {

    @Override
    public void apply(ValidationContext ctx) {
        for (PersonRow row : ctx.personRows()) {
            kiem(ctx, row, row.fatherCode(), ImportColumn.MA_CHA, "cha");
            kiem(ctx, row, row.motherCode(), ImportColumn.MA_ME, "mẹ");
            kiemKeTu(ctx, row);
        }
    }

    private void kiem(ValidationContext ctx, PersonRow row, String maCha, ImportColumn cot,
                      String vaiTro) {
        if (maCha == null || maCha.isBlank()) {
            return;
        }
        if (maCha.equals(row.externalCode())) {
            ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_SELF_PARENT, row.rowNo(), cot.tieuDe(),
                    "Dòng " + row.nhan() + " khai chính mình là " + vaiTro + " của mình.",
                    Map.of("ma", maCha)));
            return;
        }
        if (ctx.phanGiaiDuoc(maCha)) {
            return;
        }
        List<String> goiY = CodeSuggester.goiY(maCha, ctx.moiMaBietDuoc());
        StringBuilder msg = new StringBuilder()
                .append("Dòng ").append(row.nhan()).append(" khai mã ").append(vaiTro).append(" là ")
                .append(maCha).append(" nhưng không có mã đó trong tệp, cũng chưa có trong phả.");
        if (!goiY.isEmpty()) {
            msg.append(" Có phải anh/chị định gõ ").append(moTa(ctx, goiY)).append(" không?");
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("maKhongTimThay", maCha);
        context.put("vaiTro", vaiTro);
        if (!goiY.isEmpty()) {
            context.put("goiY", goiY);
        }
        ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_PARENT_NOT_FOUND, row.rowNo(), cot.tieuDe(),
                msg.toString(), context));
    }

    private void kiemKeTu(ValidationContext ctx, PersonRow row) {
        String ma = row.heirOfCode();
        if (ma == null || ma.isBlank() || ctx.phanGiaiDuoc(ma)) {
            return;
        }
        List<String> goiY = CodeSuggester.goiY(ma, ctx.moiMaBietDuoc());
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("maKhongTimThay", ma);
        if (!goiY.isEmpty()) {
            context.put("goiY", goiY);
        }
        // CANH BAO chu khong phai loi chan: ke tu la quan he them, khong phai canh giu cay. Thieu
        // no thi nguoi ay van dung dung cho trong pha, chi la chua ghi duoc viec lap nguoi noi doi.
        ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_HEIR_TARGET_NOT_FOUND, row.rowNo(),
                ImportColumn.KE_TU_CHO_AI.tieuDe(),
                "Dòng " + row.nhan() + " khai kế tự cho mã " + ma + " nhưng không tìm thấy mã đó."
                        + (goiY.isEmpty() ? "" : " Có thể là " + String.join(", ", goiY) + "."),
                context));
    }

    /** Gợi ý kèm tên để người nhập nhận ra ngay, thay vì phải đi tra tiếp một cái mã nữa. */
    private String moTa(ValidationContext ctx, List<String> goiY) {
        return goiY.stream().map(ma -> {
            PersonRow r = ctx.rowOf(ma);
            return r != null && r.fullName() != null ? ma + " (" + r.fullName() + ")" : ma;
        }).reduce((a, b) -> a + ", " + b).orElse("");
    }
}
