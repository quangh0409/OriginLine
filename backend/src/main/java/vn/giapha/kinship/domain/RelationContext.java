package vn.giapha.kinship.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import vn.giapha.shared.vo.PersonId;

/**
 * Toàn bộ bằng chứng đồ thị về <b>một cặp người</b>, gom lại một lần rồi mới suy luận.
 *
 * <p>Gom trước là có chủ ý: {@link RelationFactsFactory} và {@link KinshipResolver} nhờ vậy trở
 * thành hàm thuần trên dữ liệu, không gọi CSDL giữa chừng, và tính được cả danh xưng chiều ngược
 * ({@link #reversed()}) mà không phải hỏi lại đồ thị lần nữa.</p>
 *
 * @param ego         người hỏi (A)
 * @param alter       người được gọi (B)
 * @param lca         tổ chung gần nhất; {@code null} khi hai người không cùng huyết thống
 * @param directLinks cạnh nối trực tiếp giữa hai người
 * @param inLaw       quan hệ hôn nhân gián tiếp qua NGƯỜI NỐI; {@code null} nếu không có
 * @param people      bản chiếu của mọi người xuất hiện trên các đường đi, tra theo id
 */
public record RelationContext(
        PersonView ego,
        PersonView alter,
        LcaResult lca,
        List<DirectLink> directLinks,
        InLawLink inLaw,
        Map<PersonId, PersonView> people) {

    public RelationContext {
        Objects.requireNonNull(ego, "ego khong duoc null");
        Objects.requireNonNull(alter, "alter khong duoc null");
        directLinks = directLinks == null ? List.of() : List.copyOf(directLinks);
        Map<PersonId, PersonView> merged = new HashMap<>();
        if (people != null) {
            merged.putAll(people);
        }
        merged.putIfAbsent(ego.id(), ego);
        merged.putIfAbsent(alter.id(), alter);
        people = Map.copyOf(merged);
    }

    public static RelationContext bloodOnly(PersonView ego, PersonView alter, LcaResult lca,
            Map<PersonId, PersonView> people) {
        return new RelationContext(ego, alter, lca, List.of(), null, people);
    }

    /** Bản chiếu của một người trên đường đi; trả bản tối giản nếu chưa nạp được (không bao giờ null). */
    public PersonView view(PersonId id) {
        PersonView found = people.get(id);
        return found != null ? found : PersonView.minimal(id, null);
    }

    public boolean isSelf() {
        return ego.id().equals(alter.id());
    }

    /** Không có tổ chung, không cạnh trực tiếp còn hiệu lực, không quan hệ hôn nhân nào. */
    public boolean hasNoConnection() {
        return lca == null && inLaw == null && directLinks.stream().noneMatch(DirectLink::active);
    }

    /** Đảo vai ego/alter để tra danh xưng chiều ngược — thuần tính toán, không chạm CSDL. */
    public RelationContext reversed() {
        List<DirectLink> flipped = new ArrayList<>(directLinks.size());
        for (DirectLink link : directLinks) {
            flipped.add(link.reverse());
        }
        InLawLink flippedInLaw = null;
        if (inLaw != null) {
            InLawDirection direction = inLaw.direction() == InLawDirection.ALTER_IS_SPOUSE
                    ? InLawDirection.EGO_IS_SPOUSE
                    : InLawDirection.ALTER_IS_SPOUSE;
            flippedInLaw = new InLawLink(inLaw.linkPerson(), direction, inLaw.bloodPath().reversed(),
                    inLaw.spouseOrder());
        }
        return new RelationContext(alter, ego, lca == null ? null : lca.reversed(), flipped,
                flippedInLaw, people);
    }
}
