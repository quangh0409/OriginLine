package vn.giapha.genealogy.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.giapha.genealogy.domain.Branch;
import vn.giapha.genealogy.domain.GraphNodeRef;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.LcaResult;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.genealogy.domain.Relationship;
import vn.giapha.genealogy.domain.TabooConflict;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.CallerIdentityPort;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.genealogy.domain.port.PersonSearchPort;
import vn.giapha.genealogy.domain.port.RelationshipRepository;
import vn.giapha.genealogy.domain.port.TabooNamePort;
import vn.giapha.genealogy.domain.port.TreeCachePort;
import vn.giapha.genealogy.domain.port.TreeGraphPort;
import vn.giapha.shared.vo.BranchPath;
import vn.giapha.shared.vo.PersonId;

/**
 * Bộ giả lập các cổng của context {@code genealogy} cho test đơn vị — <b>không Docker, không
 * Spring context</b>.
 *
 * <p>Viết tay thay vì dùng mock sinh tự động vì hai lý do: các bất biến cần kiểm (đồ thị và bản
 * chiếu cùng được ghi, cache bị dọn sau mutation, kho nhân khẩu không có đường xoá cứng) là bất
 * biến <i>giữa nhiều cổng</i> chứ không phải một lời gọi lẻ; và kho nhân khẩu ở đây <b>mô phỏng
 * đúng hành vi tăng {@code @Version} lúc flush của Hibernate</b> — đó chính là chỗ từng sinh ra lỗi
 * ETag trễ một nhịp.
 */
public final class GenealogyTestDoubles {

    private GenealogyTestDoubles() {
    }

    // -------------------------------------------------------------------------------------
    // Danh tính người gọi
    // -------------------------------------------------------------------------------------

    /** Đặt người gọi hiện tại vào {@code SecurityContext}; {@code null} nghĩa là Khách vãng lai. */
    public static void dangNhap(String subject, String... roles) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(subject, "n/a", authorities));
    }

    /** Khách vãng lai: không token, không nhân khẩu, không phạm vi. */
    public static void dangXuat() {
        SecurityContextHolder.clearContext();
    }

    /** Cổng danh tính có thể lập trình được — thay cho việc đọc bảng {@code app_user}. */
    public static final class FakeCallerIdentity implements CallerIdentityPort {

        private UUID personId;
        private List<BranchPath> managed = List.of();
        private BranchPath home;

        public FakeCallerIdentity la(UUID personId) {
            this.personId = personId;
            return this;
        }

        public FakeCallerIdentity quanTri(BranchPath... paths) {
            this.managed = List.of(paths);
            return this;
        }

        public FakeCallerIdentity chiNha(BranchPath path) {
            this.home = path;
            return this;
        }

        @Override
        public Optional<UUID> currentPersonId() {
            return Optional.ofNullable(personId);
        }

        @Override
        public List<BranchPath> managedBranches() {
            return managed;
        }

        @Override
        public Optional<BranchPath> homeBranch() {
            return Optional.ofNullable(home);
        }
    }

    // -------------------------------------------------------------------------------------
    // Kho nhân khẩu
    // -------------------------------------------------------------------------------------

    /**
     * Kho nhân khẩu trong bộ nhớ.
     *
     * <p><b>Mô phỏng có chủ ý hành vi của Hibernate:</b> {@code save} trả về một aggregate
     * <i>mới dựng lại từ trạng thái đã ghi</i> với {@code version} đã tăng, đúng như
     * {@code PersonRepositoryAdapter.save()} làm sau khi {@code saveAndFlush}. Ai bỏ qua giá trị
     * trả về sẽ tiếp tục cầm phiên bản cũ — và test sẽ nhìn thấy điều đó.
     */
    public static final class InMemoryPersonRepository implements PersonRepository {

        private final Map<UUID, Person> store = new LinkedHashMap<>();
        private final Map<UUID, Long> versions = new HashMap<>();
        private InMemoryRelationshipRepository relationships;
        public int soLanSave;

        public InMemoryPersonRepository lienKet(InMemoryRelationshipRepository relationships) {
            this.relationships = relationships;
            return this;
        }

        /** Nạp sẵn một nhân khẩu vào kho, giữ nguyên phiên bản hiện có của aggregate. */
        public Person seed(Person person) {
            versions.put(person.rawId(), person.version());
            store.put(person.rawId(), copy(person, person.version()));
            return store.get(person.rawId());
        }

        @Override
        public Optional<Person> byId(PersonId id) {
            Person stored = store.get(id.value());
            return stored == null ? Optional.empty()
                    : Optional.of(copy(stored, versions.getOrDefault(id.value(), 0L)));
        }

        @Override
        public List<Person> byIds(Collection<UUID> ids) {
            List<Person> result = new ArrayList<>();
            for (UUID id : ids) {
                Person stored = store.get(id);
                if (stored != null) {
                    result.add(copy(stored, versions.getOrDefault(id, 0L)));
                }
            }
            return result;
        }

        @Override
        public Person save(Person person) {
            soLanSave++;
            boolean moi = !store.containsKey(person.rawId());
            long version = moi ? 0L : versions.getOrDefault(person.rawId(), 0L) + 1;
            versions.put(person.rawId(), version);
            Person ghi = copy(person, version);
            store.put(person.rawId(), ghi);
            return copy(ghi, version);
        }

        @Override
        public boolean exists(PersonId id) {
            return store.containsKey(id.value());
        }

        @Override
        public Optional<Integer> generationOf(PersonId id) {
            Person stored = store.get(id.value());
            return stored == null ? Optional.empty() : Optional.ofNullable(stored.generation());
        }

        @Override
        public Map<UUID, Integer> countChildren(Collection<UUID> personIds) {
            Map<UUID, Integer> counts = new LinkedHashMap<>();
            if (relationships == null) {
                return counts;
            }
            for (UUID parent : personIds) {
                int soCon = 0;
                for (Relationship edge : relationships.all()) {
                    if (edge.isDeleted() || !edge.relType().isParentEdge()) {
                        continue;
                    }
                    if (!edge.fromPersonId().equals(parent)) {
                        continue;
                    }
                    Person con = store.get(edge.toPersonId());
                    if (con != null && !con.isDeleted()) {
                        soCon++;
                    }
                }
                if (soCon > 0) {
                    counts.put(parent, soCon);
                }
            }
            return counts;
        }

        public long versionOf(UUID personId) {
            return versions.getOrDefault(personId, -1L);
        }

        /** Bản sao aggregate, đúng cách adapter thật dựng lại từ dòng CSDL vừa ghi. */
        private static Person copy(Person person, long version) {
            List<PersonName> names = new ArrayList<>();
            for (PersonName name : person.names()) {
                names.add(name.id() == null ? name.withId(UUID.randomUUID()) : name);
            }
            return Person.rehydrate(PersonId.of(person.rawId()))
                    .gender(person.gender())
                    .generation(person.generation())
                    .birthOrder(person.birthOrder())
                    .birth(person.birth())
                    .death(person.death())
                    .alive(person.isAlive())
                    .nativePlace(person.nativePlace())
                    .currentPlaceProvince(person.currentPlaceProvince())
                    .currentPlaceFull(person.currentPlaceFull())
                    .occupation(person.occupation())
                    .biography(person.biography())
                    .avatarKey(person.avatarKey())
                    .contact(person.contact())
                    .names(names)
                    .primaryBranchId(person.primaryBranchId())
                    .lineageStatus(person.lineageStatus())
                    .privacyLevel(person.privacyLevel())
                    .attributes(person.attributes())
                    .deleted(person.isDeleted(), person.deletedAt())
                    .anonymized(person.isAnonymized(), person.anonymizedAt())
                    .updatedAt(person.updatedAt())
                    .version(version)
                    .build();
        }
    }

    // -------------------------------------------------------------------------------------
    // Kho quan hệ (bản chiếu của cạnh AGE)
    // -------------------------------------------------------------------------------------

    public static final class InMemoryRelationshipRepository implements RelationshipRepository {

        private final List<Relationship> edges = new ArrayList<>();

        public Relationship seed(Relationship edge) {
            edges.add(edge);
            return edge;
        }

        public List<Relationship> all() {
            return List.copyOf(edges);
        }

        @Override
        public Relationship save(Relationship relationship) {
            edges.removeIf(edge -> edge.id().equals(relationship.id()));
            edges.add(relationship);
            return relationship;
        }

        @Override
        public List<Relationship> byPerson(UUID personId) {
            return edges.stream()
                    .filter(edge -> !edge.isDeleted())
                    .filter(edge -> edge.otherEnd(personId) != null)
                    .toList();
        }

        @Override
        public List<Relationship> betweenAll(Collection<UUID> personIds) {
            Set<UUID> ids = new LinkedHashSet<>(personIds);
            return edges.stream()
                    .filter(edge -> !edge.isDeleted())
                    .filter(edge -> ids.contains(edge.fromPersonId()) && ids.contains(edge.toPersonId()))
                    .toList();
        }

        @Override
        public List<Relationship> touchingAny(Collection<UUID> personIds) {
            Set<UUID> ids = new LinkedHashSet<>(personIds);
            return edges.stream()
                    .filter(edge -> !edge.isDeleted())
                    .filter(edge -> ids.contains(edge.fromPersonId()) || ids.contains(edge.toPersonId()))
                    .toList();
        }

        @Override
        public boolean existsParentEdge(UUID parentId, UUID childId) {
            return edges.stream()
                    .filter(edge -> !edge.isDeleted())
                    .anyMatch(edge -> edge.relType().isParentEdge()
                            && edge.fromPersonId().equals(parentId)
                            && edge.toPersonId().equals(childId));
        }
    }

    // -------------------------------------------------------------------------------------
    // Kho chi/ngành
    // -------------------------------------------------------------------------------------

    public static final class InMemoryBranchRepository implements BranchRepository {

        private final Map<UUID, Branch> store = new LinkedHashMap<>();

        public Branch seed(Branch branch) {
            store.put(branch.id(), branch);
            return branch;
        }

        @Override
        public Optional<Branch> byId(UUID id) {
            return Optional.ofNullable(id == null ? null : store.get(id));
        }

        @Override
        public List<Branch> byIds(Collection<UUID> ids) {
            List<Branch> result = new ArrayList<>();
            for (UUID id : ids) {
                Branch branch = store.get(id);
                if (branch != null) {
                    result.add(branch);
                }
            }
            return result;
        }

        @Override
        public Optional<Branch> byPath(BranchPath path) {
            return store.values().stream().filter(b -> b.path().equals(path)).findFirst();
        }

        @Override
        public List<Branch> subtreeOf(BranchPath path) {
            return store.values().stream().filter(b -> path.isAncestorOf(b.path())).toList();
        }

        @Override
        public List<Branch> all() {
            return List.copyOf(store.values());
        }

        @Override
        public Map<UUID, BranchPath> pathsOf(Collection<UUID> branchIds) {
            Map<UUID, BranchPath> result = new LinkedHashMap<>();
            for (UUID id : branchIds) {
                Branch branch = store.get(id);
                if (branch != null) {
                    result.put(id, branch.path());
                }
            }
            return result;
        }
    }

    // -------------------------------------------------------------------------------------
    // Đồ thị phả hệ
    // -------------------------------------------------------------------------------------

    /** Đồ thị trong bộ nhớ, đồng thời ghi lại mọi lệnh ghi để kiểm bất biến "đồ thị + bản chiếu". */
    public static final class FakeTreeGraph implements TreeGraphPort {

        private final Map<UUID, List<UUID>> conCua = new LinkedHashMap<>();
        private final Map<UUID, List<UUID>> chaMeCua = new LinkedHashMap<>();
        private final Set<UUID> nodes = new LinkedHashSet<>();

        public final List<String> lenhGhi = new ArrayList<>();
        public final List<UUID> nodeDaTao = new ArrayList<>();
        public final List<String> nodeDaDongBo = new ArrayList<>();

        /** Nạp sẵn một cạnh cha → con vào đồ thị (không tính là lệnh ghi của use case). */
        public FakeTreeGraph seedParent(UUID parent, UUID child) {
            nodes.add(parent);
            nodes.add(child);
            conCua.computeIfAbsent(parent, k -> new ArrayList<>()).add(child);
            chaMeCua.computeIfAbsent(child, k -> new ArrayList<>()).add(parent);
            return this;
        }

        public FakeTreeGraph seedNode(UUID personId) {
            nodes.add(personId);
            return this;
        }

        @Override
        public void createPersonNode(UUID personId, String gender, Integer generation) {
            nodes.add(personId);
            nodeDaTao.add(personId);
            lenhGhi.add("createPersonNode:" + personId);
        }

        @Override
        public void syncPersonNode(UUID personId, String gender, Integer generation, boolean deleted) {
            nodeDaDongBo.add(personId + ":deleted=" + deleted);
            lenhGhi.add("syncPersonNode:" + personId + ":deleted=" + deleted);
        }

        @Override
        public void linkParent(UUID parentId, UUID childId, RelType relType) {
            seedParent(parentId, childId);
            lenhGhi.add("linkParent:" + relType + ":" + parentId + "->" + childId);
        }

        @Override
        public void linkSpouse(UUID fromPersonId, UUID toPersonId, Integer spouseOrder,
                               String validFrom, String validTo) {
            lenhGhi.add("linkSpouse:" + fromPersonId + "->" + toPersonId + ":order=" + spouseOrder);
        }

        @Override
        public void linkHeir(UUID fromPersonId, UUID toPersonId, HeirKind heirKind) {
            lenhGhi.add("linkHeir:" + heirKind + ":" + fromPersonId + "->" + toPersonId);
        }

        @Override
        public void unlink(UUID fromPersonId, UUID toPersonId, RelType relType) {
            lenhGhi.add("unlink:" + relType + ":" + fromPersonId + "->" + toPersonId);
        }

        @Override
        public List<GraphNodeRef> ancestors(UUID personId, int maxDepth) {
            List<GraphNodeRef> result = new ArrayList<>();
            Deque<UUID> hangDoi = new ArrayDeque<>(List.of(personId));
            Set<UUID> daGap = new LinkedHashSet<>(List.of(personId));
            int doSau = 0;
            while (!hangDoi.isEmpty() && doSau < maxDepth) {
                doSau++;
                int soPhanTu = hangDoi.size();
                for (int i = 0; i < soPhanTu; i++) {
                    UUID hienTai = hangDoi.poll();
                    for (UUID cha : chaMeCua.getOrDefault(hienTai, List.of())) {
                        if (daGap.add(cha)) {
                            result.add(new GraphNodeRef(cha, doSau));
                            hangDoi.add(cha);
                        }
                    }
                }
            }
            return result;
        }

        @Override
        public List<GraphNodeRef> descendants(UUID personId, int maxDepth) {
            List<GraphNodeRef> result = new ArrayList<>();
            result.add(new GraphNodeRef(personId, 0));
            Deque<UUID> hangDoi = new ArrayDeque<>(List.of(personId));
            Set<UUID> daGap = new LinkedHashSet<>(List.of(personId));
            int doSau = 0;
            while (!hangDoi.isEmpty() && doSau < maxDepth) {
                doSau++;
                int soPhanTu = hangDoi.size();
                for (int i = 0; i < soPhanTu; i++) {
                    UUID hienTai = hangDoi.poll();
                    for (UUID con : conCua.getOrDefault(hienTai, List.of())) {
                        if (daGap.add(con)) {
                            result.add(new GraphNodeRef(con, doSau));
                            hangDoi.add(con);
                        }
                    }
                }
            }
            return result;
        }

        @Override
        public LcaResult lca(UUID firstPersonId, UUID secondPersonId) {
            return LcaResult.NONE;
        }

        @Override
        public boolean isAncestorOf(UUID candidateAncestorId, UUID personId) {
            if (candidateAncestorId.equals(personId)) {
                return true;
            }
            for (GraphNodeRef ref : ancestors(personId, 50)) {
                if (ref.personId().equals(candidateAncestorId)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean nodeExists(UUID personId) {
            return nodes.contains(personId);
        }
    }

    // -------------------------------------------------------------------------------------
    // Nhật ký, cache, kỵ húy, tìm kiếm
    // -------------------------------------------------------------------------------------

    /** Nhật ký thay đổi ghi vào bộ nhớ, giữ nguyên before/after để kiểm rò rỉ Tầng 3. */
    public static final class RecordingAudit implements AuditPort {

        public record Ban(String entityType, String entityId, String action,
                          Map<String, Object> before, Map<String, Object> after,
                          List<String> changedFields, String note) {
        }

        public final List<Ban> dong = new ArrayList<>();

        @Override
        public void record(String entityType, String entityId, String action,
                           Map<String, Object> before, Map<String, Object> after,
                           List<String> changedFields, String note) {
            dong.add(new Ban(entityType, entityId, action, before, after, changedFields, note));
        }

        public Ban cuoiCung() {
            return dong.isEmpty() ? null : dong.get(dong.size() - 1);
        }

        public List<String> hanhDong() {
            return dong.stream().map(Ban::action).toList();
        }
    }

    public static final class InMemoryTreeCache implements TreeCachePort {

        private final Map<String, String> store = new LinkedHashMap<>();
        public int soLanEvictAll;
        public final List<String> khoaDaGhi = new ArrayList<>();

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(store.get(key));
        }

        @Override
        public void put(String key, String value) {
            khoaDaGhi.add(key);
            store.put(key, value);
        }

        @Override
        public void evictAll() {
            soLanEvictAll++;
            store.clear();
        }

        public Map<String, String> noiDung() {
            return Map.copyOf(store);
        }
    }

    /** Cổng kỵ húy lập trình được: khai báo trước những va chạm mà truy vấn thật sẽ trả về. */
    public static final class FakeTabooNamePort implements TabooNamePort {

        private final Map<String, List<TabooConflict>> vaCham = new LinkedHashMap<>();
        public final List<String> daHoi = new ArrayList<>();
        public final List<Integer> doiThuDaNhan = new ArrayList<>();
        public UUID excludeIdDaNhan;

        public FakeTabooNamePort vaChamVoi(String ten, TabooConflict... conflicts) {
            vaCham.put(ten, List.of(conflicts));
            return this;
        }

        public static TabooConflict cuTo(String tenHuy, int doiThu) {
            return new TabooConflict(UUID.randomUUID(), "Cụ đời " + doiThu, doiThu, tenHuy,
                    NameType.HUY, vn.giapha.genealogy.domain.TabooMatchKind.EXACT, "bậc trên trực hệ");
        }

        @Override
        public List<TabooConflict> findConflicts(String candidateFullName, Integer generationOfNewPerson,
                                                 UUID excludePersonId) {
            daHoi.add(candidateFullName);
            doiThuDaNhan.add(generationOfNewPerson);
            excludeIdDaNhan = excludePersonId;
            return vaCham.getOrDefault(candidateFullName, List.of());
        }
    }

    /** Cổng tìm kiếm lập trình được — trả id thô, chưa qua bộ lọc riêng tư (đúng hợp đồng). */
    public static final class FakePersonSearchPort implements PersonSearchPort {

        private final List<Hit> hits = new ArrayList<>();
        public SearchFilter filterDaNhan;
        public int limitDaNhan;

        public FakePersonSearchPort tra(UUID personId, NameType type, double score) {
            hits.add(new Hit(personId, type, score));
            return this;
        }

        @Override
        public List<Hit> search(String query, SearchFilter filter, int limit) {
            this.filterDaNhan = filter;
            this.limitDaNhan = limit;
            return hits.stream().limit(limit).toList();
        }
    }
}
