package vn.giapha.genealogy.application;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.genealogy.domain.port.TreeCachePort;
import vn.giapha.genealogy.domain.port.TreeGraphPort;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.PersonId;

/**
 * Khôi phục một nhân khẩu đã xoá mềm.
 *
 * <p>Chỉ {@code ADMIN}/{@code COUNCIL}: người khác thậm chí không nhìn thấy bản ghi đã xoá, nên
 * với họ endpoint này không thể có đối tượng để gọi. Phạm vi chi <b>không</b> nới quyền ở đây -
 * một Trưởng chi khôi phục lại người mà Hội đồng vừa cho xoá là lật quyết định của cấp trên.</p>
 */
@Service
public class RestorePersonService {

    private static final Logger log = LoggerFactory.getLogger(RestorePersonService.class);

    private final PersonRepository persons;
    private final BranchRepository branches;
    private final TreeGraphPort graph;
    private final AuditPort audit;
    private final TreeCachePort treeCache;
    private final PrivacyTierService privacy;
    private final GenealogyAccessGuard guard;
    private final DomainEventPublisher events;

    public RestorePersonService(PersonRepository persons, BranchRepository branches, TreeGraphPort graph,
                                AuditPort audit, TreeCachePort treeCache, PrivacyTierService privacy,
                                GenealogyAccessGuard guard, DomainEventPublisher events) {
        this.persons = persons;
        this.branches = branches;
        this.graph = graph;
        this.audit = audit;
        this.treeCache = treeCache;
        this.privacy = privacy;
        this.guard = guard;
        this.events = events;
    }

    @Transactional
    public PersonView restore(UUID personId, String reason) {
        CallerContext caller = privacy.caller();
        guard.requireClanWide(caller, "khoi phuc ban ghi da xoa mem");
        Person person = persons.byId(PersonId.of(personId)).orElseThrow(() -> new NotFoundException(
                GenealogyProblemCodes.NOT_FOUND, "Khong tim thay nhan khau voi dinh danh " + personId));

        Map<String, Object> before = person.auditSnapshot();
        person.restore();
        // Phai nhan lai ket qua: PersonRepositoryAdapter.save() flush roi map lai entity
        // da ghi, nen @Version o day moi la phien ban that. Vut ket qua di thi ETag tra ve
        // tre mot nhip va lan PATCH ke tiep an 409 gia du khong ai sua ban ghi.
        //
        // Nhung ban doc lai ay duoc rehydrate tu dong CSDL nen hang doi domain event CUA NO RONG.
        // Giu lai tham chieu toi aggregate vua sua de con phat su kien - publish tu `person` sau
        // khi gan de la mat trang moi PersonUpdatedEvent, va consumer nhac gio khong bao gio
        // biet mot cu vua duoc bao mat.
        Person daSua = person;
        person = persons.save(daSua);
        graph.syncPersonNode(person.rawId(), person.gender() == null ? null : person.gender().name(),
                person.generation(), false);

        audit.record("Person", personId.toString(), AuditPort.Action.RESTORE, before,
                person.auditSnapshot(), List.of("isDeleted"), reason);
        events.publishAndClear(daSua);
        treeCache.evictAll();
        log.info("Da khoi phuc nhan khau {}", personId);

        BranchDirectory dir = BranchDirectory.load(branches, Arrays.asList(person.primaryBranchId()));
        return privacy.toView(person, caller, dir);
    }
}
