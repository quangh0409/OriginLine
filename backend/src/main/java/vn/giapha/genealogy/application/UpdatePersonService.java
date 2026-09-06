package vn.giapha.genealogy.application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.application.command.MoveBranchCommand;
import vn.giapha.genealogy.application.command.UpdatePersonCommand;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.ProfileEdit;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.genealogy.domain.port.TreeCachePort;
import vn.giapha.genealogy.domain.port.TreeGraphPort;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.PersonId;

/**
 * Sửa một phần hồ sơ nhân khẩu.
 *
 * <h2>Những thay đổi không đi qua đây</h2>
 * Xoá mềm, khôi phục và chuyển chi được <b>uỷ thác</b> sang use case riêng ngay trong cùng
 * transaction, vì mỗi thứ có quy tắc phân quyền và nhật ký riêng: khôi phục là việc của Hội đồng,
 * chuyển chi cần quyền ở cả hai chi, còn sửa hồ sơ thì chính chủ cũng làm được. Gộp chúng thành
 * một khối {@code if} trong đây là cách nhanh nhất để mất luôn các quy tắc đó.
 *
 * <h2>Sống/mất và tính trung thực của nhật ký</h2>
 * Việc diễn giải {@code isAlive} + {@code death} nằm ở {@link LifeStatusResolver} (Phương án B:
 * <b>có ngày mất tức là đã mất</b>). Ở đây chỉ cần nhớ một điều: {@code changed} đi vào
 * {@code audit_log.changed_fields} phải là <b>những trường thực sự đổi</b>, do chính aggregate báo
 * lại. Bản cũ tự dựng danh sách {@code [isAlive, death]} bất kể có đổi gì hay không, nên nhật ký
 * ghi những thay đổi chưa hề xảy ra — nguy hiểm hơn cả việc mất ngày mất, vì một gia phả sống bằng
 * khả năng truy vết ai sửa gì.
 *
 * <h2>Khoá lạc quan</h2>
 * {@code expectedVersion} đến từ {@code ETag} của lần {@code GET} trước. Lệch nghĩa là người khác
 * đã sửa hồ sơ này trong lúc người dùng đang mở form - trả {@code 409} để họ nạp lại thay vì âm
 * thầm ghi đè công của nhau. Gia phả là dữ liệu nhiều người cùng biên tập; đây không phải trường
 * hợp hiếm.
 */
@Service
public class UpdatePersonService {

    private static final Logger log = LoggerFactory.getLogger(UpdatePersonService.class);

    private final PersonRepository persons;
    private final BranchRepository branches;
    private final TreeGraphPort graph;
    private final AuditPort audit;
    private final TreeCachePort treeCache;
    private final TabooNameChecker tabooNames;
    private final PrivacyTierService privacy;
    private final GenealogyAccessGuard guard;
    private final DomainEventPublisher events;
    private final SoftDeletePersonService softDelete;
    private final RestorePersonService restore;
    private final MoveBranchService moveBranch;

    public UpdatePersonService(PersonRepository persons, BranchRepository branches, TreeGraphPort graph,
                               AuditPort audit, TreeCachePort treeCache, TabooNameChecker tabooNames,
                               PrivacyTierService privacy, GenealogyAccessGuard guard,
                               DomainEventPublisher events, SoftDeletePersonService softDelete,
                               RestorePersonService restore, MoveBranchService moveBranch) {
        this.persons = persons;
        this.branches = branches;
        this.graph = graph;
        this.audit = audit;
        this.treeCache = treeCache;
        this.tabooNames = tabooNames;
        this.privacy = privacy;
        this.guard = guard;
        this.events = events;
        this.softDelete = softDelete;
        this.restore = restore;
        this.moveBranch = moveBranch;
    }

    @Transactional
    public PersonView update(UpdatePersonCommand cmd) {
        CallerContext caller = privacy.caller();
        Person person = load(cmd);
        requireMatchingVersion(cmd, person);

        BranchDirectory dir = BranchDirectory.load(branches, Arrays.asList(person.primaryBranchId()));
        guard.requireProfileWriteAccess(caller, dir.pathOf(person.primaryBranchId()), person.rawId());

        applyDeletionFlag(cmd);
        applyBranchMove(cmd, person);

        // Nạp lại sau khi uỷ thác: hai use case kia đã ghi bản của chúng, tiếp tục dùng bản cũ
        // trong bộ nhớ là ghi đè ngược lại thay đổi vừa thực hiện.
        person = load(cmd);
        Map<String, Object> before = person.auditSnapshot();
        List<String> changed = new ArrayList<>();

        // Diễn giải ý định về sống/mất TRƯỚC khi chạm vào aggregate: mâu thuẫn tường minh
        // (isAlive=true kèm ngày mất) phải nổ ra khi hồ sơ còn nguyên vẹn.
        LifeStatusResolver.Target lifeStatus = LifeStatusResolver.resolve(
                cmd.alive(), cmd.death(), person.isAlive(), person.death());

        changed.addAll(applyNames(cmd, person));
        changed.addAll(applyLifeStatus(lifeStatus, person));
        changed.addAll(applyPrivacyLevel(cmd, person, caller));
        changed.addAll(person.applyProfileEdit(profileEdit(cmd)));

        if (changed.isEmpty()) {
            return privacy.toView(person, caller, dir).withRelationships(List.of());
        }

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
        if (changed.contains("gender")) {
            graph.syncPersonNode(person.rawId(),
                    person.gender() == null ? null : person.gender().name(),
                    person.generation(), person.isDeleted());
        }

        audit.record("Person", cmd.personId().toString(), AuditPort.Action.UPDATE, before,
                person.auditSnapshot(), changed, auditNote(cmd, lifeStatus, changed));
        events.publishAndClear(daSua);
        treeCache.evictAll();
        log.info("Da cap nhat nhan khau {}, cac truong: {}", cmd.personId(), changed);
        if (lifeStatus != null && lifeStatus.inferred() && changed.contains("isAlive")) {
            log.info("Nhan khau {}: body co ngay mat nhung khong khai isAlive - suy dien la da mat "
                    + "(Phuong an B). Ho so chuyen sang tang hien thi PUBLIC va death_lunar tro "
                    + "thanh nguon chan ly cua gio.", cmd.personId());
        }

        BranchDirectory after = BranchDirectory.load(branches, Arrays.asList(person.primaryBranchId()));
        return privacy.toView(person, caller, after);
    }

    private Person load(UpdatePersonCommand cmd) {
        return persons.byId(PersonId.of(cmd.personId())).orElseThrow(() -> new NotFoundException(
                GenealogyProblemCodes.NOT_FOUND,
                "Khong tim thay nhan khau voi dinh danh " + cmd.personId()));
    }

    private void requireMatchingVersion(UpdatePersonCommand cmd, Person person) {
        if (cmd.expectedVersion() != null && cmd.expectedVersion() != person.version()) {
            throw new GenealogyConflictException(GenealogyProblemCodes.OPTIMISTIC_LOCK_CONFLICT,
                    "Ban ghi da duoc nguoi khac cap nhat, hay tai lai va thu lai");
        }
    }

    /** {@code isDeleted} trong body tương đương gọi {@code DELETE} hoặc khôi phục. */
    private void applyDeletionFlag(UpdatePersonCommand cmd) {
        if (!cmd.deleted().present() || cmd.deleted().value() == null) {
            return;
        }
        if (Boolean.TRUE.equals(cmd.deleted().value())) {
            softDelete.softDelete(cmd.personId(), cmd.note());
        } else {
            restore.restore(cmd.personId(), cmd.note());
        }
    }

    private void applyBranchMove(UpdatePersonCommand cmd, Person person) {
        if (!cmd.primaryBranchId().present() || cmd.primaryBranchId().value() == null) {
            return;
        }
        if (cmd.primaryBranchId().value().equals(person.primaryBranchId())) {
            return;
        }
        moveBranch.move(new MoveBranchCommand(cmd.personId(), cmd.primaryBranchId().value(), cmd.note()));
    }

    /**
     * Thay thế toàn bộ danh sách tên, kèm kiểm <b>kỵ húy</b> cho các tên húy mới.
     *
     * <p>Ngữ nghĩa "gửi là thay hết" là của contract, không phải lựa chọn ở đây: muốn thêm một tên
     * thì client gửi lại cả danh sách cũ cộng tên mới.</p>
     */
    private List<String> applyNames(UpdatePersonCommand cmd, Person person) {
        if (!cmd.names().present() || cmd.names().value() == null || cmd.names().value().isEmpty()) {
            return List.of();
        }
        List<PersonName> newNames = cmd.names().value();
        tabooNames.check(newNames, person.generation(), person.rawId(), cmd.confirmTabooOverride());
        person.replaceNames(newNames);
        return List.of("names");
    }

    /**
     * Báo mất hoặc đính chính "thật ra còn sống" - hai hành vi nghiệp vụ có tên, không phải setter.
     *
     * <p>Việc <b>diễn giải ý định</b> của body (kể cả phép suy diễn "có ngày mất tức là đã mất")
     * nằm ở {@link LifeStatusResolver}; ở đây chỉ còn việc áp lên aggregate và nhận lại danh sách
     * trường <b>thực sự</b> đổi. Không tự dựng danh sách {@code [isAlive, death]} nữa: đó chính là
     * chỗ nhật ký từng nói dối về những thay đổi chưa hề xảy ra.</p>
     */
    private List<String> applyLifeStatus(LifeStatusResolver.Target target, Person person) {
        if (target == null) {
            return List.of();
        }
        return target.alive() ? person.markAlive() : person.markDeceased(target.death());
    }

    /**
     * Ghi chú nhật ký: khi việc chuyển sang "đã mất" là do <b>backend suy ra</b> từ ngày mất chứ
     * không phải do client khai, nhật ký phải nói rõ điều đó. Người đọc lại lịch sử sửa đổi ba năm
     * sau cần phân biệt được "người nhập liệu tuyên bố cụ đã mất" với "hệ thống suy ra từ ngày mất
     * mà họ điền".
     */
    private String auditNote(UpdatePersonCommand cmd, LifeStatusResolver.Target target,
                             List<String> changed) {
        if (target == null || !target.inferred() || !changed.contains("isAlive")) {
            return cmd.note();
        }
        String suyDien = "Suy dien: body co ngay mat nhung khong co isAlive, he thong tu dat "
                + "isAlive=false.";
        return cmd.note() == null || cmd.note().isBlank() ? suyDien : cmd.note() + " | " + suyDien;
    }

    /**
     * Mức chia sẻ là quyền của <b>chính chủ thể</b>.
     *
     * <p>Quản trị viên không siết hộ và cũng không nới hộ: {@code privacyLevel} là ý chí của người
     * được ghi trong gia phả, đúng tinh thần Nghị định 13/2023.</p>
     */
    private List<String> applyPrivacyLevel(UpdatePersonCommand cmd, Person person, CallerContext caller) {
        if (!cmd.privacyLevel().present() || cmd.privacyLevel().value() == null) {
            return List.of();
        }
        if (!caller.isSelf(person.rawId()) && !caller.isAdmin()) {
            throw new ForbiddenException(GenealogyProblemCodes.FORBIDDEN,
                    "Chi chinh chu the (hoac Quan tri he thong thay mat ho) duoc doi muc rieng tu");
        }
        if (cmd.privacyLevel().value() == person.privacyLevel()) {
            return List.of();
        }
        person.choosePrivacyLevel(cmd.privacyLevel().value());
        return List.of("privacyLevel");
    }

    private ProfileEdit profileEdit(UpdatePersonCommand cmd) {
        return ProfileEdit.builder()
                .gender(cmd.gender())
                .birth(cmd.birth())
                .nativePlace(cmd.nativePlace())
                .currentPlaceProvince(cmd.currentPlaceProvince())
                .currentPlaceFull(cmd.currentPlaceFull())
                .occupation(cmd.occupation())
                .biography(cmd.biography())
                .avatarKey(cmd.avatarKey())
                .contact(cmd.contact())
                .attributes(cmd.attributes())
                .build();
    }
}
