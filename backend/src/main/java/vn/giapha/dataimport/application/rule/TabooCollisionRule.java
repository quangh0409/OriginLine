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
import vn.giapha.dataimport.domain.port.TabooScanPort;

/**
 * <b>Kỵ húy</b> (FR-1.6): tên huý định đặt trùng tên huý của một bậc trên.
 *
 * <h2>Cảnh báo, không phải lệnh cấm — và đó là quyết định của dòng họ, không phải của phần mềm</h2>
 * Kỵ húy là tục kiêng gọi tên thật của bậc trên. Đặt trùng bị xem là thất kính, nhưng thẩm quyền
 * phán xử thuộc về Hội đồng Tộc biểu. Hơn nữa trong một cuốn gia phả đang được <b>chép lại</b>, va
 * chạm kỵ húy thường là sự thật lịch sử chứ không phải lỗi nhập liệu: các cụ đã đặt tên như thế
 * rồi. Chặn ở đây là bắt Trưởng chi sửa lịch sử để qua được ô nhập.
 *
 * <p>Mỗi lần ghi đè phải để lại dấu trong {@code audit_log} — việc đó thuộc nửa sau của đường ống.</p>
 *
 * <p>Chỉ soi lớp tên HUY. Quét cả các lớp tên khác (tự, hiệu, thụy, thường gọi, pháp danh) chỉ tạo
 * ra một biển cảnh báo giả, và người dùng sẽ bấm bỏ qua theo phản xạ — lúc đó cảnh báo mất sạch
 * giá trị.</p>
 *
 * <h2>Vì sao cảnh báo này cũng chỉ nêu KHOÁ của bậc trên</h2>
 * Lập luận "tên huý theo tập quán là của bậc trên <b>đã khuất</b>, mà người đã khuất thì công khai,
 * nên nêu tên vô hại" nghe xuôi nhưng <b>không đúng với phép dò này</b>. Phép dò chọn bậc trên
 * theo <b>đời thứ</b> ({@code p.generation < đời của dòng đang nhập}), <b>không</b> theo
 * {@code is_alive}. Trong một dòng họ đang sống, "bậc trên" hoàn toàn có thể là một ông bác còn
 * sống ở chi khác. Cổng {@link TabooScanPort} lại không mang cờ sống/mất, nên ở đây <b>không biết
 * được</b> — và luật được chọn là <b>an toàn khi không biết</b>: chỉ trả {@code bacTrenId}, để
 * {@code GET /api/v1/persons/&#123;id&#125;} quyết định. Nếu cụ ấy đã khuất — tuyệt đại đa số
 * trường hợp kỵ húy — thì hồ sơ là công khai và tên hiện ra bình thường ngay khi bấm vào khoá.
 * Cái giá của việc chọn sai chiều kia là không đối xứng: giấu nhầm một cụ đã khuất chỉ tốn một cú
 * bấm, còn lộ nhầm một người còn sống thì không thu lại được.
 *
 * <h2>Bẫy: {@code KetQua.tabooName()} KHÔNG phải thứ người nhập vừa gõ</h2>
 * Nó là {@code person_name.full_name} <b>đọc từ phả</b> — tên huý đã lưu của bậc trên. Phép dò
 * khớp cả ở mức tên chính (tên gọi cuối cùng sau khoảng trắng), nên người nhập gõ "Cẩn" vẫn có thể
 * khớp một bậc trên có tên huý "Nguyễn Văn Cẩn", và in {@code ketQua.tabooName()} ra là in nguyên
 * tên đầy đủ của người ấy. Câu thông báo vì vậy dùng {@link PersonRow#tabooName()} — ô Tên huý của
 * chính dòng đang nhập.
 */
@Component
@Order(120)
public class TabooCollisionRule implements ImportRule {

    private final TabooScanPort taboos;

    public TabooCollisionRule(TabooScanPort taboos) {
        this.taboos = taboos;
    }

    @Override
    public void apply(ValidationContext ctx) {
        List<TabooScanPort.UngVien> ungVien = new ArrayList<>();
        Map<String, PersonRow> theoRef = new LinkedHashMap<>();
        for (PersonRow row : ctx.personRows()) {
            if (row.tabooName() == null || row.tabooName().isBlank() || row.externalCode() == null) {
                continue;
            }
            theoRef.put(row.externalCode(), row);
            ungVien.add(new TabooScanPort.UngVien(row.externalCode(), row.tabooName(),
                    row.generation(), row.resolvedPersonId()));
        }
        if (ungVien.isEmpty()) {
            return;
        }
        for (TabooScanPort.KetQua ketQua : taboos.scan(ungVien)) {
            PersonRow row = theoRef.get(ketQua.ref());
            if (row == null) {
                continue;
            }
            Map<String, Object> context = new LinkedHashMap<>();
            // Ten huy cua CHINH DONG DANG NHAP, khong phai ten huy doc tu pha — xem javadoc lop.
            context.put("tenHuy", row.tabooName());
            if (ketQua.ancestorPersonId() != null) {
                context.put("bacTrenId", ketQua.ancestorPersonId().toString());
            }
            ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_TABOO_COLLISION, row.rowNo(),
                    ImportColumn.TEN_HUY.tieuDe(),
                    "Tên huý " + row.tabooName() + " ở dòng " + row.nhan()
                            + " trùng tên huý của một bậc trên đã có trong phả. Vì lý do riêng tư,"
                            + " bảng lỗi không nêu danh tính bậc trên ấy — bấm vào hồ sơ đính kèm"
                            + " cảnh báo để xem phần mình được phép xem. Đây là cảnh báo kỵ húy —"
                            + " nếu sổ chép đúng như vậy thì cứ giữ nguyên, hệ thống sẽ ghi lại"
                            + " việc ghi đè.",
                    context));
        }
    }
}
