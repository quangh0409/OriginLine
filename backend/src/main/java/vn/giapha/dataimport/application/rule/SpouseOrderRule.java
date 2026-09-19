package vn.giapha.dataimport.application.rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.MarriageColumn;
import vn.giapha.dataimport.domain.MarriageRow;

/**
 * Hai người vợ cùng <b>Bậc</b> trên cùng một người chồng.
 *
 * <h2>Vì sao là lỗi chặn</h2>
 * Trùng đúng chỉ mục duy nhất {@code ux_relationship_spouse_order}. Nếu để lọt, việc ghi sẽ chết
 * giữa transaction với một lỗi ràng buộc, cả lô cuộn lại, và người nhập không có cách nào biết cặp
 * nào gây ra.
 *
 * <h2>Và vì sao bậc lại quan trọng đến thế với dòng họ</h2>
 * Vợ cả, vợ hai, vợ lẽ, vợ kế là bốn thứ khác nhau, và dòng họ phân biệt rất rõ: con của vợ cả với
 * con của vợ lẽ có thứ bậc khác nhau trong lễ nghi, và <b>danh xưng</b> tính ra cũng khác. Thiếu
 * bậc thì cả hai đều sai mà không có gì báo.
 */
@Component
@Order(80)
public class SpouseOrderRule implements ImportRule {

    @Override
    public void apply(ValidationContext ctx) {
        // (ma chong, bac) -> cac dong khai cap do
        Map<String, List<MarriageRow>> theoCapBac = new LinkedHashMap<>();
        for (MarriageRow row : ctx.marriageRows()) {
            if (row.husbandCode() == null || row.spouseOrder() == null) {
                continue;
            }
            theoCapBac.computeIfAbsent(row.husbandCode() + "#" + row.spouseOrder(),
                    k -> new ArrayList<>()).add(row);
        }
        theoCapBac.forEach((khoa, rows) -> {
            if (rows.size() < 2) {
                return;
            }
            MarriageRow dau = rows.get(0);
            List<String> vo = rows.stream().map(MarriageRow::wifeCode).toList();
            List<Integer> dong = rows.stream().map(MarriageRow::rowNo).toList();
            for (MarriageRow row : rows) {
                Map<String, Object> context = new LinkedHashMap<>();
                context.put("maChong", dau.husbandCode());
                context.put("bac", dau.spouseOrder());
                context.put("cacVo", vo);
                context.put("cacDong", dong);
                ctx.add(ImportIssue.honPhoi(IssueCode.IMP_SPOUSE_ORDER_CONFLICT, row.rowNo(),
                        MarriageColumn.BAC.tieuDe(),
                        "Người chồng " + dau.husbandCode() + " có " + rows.size() + " bà cùng Bậc "
                                + dau.spouseOrder() + " (" + String.join(", ", vo) + "). Mỗi bậc"
                                + " chỉ thuộc về một người — đánh lại số theo thứ tự cưới.",
                        context));
            }
        });
    }
}
