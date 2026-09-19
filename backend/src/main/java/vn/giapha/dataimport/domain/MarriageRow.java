package vn.giapha.dataimport.domain;

import java.util.Map;

/**
 * Một dòng trang <b>Hôn phối</b> ở khu vực chờ.
 *
 * @param spouseOrder bậc — vợ cả là 1. Đây là dữ liệu đầu vào của
 *        {@code ux_relationship_spouse_order} và của phép tính danh xưng cho con các bà; thiếu nó
 *        thì cả hai đều sai mà không báo gì.
 */
public record MarriageRow(int rowNo,
                          String husbandCode,
                          String wifeCode,
                          Integer spouseOrder,
                          Integer validFromYear,
                          Integer validToYear,
                          CellCodec.EndReason endReason,
                          Map<String, String> raw) {

    public MarriageRow {
        raw = raw == null ? Map.of() : Map.copyOf(raw);
    }

    public boolean duCap() {
        return husbandCode != null && !husbandCode.isBlank()
                && wifeCode != null && !wifeCode.isBlank();
    }

    /**
     * Trỏ lại cặp vợ chồng sau khi một dòng Nhân khẩu bị bỏ vì được gộp.
     *
     * <p>Quên bước này thì một dòng hôn phối trỏ tới một mã không còn tồn tại, và bước ghi lặng lẽ
     * bỏ qua nó — nghĩa là <b>một cặp vợ chồng biến mất khỏi phả mà không ai biết</b>. Đó là loại
     * mất mát không bao giờ tự lộ ra.</p>
     */
    public MarriageRow withCap(String chong, String vo) {
        return new MarriageRow(rowNo, chong, vo, spouseOrder, validFromYear, validToYear,
                endReason, raw);
    }
}
