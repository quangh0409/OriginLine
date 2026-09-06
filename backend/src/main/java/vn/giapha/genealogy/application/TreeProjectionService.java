package vn.giapha.genealogy.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.application.command.TreeQuery;
import vn.giapha.genealogy.application.view.TreeDirection;
import vn.giapha.genealogy.application.view.TreeEdgeView;
import vn.giapha.genealogy.application.view.TreeMetaView;
import vn.giapha.genealogy.application.view.TreeNodeView;
import vn.giapha.genealogy.application.view.TreeProjectionView;
import vn.giapha.genealogy.domain.GraphNodeRef;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.genealogy.domain.Relationship;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.genealogy.domain.port.RelationshipRepository;
import vn.giapha.genealogy.domain.port.TreeCachePort;
import vn.giapha.genealogy.domain.port.TreeGraphPort;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Dựng <b>projection phẳng</b> của một nhánh phả đồ (W7).
 *
 * <h2>Ba nhịp, tách bạch có chủ ý</h2>
 * <ol>
 *   <li><b>Duyệt đồ thị</b> ra khung xương (id + độ sâu) - phần đắt, và là phần <b>được cache</b>
 *       vì nó giống nhau với mọi người gọi.</li>
 *   <li><b>Nạp hồ sơ theo lô</b> bằng {@code byIds} - một truy vấn cho cả trăm node, không phải
 *       một truy vấn mỗi node.</li>
 *   <li><b>Lọc phân tầng riêng tư</b> - luôn chạy <b>tươi</b>, không bao giờ lấy từ cache.</li>
 * </ol>
 *
 * <p>Nhịp 3 không được trộn vào nhịp 1: cache một cây đã lọc theo vai này rồi trả cho vai khác là
 * rò rỉ dữ liệu người còn sống mà không có bất kỳ dấu vết nào trong log.</p>
 *
 * <p><b>Cây có lỗ là kết quả đúng.</b> Cạnh chỉ xuất hiện khi cả hai đầu đều hiển thị được, nên
 * với Khách - người không được thấy bất kỳ ai còn sống - phả đồ có thể đứt đoạn giữa các đời. Giao
 * diện phải render được cây thiếu người, đừng coi cạnh thiếu là lỗi dữ liệu.</p>
 */
@Service
public class TreeProjectionService {

    private static final Logger log = LoggerFactory.getLogger(TreeProjectionService.class);

    private final TreeGraphPort graph;
    private final PersonRepository persons;
    private final RelationshipRepository relationships;
    private final BranchRepository branches;
    private final TreeCachePort treeCache;
    private final PrivacyTierService privacy;

    public TreeProjectionService(TreeGraphPort graph, PersonRepository persons,
                                 RelationshipRepository relationships, BranchRepository branches,
                                 TreeCachePort treeCache, PrivacyTierService privacy) {
        this.graph = graph;
        this.persons = persons;
        this.relationships = relationships;
        this.branches = branches;
        this.treeCache = treeCache;
        this.privacy = privacy;
    }

    @Transactional(readOnly = true)
    public TreeProjectionView project(TreeQuery query) {
        CallerContext caller = privacy.caller();
        if (query.includeDeleted() && !caller.role().isClanWide()) {
            throw new ForbiddenException(GenealogyProblemCodes.FORBIDDEN,
                    "Chi Hoi dong Toc bieu hoac Quan tri he thong duoc xem ban ghi da xoa mem");
        }

        boolean fromCache = false;
        TreeSkeleton skeleton = null;
        String key = cacheKey(query);
        Optional<String> cached = treeCache.get(key);
        if (cached.isPresent()) {
            skeleton = TreeSkeleton.decode(cached.get());
            fromCache = skeleton != null;
        }
        if (skeleton == null) {
            skeleton = traverse(query);
            treeCache.put(key, skeleton.encode());
        }

        Map<UUID, Integer> depths = new LinkedHashMap<>();
        for (GraphNodeRef node : skeleton.nodes()) {
            depths.putIfAbsent(node.personId(), node.depth());
        }

        Map<UUID, Person> people = visiblePeople(depths.keySet(), caller, query.includeDeleted());
        if (!people.containsKey(query.rootId())) {
            // Gốc không hiển thị được: không tiết lộ rằng nó tồn tại.
            throw new NotFoundException(GenealogyProblemCodes.NOT_FOUND,
                    "Khong tim thay nhan khau goc cua pha do");
        }
        Set<UUID> bloodline = new LinkedHashSet<>(people.keySet());

        if (query.includeSpouses()) {
            addSpouses(people, depths, caller, query.includeDeleted());
        }

        List<Relationship> edges = relationships.betweenAll(people.keySet()).stream()
                .filter(edge -> query.includeSpouses() || edge.relType() != RelType.SPOUSE)
                .toList();

        return assemble(query, skeleton, people, depths, bloodline, edges, caller, fromCache);
    }

    /**
     * Khoá cache cố ý <b>không</b> chứa danh tính hay vai người gọi: khung xương giống nhau với
     * mọi vai, và trộn vai vào khoá vừa nhân bản cache lên nhiều lần vừa tạo ảo giác rằng cache
     * này đã an toàn về riêng tư - trong khi thứ bảo vệ riêng tư là bước lọc chạy sau.
     */
    private String cacheKey(TreeQuery query) {
        return "tree:v1:" + query.rootId() + ":" + query.depth() + ":" + query.direction()
                + ":" + query.maxNodes();
    }

    private TreeSkeleton traverse(TreeQuery query) {
        Map<UUID, GraphNodeRef> collected = new LinkedHashMap<>();
        if (query.direction() != TreeDirection.ANCESTORS) {
            for (GraphNodeRef node : graph.descendants(query.rootId(), query.depth())) {
                collected.putIfAbsent(node.personId(), node);
            }
        }
        if (query.direction() != TreeDirection.DESCENDANTS) {
            collected.putIfAbsent(query.rootId(), new GraphNodeRef(query.rootId(), 0));
            for (GraphNodeRef node : graph.ancestors(query.rootId(), query.depth())) {
                // Đời trên luôn mang độ sâu âm, kể cả khi adapter trả về số dương.
                collected.putIfAbsent(node.personId(),
                        new GraphNodeRef(node.personId(), -Math.abs(node.depth())));
            }
        }

        List<GraphNodeRef> ordered = new ArrayList<>(collected.values());
        ordered.sort(Comparator.comparingInt(node -> Math.abs(node.depth())));

        if (ordered.size() <= query.maxNodes()) {
            return new TreeSkeleton(query.rootId(), query.depth(), query.direction(), ordered,
                    false, List.of());
        }
        // Chạm trần: giữ các đời gần gốc nhất và đánh dấu tầng ngoài cùng là nơi cần nút mở rộng.
        List<GraphNodeRef> kept = new ArrayList<>(ordered.subList(0, query.maxNodes()));
        int deepestKept = kept.stream().mapToInt(node -> Math.abs(node.depth())).max().orElse(0);
        List<UUID> boundary = kept.stream()
                .filter(node -> Math.abs(node.depth()) == deepestKept)
                .map(GraphNodeRef::personId)
                .toList();
        log.debug("Pha do tu goc {} bi cat con {}/{} node", query.rootId(), kept.size(), ordered.size());
        return new TreeSkeleton(query.rootId(), query.depth(), query.direction(), kept, true, boundary);
    }

    private Map<UUID, Person> visiblePeople(Set<UUID> ids, CallerContext caller, boolean includeDeleted) {
        Map<UUID, Person> visible = new LinkedHashMap<>();
        for (Person person : persons.byIds(ids)) {
            if (!privacy.canSee(person, caller)) {
                continue;
            }
            if (person.isDeleted() && !includeDeleted) {
                continue;
            }
            visible.put(person.rawId(), person);
        }
        return visible;
    }

    /**
     * Kéo vợ/chồng của các node đã có vào cây.
     *
     * <p>Phép duyệt đi theo cạnh {@code PARENT} nên không bao giờ chạm tới vợ/chồng; thiếu họ thì
     * phả đồ mất hẳn dâu, rể và toàn bộ trường hợp đa thê. Họ nhận cùng độ sâu với người phối
     * ngẫu vì đứng cùng một đời trên canvas.</p>
     */
    private void addSpouses(Map<UUID, Person> people, Map<UUID, Integer> depths, CallerContext caller,
                            boolean includeDeleted) {
        Set<UUID> anchors = new LinkedHashSet<>(people.keySet());
        Map<UUID, Integer> spouseDepth = new LinkedHashMap<>();
        for (Relationship edge : relationships.touchingAny(anchors)) {
            if (edge.relType() != RelType.SPOUSE) {
                continue;
            }
            UUID inside = anchors.contains(edge.fromPersonId()) ? edge.fromPersonId() : edge.toPersonId();
            UUID outside = edge.otherEnd(inside);
            if (outside == null || anchors.contains(outside)) {
                continue;
            }
            spouseDepth.putIfAbsent(outside, depths.getOrDefault(inside, 0));
        }
        if (spouseDepth.isEmpty()) {
            return;
        }
        for (Person spouse : persons.byIds(spouseDepth.keySet())) {
            if (!privacy.canSee(spouse, caller) || (spouse.isDeleted() && !includeDeleted)) {
                continue;
            }
            people.put(spouse.rawId(), spouse);
            depths.put(spouse.rawId(), spouseDepth.get(spouse.rawId()));
        }
    }

    private TreeProjectionView assemble(TreeQuery query, TreeSkeleton skeleton, Map<UUID, Person> people,
                                        Map<UUID, Integer> depths, Set<UUID> bloodline,
                                        List<Relationship> edges, CallerContext caller,
                                        boolean fromCache) {
        BranchDirectory dir = BranchDirectory.load(branches, people.values().stream()
                .map(Person::primaryBranchId).toList());
        Map<UUID, Integer> childCounts = persons.countChildren(people.keySet());

        Map<UUID, List<UUID>> parentsOf = new LinkedHashMap<>();
        Map<UUID, List<UUID>> spousesOf = new LinkedHashMap<>();
        Map<UUID, Integer> childrenPresent = new LinkedHashMap<>();
        Map<UUID, List<Relationship>> touching = new LinkedHashMap<>();
        for (Relationship edge : edges) {
            touching.computeIfAbsent(edge.fromPersonId(), key -> new ArrayList<>()).add(edge);
            touching.computeIfAbsent(edge.toPersonId(), key -> new ArrayList<>()).add(edge);
            if (edge.relType().isParentEdge()) {
                parentsOf.computeIfAbsent(edge.toPersonId(), key -> new ArrayList<>())
                        .add(edge.fromPersonId());
                childrenPresent.merge(edge.fromPersonId(), 1, Integer::sum);
            } else if (edge.relType() == RelType.SPOUSE) {
                spousesOf.computeIfAbsent(edge.fromPersonId(), key -> new ArrayList<>())
                        .add(edge.toPersonId());
                spousesOf.computeIfAbsent(edge.toPersonId(), key -> new ArrayList<>())
                        .add(edge.fromPersonId());
            }
        }

        List<TreeNodeView> nodes = new ArrayList<>();
        for (Person person : people.values()) {
            UUID id = person.rawId();
            int present = childrenPresent.getOrDefault(id, 0);
            Integer total = childCount(caller, childCounts.get(id), present);
            nodes.add(new TreeNodeView(
                    id,
                    privacy.toSummary(person, caller, dir),
                    depths.getOrDefault(id, 0),
                    parentsOf.getOrDefault(id, List.of()),
                    spousesOf.getOrDefault(id, List.of()),
                    total,
                    total != null && total > present,
                    TreeBadgeResolver.badgesOf(person, touching.getOrDefault(id, List.of()), dir,
                            bloodline.contains(id))));
        }
        nodes.sort(Comparator.comparingInt(TreeNodeView::depth)
                .thenComparing(node -> node.person().displayName(),
                        Comparator.nullsLast(Comparator.naturalOrder())));

        List<TreeEdgeView> edgeViews = edges.stream()
                .map(edge -> new TreeEdgeView(edge.id().toString(), edge.fromPersonId(),
                        edge.toPersonId(), edge.relType(), edge.heirKind(), edge.spouseOrder(),
                        edge.validTo()))
                .toList();

        List<UUID> truncatedVisible = skeleton.truncatedNodeIds().stream()
                .filter(people::containsKey)
                .toList();
        TreeMetaView meta = new TreeMetaView(query.depth(), query.direction(), nodes.size(),
                edgeViews.size(), skeleton.truncated(), truncatedVisible, Instant.now(), fromCache);
        return new TreeProjectionView(query.rootId(), nodes, edgeViews, meta);
    }

    /**
     * Số con hiển thị trên nút mở rộng.
     *
     * <p>Với Khách chỉ đếm những người con <b>đã có mặt</b> trong projection: con số thật gồm cả
     * người còn sống, và để lộ nó ra là gián tiếp nói "ông này còn ba người con mà bạn không được
     * thấy" - đúng thứ mà quy định cấm.</p>
     */
    private Integer childCount(CallerContext caller, Integer actual, int present) {
        if (caller.isGuest()) {
            return present;
        }
        return actual == null ? present : actual;
    }
}
