package vn.giapha.genealogy.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.UUID;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.RelType;

/**
 * Một cạnh quan hệ. Hướng luôn là {@code fromPersonId} tới {@code toPersonId}; ý nghĩa của hướng
 * phụ thuộc {@code relType} - với cạnh cha/mẹ thì {@code from} là cha/mẹ.
 *
 * @param spouseOrder thứ tự vợ/chồng cho <b>đa thê/đa phu</b>, 1 là vợ cả/chồng cả
 * @param validTo có giá trị nghĩa là quan hệ đã kết thúc (ly hôn hoặc một bên mất)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RelationshipDto(UUID id,
                              UUID fromPersonId,
                              UUID toPersonId,
                              RelType relType,
                              HeirKind heirKind,
                              Integer spouseOrder,
                              LocalDate validFrom,
                              LocalDate validTo,
                              String note) {
}
