package vn.giapha.genealogy.api.rest.public_.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.RelType;

/**
 * Một cạnh quan hệ trên bề mặt công khai.
 *
 * <p>Giữ {@code heirKind} vì {@code đích tôn}/{@code thừa tự} là dữ liệu phả hệ đúng nghĩa của
 * người đã khuất. <b>Bỏ</b> {@code validFrom}/{@code validTo} và {@code note}: mốc kết thúc hôn
 * nhân là chuyện ly hôn hoặc tang chế của gia đình, còn {@code note} là văn bản tự do không ai
 * kiểm duyệt trước khi nó ra tới người lạ.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicRelationDto(UUID id, UUID fromPersonId, UUID toPersonId, RelType relType,
                                HeirKind heirKind, Integer spouseOrder) {
}
