package vn.giapha.genealogy.application;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.application.command.MoveBranchCommand;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.Branch;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.genealogy.domain.port.TreeCachePort;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.PersonId;

/**
 * Chuyển một nhân khẩu sang chi/ngành khác.
 *
 * <p>Chi/ngành không chỉ là nhãn hiển thị: nó là <b>phạm vi phân quyền hạng nhất</b>
 * ({@code ltree}). Đổi chi của một người là đổi luôn danh sách người được sửa hồ sơ đó và người
 * được xem dữ liệu Tầng 2 của họ. Vì vậy người gọi phải có quyền ở <b>cả chi cũ lẫn chi mới</b> -
 * nếu chỉ kiểm chi đích thì một Trưởng chi có thể kéo người của chi khác về chi mình rồi tự cấp
 * quyền cho chính mình.</p>
 *
 * <p>Việc chuyển chi <b>không</b> đụng tới cạnh quan hệ: huyết thống không đổi khi cách phân
 * nhánh trên giấy tờ được sửa lại.</p>
 */
@Service
public class MoveBranchService {

    private static final Logger log = LoggerFactory.getLogger(MoveBranchService.class);

    private final PersonRepository persons;
    private final BranchRepository branches;
    private final AuditPort audit;
    private final TreeCachePort treeCache;
    private final PrivacyTierService privacy;
    private final GenealogyAccessGuard guard;
    private final DomainEventPublisher events;

    public MoveBranchService(PersonRepository persons, BranchRepository branches, AuditPort audit,
                             TreeCachePort treeCache, PrivacyTierService privacy,
                             GenealogyAccessGuard guard, DomainEventPublisher events) {
        this.persons = persons;
        this.branches = branches;
        this.audit = audit;
        this.treeCache = treeCache;
        this.privacy = privacy;
        this.guard = guard;
        this.events = events;
    }

    @Transactional
    public PersonView move(MoveBranchCommand cmd) {
        CallerContext caller = privacy.caller();
        Person person = persons.byId(PersonId.of(cmd.personId())).orElseThrow(() -> new NotFoundException(
                GenealogyProblemCodes.NOT_FOUND,
                "Khong tim thay nhan khau voi dinh danh " + cmd.personId()));
        Branch target = branches.byId(cmd.targetBranchId()).orElseThrow(() -> new NotFoundException(
                GenealogyProblemCodes.NOT_FOUND,
                "Khong tim thay chi/nganh voi dinh danh " + cmd.targetBranchId()));

        BranchDirectory dir = BranchDirectory.load(branches,
                Arrays.asList(person.primaryBranchId(), target.id()));
        guard.requireMoveAccess(caller, dir.pathOf(person.primaryBranchId()), target.path());

        Map<String, Object> before = person.auditSnapshot();
        person.moveToBranch(target.id());
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

        audit.record("Person", cmd.personId().toString(), AuditPort.Action.MOVE_BRANCH, before,
                person.auditSnapshot(), List.of("primaryBranchId"), cmd.note());
        events.publishAndClear(daSua);
        // Phạm vi hiển thị của chính người này vừa đổi, nên khung xương cây đang cache có thể chứa
        // họ ở nhánh cũ.
        treeCache.evictAll();
        log.info("Da chuyen nhan khau {} sang chi {}", cmd.personId(), target.path());

        return privacy.toView(person, caller, dir);
    }
}
