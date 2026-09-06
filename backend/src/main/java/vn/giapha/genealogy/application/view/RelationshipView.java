package vn.giapha.genealogy.application.view;

import java.time.LocalDate;
import java.util.UUID;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.genealogy.domain.Relationship;

/**
 * Một cạnh quan hệ đã qua bộ lọc riêng tư.
 *
 * <p>Chỉ xuất hiện khi <b>cả hai đầu</b> cạnh đều được phép hiển thị với người gọi; quan hệ tới
 * một người còn sống bị ẩn sẽ biến mất hoàn toàn, đúng như ở {@code /tree}. Hướng luôn là
 * {@code from → to} và ý nghĩa của hướng phụ thuộc {@link RelType}.</p>
 */
public record RelationshipView(UUID id, UUID fromPersonId, UUID toPersonId, RelType relType,
                               HeirKind heirKind, Integer spouseOrder, LocalDate validFrom,
                               LocalDate validTo, String note) {

    public static RelationshipView of(Relationship rel) {
        return new RelationshipView(rel.id(), rel.fromPersonId(), rel.toPersonId(), rel.relType(),
                rel.heirKind(), rel.spouseOrder(), rel.validFrom(), rel.validTo(), rel.note());
    }
}
