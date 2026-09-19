package vn.giapha.dataimport.application.rule;

import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.port.PlaceCodePort;

/**
 * Mã tỉnh/quốc gia của nguyên quán.
 *
 * <h2>Vì sao cột này có mặt ngay từ lần nhập đầu tiên, chứ không đợi đợt báo cáo</h2>
 * {@code person.native_place} là chữ tự do và không có bảng mã nào đi kèm. Hệ quả: "Hà Nội",
 * "TP. Hà Nội" và "Hanoi" là ba nhóm khác nhau với mọi phép gộp, nên <b>báo cáo dân số (FR-4.3)
 * không dựng được</b> — và không có cách nào hoà giải sau khi cả dòng họ đã gõ xong. Quyết muộn
 * nghĩa là cả dòng họ phải nhập lại nơi ở, một việc không ai chịu làm lần thứ hai. Một cột thêm
 * vào mẫu ngay bây giờ rẻ hơn nhiều lần.
 *
 * <h2>Cả hai vấn đề đều là CẢNH BÁO, cố ý</h2>
 * Nguyên quán không phải dữ kiện phả hệ: thiếu nó thì cây vẫn đúng, giỗ vẫn đúng, danh xưng vẫn
 * đúng. Chặn cả một lô 400 người vì vài ô nguyên quán là đánh đổi tồi.
 *
 * <h2>Danh mục rỗng thì luật tự tắt</h2>
 * Nội dung {@code place_division} là dữ liệu pháp quy do quản trị nạp, không phải thứ chép tay vào
 * migration. Trước khi nó được nạp, mọi mã đều "không hợp lệ" — và bắn 400 cảnh báo vì một bảng ta
 * chưa nạp là lỗi của ta, không phải của người nhập.
 */
@Component
@Order(100)
public class PlaceCodeRule implements ImportRule {

    private static final Logger log = LoggerFactory.getLogger(PlaceCodeRule.class);

    private final PlaceCodePort places;

    public PlaceCodeRule(PlaceCodePort places) {
        this.places = places;
    }

    @Override
    public void apply(ValidationContext ctx) {
        Set<String> hopLe = places.allCodes();
        if (hopLe.isEmpty()) {
            log.info("Danh muc place_division con rong: bo qua luat ma tinh cho lo {}", ctx.batchId());
            return;
        }
        for (PersonRow row : ctx.personRows()) {
            String ma = row.nativePlaceCode();
            boolean coChu = row.nativePlace() != null && !row.nativePlace().isBlank();
            if (ma == null || ma.isBlank()) {
                if (coChu) {
                    ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_MISSING_PLACE_CODE, row.rowNo(),
                            ImportColumn.MA_NGUYEN_QUAN.tieuDe(),
                            "Dòng " + row.nhan() + " có Nguyên quán bằng chữ (" + row.nativePlace()
                                    + ") nhưng chưa có mã tỉnh. Không có mã thì người này không vào"
                                    + " được bảng thống kê theo quê quán.",
                            Map.of("nguyenQuan", row.nativePlace())));
                }
                continue;
            }
            if (!hopLe.contains(ma)) {
                ctx.add(ImportIssue.nhanKhau(IssueCode.IMP_UNKNOWN_PLACE_CODE, row.rowNo(),
                        ImportColumn.MA_NGUYEN_QUAN.tieuDe(),
                        "Dòng " + row.nhan() + " có mã nguyên quán " + ma
                                + " không nằm trong danh mục tỉnh/quốc gia.",
                        Map.of("ma", ma)));
            }
        }
    }
}
