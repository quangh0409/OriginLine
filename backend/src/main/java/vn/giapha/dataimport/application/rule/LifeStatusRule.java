package vn.giapha.dataimport.application.rule;

import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.PersonRow;

/**
 * Còn sống hay đã mất, và hệ quả trực tiếp của nó: <b>có được nhắc giỗ không</b>.
 *
 * <h2>Còn sống = Có nhưng vẫn có ngày mất</h2>
 * Trùng đúng ràng buộc {@code ck_person_alive_vs_death} của cơ sở dữ liệu. Bắt ở đây để người nhập
 * thấy một câu tiếng Việt, thay vì nhận một {@code SQLException} giữa lúc ghi 400 dòng — lúc đó cả
 * lô cuộn lại và họ không có cách nào biết dòng nào gây ra.
 *
 * <h2>Thiếu ngày giỗ là cảnh báo đáng giá nhất trong cả bộ kiểm</h2>
 * Người đó sẽ nằm trong phả đồ, có tên, có cha mẹ, trông hoàn toàn bình thường — và
 * <b>không bao giờ được nhắc giỗ</b>. Mà nhắc giỗ là lý do dòng họ mở ứng dụng này. Không có lỗi
 * nào rẻ hơn để sửa và ít lộ ra hơn nếu bỏ qua, nên con số "bao nhiêu người thiếu ngày giỗ" phải
 * hiện ngay đầu báo cáo đối soát chứ không nằm lẫn trong danh sách.
 */
@Component
@Order(60)
public class LifeStatusRule implements ImportRule {

    @Override
    public void apply(ValidationContext ctx) {
        for (PersonRow row : ctx.personRows()) {
            if (Boolean.TRUE.equals(row.alive()) && row.death() != null) {
                ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_ALIVE_WITH_DEATH, row.rowNo(),
                        ImportColumn.CON_SONG.tieuDe(),
                        "Dòng " + row.nhan() + " ghi Còn sống = Có nhưng lại có Ngày mất âm ("
                                + row.death() + "). Một trong hai ô sai.",
                        Map.of("ngayMatAm", row.death().toString())));
                continue;
            }
            if (Boolean.TRUE.equals(row.daMat()) && row.death() == null) {
                ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_MISSING_GIO, row.rowNo(),
                        ImportColumn.NGAY_MAT_AM.tieuDe(),
                        "Dòng " + row.nhan() + " đã mất nhưng trống Ngày mất âm. Người này sẽ có"
                                + " trong phả đồ nhưng không bao giờ được nhắc giỗ.",
                        Map.of()));
            }
            if (row.death() != null && row.death().day() == 30 && !row.death().coNam()) {
                ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_GIO_DAY_30, row.rowNo(),
                        ImportColumn.NGAY_MAT_AM.tieuDe(),
                        "Dòng " + row.nhan() + " giỗ ngày 30 tháng " + row.death().month()
                                + " nhưng không rõ năm. Năm nào tháng ấy thiếu thì không có ngày"
                                + " 30 và giỗ trôi về 29 — dòng họ cần chốt tục lệ, máy không tự"
                                + " quyết được.",
                        Map.of("thang", row.death().month())));
            }
        }
    }
}
