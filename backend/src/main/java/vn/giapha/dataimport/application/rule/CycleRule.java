package vn.giapha.dataimport.application.rule;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.CycleFinder;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.port.AnchorAncestorPort;

/**
 * Vòng lặp tổ tiên — trong tệp, và <b>xuyên biên</b> giữa tệp với phả đã có.
 *
 * <h2>Vì sao phải bắt trước, không để tới lúc ghi</h2>
 * Việc nối cạnh đã có phép chặn vòng lặp ở mức <b>từng cạnh một</b>, nhưng nó bật ra giữa
 * transaction ghi 400 dòng — tức là cả lô cuộn lại, người nhập chỉ biết "hỏng" và phải đoán hỏng ở
 * đâu. Bắt ở đây thì họ nhận được ba cái mã và mở sổ ra tra được ngay.
 *
 * <h2>Ba đời của một phép dò</h2>
 * <ol>
 *   <li>Dựng đồ thị trong bộ nhớ từ các dòng trong tệp.</li>
 *   <li><b>Gieo tổ tiên của các mỏ neo</b>: mỗi người đã có trong phả mà tệp trỏ tới được lấy
 *       chuỗi tổ tiên bằng một lượt duyệt. Đây là thứ bắt được vòng lặp xuyên biên — dòng trong
 *       tệp khai cha là một người đã có, mà người đã có ấy lại là hậu duệ của một người khác trong
 *       chính tệp này.</li>
 *   <li>Duyệt sâu ba màu bằng ngăn xếp tường minh.</li>
 * </ol>
 */
@Component
@Order(40)
public class CycleRule implements ImportRule {

    private static final Logger log = LoggerFactory.getLogger(CycleRule.class);

    /**
     * Trần số đời đi ngược khi gieo tổ tiên mỏ neo.
     *
     * <p>Gia phả Việt dài nhất được chép lại vào khoảng 30 đời. Trần 60 là dư gấp đôi, và nó tồn
     * tại chủ yếu để một đồ thị <b>đã</b> có vòng lặp từ trước (dữ liệu cũ nhập tay) không làm phép
     * duyệt chạy mãi.</p>
     */
    private static final int TRAN_DOI = 60;

    /** Tiền tố phân biệt đỉnh là người đã có trong phả với đỉnh là mã trong tệp. */
    private static final String TIEN_TO_DA_CO = "person:";

    private final AnchorAncestorPort anchors;

    public CycleRule(AnchorAncestorPort anchors) {
        this.anchors = anchors;
    }

    @Override
    public void apply(ValidationContext ctx) {
        CycleFinder finder = new CycleFinder();
        Map<String, PersonRow> theoDinh = new LinkedHashMap<>();

        for (PersonRow row : ctx.personRows()) {
            String con = row.externalCode();
            if (con == null || con.isBlank()) {
                continue;
            }
            theoDinh.put(con, row);
            themCanh(ctx, finder, con, row.fatherCode());
            themCanh(ctx, finder, con, row.motherCode());
        }
        gieoMoNeo(ctx, finder);

        for (List<String> vong : finder.timVongLap()) {
            List<String> hienThi = vong.stream().map(CycleRule::nhan).toList();
            String chuoi = String.join(" -> ", hienThi);
            Integer dong = vong.stream()
                    .map(theoDinh::get)
                    .filter(r -> r != null)
                    .map(PersonRow::rowNo)
                    .min(Integer::compareTo)
                    .orElse(null);
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("chuoi", hienThi);
            ctx.add(new ImportIssue(IssueCode.IMP_CYCLE, ImportIssue.SHEET_NHAN_KHAU, dong,
                    ImportColumn.MA_CHA.tieuDe(),
                    "Có vòng lặp tổ tiên: " + chuoi + ". Một trong các mã cha/mẹ trên đường này bị"
                            + " chép nhầm — mở sổ ra tra ba mã đó là thấy.",
                    context));
        }
    }

    private void themCanh(ValidationContext ctx, CycleFinder finder, String con, String cha) {
        if (cha == null || cha.isBlank()) {
            return;
        }
        // Ma cha tro toi mot nguoi DA CO trong pha thi dinh phai la nguoi ay, khong phai cai ma —
        // neu khong thi chuoi to tien gieo o buoc sau se khong noi duoc vao do thi nay.
        UUID daCo = ctx.resolved().get(cha);
        finder.themCanh(dinhCua(ctx, con), daCo != null ? TIEN_TO_DA_CO + daCo : cha);
    }

    private String dinhCua(ValidationContext ctx, String ma) {
        UUID daCo = ctx.resolved().get(ma);
        return daCo != null ? TIEN_TO_DA_CO + daCo : ma;
    }

    /**
     * Lấy chuỗi tổ tiên của từng mỏ neo và nối vào đồ thị.
     *
     * <p>Mỏ neo = người đã có trong phả mà tệp trỏ tới. Thực tế 1–5 người một tệp (thường là cụ tổ
     * của chi), nên đây là vài lượt duyệt chứ không phải 400.</p>
     */
    private void gieoMoNeo(ValidationContext ctx, CycleFinder finder) {
        Set<UUID> moNeo = new LinkedHashSet<>();
        for (PersonRow row : ctx.personRows()) {
            themMoNeo(ctx, moNeo, row.fatherCode());
            themMoNeo(ctx, moNeo, row.motherCode());
            themMoNeo(ctx, moNeo, row.externalCode());
        }
        for (UUID neo : moNeo) {
            for (AnchorAncestorPort.Canh canh : anchors.ancestorEdges(neo, TRAN_DOI)) {
                finder.themCanh(TIEN_TO_DA_CO + canh.con(), TIEN_TO_DA_CO + canh.cha());
            }
        }
        if (!moNeo.isEmpty()) {
            log.debug("Do vong lap: gieo to tien cua {} mo neo", moNeo.size());
        }
    }

    private void themMoNeo(ValidationContext ctx, Set<UUID> moNeo, String ma) {
        UUID daCo = ma == null ? null : ctx.resolved().get(ma);
        if (daCo != null) {
            moNeo.add(daCo);
        }
    }

    /** Người đã có trong phả hiện ra dưới dạng id; mã trong tệp giữ nguyên để người nhập tra sổ. */
    private static String nhan(String dinh) {
        return dinh.startsWith(TIEN_TO_DA_CO)
                ? "người đã có " + dinh.substring(TIEN_TO_DA_CO.length())
                : dinh;
    }
}
