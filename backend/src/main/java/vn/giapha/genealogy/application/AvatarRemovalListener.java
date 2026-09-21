package vn.giapha.genealogy.application;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.domain.FieldChange;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.ProfileEdit;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.genealogy.domain.port.TreeCachePort;
import vn.giapha.media.domain.event.PersonAvatarRemovedEvent;
import vn.giapha.shared.vo.PersonId;

/**
 * Xoá con trỏ {@code person.avatar_key} khi ảnh chân dung bị <b>gỡ theo một đơn báo vi phạm</b>.
 *
 * <h2>Triệu chứng nếu lớp này không tồn tại</h2>
 * Người duyệt bấm gỡ, byte biến mất khỏi kho, nhưng {@code person.avatar_key} vẫn trỏ vào khoá
 * cũ. Hồ sơ ấy sẽ <b>mãi mãi</b> hiện một ô ảnh vỡ, và triệu chứng trông y hệt một sự cố hạ tầng
 * ("MinIO hỏng à?") chứ không giống một tấm ảnh đã bị gỡ đúng quy trình — nên người đi sửa sẽ đi
 * xem kho chứ không đi xem bảng.
 *
 * <h2>{@code @EventListener} thường, KHÔNG {@code @TransactionalEventListener}</h2>
 * Cùng lập luận mà {@code ChangeRequestApplier} đã ghi: việc này phải nằm <b>trong cùng giao
 * dịch</b> với lệnh gỡ. Nếu nó chạy sau commit và thất bại, ta có một tấm ảnh đã bị xoá khỏi kho
 * và một hồ sơ vẫn trỏ vào nó — đúng trạng thái nửa vời mà lớp này sinh ra để chặn. Ngược lại,
 * nếu nó thất bại <i>trước</i> commit thì cả lệnh gỡ bị huỷ và người duyệt thấy lỗi, bấm lại
 * được. Hỏng-toàn-bộ tốt hơn hỏng-một-nửa ở đây.
 *
 * <p>Lối ngược {@code media → genealogy} chỉ đi qua sự kiện, không qua lời gọi Java — nếu không
 * thì có chu trình và {@code ModularityTests} đỏ. Xem {@code PersonAvatarRemovedEvent}.</p>
 */
@Component
public class AvatarRemovalListener {

    private static final Logger log = LoggerFactory.getLogger(AvatarRemovalListener.class);

    private final PersonRepository persons;
    private final AuditPort audit;
    private final TreeCachePort treeCache;

    public AvatarRemovalListener(PersonRepository persons, AuditPort audit, TreeCachePort treeCache) {
        this.persons = persons;
        this.audit = audit;
        this.treeCache = treeCache;
    }

    @EventListener
    @Transactional
    public void onAvatarRemoved(PersonAvatarRemovedEvent event) {
        Person person = persons.byId(PersonId.of(event.personId())).orElse(null);
        if (person == null) {
            log.warn("Go anh chan dung cua nhan khau {} nhung ho so khong ton tai", event.personId());
            return;
        }
        if (person.avatarKey() == null) {
            // Da rong san — mot don bao go duoc xu SAU khi chinh chu da tu doi anh. Khong phai loi.
            return;
        }
        List<String> changed = person.applyProfileEdit(
                ProfileEdit.builder().avatarKey(FieldChange.clear()).build());
        persons.save(person);
        audit.record("Person", person.rawId().toString(), AuditPort.Action.UPDATE,
                null, Map.of("avatarChanged", true), changed,
                "Go anh chan dung theo don bao vi pham (tep " + event.mediaId() + ")");
        treeCache.evictAll();
        log.info("Da xoa con tro anh chan dung cua nhan khau {} sau khi tep {} bi go",
                event.personId(), event.mediaId());
    }
}
