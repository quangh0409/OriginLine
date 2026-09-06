package vn.giapha.genealogy.application;

import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.application.command.AddPersonCommand;
import vn.giapha.genealogy.application.command.LinkRelationshipCommand;
import vn.giapha.genealogy.application.command.RelationshipLinkCommand;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.FieldChange;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PrivacyLevel;
import vn.giapha.genealogy.domain.ProfileEdit;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.genealogy.domain.port.TreeCachePort;
import vn.giapha.genealogy.domain.port.TreeGraphPort;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * Thêm một nhân khẩu vào gia phả.
 *
 * <h2>Trình tự, và vì sao đúng trình tự đó</h2>
 * <ol>
 *   <li><b>Suy ra vị trí trong cây</b> từ các liên kết ban đầu: đời thứ, chi/ngành kế thừa, thứ tự
 *       sinh. Phải làm trước vì phạm vi kiểm quyền và phạm vi quét kỵ húy đều phụ thuộc vào nó.</li>
 *   <li><b>Kiểm quyền</b> theo chi đích - vai trò cộng phạm vi {@code ltree}.</li>
 *   <li><b>Kiểm kỵ húy</b> trước mọi lệnh ghi. Có va chạm mà chưa xác nhận thì <b>không bản ghi
 *       nào được tạo</b>; client nhận danh sách va chạm rồi gọi lại kèm cờ xác nhận.</li>
 *   <li><b>Ghi</b>: bảng {@code person}, đỉnh trong đồ thị, rồi từng cạnh quan hệ.</li>
 *   <li><b>Nhật ký + sự kiện + dọn cache cây.</b></li>
 * </ol>
 *
 * <p>Toàn bộ nằm trong <b>một</b> transaction: đỉnh AGE, cạnh AGE và các dòng {@code relationship}
 * cùng sống hoặc cùng biến mất. Một nhân khẩu có đỉnh mà không có dòng, hay ngược lại, là loại hỏng
 * dữ liệu không có gì báo và cũng không có cách nào tự sửa.</p>
 */
@Service
public class AddPersonService {

    private static final Logger log = LoggerFactory.getLogger(AddPersonService.class);

    /** Dưới tuổi này thì mức riêng tư bị ép về {@code RESTRICTED} bất kể client gửi gì. */
    private static final int MINOR_AGE = 18;

    private final PersonRepository persons;
    private final BranchRepository branches;
    private final TreeGraphPort graph;
    private final AuditPort audit;
    private final TreeCachePort treeCache;
    private final TabooNameChecker tabooNames;
    private final LinkRelationshipService links;
    private final PrivacyTierService privacy;
    private final GenealogyAccessGuard guard;
    private final DomainEventPublisher events;

    public AddPersonService(PersonRepository persons, BranchRepository branches, TreeGraphPort graph,
                            AuditPort audit, TreeCachePort treeCache, TabooNameChecker tabooNames,
                            LinkRelationshipService links, PrivacyTierService privacy,
                            GenealogyAccessGuard guard, DomainEventPublisher events) {
        this.persons = persons;
        this.branches = branches;
        this.graph = graph;
        this.audit = audit;
        this.treeCache = treeCache;
        this.tabooNames = tabooNames;
        this.links = links;
        this.privacy = privacy;
        this.guard = guard;
        this.events = events;
    }

    @Transactional
    public PersonView add(AddPersonCommand cmd) {
        if (cmd.names().isEmpty()) {
            throw new DomainException(GenealogyProblemCodes.VALIDATION_FAILED,
                    "Nhan khau phai co it nhat mot lop ten");
        }
        CallerContext caller = privacy.caller();
        Placement placement = resolvePlacement(cmd);
        BranchDirectory dir = BranchDirectory.load(branches, Arrays.asList(placement.branchId()));
        guard.requireWriteAccess(caller, dir.pathOf(placement.branchId()));

        String tabooNote = tabooNames.check(cmd.names(), placement.generation(), null,
                cmd.confirmTabooOverride());

        PersonId id = PersonId.newId();
        Person person = Person.create(id, cmd.gender() == null ? Gender.UNKNOWN : cmd.gender(),
                cmd.alive(), cmd.names());
        if (!cmd.alive()) {
            person.markDeceased(cmd.death());
        }
        person.applyProfileEdit(profileEdit(cmd));
        if (placement.branchId() != null) {
            person.moveToBranch(placement.branchId());
        }
        person.placeInGeneration(placement.generation());
        person.placeInBirthOrder(placement.birthOrder());
        person.choosePrivacyLevel(effectivePrivacyLevel(cmd));

        persons.save(person);
        graph.createPersonNode(id.value(), person.gender() == null ? null : person.gender().name(),
                person.generation());

        for (RelationshipLinkCommand link : cmd.links()) {
            links.attach(toLinkCommand(id.value(), link));
        }

        audit.record("Person", id.toString(), AuditPort.Action.CREATE, null, person.auditSnapshot(),
                List.of(), joinNotes(cmd.note(), tabooNote));
        events.publishAndClear(person);
        // Cây đang cache không có người vừa thêm; xoá thô cả vùng vì xác định đúng những gốc nào
        // chứa người này lại cần chính phép duyệt mà cache sinh ra để tránh.
        treeCache.evictAll();
        log.info("Da them nhan khau {} (doi thu {}), {} lien ket ban dau",
                id, person.generation(), cmd.links().size());

        return privacy.toView(person, caller, dir);
    }

    /**
     * Suy ra đời thứ, chi/ngành và thứ tự sinh từ các liên kết ban đầu.
     *
     * <p>Ưu tiên cha/mẹ, rồi tới con, rồi tới vợ/chồng - đúng thứ tự độ tin cậy của thông tin:
     * đời thứ của một người là đời của cha cộng một, còn suy từ vợ/chồng chỉ là ước lượng theo tập
     * quán cưới cùng đời.</p>
     */
    private Placement resolvePlacement(AddPersonCommand cmd) {
        Integer generation = null;
        UUID inheritedBranch = null;
        Integer birthOrder = null;

        Optional<RelationshipLinkCommand> parentLink = cmd.links().stream()
                .filter(RelationshipLinkCommand::otherIsParentOfNewPerson).findFirst();
        if (parentLink.isPresent()) {
            Person parent = load(parentLink.get().otherPersonId());
            generation = parent.generation() == null ? null : parent.generation() + 1;
            inheritedBranch = parent.primaryBranchId();
            Integer existingChildren = persons.countChildren(List.of(parent.rawId()))
                    .getOrDefault(parent.rawId(), 0);
            birthOrder = existingChildren + 1;
        } else {
            Optional<RelationshipLinkCommand> childLink = cmd.links().stream()
                    .filter(RelationshipLinkCommand::newPersonIsParentOfOther).findFirst();
            if (childLink.isPresent()) {
                Person child = load(childLink.get().otherPersonId());
                generation = child.generation() == null || child.generation() <= 1
                        ? null : child.generation() - 1;
                inheritedBranch = child.primaryBranchId();
            } else {
                Optional<RelationshipLinkCommand> spouseLink = cmd.links().stream()
                        .filter(l -> l.relType() != null && !l.relType().isParentEdge()).findFirst();
                if (spouseLink.isPresent()) {
                    Person spouse = load(spouseLink.get().otherPersonId());
                    generation = spouse.generation();
                    inheritedBranch = spouse.primaryBranchId();
                }
            }
        }
        UUID branchId = cmd.primaryBranchId() != null ? cmd.primaryBranchId() : inheritedBranch;
        return new Placement(generation, branchId, birthOrder);
    }

    private LinkRelationshipCommand toLinkCommand(UUID newPersonId, RelationshipLinkCommand link) {
        UUID from = link.otherIsSource() ? link.otherPersonId() : newPersonId;
        UUID to = link.otherIsSource() ? newPersonId : link.otherPersonId();
        return new LinkRelationshipCommand(from, to, link.relType(), link.heirKind(),
                link.spouseOrder(), link.validFrom(), link.validTo(), link.note());
    }

    private ProfileEdit profileEdit(AddPersonCommand cmd) {
        return ProfileEdit.builder()
                .birth(FieldChange.setIfNotNull(cmd.birth()))
                .nativePlace(FieldChange.setIfNotNull(cmd.nativePlace()))
                .currentPlaceProvince(FieldChange.setIfNotNull(cmd.currentPlaceProvince()))
                .currentPlaceFull(FieldChange.setIfNotNull(cmd.currentPlaceFull()))
                .occupation(FieldChange.setIfNotNull(cmd.occupation()))
                .biography(FieldChange.setIfNotNull(cmd.biography()))
                .contact(FieldChange.setIfNotNull(cmd.contact()))
                .attributes(FieldChange.<Map<String, Object>>setIfNotNull(cmd.attributes()))
                .build();
    }

    /**
     * Trẻ vị thành niên bị ép {@code RESTRICTED} bất kể client gửi gì.
     *
     * <p>Một đứa trẻ không tự quyết định được việc công khai dữ liệu của mình, và người nhập liệu
     * cũng không quyết thay được - đây là ràng buộc của Nghị định 13/2023 chứ không phải một tuỳ
     * chọn giao diện.</p>
     */
    private PrivacyLevel effectivePrivacyLevel(AddPersonCommand cmd) {
        if (cmd.alive() && cmd.birth() != null) {
            Integer year = cmd.birth().year().orElse(null);
            if (year != null && Year.now().getValue() - year < MINOR_AGE) {
                return PrivacyLevel.RESTRICTED;
            }
        }
        return cmd.privacyLevel() == null ? PrivacyLevel.DEFAULT : cmd.privacyLevel();
    }

    private Person load(UUID id) {
        return persons.byId(PersonId.of(id)).orElseThrow(() -> new NotFoundException(
                GenealogyProblemCodes.NOT_FOUND, "Khong tim thay nhan khau voi dinh danh " + id));
    }

    private String joinNotes(String... parts) {
        List<String> present = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                present.add(part);
            }
        }
        return present.isEmpty() ? null : String.join(" | ", present);
    }

    /** Vị trí của nhân khẩu mới trong cây, suy ra chứ không nhận từ client. */
    private record Placement(Integer generation, UUID branchId, Integer birthOrder) {
    }
}
