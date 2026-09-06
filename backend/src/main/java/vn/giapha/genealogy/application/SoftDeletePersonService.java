package vn.giapha.genealogy.application;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.genealogy.domain.port.TreeCachePort;
import vn.giapha.genealogy.domain.port.TreeGraphPort;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.PersonId;

/**
 * <b>Xoá mềm</b> một nhân khẩu (FR-1.5).
 *
 * <p>Không có và sẽ không bao giờ có đường xoá cứng. Node trong đồ thị AGE và toàn bộ cạnh quan hệ
 * <b>ở lại nguyên vẹn</b>; chỉ có một cờ được bật. Xoá cứng một người là cắt đứt đường nối giữa
 * tổ tiên và toàn bộ hậu duệ của người đó - mất một người thành mất cả một nhánh.</p>
 *
 * <p>Sau khi xoá mềm, nhân khẩu biến mất khỏi phả đồ, tìm kiếm và sự kiện với mọi vai trừ
 * {@code ADMIN}/{@code COUNCIL}. Đỉnh đồ thị vẫn được cập nhật cờ {@code is_deleted} để phép duyệt
 * <b>đi xuyên qua</b> người này mà không nhận họ làm kết quả.</p>
 *
 * <p><b>Đây không phải nghiệp vụ xoá dữ liệu cá nhân theo yêu cầu hợp pháp.</b> Việc đó là ẩn danh
 * hoá - xem {@link AnonymizePersonService}.</p>
 */
@Service
public class SoftDeletePersonService {

    private static final Logger log = LoggerFactory.getLogger(SoftDeletePersonService.class);

    private final PersonRepository persons;
    private final BranchRepository branches;
    private final TreeGraphPort graph;
    private final AuditPort audit;
    private final TreeCachePort treeCache;
    private final PrivacyTierService privacy;
    private final GenealogyAccessGuard guard;
    private final DomainEventPublisher events;

    public SoftDeletePersonService(PersonRepository persons, BranchRepository branches,
                                   TreeGraphPort graph, AuditPort audit, TreeCachePort treeCache,
                                   PrivacyTierService privacy, GenealogyAccessGuard guard,
                                   DomainEventPublisher events) {
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
    public void softDelete(UUID personId, String reason) {
        CallerContext caller = privacy.caller();
        Person person = persons.byId(PersonId.of(personId)).orElseThrow(() -> new NotFoundException(
                GenealogyProblemCodes.NOT_FOUND, "Khong tim thay nhan khau voi dinh danh " + personId));
        BranchDirectory dir = BranchDirectory.load(branches, Arrays.asList(person.primaryBranchId()));
        guard.requireWriteAccess(caller, dir.pathOf(person.primaryBranchId()));

        Map<String, Object> before = person.auditSnapshot();
        try {
            person.softDelete(reason);
        } catch (IllegalStateException ex) {
            throw new GenealogyConflictException(GenealogyProblemCodes.PERSON_ALREADY_DELETED,
                    "Nhan khau da o trang thai xoa mem", ex);
        }
        persons.save(person);
        graph.syncPersonNode(person.rawId(), person.gender() == null ? null : person.gender().name(),
                person.generation(), true);

        audit.record("Person", personId.toString(), AuditPort.Action.SOFT_DELETE, before,
                person.auditSnapshot(), List.of("isDeleted"), reason);
        events.publishAndClear(person);
        treeCache.evictAll();
        log.info("Da xoa mem nhan khau {}", personId);
    }
}
