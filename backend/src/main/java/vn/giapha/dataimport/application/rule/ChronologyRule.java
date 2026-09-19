package vn.giapha.dataimport.application.rule;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.PersonRow;

/**
 * Hai phép đối chiếu con với cha: <b>năm sinh</b> và <b>đời thứ</b>.
 *
 * <h2>Con sinh trước cha</h2>
 * Chắc chắn là chép nhầm dòng hoặc nhầm mã. Rẻ để sửa bây giờ, đắt để phát hiện sau — vì khi ấy cả
 * nhánh con cháu đã treo vào sai người.
 *
 * <h2>Đời khai khác đời suy ra — và vì sao KHÔNG lặng lẽ lấy số suy ra</h2>
 * Hệ thống tự suy được đời thứ từ liên kết cha–con, nên về mặt kỹ thuật ta hoàn toàn có thể bỏ qua
 * con số người nhập khai. <b>Đừng làm thế.</b> Cột Đời trong tệp là thứ Trưởng chi chép từ cuốn sổ
 * giấy; khi nó lệch với số suy ra, một trong hai sai, và đó là tín hiệu <i>duy nhất</i> ta có để
 * phát hiện một mã cha bị gán nhầm — loại lỗi mà mọi luật khác đều mù, vì dữ liệu hoàn toàn nhất
 * quán. Lặng lẽ lấy số suy ra là tự tay vứt bỏ khả năng đối chiếu với sổ giấy.
 *
 * <p>Chỉ so khi <b>cả hai</b> đầu đều có số; sổ cũ thiếu đời là chuyện thường và không đáng chặn.</p>
 */
@Component
@Order(50)
public class ChronologyRule implements ImportRule {

    /**
     * Khoảng cách tuổi tối thiểu giữa cha/mẹ và con, tính theo năm.
     *
     * <p>Đặt 0 chứ không phải 13: mục tiêu là bắt <b>chép nhầm</b>, không phải phán xét chuyện
     * sinh nở. Một người cha sinh con năm 15 tuổi là chuyện có thật trong gia phả cũ; một người
     * cha sinh sau con thì không.</p>
     */
    private static final int CACH_TUOI_TOI_THIEU = 0;

    @Override
    public void apply(ValidationContext ctx) {
        for (PersonRow con : ctx.personRows()) {
            soSanhNamSinh(ctx, con, con.fatherCode(), ImportColumn.MA_CHA, "cha");
            soSanhNamSinh(ctx, con, con.motherCode(), ImportColumn.MA_ME, "mẹ");
            soSanhDoi(ctx, con);
        }
    }

    private void soSanhNamSinh(ValidationContext ctx, PersonRow con, String maCha,
                               ImportColumn cot, String vaiTro) {
        PersonRow cha = ctx.rowOf(maCha);
        if (cha == null || con.birthYear() == null || cha.birthYear() == null) {
            return;
        }
        if (con.birthYear() - cha.birthYear() >= CACH_TUOI_TOI_THIEU) {
            return;
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("namSinhCon", con.birthYear());
        context.put("namSinhCha", cha.birthYear());
        context.put("maCha", maCha);
        ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_CHILD_BEFORE_PARENT, con.rowNo(),
                ImportColumn.NAM_SINH.tieuDe(),
                "Dòng " + con.nhan() + " sinh năm " + con.birthYear() + ", trước " + vaiTro + " ("
                        + cha.nhan() + ", sinh " + cha.birthYear() + "). Nhiều khả năng cột "
                        + cot.tieuDe() + " bị chép nhầm dòng.",
                context));
    }

    private void soSanhDoi(ValidationContext ctx, PersonRow con) {
        PersonRow cha = ctx.rowOf(con.fatherCode());
        if (cha == null) {
            cha = ctx.rowOf(con.motherCode());
        }
        if (cha == null || con.generation() == null || cha.generation() == null) {
            return;
        }
        int mongDoi = cha.generation() + 1;
        if (con.generation() == mongDoi) {
            return;
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("doiKhai", con.generation());
        context.put("doiSuyRa", mongDoi);
        context.put("maCha", cha.externalCode());
        ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_GENERATION_MISMATCH, con.rowNo(),
                ImportColumn.DOI.tieuDe(),
                "Dòng " + con.nhan() + " khai đời " + con.generation() + ", nhưng cha/mẹ ("
                        + cha.nhan() + ") ở đời " + cha.generation() + " nên con phải là đời "
                        + mongDoi + ". Một trong hai con số sai — đối chiếu lại với sổ giấy trước"
                        + " khi sửa.",
                context));
    }
}
