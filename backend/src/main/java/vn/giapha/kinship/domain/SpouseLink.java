package vn.giapha.kinship.domain;

import java.time.LocalDate;
import java.util.Objects;
import vn.giapha.shared.vo.PersonId;

/**
 * Một cạnh {@code SPOUSE} của một người. Đa thê/đa phu được biểu diễn bằng <b>nhiều dòng</b>, phân
 * biệt bằng {@code spouseOrder} (vợ cả = 1, vợ hai = 2...). Tái hôn phân biệt bằng
 * {@code validFrom}/{@code validTo}.
 *
 * <p>Thứ tự vợ/chồng <b>không</b> làm đổi danh xưng gốc — rule engine chỉ trả "vợ"/"chồng"; việc
 * ghép thành "vợ cả", "vợ hai" là chuyện của tầng hiển thị (V3 nhóm I).</p>
 */
public record SpouseLink(
        PersonId person,
        PersonId spouse,
        Integer spouseOrder,
        LocalDate validFrom,
        LocalDate validTo,
        boolean active) {

    public SpouseLink {
        Objects.requireNonNull(person, "SpouseLink.person khong duoc null");
        Objects.requireNonNull(spouse, "SpouseLink.spouse khong duoc null");
    }

    public static SpouseLink current(PersonId person, PersonId spouse, Integer order) {
        return new SpouseLink(person, spouse, order, null, null, true);
    }

    /** Hôn nhân đã chấm dứt (ly hôn/goá đã ghi {@code valid_to}) — không sinh danh xưng dâu/rể. */
    public static SpouseLink ended(PersonId person, PersonId spouse, Integer order, LocalDate validTo) {
        return new SpouseLink(person, spouse, order, null, validTo, false);
    }
}
