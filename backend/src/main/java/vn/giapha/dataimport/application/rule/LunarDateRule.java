package vn.giapha.dataimport.application.rule;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.LunarDeathDate;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.port.LunarDatePort;

/**
 * Ô "Ngày mất âm" có đọc được không, và ngày ấy có <b>thật sự tồn tại</b> không.
 *
 * <h2>Vì sao đây là lỗi chặn</h2>
 * Ngày giỗ sai thì nhắc giỗ sai, và một lời nhắc đã gửi thì <b>không gỡ lại được</b>: cả họ đã
 * cúng nhầm ngày, có khi vài năm liền trước khi ai đó nhận ra. Đây là loại sai duy nhất trong cả
 * bộ kiểm mà hậu quả rơi vào đời sống lễ nghi của dòng họ chứ không chỉ vào dữ liệu.
 *
 * <h2>Hai cách một ngày âm không tồn tại</h2>
 * <ul>
 *   <li><b>Mùng 30 của tháng thiếu.</b> Tháng âm có 29 hoặc 30 ngày tuỳ năm, và người chép sổ
 *       không có cách nào biết.</li>
 *   <li><b>Tháng nhuận của một năm không nhuận tháng ấy.</b> "15 tháng 8 nhuận" chỉ tồn tại ở
 *       những năm nhuận đúng tháng 8.</li>
 * </ul>
 * Cả hai đều trông hoàn toàn bình thường trên giấy, nên máy là thứ duy nhất bắt được.
 *
 * <h2>Không có năm thì không kiểm được — và đó là câu trả lời đúng</h2>
 * "Mất ngày 15 tháng 8, không rõ năm" là chuyện rất thường trong sổ cũ. Không có năm thì câu hỏi
 * "ngày này có tồn tại không" <b>không có đáp án</b>, vì nó phụ thuộc từng năm. Luật im lặng ở đây,
 * và riêng ca ngày 30 thì {@code LifeStatusRule} đã cảnh báo.
 */
@Component
@Order(70)
public class LunarDateRule implements ImportRule {

    private final LunarDatePort lunar;

    public LunarDateRule(LunarDatePort lunar) {
        this.lunar = lunar;
    }

    @Override
    public void apply(ValidationContext ctx) {
        for (PersonRow row : ctx.personRows()) {
            String raw = row.normalized().get(ImportColumn.NGAY_MAT_AM.tieuDe());
            Optional<LunarDeathDate.KetQua> ketQua = LunarDeathDate.doc(raw);
            if (ketQua.isEmpty()) {
                continue;
            }
            if (!ketQua.get().thanhCong()) {
                baoLoiDoc(ctx, row, raw, ketQua.get().loi());
                continue;
            }
            LunarDeathDate d = ketQua.get().value();
            if (!d.coNam() || !lunar.kiemDuoc(d.year())) {
                continue;
            }
            if (!lunar.tonTai(d.year(), d.month(), d.day(), d.leap())) {
                Map<String, Object> context = new LinkedHashMap<>();
                context.put("ngay", d.toString());
                context.put("nam", d.year());
                context.put("thangNhuan", d.leap());
                ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_LUNAR_DATE_NOT_EXIST, row.rowNo(),
                        ImportColumn.NGAY_MAT_AM.tieuDe(),
                        "Dòng " + row.nhan() + " ghi giỗ " + d + " nhưng năm âm " + d.year()
                                + " không có ngày đó"
                                + (d.leap() ? " (năm ấy không nhuận tháng " + d.month() + ")"
                                            : " (tháng " + d.month() + " năm ấy chỉ có 29 ngày)")
                                + ". Đối chiếu lại với sổ giấy — nhắc giỗ sai không sửa lại được"
                                + " sau khi đã gửi.",
                        context));
            }
        }
    }

    private void baoLoiDoc(ValidationContext ctx, PersonRow row, String raw, IssueCode code) {
        String msg = code == IssueCode.IMP_LUNAR_DATE_IS_SERIAL
                ? "Dòng " + row.nhan() + " có ô Ngày mất âm là một con số (" + raw + "). Excel đã"
                        + " tự đổi cách gõ 15/8 thành một ngày dương lịch. Định dạng cột Ngày mất"
                        + " âm thành Văn bản rồi gõ lại — máy tuyệt đối không đoán ngược, vì đoán"
                        + " sai là cả họ giỗ nhầm ngày."
                : "Dòng " + row.nhan() + " có ô Ngày mất âm không đọc được: " + raw
                        + ". Gõ theo dạng 15/8, hoặc 15/8 nhuận, hoặc 15/8/1945.";
        ctx.add(ImportIssue.nhanKhau(code, row.rowNo(), ImportColumn.NGAY_MAT_AM.tieuDe(), msg,
                Map.of("oGoc", raw)));
    }
}
