package vn.giapha.genealogy.api.graphql;

import java.time.LocalDate;
import vn.giapha.genealogy.api.rest.dto.PersonDto;
import vn.giapha.genealogy.application.view.PersonBadge;

/**
 * Vợ/chồng kèm dữ liệu của <b>chính cạnh hôn nhân</b>.
 *
 * <p>Tồn tại để client khỏi phải tự dò trong {@code relationships} tìm cạnh {@code SPOUSE} tương
 * ứng. {@code spouseOrder} phục vụ <b>đa thê/đa phu</b> (1 là vợ cả/chồng cả) và
 * {@code validFrom}/{@code validTo} mô tả tái hôn, ly hôn, goá - ba tình huống mà gia phả Việt gặp
 * thường xuyên.</p>
 *
 * @param isCurrent {@code false} khi {@code validTo} đã có giá trị
 * @param inLawRole con dâu hay con rể xét theo dòng họ này; {@code null} khi chưa xác định được
 */
public record SpouseLinkGql(PersonDto person, Integer spouseOrder, LocalDate validFrom,
                            LocalDate validTo, boolean isCurrent, PersonBadge inLawRole) {
}
