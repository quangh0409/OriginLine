package vn.giapha.genealogy.application.command;

import java.time.LocalDate;
import java.util.UUID;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.RelType;

/**
 * Nối hai nhân khẩu <b>đã tồn tại</b> bằng một cạnh quan hệ.
 *
 * <p>Hướng là {@code from} tới {@code to} và ý nghĩa phụ thuộc {@link RelType}: với cạnh cha/mẹ,
 * {@code from} là cha/mẹ. Vẽ ngược cạnh là lật ngược cả cây.</p>
 */
public record LinkRelationshipCommand(UUID fromPersonId, UUID toPersonId, RelType relType,
                                      HeirKind heirKind, Integer spouseOrder, LocalDate validFrom,
                                      LocalDate validTo, String note) {
}
