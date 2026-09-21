package vn.giapha.genealogy.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.FieldChange;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.ProfileEdit;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.genealogy.domain.port.TreeCachePort;
import vn.giapha.media.application.MediaLinkService;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.PersonId;

/**
 * Đặt và gỡ <b>ảnh chân dung</b> của một nhân khẩu.
 *
 * <h2>Quyết định số 3 của chủ dự án: KHÔNG có luật riêng tư mới</h2>
 * Ảnh chân dung đi qua nhóm trường <b>{@code birthDetailAndPhoto} đã có sẵn</b> (V8, mở rộng ở
 * V17). Không thêm khoá đồng thuận thứ bảy, không sửa {@code is_valid_privacy_consent()}, không
 * viết một phép so mới. Hệ quả cụ thể, và nó đã có sẵn từ trước đợt này:
 * {@code PrivacyTierService} chỉ trả {@code avatarKey} khi
 * {@code vis.allows(BIRTH_DETAIL_AND_PHOTO)}, ở cả {@code toView} lẫn {@code toSummary}, và
 * {@code DirectoryService} cũng thế. Đợt này <b>chỉ thêm đường ghi</b> — đường đọc vốn đã đúng và
 * không được chạm vào.
 *
 * <h2>Vì sao có một use case riêng thay vì một trường trong {@code PATCH /persons/{id}}</h2>
 * {@code UpdatePersonCommand} vốn <i>đã</i> có {@code avatarKey}, và để nguyên như thế thì client
 * tự điền một chuỗi bất kỳ vào cột ấy — đúng cái mà V17 gọi là "một trường trỏ vào hư không". Lối
 * đúng phải đi qua {@code media}: khoá không do client chọn mà do chính backend cấp lúc phát
 * phiếu, và nó chỉ được ghi <b>sau khi</b> backend đã tự nhìn thấy tệp trên kho. Một use case
 * riêng làm ràng buộc ấy hiện rõ trong chữ ký ({@code mediaId}, không phải {@code String}).
 *
 * <h2>Hai lần ghi, MỘT giao dịch</h2>
 * {@code media_link} (để dọn tệp mồ côi và để ký URL) và khoá ảnh chân dung của nhân khẩu (nguồn
 * chân lý của ô avatar, thứ mọi bộ lọc riêng tư đang soi) được ghi trong cùng một
 * {@code @Transactional}. Tách ra là tự tạo hai trạng thái nửa vời: một khoá trỏ vào tệp không ai
 * sở hữu (đường dọn sẽ xoá byte, hồ sơ còn ô ảnh vỡ), hoặc một liên kết không ai dùng.
 *
 * <h2>Khoá ảnh chân dung KHÔNG phải một cột — đây là chỗ dễ đoán sai nhất</h2>
 * Nó nằm trong JSONB: {@code person.attributes -> '_profile' ->> 'avatarKey'} (xem
 * {@code PersonMapper.K_AVATAR}). Cả {@code V17} lẫn bản nháp đầu của {@code V19} đều viết nhầm là
 * "cột {@code person.avatar_key}", và một câu SQL dựa vào cái tên đó sẽ chết với <i>column does not
 * exist</i> ở đúng lúc đang đi truy một sự cố riêng tư. Hệ quả có lợi: đợt này <b>không cần một
 * migration nào trên bảng {@code person}</b>.
 *
 * <p>Vì sao không đổi nó thành khoá ngoại sang {@code media_asset}: làm thế là sửa <b>năm chỗ lọc
 * riêng tư cùng lúc</b> để đổi lấy một phép nối — cách chắc chắn nhất để một trong năm chỗ ấy bị
 * bỏ sót và ảnh người còn sống lọt ra. Xem khối ghi chú đầu {@code V19}.</p>
 */
@Service
public class SetPersonAvatarService {

    private static final Logger log = LoggerFactory.getLogger(SetPersonAvatarService.class);

    private final PersonRepository persons;
    private final BranchRepository branches;
    private final PrivacyTierService privacy;
    private final GenealogyAccessGuard guard;
    private final MediaLinkService media;
    private final AuditPort audit;
    private final TreeCachePort treeCache;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public SetPersonAvatarService(PersonRepository persons, BranchRepository branches,
                                  PrivacyTierService privacy, GenealogyAccessGuard guard,
                                  MediaLinkService media, AuditPort audit, TreeCachePort treeCache) {
        this.persons = persons;
        this.branches = branches;
        this.privacy = privacy;
        this.guard = guard;
        this.media = media;
        this.audit = audit;
        this.treeCache = treeCache;
    }

    /**
     * Đặt ảnh chân dung.
     *
     * @param mediaId tệp <b>đã xác nhận</b>, do chính người gọi tải lên. {@code media} kiểm cả hai
     *                điều đó; lớp này không chép lại phép kiểm ấy
     */
    @Transactional
    public PersonView setAvatar(UUID personId, UUID mediaId) {
        CallerContext caller = privacy.caller();
        Person person = load(personId);
        BranchDirectory dir = BranchDirectory.load(branches, List.of(person.primaryBranchId()));
        guard.requireProfileWriteAccess(caller, dir.pathOf(person.primaryBranchId()), person.rawId());

        String objectKey = media.setPersonAvatar(personId, mediaId);
        return apply(person, caller, dir, FieldChange.set(objectKey), "Dat anh chan dung");
    }

    /**
     * Gỡ ảnh chân dung.
     *
     * <p>Tệp <b>không bị xoá ngay</b>: liên kết bị cắt, nó thành mồ côi, và đường dọn mang đi sau
     * {@code MediaLimits.ORPHAN_GRACE} (24 giờ). Xoá ngay sẽ làm một lần bấm nhầm thành một lần
     * mất ảnh vĩnh viễn — thứ đặc biệt đắt với một tấm ảnh chân dung cũ đã scan từ ảnh giấy.</p>
     *
     * <p><b>Ngoại lệ:</b> gỡ theo một đơn báo vi phạm thì không có ân hạn — xem
     * {@code MediaReportService}. Hai lối gỡ, hai mức khẩn cấp.</p>
     */
    @Transactional
    public PersonView clearAvatar(UUID personId) {
        CallerContext caller = privacy.caller();
        Person person = load(personId);
        BranchDirectory dir = BranchDirectory.load(branches, List.of(person.primaryBranchId()));
        guard.requireProfileWriteAccess(caller, dir.pathOf(person.primaryBranchId()), person.rawId());

        media.clearPersonAvatar(personId);
        return apply(person, caller, dir, FieldChange.clear(), "Go anh chan dung");
    }

    private PersonView apply(Person person, CallerContext caller, BranchDirectory dir,
                             FieldChange<String> avatarKey, String note) {
        List<String> changed = person.applyProfileEdit(
                ProfileEdit.builder().avatarKey(avatarKey).build());
        Person saved = persons.save(person);

        // Anh chup audit KHONG cho khoa doi tuong: mot khoa ky duoc thanh URL doc, nen voi anh
        // chan dung cua nguoi con song no la du lieu Tang 3 theo dung nghia cua BA v2 §10.
        // SensitiveFieldRedactor da che khoa ten "avatarkey" va se che no, nhung dua vao luoi cuoi
        // de giu mot bat bien la cach no truot qua vao ngay ai do doi ten truong.
        audit.record("Person", person.rawId().toString(), AuditPort.Action.UPDATE,
                null, Map.of("avatarChanged", true), changed, note);
        treeCache.evictAll();
        log.info("{} cho nhan khau {}", note, person.rawId());
        return privacy.toView(saved, caller, dir).withRelationships(List.of());
    }

    private Person load(UUID personId) {
        return persons.byId(PersonId.of(personId))
                .orElseThrow(() -> NotFoundException.of("Person", personId));
    }
}
