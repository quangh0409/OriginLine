package vn.giapha.dataimport.application.rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.PersonRow;

/**
 * Hai dòng cùng một Mã trong một tệp.
 *
 * <h2>Vì sao đây là lỗi chặn chứ không phải cảnh báo</h2>
 * Mã là khoá bất biến của cả đường ống. Hai dòng cùng mã thì lần tải lại sau, mã ấy trỏ tới một
 * người, và <b>không có cách nào đoán đúng dòng nào</b> đã sinh ra người đó. Cho qua thì ta mất
 * khả năng đối soát vĩnh viễn, còn bắt sửa thì tốn của người nhập ba mươi giây.
 *
 * <p>Báo <b>mọi</b> dòng liên quan chứ không chỉ dòng thứ hai: người nhập cần thấy cả hai để so,
 * vì rất hay là hai người thật bị gán nhầm chung một mã, chứ không phải một dòng bị chép đôi.</p>
 */
@Component
@Order(20)
public class DuplicateCodeRule implements ImportRule {

    @Override
    public void apply(ValidationContext ctx) {
        Map<String, List<PersonRow>> theoMa = new LinkedHashMap<>();
        for (PersonRow row : ctx.personRows()) {
            if (row.externalCode() != null && !row.externalCode().isBlank()) {
                theoMa.computeIfAbsent(row.externalCode(), k -> new ArrayList<>()).add(row);
            }
        }
        theoMa.forEach((ma, rows) -> {
            if (rows.size() < 2) {
                return;
            }
            List<Integer> dong = rows.stream().map(PersonRow::rowNo).toList();
            for (PersonRow row : rows) {
                ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_DUP_CODE, row.rowNo(),
                        ImportColumn.MA.tieuDe(),
                        "Mã " + ma + " xuất hiện ở " + rows.size() + " dòng: " + dong
                                + ". Mỗi người phải có một mã riêng — nếu đây là hai người thật thì"
                                + " đánh lại số cho một trong hai.",
                        Map.of("ma", ma, "cacDong", dong)));
            }
        });
    }
}
