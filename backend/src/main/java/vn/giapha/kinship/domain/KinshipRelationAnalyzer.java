package vn.giapha.kinship.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import vn.giapha.shared.vo.PersonId;

/**
 * Gom bằng chứng đồ thị cho một cặp người: LCA, cạnh trực tiếp, và quan hệ dâu/rể qua NGƯỜI NỐI.
 *
 * <p>Là domain service — chỉ phụ thuộc vào các port, không phụ thuộc Spring hay CSDL, nên test được
 * bằng đồ thị trong bộ nhớ. Tầng application chỉ việc nối analyzer với
 * {@link KinshipResolver} và cache.</p>
 *
 * <p><b>Quan hệ dâu/rể tính gián tiếp</b> (V3 §link_side): hai người có thể chẳng có tổ chung nào
 * mà vẫn phải gọi nhau là "thím" hay "bố chồng". Cách tìm:</p>
 * <ul>
 *   <li>{@link InLawDirection#ALTER_IS_SPOUSE} — duyệt vợ/chồng của alter, ai có huyết thống với
 *       ego thì người đó là NGƯỜI NỐI (thím = vợ của chú).</li>
 *   <li>{@link InLawDirection#EGO_IS_SPOUSE} — duyệt vợ/chồng của ego, ai có huyết thống với alter
 *       thì người đó là NGƯỜI NỐI (bố chồng = bố của chồng).</li>
 * </ul>
 * <p>Nhiều đường nối thì lấy đường <b>ngắn nhất</b>; hoà thì ưu tiên ALTER_IS_SPOUSE, vì đó là
 * người mới lấy vào họ nhà ego và danh xưng đi theo họ nhà ego.</p>
 */
public final class KinshipRelationAnalyzer {

    private final LcaPort lcaPort;
    private final PersonLookupPort personLookup;

    public KinshipRelationAnalyzer(LcaPort lcaPort, PersonLookupPort personLookup) {
        this.lcaPort = Objects.requireNonNull(lcaPort, "lcaPort khong duoc null");
        this.personLookup = Objects.requireNonNull(personLookup, "personLookup khong duoc null");
    }

    /** @return rỗng khi một trong hai người không tồn tại. */
    public Optional<RelationContext> analyze(PersonId egoId, PersonId alterId) {
        Optional<PersonView> ego = personLookup.byId(egoId);
        Optional<PersonView> alter = personLookup.byId(alterId);
        if (ego.isEmpty() || alter.isEmpty()) {
            return Optional.empty();
        }

        LcaResult blood = lcaPort.findLca(egoId, alterId).orElse(null);
        List<DirectLink> directLinks = lcaPort.directLinks(egoId, alterId);
        InLawLink inLaw = egoId.equals(alterId) ? null : findInLawLink(egoId, alterId);

        Set<PersonId> needed = new HashSet<>();
        needed.add(egoId);
        needed.add(alterId);
        collect(blood, needed);
        if (inLaw != null) {
            needed.add(inLaw.linkPerson());
            collect(inLaw.bloodPath(), needed);
        }
        Map<PersonId, PersonView> people = personLookup.byIds(needed);

        return Optional.of(new RelationContext(ego.get(), alter.get(), blood, directLinks, inLaw, people));
    }

    private void collect(LcaResult path, Set<PersonId> target) {
        if (path == null) {
            return;
        }
        target.addAll(path.egoPathUp());
        target.addAll(path.alterPathUp());
    }

    private InLawLink findInLawLink(PersonId egoId, PersonId alterId) {
        List<InLawLink> candidates = new ArrayList<>();

        for (SpouseLink spouse : lcaPort.spousesOf(alterId)) {
            if (!spouse.active() || spouse.spouse().equals(egoId)) {
                continue;
            }
            lcaPort.findLca(egoId, spouse.spouse()).ifPresent(path -> candidates.add(
                    new InLawLink(spouse.spouse(), InLawDirection.ALTER_IS_SPOUSE, path,
                            spouse.spouseOrder())));
        }

        for (SpouseLink spouse : lcaPort.spousesOf(egoId)) {
            if (!spouse.active() || spouse.spouse().equals(alterId)) {
                continue;
            }
            lcaPort.findLca(spouse.spouse(), alterId).ifPresent(path -> candidates.add(
                    new InLawLink(spouse.spouse(), InLawDirection.EGO_IS_SPOUSE, path,
                            spouse.spouseOrder())));
        }

        return candidates.stream()
                .min(Comparator.comparingInt(InLawLink::distance)
                        .thenComparing(link -> link.direction() == InLawDirection.ALTER_IS_SPOUSE ? 0 : 1)
                        .thenComparing(link -> link.linkPerson().value()))
                .orElse(null);
    }
}
