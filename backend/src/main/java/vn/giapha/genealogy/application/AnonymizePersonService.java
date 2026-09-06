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
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.PersonId;

/**
 * <b>Ẩn danh hoá</b> - cách hệ thống thực hiện quyền xoá dữ liệu cá nhân theo Nghị định 13/2023.
 *
 * <h2>Vì sao không phải là xoá</h2>
 * Xoá một nhân khẩu khỏi gia phả làm đứt đường nối giữa tổ tiên và hậu duệ của người đó: một yêu
 * cầu xoá hợp pháp của <i>một</i> cá nhân sẽ phá hỏng dữ liệu của <i>cả dòng họ</i>, gồm cả những
 * người đã khuất mà quyền riêng tư không còn áp dụng. Vì thế nghĩa vụ pháp lý được thực hiện bằng
 * cách <b>xoá sạch dữ liệu Tầng 3 và giữ lại node phả hệ</b>: tên chính, đời thứ, giới tính, các
 * cạnh quan hệ ở lại; liên hệ, địa chỉ, tiểu sử, ảnh, thuộc tính mở rộng và ngày sinh chi tiết
 * biến mất. Mức riêng tư bị ép về {@code RESTRICTED} để dữ liệu còn lại không bị mở rộng trở lại.
 *
 * <h2>Ai được gọi</h2>
 * <b>Chính chủ thể</b> (đây là quyền của họ) hoặc {@code ADMIN} (thực hiện thay khi có yêu cầu
 * bằng văn bản). Trưởng chi và Hội đồng không có quyền này: ẩn danh hoá hộ người khác là một quyết
 * định về dữ liệu cá nhân, không phải một thao tác quản trị phả hệ.
 *
 * <p><b>Chưa có endpoint.</b> Contract Sprint 1 không có đường đi cho nghiệp vụ này (xem
 * {@code contracts/README.md}, mục "Việc còn treo" - đề xuất {@code POST /persons/{id}/anonymize}).
 * Use case được hiện thực sẵn và có nhật ký đầy đủ để khi BA chốt thì chỉ còn việc nối dây, thay
 * vì phải viết vội một nghĩa vụ pháp lý dưới áp lực thời hạn.</p>
 */
@Service
public class AnonymizePersonService {

    private static final Logger log = LoggerFactory.getLogger(AnonymizePersonService.class);

    private final PersonRepository persons;
    private final BranchRepository branches;
    private final AuditPort audit;
    private final TreeCachePort treeCache;
    private final PrivacyTierService privacy;
    private final DomainEventPublisher events;

    public AnonymizePersonService(PersonRepository persons, BranchRepository branches, AuditPort audit,
                                  TreeCachePort treeCache, PrivacyTierService privacy,
                                  DomainEventPublisher events) {
        this.persons = persons;
        this.branches = branches;
        this.audit = audit;
        this.treeCache = treeCache;
        this.privacy = privacy;
        this.events = events;
    }

    @Transactional
    public PersonView anonymize(UUID personId, String reason) {
        CallerContext caller = privacy.caller();
        Person person = persons.byId(PersonId.of(personId)).orElseThrow(() -> new NotFoundException(
                GenealogyProblemCodes.NOT_FOUND, "Khong tim thay nhan khau voi dinh danh " + personId));
        if (!caller.isSelf(personId) && !caller.isAdmin()) {
            throw new ForbiddenException(GenealogyProblemCodes.FORBIDDEN,
                    "Chi chinh chu the hoac Quan tri he thong duoc yeu cau an danh hoa");
        }

        Map<String, Object> before = person.auditSnapshot();
        person.anonymize();
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

        // Nhật ký ghi việc ĐÃ ẩn danh hoá, không ghi thứ vừa bị xoá: chép dữ liệu Tầng 3 vào
        // audit_log là giữ lại đúng cái mà người ta vừa yêu cầu xoá. auditSnapshot() đã loại sẵn
        // toàn bộ trường Tầng 3, nên before/after ở đây an toàn để lưu.
        audit.record("Person", personId.toString(), AuditPort.Action.ANONYMIZE, before,
                person.auditSnapshot(),
                List.of("contact", "currentPlaceFull", "currentPlaceProvince", "occupation",
                        "biography", "avatarKey", "attributes", "birth", "names", "privacyLevel"),
                reason);
        events.publishAndClear(daSua);
        treeCache.evictAll();
        log.info("Da an danh hoa nhan khau {} theo yeu cau hop phap, node pha he duoc giu lai", personId);

        BranchDirectory dir = BranchDirectory.load(branches, Arrays.asList(person.primaryBranchId()));
        return privacy.toView(person, caller, dir);
    }
}
