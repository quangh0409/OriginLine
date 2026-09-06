package vn.giapha.genealogy.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import vn.giapha.genealogy.application.GenealogyTestDoubles.FakeCallerIdentity;
import vn.giapha.genealogy.application.GenealogyTestDoubles.FakePersonSearchPort;
import vn.giapha.genealogy.application.GenealogyTestDoubles.FakeTabooNamePort;
import vn.giapha.genealogy.application.GenealogyTestDoubles.FakeTreeGraph;
import vn.giapha.genealogy.application.GenealogyTestDoubles.InMemoryBranchRepository;
import vn.giapha.genealogy.application.GenealogyTestDoubles.InMemoryPersonRepository;
import vn.giapha.genealogy.application.GenealogyTestDoubles.InMemoryRelationshipRepository;
import vn.giapha.genealogy.application.GenealogyTestDoubles.InMemoryTreeCache;
import vn.giapha.genealogy.application.GenealogyTestDoubles.RecordingAudit;
import vn.giapha.genealogy.domain.Branch;
import vn.giapha.genealogy.domain.BranchKind;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.shared.domain.DomainEvent;
import vn.giapha.shared.vo.BranchPath;

/**
 * Bộ dây nối sẵn toàn bộ use case của {@code genealogy} với các cổng giả — dựng trong vài
 * micro-giây, không Spring context, không Docker.
 *
 * <p>Nối thật các use case với nhau (ví dụ {@code AddPersonService} gọi
 * {@code LinkRelationshipService}) thay vì mock lẫn nhau, vì phần lớn bất biến cần kiểm nằm đúng ở
 * chỗ giao nhau đó: cạnh đồ thị và bản chiếu phải cùng được ghi, cache phải bị dọn, nhật ký phải
 * có dòng.
 */
public final class GenealogyServiceFixture {

    public static final BranchPath P_GOC = BranchPath.of("goc");
    public static final BranchPath P_CHI_GIAP = BranchPath.of("goc.chi_giap");
    public static final BranchPath P_CHI_AT = BranchPath.of("goc.chi_at");

    public final InMemoryRelationshipRepository relationships = new InMemoryRelationshipRepository();
    public final InMemoryPersonRepository persons =
            new InMemoryPersonRepository().lienKet(relationships);
    public final InMemoryBranchRepository branches = new InMemoryBranchRepository();
    public final FakeTreeGraph graph = new FakeTreeGraph();
    public final RecordingAudit audit = new RecordingAudit();
    public final InMemoryTreeCache treeCache = new InMemoryTreeCache();
    public final FakeTabooNamePort tabooPort = new FakeTabooNamePort();
    public final FakePersonSearchPort searchPort = new FakePersonSearchPort();
    public final FakeCallerIdentity identity = new FakeCallerIdentity();
    public final List<DomainEvent> suKien = new ArrayList<>();

    public final TabooNameChecker tabooNames = new TabooNameChecker(tabooPort);
    public final PrivacyTierService privacy = new PrivacyTierService(identity);
    public final GenealogyAccessGuard guard = new GenealogyAccessGuard();
    public final DomainEventPublisher events = new DomainEventPublisher(event -> {
        if (event instanceof DomainEvent domainEvent) {
            suKien.add(domainEvent);
        }
    });

    public final LinkRelationshipService links = new LinkRelationshipService(
            persons, relationships, graph, branches, audit, treeCache, privacy, guard, events);
    public final AddPersonService addPerson = new AddPersonService(
            persons, branches, graph, audit, treeCache, tabooNames, links, privacy, guard, events);
    public final SoftDeletePersonService softDelete = new SoftDeletePersonService(
            persons, branches, graph, audit, treeCache, privacy, guard, events);
    public final RestorePersonService restore = new RestorePersonService(
            persons, branches, graph, audit, treeCache, privacy, guard, events);
    public final MoveBranchService moveBranch = new MoveBranchService(
            persons, branches, audit, treeCache, privacy, guard, events);
    public final UpdatePersonService updatePerson = new UpdatePersonService(
            persons, branches, graph, audit, treeCache, tabooNames, privacy, guard, events,
            softDelete, restore, moveBranch);
    public final AnonymizePersonService anonymize = new AnonymizePersonService(
            persons, branches, audit, treeCache, privacy, events);
    public final PersonQueryService query = new PersonQueryService(
            persons, relationships, branches, graph, privacy);
    public final PersonSearchService search = new PersonSearchService(
            searchPort, persons, branches, privacy);
    public final TreeProjectionService tree = new TreeProjectionService(
            graph, persons, relationships, branches, treeCache, privacy);

    public final Branch goc = branches.seed(Branch.create(UUID.randomUUID(), "Dòng họ Nguyễn",
            P_GOC, null, BranchKind.DONG_HO));
    public final Branch chiGiap = branches.seed(Branch.create(UUID.randomUUID(), "Chi Giáp",
            P_CHI_GIAP, goc.id(), BranchKind.CHI));
    public final Branch chiAt = branches.seed(Branch.create(UUID.randomUUID(), "Chi Ất",
            P_CHI_AT, goc.id(), BranchKind.CHI));

    /** Nạp một nhân khẩu vào kho + đồ thị, gắn sẵn chi. */
    public Person seed(Person person, Branch chi) {
        if (chi != null) {
            person.moveToBranch(chi.id());
        }
        graph.seedNode(person.rawId());
        return persons.seed(person);
    }

    /** Nạp quan hệ cha/mẹ → con vào cả đồ thị lẫn bản chiếu, đúng như một transaction thật. */
    public void seedParent(Person cha, Person con, boolean nuoi) {
        graph.seedParent(cha.rawId(), con.rawId());
        relationships.seed(vn.giapha.genealogy.domain.Relationship.parent(
                UUID.randomUUID(), cha.rawId(), con.rawId(), nuoi));
    }

    /** Đăng nhập với vai Quản trị hệ thống toàn cục. */
    public void dangNhapAdmin() {
        GenealogyTestDoubles.dangNhap("sub-admin", "ADMIN");
    }

    public void dangNhapHoiDong() {
        GenealogyTestDoubles.dangNhap("sub-council", "COUNCIL");
    }

    /** Trưởng chi chỉ có phạm vi trên {@code path}. */
    public void dangNhapTruongChi(BranchPath path) {
        identity.quanTri(path).chiNha(path);
        GenealogyTestDoubles.dangNhap("sub-branch-head", "BRANCH_HEAD");
    }

    /** Thành viên thường, có thể gắn với một nhân khẩu để đóng vai chính chủ. */
    public void dangNhapThanhVien(UUID personId, BranchPath chiNha) {
        identity.la(personId).chiNha(chiNha);
        GenealogyTestDoubles.dangNhap("sub-member", "MEMBER");
    }

    public void dangXuat() {
        GenealogyTestDoubles.dangXuat();
    }
}
