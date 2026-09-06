package vn.giapha.genealogy.api.graphql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import vn.giapha.genealogy.api.rest.GenealogyDtoMapper;
import vn.giapha.genealogy.api.rest.dto.BranchRefDto;
import vn.giapha.genealogy.api.rest.dto.PersonAccessMetaDto;
import vn.giapha.genealogy.api.rest.dto.PersonDto;
import vn.giapha.genealogy.api.rest.dto.PersonNameDto;
import vn.giapha.genealogy.api.rest.dto.RelationshipDto;
import vn.giapha.genealogy.application.PersonQueryService;
import vn.giapha.genealogy.application.PersonSearchService;
import vn.giapha.genealogy.application.command.PersonSearchQuery;
import vn.giapha.genealogy.application.view.PageView;
import vn.giapha.genealogy.application.view.PersonBadge;
import vn.giapha.genealogy.application.view.PersonSummaryView;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.RelType;

/**
 * Resolver GraphQL cho {@code Person} và {@code PersonPage}.
 *
 * <h2>Vì sao có GraphQL bên cạnh REST</h2>
 * REST trả projection cây <b>phẳng</b> với độ sâu cố định. GraphQL để client <b>tự chọn hình dạng
 * và độ sâu</b> của cây lồng nhau - ví dụ "3 đời con cháu, mỗi người kèm vợ/chồng và ngày giỗ,
 * nhưng không cần tiểu sử". Mọi thao tác <b>ghi</b> đi qua REST; schema này không có Mutation.
 *
 * <h2>Khác biệt duy nhất về ngữ nghĩa so với REST</h2>
 * Trường bị ẩn theo phân tầng riêng tư trả {@code null} thay vì vắng mặt, vì GraphQL không có khái
 * niệm "trường vắng mặt". Ý nghĩa giữ nguyên: {@code null} ở đây có thể là "không có dữ liệu"
 * <b>hoặc</b> "bạn không được xem", và cố ý không phân biệt được. Người còn sống mà người gọi
 * không được thấy thì {@code person(id:)} trả {@code null} - tương đương 404 bên REST.
 *
 * <p>Bộ lọc riêng tư nằm ở tầng application và chạy tươi cho <b>mọi</b> trường lồng nhau, kể cả
 * khi client đi sâu nhiều bậc: mỗi bậc đều gọi lại use case đã lọc, không có đường tắt nào đọc
 * thẳng kho dữ liệu.</p>
 */
@Controller
public class PersonGraphQlController {

    private final PersonQueryService personQuery;
    private final PersonSearchService personSearch;

    public PersonGraphQlController(PersonQueryService personQuery, PersonSearchService personSearch) {
        this.personQuery = personQuery;
        this.personSearch = personSearch;
    }

    /** Một nhân khẩu; {@code null} khi không tồn tại <b>hoặc</b> người gọi không được biết là có. */
    @QueryMapping
    public PersonDto person(@Argument UUID id) {
        return personQuery.find(id).map(GenealogyDtoMapper::toDto).orElse(null);
    }

    /** Tìm kiếm không dấu trên mọi lớp tên (FR-4.4). */
    @QueryMapping
    public PersonPageGql searchPersons(@Argument String q, @Argument PersonFilterInput filter,
                                       @Argument PageInputGql page) {
        PageInputGql paging = PageInputGql.orDefault(page);
        PersonSearchQuery query = new PersonSearchQuery(q,
                filter == null ? null : filter.generation(),
                filter == null ? null : filter.branchId(),
                filter == null ? null : filter.nativePlace(),
                filter == null ? null : filter.isAlive(),
                filter != null && Boolean.TRUE.equals(filter.includeDeleted()),
                paging.pageOrDefault(), paging.sizeOrDefault(), paging.sort());

        PageView<PersonSummaryView> result = personSearch.search(query);
        // Dạng rút gọn của tìm kiếm không đủ cho type Person, nên nạp lại đầy đủ theo lô cho đúng
        // một trang - vẫn là một truy vấn, không phải N.
        List<PersonDto> items = toDtos(personQuery.visibleByIds(
                result.items().stream().map(PersonSummaryView::id).toList()));
        return new PersonPageGql(items, new PersonPageGql.PageInfoGql(result.page().page(),
                result.page().size(), (int) result.page().totalElements(),
                result.page().totalPages(), result.page().hasNext(), result.page().sort()));
    }

    // -----------------------------------------------------------------------------------
    // Trường của type Person
    // -----------------------------------------------------------------------------------

    /** Lọc theo lớp tên, ví dụ chỉ lấy tên thụy để soạn văn khấn. */
    @SchemaMapping(typeName = "Person")
    public List<PersonNameDto> names(PersonDto person, @Argument NameType type) {
        List<PersonNameDto> names = person.names() == null ? List.of() : person.names();
        return type == null ? names : names.stream().filter(name -> name.nameType() == type).toList();
    }

    @SchemaMapping(typeName = "Person")
    public BranchRefDto branch(PersonDto person) {
        return person.primaryBranch();
    }

    @SchemaMapping(typeName = "Person")
    public PersonAccessMetaDto access(PersonDto person) {
        return person.meta();
    }

    /**
     * Nhãn nghiệp vụ cấp hồ sơ.
     *
     * <p>Bộ nhãn đầy đủ (đích tôn, con nuôi, dâu/rể) cần ngữ cảnh cả một nhánh cây nên được tính ở
     * {@code TreeNode.badges}. Ở đây chỉ có nhãn suy được từ chính hồ sơ, để canvas không phải suy
     * luận và cũng không nhận nhãn sai.</p>
     */
    @SchemaMapping(typeName = "Person")
    public List<PersonBadge> badges(PersonDto person) {
        return person.isAlive() ? List.of() : List.of(PersonBadge.DECEASED);
    }

    @SchemaMapping(typeName = "Person")
    public List<PersonDto> parents(PersonDto person, @Argument String kind) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (RelationshipDto edge : edgesOf(person)) {
            if (isParentKind(edge.relType(), kind) && person.id().equals(edge.toPersonId())) {
                ids.add(edge.fromPersonId());
            }
        }
        return toDtos(personQuery.visibleByIds(ids));
    }

    /**
     * Con, đã lọc phân tầng nên danh sách có thể <b>ngắn hơn</b> {@code childCount} - đó là kết quả
     * đúng, không phải dữ liệu thiếu.
     */
    @SchemaMapping(typeName = "Person")
    public List<PersonDto> children(PersonDto person, @Argument String kind, @Argument Integer limit,
                                    @Argument Integer offset) {
        List<UUID> ids = new ArrayList<>();
        for (RelationshipDto edge : edgesOf(person)) {
            if (isParentKind(edge.relType(), kind) && person.id().equals(edge.fromPersonId())) {
                ids.add(edge.toPersonId());
            }
        }
        int from = Math.min(offset == null ? 0 : Math.max(0, offset), ids.size());
        int to = Math.min(from + (limit == null ? 50 : Math.max(1, limit)), ids.size());
        return toDtos(personQuery.visibleByIds(ids.subList(from, to)));
    }

    @SchemaMapping(typeName = "Person")
    public int childCount(PersonDto person) {
        return personQuery.childCountOf(person.id());
    }

    /** Vợ/chồng kèm dữ liệu cạnh hôn nhân; {@code includeFormer} để lấy cả quan hệ đã kết thúc. */
    @SchemaMapping(typeName = "Person")
    public List<SpouseLinkGql> spouses(PersonDto person, @Argument Boolean includeFormer) {
        boolean withFormer = includeFormer == null || includeFormer;
        Map<UUID, RelationshipDto> edgeByPerson = new LinkedHashMap<>();
        for (RelationshipDto edge : edgesOf(person)) {
            if (edge.relType() != RelType.SPOUSE) {
                continue;
            }
            if (!withFormer && edge.validTo() != null) {
                continue;
            }
            UUID other = person.id().equals(edge.fromPersonId()) ? edge.toPersonId() : edge.fromPersonId();
            edgeByPerson.putIfAbsent(other, edge);
        }
        List<SpouseLinkGql> links = new ArrayList<>();
        for (PersonDto spouse : toDtos(personQuery.visibleByIds(edgeByPerson.keySet()))) {
            RelationshipDto edge = edgeByPerson.get(spouse.id());
            links.add(new SpouseLinkGql(spouse, edge.spouseOrder(), edge.validFrom(), edge.validTo(),
                    edge.validTo() == null, null));
        }
        return links;
    }

    /**
     * Anh chị em.
     *
     * <p>{@code includeHalf = false} chỉ giữ người <b>chung đủ mọi cha mẹ</b> đã biết - cách duy
     * nhất phân biệt được anh em ruột với anh em cùng cha khác mẹ, chuyện thường gặp trong dòng họ
     * có đa thê.</p>
     */
    @SchemaMapping(typeName = "Person")
    public List<PersonDto> siblings(PersonDto person, @Argument Boolean includeHalf) {
        boolean withHalf = includeHalf == null || includeHalf;
        List<PersonDto> parents = parents(person, "ANY");
        Map<UUID, Integer> sharedParents = new LinkedHashMap<>();
        for (PersonDto parent : parents) {
            for (RelationshipDto edge : edgesOf(parent)) {
                if (edge.relType().isParentEdge() && parent.id().equals(edge.fromPersonId())
                        && !person.id().equals(edge.toPersonId())) {
                    sharedParents.merge(edge.toPersonId(), 1, Integer::sum);
                }
            }
        }
        List<UUID> ids = sharedParents.entrySet().stream()
                .filter(entry -> withHalf || entry.getValue() == parents.size())
                .map(Map.Entry::getKey)
                .toList();
        return toDtos(personQuery.visibleByIds(ids));
    }

    /** Cạnh quan hệ trực tiếp một bậc; chỉ gồm cạnh mà đầu kia cũng hiển thị được với người gọi. */
    @SchemaMapping(typeName = "Person")
    public List<RelationshipDto> relationships(PersonDto person) {
        return edgesOf(person);
    }

    /** Quan hệ thừa kế hương hoả (đích tôn / thừa tự / kế tự) nếu có. */
    @SchemaMapping(typeName = "Person")
    public List<RelationshipDto> heirs(PersonDto person) {
        return edgesOf(person).stream().filter(edge -> edge.relType() == RelType.HEIR).toList();
    }

    @SchemaMapping(typeName = "Person")
    public List<PersonDto> ancestors(PersonDto person, @Argument Integer depth) {
        return toDtos(personQuery.ancestorsOf(person.id(), depth == null ? 1 : depth));
    }

    @SchemaMapping(typeName = "Person")
    public List<PersonDto> descendants(PersonDto person, @Argument Integer depth,
                                       @Argument Integer limit) {
        return toDtos(personQuery.descendantsOf(person.id(), depth == null ? 1 : depth,
                limit == null ? 200 : limit));
    }

    // -----------------------------------------------------------------------------------
    // Trường của type Relationship
    // -----------------------------------------------------------------------------------

    @SchemaMapping(typeName = "Relationship", field = "from")
    public PersonDto relationshipFrom(RelationshipDto relationship) {
        return person(relationship.fromPersonId());
    }

    @SchemaMapping(typeName = "Relationship", field = "to")
    public PersonDto relationshipTo(RelationshipDto relationship) {
        return person(relationship.toPersonId());
    }

    // -----------------------------------------------------------------------------------
    // Nội bộ
    // -----------------------------------------------------------------------------------

    /**
     * Cạnh quan hệ của một hồ sơ.
     *
     * <p>{@code PersonDto} nhận từ REST đã kèm sẵn quan hệ; hồ sơ nạp theo lô thì chưa, nên phải
     * hỏi lại. Hỏi lại vẫn đi qua use case đã lọc - không có đường tắt nào bỏ qua phân tầng riêng
     * tư, kể cả để tiết kiệm một truy vấn.</p>
     */
    private List<RelationshipDto> edgesOf(PersonDto person) {
        if (person.relationships() != null) {
            return person.relationships();
        }
        return personQuery.relationshipsOf(person.id()).stream()
                .map(GenealogyDtoMapper::toDto).toList();
    }

    private boolean isParentKind(RelType relType, String kind) {
        if (!relType.isParentEdge()) {
            return false;
        }
        if (kind == null || "ANY".equals(kind)) {
            return true;
        }
        return "ADOPTIVE".equals(kind) ? relType == RelType.PARENT_ADOPT : relType == RelType.PARENT_BIO;
    }

    private List<PersonDto> toDtos(Collection<PersonView> views) {
        return views.stream().map(GenealogyDtoMapper::toDto).toList();
    }
}
