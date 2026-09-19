package vn.giapha.dataimport.application.rule;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.ImportLimits;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.PlannedAction;

/**
 * Hai luật chỉ có nghĩa <b>từ lần tải thứ hai trở đi</b>.
 *
 * <h2>Dòng biến mất khỏi tệp — và tuyệt đối không xoá gì cả</h2>
 * "Vắng mặt là xoá" nghe hợp lý và là cách một chi biến mất: ai đó tải lên một trang đã lọc, hoặc
 * đã sắp xếp rồi cắt bớt cho dễ nhìn. Hệ thống này xoá mềm ở tầng nguyên tắc chứ không chỉ tầng kỹ
 * thuật, nên câu trả lời đúng là ghi một cảnh báo rồi đi tiếp.
 *
 * <h2>Lưới an toàn cuối cùng khi sổ giấy bị đánh số lại</h2>
 * Đây là chỗ cơ chế mã-làm-khoá gãy, và nó <b>gãy im lặng</b>: Hội đồng biên tập lại bản phả gốc,
 * đánh số lại, và {@code AT-02-001} giờ là một người khác. Tải lên thì không mã nào khớp, cả 400
 * dòng đều thành CREATE, và chi đó có 800 người. Không một luật nào khác bắt được, vì <b>từng dòng
 * một đều hoàn toàn hợp lệ</b>.
 *
 * <p>Ngưỡng là con số phán đoán chứ không phải đo được, nên thông báo phải nói thẳng ra rằng nếu
 * đây là một đợt bổ sung lớn thì xác nhận và đi tiếp — chứ không giả vờ rằng máy biết chắc.</p>
 */
@Component
@Order(130)
public class ReloadRule implements ImportRule {

    @Override
    public void apply(ValidationContext ctx) {
        dongBienMat(ctx);
        chanTaoHangLoat(ctx);
    }

    private void dongBienMat(ValidationContext ctx) {
        if (ctx.maDaCoCuaChi().isEmpty()) {
            return;
        }
        Set<String> trongTep = new LinkedHashSet<>();
        for (PersonRow row : ctx.personRows()) {
            if (row.externalCode() != null) {
                trongTep.add(row.externalCode());
            }
        }
        List<String> vang = ctx.maDaCoCuaChi().stream()
                .filter(ma -> !trongTep.contains(ma))
                .sorted()
                .toList();
        if (vang.isEmpty()) {
            return;
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("soMaVang", vang.size());
        context.put("maVang", vang.size() > 50 ? vang.subList(0, 50) : vang);
        ctx.add(ImportIssue.caLo(IssueCode.IMP_ROW_DISAPPEARED,
                vang.size() + " người đã nhập lần trước không còn trong tệp lần này. "
                        + "Hệ thống KHÔNG xoá ai cả. Nếu anh/chị lọc hoặc cắt bớt tệp cho dễ nhìn"
                        + " thì đây là bình thường; nếu không thì kiểm lại xem có mất dòng không.",
                context));
    }

    private void chanTaoHangLoat(ValidationContext ctx) {
        long daCo = ctx.maDaCoCuaChi().size();
        if (daCo == 0) {
            // Lan nhap dau tien cua chi: moi dong deu la CREATE, dung nhu mong doi.
            return;
        }
        long tong = ctx.personRows().size();
        if (tong == 0) {
            return;
        }
        long tao = ctx.personRows().stream()
                .filter(r -> r.plannedAction() == PlannedAction.CREATE)
                .count();
        double tiLe = (double) tao / tong;
        if (tiLe <= ImportLimits.MASS_CREATE_RATIO) {
            return;
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("soTao", tao);
        context.put("tongDong", tong);
        context.put("tiLe", Math.round(tiLe * 100));
        context.put("nguong", Math.round(ImportLimits.MASS_CREATE_RATIO * 100));
        ctx.add(ImportIssue.caLo(IssueCode.IMP_MASS_CREATE_GUARD,
                "Chi này đã có " + daCo + " người, nhưng " + tao + "/" + tong + " dòng trong tệp ("
                        + Math.round(tiLe * 100) + "%) sẽ tạo người MỚI. Dấu hiệu thường gặp nhất"
                        + " của việc sổ giấy bị đánh số lại — nếu cứ ghi thì chi này sẽ có gần gấp"
                        + " đôi số người. Nếu đây thật sự là một đợt bổ sung lớn thì xác nhận để"
                        + " đi tiếp.",
                context));
    }
}
