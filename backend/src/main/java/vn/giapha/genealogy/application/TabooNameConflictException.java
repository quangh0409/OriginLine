package vn.giapha.genealogy.application;

import java.util.List;
import vn.giapha.genealogy.domain.TabooConflict;
import vn.giapha.shared.exception.DomainException;

/**
 * Tên đang định đặt trùng <b>tên húy</b> của một bậc trên (FR-1.6).
 *
 * <p>Đây là <b>cảnh báo, không phải lệnh cấm</b>: kỵ húy là tục kiêng gọi tên thật người trên, và
 * chỉ dòng họ mới có thẩm quyền quyết định có ghi đè hay không. Luồng vì thế gồm hai bước - lần
 * gọi đầu trả {@code 409} kèm danh sách va chạm để hộp thoại liệt kê đủ "cụ nào, đời thứ mấy,
 * trùng tên húy nào", lần gọi sau kèm {@code confirmTabooOverride = true} thì ghi và lưu việc ghi
 * đè vào {@code audit_log}.</p>
 *
 * <p><b>Không bản ghi nào được tạo</b> ở lần gọi đầu - ngoại lệ này ném ra trước mọi lệnh ghi.</p>
 */
public class TabooNameConflictException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient List<TabooConflict> conflicts;

    public TabooNameConflictException(List<TabooConflict> conflicts) {
        super(GenealogyProblemCodes.KY_HUY_CONFLICT, buildMessage(conflicts));
        this.conflicts = List.copyOf(conflicts);
    }

    /** Danh sách va chạm để tầng api dựng phần {@code conflicts[]} của Problem Details. */
    public List<TabooConflict> conflicts() {
        return conflicts;
    }

    private static String buildMessage(List<TabooConflict> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) {
            return "Ten moi trung ten huy cua bac tren";
        }
        TabooConflict first = conflicts.get(0);
        String generation = first.ancestorGeneration() == null
                ? "chua ro doi" : "doi thu " + first.ancestorGeneration();
        return "Ten huy \"" + first.tabooName() + "\" trung voi bac tren (" + generation
                + "). Xac nhan de van ghi.";
    }
}
