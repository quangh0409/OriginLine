package vn.giapha.demo.model;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Một quan hệ — sẽ được ghi ĐỒNG THỜI vào bảng {@code relationship} và graph AGE.
 *
 * <p>Quy ước chiều (chốt ở V2__core.sql, không được đảo):</p>
 * <ul>
 *   <li>{@code PARENT_BIO} / {@code PARENT_ADOPT}: from = cha/mẹ, to = con.</li>
 *   <li>{@code SPOUSE}: generator luôn ghi from = chồng, to = vợ; {@code spouseOrder} đánh số theo
 *       from (vợ cả = 1). Adapter đọc cả hai chiều.</li>
 *   <li>{@code HEIR}: from = người để lại hương hoả, to = người kế tự/đích tôn.</li>
 * </ul>
 */
public record DemoRelation(UUID id, UUID from, UUID to, String relType, Integer spouseOrder,
                           String heirType, LocalDate validFrom, LocalDate validTo,
                           String endReason, String note, Map<String, Object> attributes) {

    /** Nhãn cạnh AGE tương ứng: PARENT_BIO/PARENT_ADOPT gộp về một label PARENT có thuộc tính type. */
    public String edgeLabel() {
        return relType.startsWith("PARENT") ? "PARENT" : relType;
    }

    /** Giá trị thuộc tính {@code type} của cạnh PARENT: BIO hoặc ADOPT. */
    public String parentEdgeType() {
        return "PARENT_ADOPT".equals(relType) ? "ADOPT" : "BIO";
    }
}
