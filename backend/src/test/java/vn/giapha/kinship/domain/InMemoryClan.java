package vn.giapha.kinship.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * Do thi pha he trong BO NHO — hien thuc {@link LcaPort} va {@link PersonLookupPort} de bo test ca
 * bien danh xung chay khong can Spring, khong can CSDL, khong can Apache AGE.
 *
 * <p>Javadoc cua {@link LcaPort} ghi ro y do nay: "Giu do thi sau port nay de KinshipResolver va
 * RelationFactsFactory test duoc bang do thi trong bo nho". Phan LCA khong tu viet lai ma goi thang
 * {@link LcaCalculator} — dung lop ma {@code AgeLcaAdapter} dung o production, nen hanh vi cua test
 * va cua production khong the lech nhau.</p>
 *
 * <p>Id nhan khau sinh <b>tat dinh</b> tu ten (UUID name-based), nen thong bao loi doc duoc va
 * phep pha hoa "id nho hon" cua {@link LcaCalculator} cho ket qua on dinh giua cac lan chay.</p>
 */
final class InMemoryClan implements LcaPort, PersonLookupPort {

    private final Map<String, PersonId> idsByName = new LinkedHashMap<>();
    private final Map<PersonId, PersonView> people = new LinkedHashMap<>();
    private final List<ParentEdge> parentEdges = new ArrayList<>();
    private final List<SpouseLink> spouseLinks = new ArrayList<>();
    private final List<HeirEdge> heirEdges = new ArrayList<>();

    private record HeirEdge(PersonId holder, PersonId heir, String subtype) {
    }

    // ------------------------------------------------------------------ Dung do thi

    PersonId nam(String name) {
        return person(name, Gender.MALE, null, null, false);
    }

    PersonId nu(String name) {
        return person(name, Gender.FEMALE, null, null, false);
    }

    /** Nam kem thu tu sinh trong nha — thu tu nho hon la anh/chi. */
    PersonId nam(String name, int birthOrder) {
        return person(name, Gender.MALE, birthOrder, null, false);
    }

    PersonId nu(String name, int birthOrder) {
        return person(name, Gender.FEMALE, birthOrder, null, false);
    }

    PersonId person(String name, Gender gender, Integer birthOrder, LocalDate birthSolar, boolean deleted) {
        PersonId id = idsByName.computeIfAbsent(name, InMemoryClan::deterministicId);
        people.put(id, new PersonView(id, gender, null, birthOrder, birthSolar, deleted, name, null, null));
        return id;
    }

    /** Danh dau mot nguoi da xoa mem — van di XUYEN QUA duoc nhung khong duoc chon lam LCA. */
    void xoaMem(PersonId id) {
        PersonView current = people.get(id);
        people.put(id, new PersonView(current.id(), current.gender(), current.generation(),
                current.birthOrder(), current.birthSolar(), true, current.displayName(),
                current.branchId(), current.branchPath()));
    }

    /** Canh PARENT ruot: cha/me -> con. */
    void conRuot(PersonId parent, PersonId child) {
        parentEdges.add(ParentEdge.bio(parent, child));
    }

    /** Canh PARENT nhan nuoi: cha/me nuoi -> con nuoi. Canh nuoi VAN nam tren duong di pha he. */
    void conNuoi(PersonId parent, PersonId child) {
        parentEdges.add(ParentEdge.adopt(parent, child));
    }

    /** Hon nhan con hieu luc. {@code order} la spouse_order: vo ca = 1, vo hai = 2... */
    void voChong(PersonId a, PersonId b, int order) {
        spouseLinks.add(SpouseLink.current(a, b, order));
    }

    /** Hon nhan da cham dut (ly hon / da ghi valid_to) — khong sinh danh xung dau/re. */
    void voChongDaChamDut(PersonId a, PersonId b, int order, LocalDate validTo) {
        spouseLinks.add(SpouseLink.ended(a, b, order, validTo));
    }

    /** Canh HEIR: {@code holder} la nguoi de lai huong hoa, {@code heir} la nguoi ke tu/dich ton. */
    void heir(PersonId holder, PersonId heir, String subtype) {
        heirEdges.add(new HeirEdge(holder, heir, subtype));
    }

    // ------------------------------------------------------------------ LcaPort

    @Override
    public Optional<LcaResult> findLca(PersonId ego, PersonId alter) {
        return LcaCalculator.compute(ego, alter, parentEdges, parentEdges, people::get);
    }

    @Override
    public List<DirectLink> directLinks(PersonId ego, PersonId alter) {
        List<DirectLink> links = new ArrayList<>();
        for (ParentEdge edge : parentEdges) {
            DirectLinkType type = edge.adopt() ? DirectLinkType.PARENT_ADOPT : DirectLinkType.PARENT_BIO;
            String subtype = edge.adopt() ? "ADOPT" : "BIO";
            if (edge.parent().equals(ego) && edge.child().equals(alter)) {
                links.add(new DirectLink(type, subtype, false, true, ego, alter));
            } else if (edge.parent().equals(alter) && edge.child().equals(ego)) {
                links.add(new DirectLink(type, subtype, true, true, alter, ego));
            }
        }
        for (SpouseLink spouse : spouseLinks) {
            boolean between = (spouse.person().equals(ego) && spouse.spouse().equals(alter))
                    || (spouse.person().equals(alter) && spouse.spouse().equals(ego));
            if (between) {
                // Canh SPOUSE duoc chuan hoa thanh vo huong nen reversed luon false (xem DirectLink).
                links.add(new DirectLink(DirectLinkType.SPOUSE, null, false, spouse.active(), ego, alter));
            }
        }
        for (HeirEdge edge : heirEdges) {
            if (edge.holder().equals(ego) && edge.heir().equals(alter)) {
                links.add(new DirectLink(DirectLinkType.HEIR, edge.subtype(), false, true, ego, alter));
            } else if (edge.holder().equals(alter) && edge.heir().equals(ego)) {
                links.add(new DirectLink(DirectLinkType.HEIR, edge.subtype(), true, true, alter, ego));
            }
        }
        return links;
    }

    @Override
    public List<SpouseLink> spousesOf(PersonId person) {
        List<SpouseLink> result = new ArrayList<>();
        for (SpouseLink link : spouseLinks) {
            if (link.person().equals(person)) {
                result.add(link);
            } else if (link.spouse().equals(person)) {
                result.add(new SpouseLink(person, link.person(), link.spouseOrder(), link.validFrom(),
                        link.validTo(), link.active()));
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ PersonLookupPort

    @Override
    public Optional<PersonView> byId(PersonId id) {
        return Optional.ofNullable(people.get(id));
    }

    @Override
    public Map<PersonId, PersonView> byIds(Collection<PersonId> ids) {
        Map<PersonId, PersonView> result = new LinkedHashMap<>();
        for (PersonId id : ids) {
            PersonView view = people.get(id);
            if (view != null) {
                result.put(id, view);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ Tien ich cho test

    KinshipRelationAnalyzer analyzer() {
        return new KinshipRelationAnalyzer(this, this);
    }

    RelationContext contextOf(PersonId ego, PersonId alter) {
        return analyzer().analyze(ego, alter)
                .orElseThrow(() -> new IllegalStateException("Khong dung duoc RelationContext cho cap nay"));
    }

    RelationFacts factsOf(PersonId ego, PersonId alter) {
        return new RelationFactsFactory().from(contextOf(ego, alter));
    }

    /** Tra danh xung ego goi alter theo bo luat DEFAULT mien Bac doc tu file seed. */
    KinshipResolution resolve(PersonId ego, PersonId alter) {
        return resolve(ego, alter, SeedKinshipRules.mienBac());
    }

    KinshipResolution resolve(PersonId ego, PersonId alter, KinshipRuleSet rules) {
        return new KinshipResolver().resolve(contextOf(ego, alter), rules);
    }

    String danhXung(PersonId ego, PersonId alter) {
        return resolve(ego, alter).title();
    }

    String danhXung(PersonId ego, PersonId alter, KinshipRuleSet rules) {
        return resolve(ego, alter, rules).title();
    }

    /** Tra id theo ten da dat — tien khi mot fixture dung san duoc dung lai o nhieu test. */
    PersonId id(String name) {
        PersonId id = idsByName.get(name);
        if (id == null) {
            throw new IllegalArgumentException("Chua co nhan khau ten '" + name + "' trong fixture");
        }
        return id;
    }

    String ten(PersonId id) {
        PersonView view = people.get(id);
        return view == null ? String.valueOf(id) : view.displayName();
    }

    private static PersonId deterministicId(String name) {
        return PersonId.of(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
