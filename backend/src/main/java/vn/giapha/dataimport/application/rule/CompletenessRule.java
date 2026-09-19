package vn.giapha.dataimport.application.rule;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.MarriageRow;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.shared.vo.Gender;

/**
 * Ba phép đo <b>độ đầy đủ</b> — tất cả đều là cảnh báo, và tất cả đều đáng ghi lại.
 *
 * <h2>Người đứng một mình</h2>
 * Không cha, không mẹ, không vợ/chồng. Hợp lệ với Thuỷ tổ; với mọi người khác thì hoặc là thiếu
 * liên kết, hoặc là một dòng bị bỏ quên giữa chừng. Người đó sẽ có hồ sơ nhưng không hiện trên phả
 * đồ, và chỉ lộ ra khi có người đi tìm.
 *
 * <h2>Thiếu mẹ — con số duy nhất đo được bên ngoại</h2>
 * Sổ cũ chép cha rất đầy đủ và chép mẹ rất thưa; đó là thực tế lịch sử, không phải lỗi của người
 * nhập. Nhưng BA đã chốt <b>con gái và bên ngoại ghi ngang bằng con trai</b>, và con số này là
 * cách duy nhất biết mình có thật sự làm được điều đó hay chỉ nói. Vì vậy: cảnh báo, không chặn,
 * và đếm.
 */
@Component
@Order(90)
public class CompletenessRule implements ImportRule {

    @Override
    public void apply(ValidationContext ctx) {
        Set<String> coHonPhoi = new HashSet<>();
        for (MarriageRow m : ctx.marriageRows()) {
            if (m.husbandCode() != null) {
                coHonPhoi.add(m.husbandCode());
            }
            if (m.wifeCode() != null) {
                coHonPhoi.add(m.wifeCode());
            }
        }
        // Nguoi co con trong tep thi khong phai "dung mot minh" du ho khong khai cha me — day
        // thuong la thuy to cua chi.
        Set<String> laChaMe = new HashSet<>();
        for (PersonRow row : ctx.personRows()) {
            if (row.coCha()) {
                laChaMe.add(row.fatherCode());
            }
            if (row.coMe()) {
                laChaMe.add(row.motherCode());
            }
        }

        for (PersonRow row : ctx.personRows()) {
            String ma = row.externalCode();
            if (ma == null) {
                continue;
            }
            if (!row.coCha() && !row.coMe() && !coHonPhoi.contains(ma) && !laChaMe.contains(ma)) {
                ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_LONE_NODE, row.rowNo(),
                        ImportColumn.MA_CHA.tieuDe(),
                        "Dòng " + row.nhan() + " không có cha, không có mẹ, không có vợ/chồng và"
                                + " không ai khai là con. Hợp lệ nếu đây là Thuỷ tổ của chi; nếu"
                                + " không thì người này sẽ không hiện trên phả đồ.",
                        Map.of()));
            }
            if (row.coCha() && !row.coMe()) {
                ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_MISSING_MOTHER, row.rowNo(),
                        ImportColumn.MA_ME.tieuDe(),
                        "Dòng " + row.nhan() + " có cha nhưng trống Mã mẹ. Bên ngoại được ghi ngang"
                                + " bằng bên nội, nên nếu sổ có tên bà thì nên bổ sung.",
                        Map.of()));
            }
            if (row.gender() == null || row.gender() == Gender.UNKNOWN) {
                ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_UNKNOWN_GENDER, row.rowNo(),
                        ImportColumn.GIOI.tieuDe(),
                        "Dòng " + row.nhan() + " chưa có Giới.",
                        Map.of()));
            }
        }
    }
}
