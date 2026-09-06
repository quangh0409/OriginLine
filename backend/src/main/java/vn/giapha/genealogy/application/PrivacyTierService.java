package vn.giapha.genealogy.application;

import java.time.Year;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import vn.giapha.genealogy.application.view.PersonAccessView;
import vn.giapha.genealogy.application.view.PersonSummaryView;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.PrivacyLevel;
import vn.giapha.genealogy.domain.port.CallerIdentityPort;
import vn.giapha.shared.security.CurrentUser;
import vn.giapha.shared.security.CurrentUserProvider;
import vn.giapha.shared.vo.BranchPath;

/**
 * <b>Bộ lọc phân tầng hiển thị</b> (BA v2 §10, Nghị định 13/2023) - lớp chịu trách nhiệm pháp lý
 * của context này.
 *
 * <h2>Luật, viết lại cho người sẽ sửa file này</h2>
 * <ol>
 *   <li><b>Người đã khuất là dữ liệu công khai</b> theo đúng mục đích của gia phả.</li>
 *   <li><b>Khách không thấy bất kỳ người còn sống nào.</b> Không phải "thấy mỗi cái tên", không
 *       phải "thấy node ẩn danh" - là không tồn tại trong phản hồi. Vì thế API trả {@code 404}
 *       chứ không {@code 403}: trả 403 là tự xác nhận người đó có thật.</li>
 *   <li>Tầng 1 (tên, đời, quan hệ lõi) cho mọi thành viên đã đăng nhập; Tầng 2 (năm sinh, nghề,
 *       tỉnh) cho người cùng chi hoặc có phạm vi; Tầng 3 (liên hệ, địa chỉ đầy đủ, ngày sinh đầy
 *       đủ, ảnh, tiểu sử) chỉ cho <b>chính chủ, Admin, và người được chủ thể opt-in</b>.</li>
 *   <li><b>Trẻ vị thành niên ẩn tối đa</b>: trần là Tầng 1, kể cả với người cùng chi.</li>
 * </ol>
 *
 * <h2>Hai điều tuyệt đối không được làm</h2>
 * <ul>
 *   <li><b>Không cache kết quả đã lọc.</b> Nội dung phụ thuộc người gọi; một bản cache của vai
 *       này rơi vào tay vai khác là rò rỉ dữ liệu không để lại dấu vết nào trong log. Chỉ khung
 *       xương cây (danh sách id) mới được cache, và nó không chứa dữ liệu cá nhân.</li>
 *   <li><b>Không phân biệt "ẩn vì thiếu quyền" với "không có dữ liệu".</b> Cả hai đều là
 *       {@code null} ở view và biến mất khỏi JSON ở REST. Thêm bất kỳ cờ nào cho phép phân biệt
 *       hai nguyên nhân đó là phá bỏ chính điều mà lớp này bảo vệ.</li>
 * </ul>
 */
@Service
public class PrivacyTierService {

    /** Ngưỡng vị thành niên theo Bộ luật Dân sự - dưới 18 tuổi. */
    private static final int MINOR_AGE = 18;

    private final CallerIdentityPort callerIdentity;

    public PrivacyTierService(CallerIdentityPort callerIdentity) {
        this.callerIdentity = callerIdentity;
    }

    /**
     * Dựng ngữ cảnh người gọi cho <b>một</b> request. Gọi một lần rồi truyền đi: hỏi lại giữa
     * chừng vừa tốn truy vấn vừa mở đường cho hai nhân khẩu trong cùng một phản hồi bị xét theo
     * hai ngữ cảnh khác nhau.
     */
    public CallerContext caller() {
        Optional<CurrentUser> user = CurrentUserProvider.current();
        if (user.isEmpty()) {
            return CallerContext.guest();
        }
        CallerRole role = CallerRole.from(user.get().roles());
        return new CallerContext(role,
                callerIdentity.currentPersonId().orElse(null),
                callerIdentity.managedBranches(),
                callerIdentity.homeBranch().orElse(null));
    }

    /**
     * Người gọi có được biết nhân khẩu này <b>tồn tại</b> hay không.
     *
     * <p>Trả {@code false} thì mọi lối ra phải hành xử như thể bản ghi không có: bỏ khỏi danh
     * sách, bỏ khỏi phả đồ, và trả {@code 404} ở endpoint đơn lẻ.</p>
     */
    public boolean canSee(Person person, CallerContext caller) {
        if (person == null) {
            return false;
        }
        if (person.isDeleted() && !caller.role().isClanWide()) {
            return false;
        }
        return !person.isAlive() || !caller.isGuest();
    }

    /**
     * Tầng dữ liệu người gọi nhận được cho hồ sơ này.
     *
     * <p>Chỉ gọi sau khi {@link #canSee} đã trả {@code true}: với Khách nhìn người còn sống thì
     * câu hỏi "tầng nào" không có nghĩa, vì bản ghi phải biến mất hoàn toàn.</p>
     */
    public VisibleTier tierFor(Person person, CallerContext caller, BranchPath personBranch) {
        if (!person.isAlive()) {
            return VisibleTier.PUBLIC;
        }
        boolean self = caller.isSelf(person.rawId());
        if (self || caller.isAdmin()) {
            return VisibleTier.T3;
        }
        if (caller.isGuest()) {
            return VisibleTier.T1;
        }
        // Trẻ vị thành niên ẩn tối đa - chặn trước cả opt-in, vì một đứa trẻ không tự quyết được
        // việc công khai dữ liệu của chính mình.
        if (isMinor(person)) {
            return VisibleTier.T1;
        }
        PrivacyLevel level = person.privacyLevel() == null ? PrivacyLevel.DEFAULT : person.privacyLevel();
        if (level == PrivacyLevel.RESTRICTED) {
            return VisibleTier.T1;
        }
        boolean inScope = caller.managesBranch(personBranch) || caller.sharesBranchWith(personBranch);
        if (level == PrivacyLevel.CLAN_OPT_IN) {
            return VisibleTier.T3;
        }
        if (level == PrivacyLevel.BRANCH_OPT_IN && inScope) {
            return VisibleTier.T3;
        }
        if (inScope || caller.role() == CallerRole.COUNCIL) {
            return VisibleTier.T2;
        }
        return VisibleTier.T1;
    }

    /** Hồ sơ đầy đủ đã lọc. Quan hệ lõi gắn sau bằng {@link PersonView#withRelationships}. */
    public PersonView toView(Person person, CallerContext caller, BranchDirectory branches) {
        BranchPath path = branches.pathOf(person.primaryBranchId());
        VisibleTier tier = tierFor(person, caller, path);
        boolean self = caller.isSelf(person.rawId());
        boolean tier2 = tier.atLeast(VisibleTier.T2);
        boolean tier3 = tier.atLeast(VisibleTier.T3);
        boolean publicTier = tier == VisibleTier.PUBLIC;

        // Người đã khuất: dữ liệu phả hệ công khai, nhưng khối liên hệ thì không - số điện thoại
        // ghi trong hồ sơ một cụ đã mất trên thực tế là số của người thân đang sống.
        // KHONG dung `tier3` o day: VisibleTier.atLeast() tra true cho MOI tang khi this ==
        // PUBLIC, ma nguoi da khuat luon o PUBLIC — nen `tier3` se luon dung va ve thu hai
        // thanh code chet, mo khoi lien he cho ca Khach vang lai. Phai so bang chinh xac.
        boolean contactVisible =
                tier == VisibleTier.T3 || (publicTier && (self || caller.role().isClanWide()));
        boolean richVisible = publicTier || tier3;

        LifeDate birth = null;
        if (person.birth() != null) {
            birth = richVisible ? person.birth() : (tier2 ? person.birth().coarsenToYear() : null);
        }

        return new PersonView(
                person.rawId(),
                visibleNames(person, tier),
                person.displayName(),
                person.gender(),
                person.generation(),
                person.isAlive(),
                caller.role().isClanWide() ? person.isDeleted() : null,
                birth,
                person.death(),
                tier2 ? person.nativePlace() : null,
                tier2 ? person.currentPlaceProvince() : null,
                richVisible ? person.currentPlaceFull() : null,
                tier2 ? person.occupation() : null,
                richVisible ? person.biography() : null,
                richVisible ? person.avatarKey() : null,
                branches.refOf(person.primaryBranchId()),
                contactVisible && !person.contact().isEmpty() ? person.contact() : null,
                richVisible && !person.attributes().isEmpty() ? person.attributes() : null,
                self || caller.isAdmin() ? person.privacyLevel() : null,
                person.createdAt(),
                person.updatedAt(),
                person.version(),
                List.of(),
                accessOf(person, caller, path, tier));
    }

    /** Dạng gọn cho danh sách và node phả đồ - chỉ dữ liệu Tầng 1 trở xuống. */
    public PersonSummaryView toSummary(Person person, CallerContext caller, BranchDirectory branches) {
        BranchPath path = branches.pathOf(person.primaryBranchId());
        VisibleTier tier = tierFor(person, caller, path);
        boolean tier2 = tier.atLeast(VisibleTier.T2);
        PersonName primary = person.primaryName().orElse(null);
        return new PersonSummaryView(
                person.rawId(),
                person.displayName(),
                primary == null ? null : primary.hanNom(),
                person.gender(),
                person.generation(),
                person.isAlive(),
                tier2 && person.birth() != null ? person.birth().year().orElse(null) : null,
                person.death() == null ? null : person.death().year().orElse(null),
                branches.refOf(person.primaryBranchId()),
                tier2 ? person.nativePlace() : null,
                tier.atLeast(VisibleTier.T3) ? person.avatarKey() : null,
                null);
    }

    /** Quyền của người gọi trên hồ sơ này - nói về <i>người gọi</i>, không nói về dữ liệu bị giấu. */
    public PersonAccessView accessOf(Person person, CallerContext caller, BranchPath personBranch,
                                     VisibleTier tier) {
        boolean self = caller.isSelf(person.rawId());
        boolean clanWide = caller.role().isClanWide();
        boolean branchHead = caller.role() == CallerRole.BRANCH_HEAD && caller.managesBranch(personBranch);
        return new PersonAccessView(tier, clanWide || branchHead || self, clanWide || branchHead,
                false, self, caller.role());
    }

    /**
     * Tên hiển thị được với tầng hiện tại.
     *
     * <p>Với người còn sống ở Tầng 1 chỉ trả <b>tên chính</b>. Tên húy, tên tự, tên hiệu là dữ
     * liệu lễ nghi nhạy cảm; riêng tên húy còn là căn cứ cảnh báo kỵ húy cho người khác, phơi nó
     * ra cho mọi thành viên là làm mất chính ý nghĩa của tục kiêng tên.</p>
     */
    private List<PersonName> visibleNames(Person person, VisibleTier tier) {
        if (tier == VisibleTier.PUBLIC || tier.atLeast(VisibleTier.T2)) {
            return person.names();
        }
        return person.primaryName().<List<PersonName>>map(List::of).orElseGet(List::of);
    }

    /**
     * Ước lượng vị thành niên từ <b>năm</b> sinh.
     *
     * <p>Chỉ dùng năm là cố ý: ngày sinh đầy đủ chính là dữ liệu Tầng 3 mà bộ lọc đang bảo vệ, nên
     * quyết định về mức ẩn không được phụ thuộc vào việc đọc nó. Không rõ năm sinh thì không suy
     * đoán - gia phả giấy thường thiếu năm sinh của các đời xa, coi họ là trẻ em thì vô lý.</p>
     */
    private boolean isMinor(Person person) {
        if (person.birth() == null) {
            return false;
        }
        Integer year = person.birth().year().orElse(null);
        return year != null && Year.now().getValue() - year < MINOR_AGE;
    }
}
