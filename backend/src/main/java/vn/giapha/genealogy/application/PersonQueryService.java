package vn.giapha.genealogy.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.application.view.RelationshipView;
import vn.giapha.genealogy.domain.GraphNodeRef;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.Relationship;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.genealogy.domain.port.RelationshipRepository;
import vn.giapha.genealogy.domain.port.TreeGraphPort;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.PersonId;

/**
 * Đọc hồ sơ nhân khẩu và các quan hệ trực tiếp của họ.
 *
 * <h2>Không tồn tại, hay không được biết là tồn tại</h2>
 * {@link #byId} ném {@code 404} cho <b>cả hai</b> trường hợp: bản ghi không có thật, và bản ghi có
 * thật nhưng người gọi không được phép biết điều đó (người còn sống với Khách, bản ghi đã xoá mềm
 * với người không phải quản trị). Trả {@code 403} ở trường hợp thứ hai là tự xác nhận người đó tồn
 * tại - đúng thứ mà việc lọc đang cố giấu. Bên GraphQL, cùng ngữ nghĩa đó là {@code null}.
 *
 * <h2>Quan hệ cũng bị lọc</h2>
 * Một cạnh chỉ xuất hiện khi <b>đầu kia</b> cũng hiển thị được. Nếu không thì chỉ cần đếm số cạnh
 * là suy ra được một người còn sống đang bị ẩn ở đầu bên kia.
 */
@Service
public class PersonQueryService {

    private final PersonRepository persons;
    private final RelationshipRepository relationships;
    private final BranchRepository branches;
    private final TreeGraphPort graph;
    private final PrivacyTierService privacy;

    public PersonQueryService(PersonRepository persons, RelationshipRepository relationships,
                              BranchRepository branches, TreeGraphPort graph,
                              PrivacyTierService privacy) {
        this.persons = persons;
        this.relationships = relationships;
        this.branches = branches;
        this.graph = graph;
        this.privacy = privacy;
    }

    /** Hồ sơ đầy đủ kèm quan hệ lõi; ném {@code 404} khi không được phép biết là nó tồn tại. */
    @Transactional(readOnly = true)
    public PersonView byId(UUID personId) {
        return find(personId).orElseThrow(() -> new NotFoundException(
                GenealogyProblemCodes.NOT_FOUND,
                "Khong tim thay nhan khau voi dinh danh " + personId));
    }

    /** Như {@link #byId} nhưng trả rỗng thay vì ném - dạng mà GraphQL cần. */
    @Transactional(readOnly = true)
    public Optional<PersonView> find(UUID personId) {
        CallerContext caller = privacy.caller();
        Person person = persons.byId(PersonId.of(personId)).orElse(null);
        if (person == null || !privacy.canSee(person, caller)) {
            return Optional.empty();
        }
        List<Relationship> edges = relationships.byPerson(personId);
        Set<UUID> otherEnds = new LinkedHashSet<>();
        for (Relationship edge : edges) {
            UUID other = edge.otherEnd(personId);
            if (other != null) {
                otherEnds.add(other);
            }
        }
        Map<UUID, Person> visibleOthers = visiblePersons(otherEnds, caller);
        List<RelationshipView> views = new ArrayList<>();
        for (Relationship edge : edges) {
            UUID other = edge.otherEnd(personId);
            if (other != null && visibleOthers.containsKey(other)) {
                views.add(RelationshipView.of(edge));
            }
        }
        BranchDirectory dir = branchDirectoryFor(concat(person, visibleOthers.values()));
        return Optional.of(privacy.toView(person, caller, dir).withRelationships(views));
    }

    /** Các cạnh quan hệ trực tiếp một bậc đã lọc - dùng cho những trường lồng nhau của GraphQL. */
    @Transactional(readOnly = true)
    public List<RelationshipView> relationshipsOf(UUID personId) {
        return find(personId).map(PersonView::relationships).orElseGet(List::of);
    }

    /** Nạp nhiều hồ sơ một lượt, đã lọc riêng tư - tránh N+1 khi GraphQL đi vào các nhánh lồng nhau. */
    @Transactional(readOnly = true)
    public List<PersonView> visibleByIds(Collection<UUID> personIds) {
        if (personIds == null || personIds.isEmpty()) {
            return List.of();
        }
        CallerContext caller = privacy.caller();
        List<Person> loaded = persons.byIds(personIds).stream()
                .filter(person -> privacy.canSee(person, caller))
                .toList();
        BranchDirectory dir = branchDirectoryFor(loaded);
        return loaded.stream().map(person -> privacy.toView(person, caller, dir)).toList();
    }

    /**
     * Tổ tiên tới {@code depth} đời, gần nhất trước.
     *
     * <p>Người đã bị lọc vẫn <b>được đi xuyên qua</b> ở tầng đồ thị - bỏ hẳn họ khỏi phép duyệt sẽ
     * làm đứt đường lên tổ tiên và khiến các đời trên biến mất theo, dù chính họ là người đã khuất
     * và hoàn toàn công khai.</p>
     */
    @Transactional(readOnly = true)
    public List<PersonView> ancestorsOf(UUID personId, int depth) {
        return orderedViews(graph.ancestors(personId, clampDepth(depth)));
    }

    /** Con cháu tới {@code depth} đời, đã làm phẳng; gốc bị loại khỏi kết quả. */
    @Transactional(readOnly = true)
    public List<PersonView> descendantsOf(UUID personId, int depth, int limit) {
        List<GraphNodeRef> refs = graph.descendants(personId, clampDepth(depth)).stream()
                .filter(ref -> !ref.personId().equals(personId))
                .limit(Math.max(1, limit))
                .toList();
        return orderedViews(refs);
    }

    /** Tổng số con chưa bị xoá mềm - dữ liệu cho nút "mở rộng" trên canvas. */
    @Transactional(readOnly = true)
    public int childCountOf(UUID personId) {
        return persons.countChildren(List.of(personId)).getOrDefault(personId, 0);
    }

    private List<PersonView> orderedViews(List<GraphNodeRef> refs) {
        List<UUID> ids = refs.stream().map(GraphNodeRef::personId).toList();
        Map<UUID, PersonView> byId = new LinkedHashMap<>();
        for (PersonView view : visibleByIds(ids)) {
            byId.put(view.id(), view);
        }
        // Giữ nguyên thứ tự của phép duyệt đồ thị (gần nhất trước), không phải thứ tự của kho.
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    private Map<UUID, Person> visiblePersons(Collection<UUID> ids, CallerContext caller) {
        Map<UUID, Person> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        for (Person person : persons.byIds(ids)) {
            if (privacy.canSee(person, caller)) {
                result.put(person.rawId(), person);
            }
        }
        return result;
    }

    private BranchDirectory branchDirectoryFor(Collection<Person> people) {
        List<UUID> branchIds = new ArrayList<>();
        for (Person person : people) {
            if (person.primaryBranchId() != null) {
                branchIds.add(person.primaryBranchId());
            }
        }
        return BranchDirectory.load(branches, branchIds);
    }

    private List<Person> concat(Person first, Collection<Person> rest) {
        List<Person> all = new ArrayList<>();
        all.add(first);
        all.addAll(rest);
        return all;
    }

    private int clampDepth(int depth) {
        return Math.max(1, Math.min(depth, vn.giapha.genealogy.application.command.TreeQuery.MAX_DEPTH));
    }
}
