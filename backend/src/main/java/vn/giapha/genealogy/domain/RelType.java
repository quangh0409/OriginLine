package vn.giapha.genealogy.domain;

/**
 * Loại cạnh quan hệ. <b>Hướng cạnh là {@code from → to}</b> và ý nghĩa của hướng phụ thuộc loại:
 *
 * <ul>
 *   <li>{@link #PARENT_BIO} / {@link #PARENT_ADOPT} — {@code from} là <b>cha/mẹ</b>,
 *       {@code to} là <b>con</b>. Trong đồ thị AGE: {@code (p)-[:PARENT {type:'BIO'|'ADOPT'}]->(c)}.
 *       Vì vậy đi <b>ngược lên tổ tiên</b> phải dùng mũi tên ngược {@code <-[:PARENT*0..]-}.</li>
 *   <li>{@link #SPOUSE} — vô hướng về ngữ nghĩa nhưng lưu một chiều; {@code spouseOrder} cho
 *       đa thê/đa phu, {@code validFrom}/{@code validTo} cho tái hôn, ly hôn, goá.</li>
 *   <li>{@link #HEIR} — {@code from} là người để lại hương hoả, {@code to} là người nối dõi.</li>
 * </ul>
 *
 * <p><b>Dâu / rể không phải một loại cạnh.</b> Chúng được suy ra từ {@code SPOUSE} cộng huyết
 * thống; tạo cạnh riêng cho dâu/rể là làm hỏng suy luận danh xưng của context {@code kinship}.</p>
 */
public enum RelType {

    PARENT_BIO("PARENT", "BIO"),
    PARENT_ADOPT("PARENT", "ADOPT"),
    SPOUSE("SPOUSE", null),
    HEIR("HEIR", null);

    private final String edgeLabel;
    private final String edgeSubType;

    RelType(String edgeLabel, String edgeSubType) {
        this.edgeLabel = edgeLabel;
        this.edgeSubType = edgeSubType;
    }

    /** Nhãn cạnh trong đồ thị AGE ({@code PARENT} / {@code SPOUSE} / {@code HEIR}). */
    public String edgeLabel() {
        return edgeLabel;
    }

    /** Thuộc tính {@code type} trên cạnh {@code PARENT}; {@code null} với các nhãn khác. */
    public String edgeSubType() {
        return edgeSubType;
    }

    /** {@code true} với cạnh cha/mẹ → con, ruột lẫn nuôi. Đây là cạnh dựng nên cây phả đồ. */
    public boolean isParentEdge() {
        return this == PARENT_BIO || this == PARENT_ADOPT;
    }
}
