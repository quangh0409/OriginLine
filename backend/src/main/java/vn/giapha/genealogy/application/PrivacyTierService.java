package vn.giapha.genealogy.application;

import java.time.Year;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.giapha.genealogy.application.view.PersonAccessView;
import vn.giapha.genealogy.application.view.PersonSummaryView;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.PrivacyFieldGroup;
import vn.giapha.genealogy.domain.port.CallerIdentityPort;
import vn.giapha.shared.security.CurrentUser;
import vn.giapha.shared.security.CurrentUserProvider;
import vn.giapha.shared.vo.BranchPath;

/**
 * <b>Bộ lọc riêng tư theo từng nhóm trường</b> (BA v2 §10, Nghị định 13/2023) — lớp chịu trách
 * nhiệm pháp lý của context này.
 *
 * <h2>Mô hình đồng thuận (từ V8)</h2>
 * Chủ thể chọn <b>độc lập</b> cho năm nhóm trường ({@code PrivacyFieldGroup}) một trong ba mức
 * ({@code ShareScope}): <i>Cả họ xem</i> · <i>Cùng chi</i> · <i>Riêng tư</i>. Nghề nghiệp có thể
 * "cả họ xem" trong khi điện thoại "cùng chi" và ngày sinh đầy đủ "riêng tư" — đúng như bản thiết
 * kế đã hứa. Mô hình cũ ({@code PrivacyLevel}, một mức cho cả con người) nới hoặc siết toàn bộ
 * Tầng 3 một lượt và <b>không</b> diễn đạt nổi điều đó.
 *
 * <h2>Luật nằm ngoài tay người dùng — viết lại cho người sẽ sửa file này</h2>
 * <ol>
 *   <li><b>Khách không thấy bất kỳ người còn sống nào</b>, kể cả tên, bất kể chủ thể chọn mức gì.
 *       Đây là ranh giới pháp lý. Không phải "thấy mỗi cái tên", không phải "thấy node ẩn danh" —
 *       là không tồn tại trong phản hồi, nên API trả {@code 404} chứ không {@code 403}: trả 403 là
 *       tự xác nhận người đó có thật.</li>
 *   <li><b>Người đã khuất là dữ liệu công khai</b> và <b>không</b> bị áp mô hình đồng thuận. Ngoại
 *       lệ duy nhất là khối liên hệ: số ghi trong hồ sơ một cụ đã mất trên thực tế là số của người
 *       thân đang sống.</li>
 *   <li><b>Trẻ vị thành niên ẩn tối đa</b> — chặn trước cả đồng thuận.</li>
 *   <li><b>Chính chủ và Hội đồng Tộc biểu/Admin</b> luôn xem được: đó là định nghĩa của mức
 *       "Riêng tư".</li>
 * </ol>
 *
 * <h2>Hai điều tuyệt đối không được làm</h2>
 * <ul>
 *   <li><b>Không cache kết quả đã lọc.</b> Nội dung phụ thuộc người gọi; một bản cache của vai này
 *       rơi vào tay vai khác là rò rỉ dữ liệu không để lại dấu vết nào trong log. Chỉ khung xương
 *       cây (danh sách id) mới được cache, và nó không chứa dữ liệu cá nhân.</li>
 *   <li><b>Không phân biệt "ẩn vì thiếu quyền" với "không có dữ liệu".</b> Cả hai đều là
 *       {@code null} ở view và biến mất khỏi JSON ở REST. Thêm bất kỳ cờ nào cho phép phân biệt hai
 *       nguyên nhân đó là phá bỏ chính điều mà lớp này bảo vệ.</li>
 * </ul>
 */
@Service
public class PrivacyTierService {

    private static final Logger log = LoggerFactory.getLogger(PrivacyTierService.class);

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
     * sách, bỏ khỏi phả đồ, và trả {@code 404} ở endpoint đơn lẻ. Dùng cho đường lọc theo lô, nơi
     * chưa cần biết chi/ngành; đường lọc từng hồ sơ nên dùng thẳng {@link #visibility}.</p>
     */
    public boolean canSee(Person person, CallerContext caller) {
        return existsFor(person, caller);
    }

    /**
     * <b>Cửa duy nhất</b> dẫn tới một quyết định hiển thị.
     *
     * <p>{@code Optional.empty()} nghĩa là người gọi không được biết bản ghi tồn tại — Khách nhìn
     * người còn sống, hoặc bản ghi đã xoá mềm với vai không có phạm vi toàn dòng họ. Không có
     * đường nào khác dựng được {@link PersonVisibility}, nên "quên kiểm tra" không còn là một lỗi
     * biểu diễn được: xem javadoc của {@code PersonVisibility}.</p>
     *
     * @param personBranch đường dẫn {@code ltree} của chi/ngành chứa nhân khẩu; {@code null} khi
     *                     nhân khẩu chưa gắn chi nào (khi đó không ai "cùng chi" với họ)
     */
    public Optional<PersonVisibility> visibility(Person person, CallerContext caller,
                                                 BranchPath personBranch) {
        if (!existsFor(person, caller)) {
            return Optional.empty();
        }
        boolean self = caller.isSelf(person.rawId());
        boolean inScope = caller.managesBranch(personBranch) || caller.sharesBranchWith(personBranch);
        return Optional.of(new PersonVisibility(
                !person.isAlive(),
                self,
                caller.role().isClanWide(),
                inScope,
                isMinor(person),
                person.privacyConsent()));
    }

    /** Hồ sơ đầy đủ đã lọc. Quan hệ lõi gắn sau bằng {@link PersonView#withRelationships}. */
    public PersonView toView(Person person, CallerContext caller, BranchDirectory branches) {
        BranchPath path = branches.pathOf(person.primaryBranchId());
        PersonVisibility vis = require(person, caller, path);

        // Ngay sinh: nhom 5 mo thi tra day du; khong thi con nam sinh neu du lieu pha he duoc xem.
        LifeDate birth = null;
        if (person.birth() != null) {
            if (vis.allows(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO)) {
                birth = person.birth();
            } else if (vis.ungroupedFieldsVisible()) {
                birth = person.birth().coarsenToYear();
            }
        }

        boolean contactVisible = vis.allows(PrivacyFieldGroup.CONTACT);
        boolean ngoaiNhom = vis.ungroupedFieldsVisible();

        return new PersonView(
                person.rawId(),
                visibleNames(person, vis),
                person.displayName(),
                person.gender(),
                person.generation(),
                person.isAlive(),
                caller.role().isClanWide() ? person.isDeleted() : null,
                birth,
                person.death(),
                vis.ungroupedFieldsVisible() ? person.nativePlace() : null,
                vis.allows(PrivacyFieldGroup.RESIDENCE_PROVINCE) ? person.currentPlaceProvince() : null,
                vis.allows(PrivacyFieldGroup.RESIDENCE_FULL) ? person.currentPlaceFull() : null,
                vis.allows(PrivacyFieldGroup.OCCUPATION) ? person.occupation() : null,
                ngoaiNhom ? person.biography() : null,
                vis.allows(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO) ? person.avatarKey() : null,
                branches.refOf(person.primaryBranchId()),
                contactVisible && !person.contact().isEmpty() ? person.contact() : null,
                ngoaiNhom && !person.attributes().isEmpty() ? person.attributes() : null,
                // Bang cai dat rieng tu chi chinh chu va ADMIN duoc doc: biet nguoi khac dang siet
                // quyen rieng tu cung la mot dang ro ri.
                vis.self() || caller.isAdmin() ? person.privacyConsent() : null,
                person.createdAt(),
                person.updatedAt(),
                person.version(),
                List.of(),
                accessOf(person, caller, vis));
    }

    /** Dạng gọn cho danh sách và node phả đồ - chỉ dữ liệu phả hệ, không có khối nhạy cảm nào. */
    public PersonSummaryView toSummary(Person person, CallerContext caller, BranchDirectory branches) {
        BranchPath path = branches.pathOf(person.primaryBranchId());
        PersonVisibility vis = require(person, caller, path);
        boolean birthDetail = vis.allows(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO);
        boolean yearVisible = birthDetail || vis.ungroupedFieldsVisible();
        PersonName primary = person.primaryName().orElse(null);
        return new PersonSummaryView(
                person.rawId(),
                person.displayName(),
                primary == null ? null : primary.hanNom(),
                person.gender(),
                person.generation(),
                person.isAlive(),
                yearVisible && person.birth() != null ? person.birth().year().orElse(null) : null,
                person.death() == null ? null : person.death().year().orElse(null),
                branches.refOf(person.primaryBranchId()),
                vis.ungroupedFieldsVisible() ? person.nativePlace() : null,
                birthDetail ? person.avatarKey() : null,
                null);
    }

    /** Quyền của người gọi trên hồ sơ này - nói về <i>người gọi</i>, không nói về dữ liệu bị giấu. */
    public PersonAccessView accessOf(Person person, CallerContext caller, PersonVisibility vis) {
        boolean clanWide = caller.role().isClanWide();
        boolean branchHead = caller.role() == CallerRole.BRANCH_HEAD && vis.inBranchScope();
        return new PersonAccessView(vis.tier(), clanWide || branchHead || vis.self(),
                clanWide || branchHead, false, vis.self(), caller.role());
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ
    // -------------------------------------------------------------------------------------

    /**
     * Hai luật "bản ghi có tồn tại với người gọi này không", tách riêng để {@link #canSee} và
     * {@link #visibility} không bao giờ trả lời khác nhau.
     */
    private boolean existsFor(Person person, CallerContext caller) {
        if (person == null) {
            return false;
        }
        if (person.isDeleted() && !caller.role().isClanWide()) {
            return false;
        }
        return !person.isAlive() || !caller.isGuest();
    }

    /**
     * Mở {@link #visibility} ra, hoặc <b>đóng sập</b>.
     *
     * <p>Tới được đây với một bản ghi không hiển thị được nghĩa là một lối vào đã quên lọc. Ném lỗi
     * (thành {@code 500}) là lựa chọn đúng: fail-closed, ồn ào, và để lại dấu trong log — khác hẳn
     * hành vi cũ là lặng lẽ hạ xuống {@code T1} rồi vẫn trả dữ liệu ra.</p>
     */
    private PersonVisibility require(Person person, CallerContext caller, BranchPath path) {
        return visibility(person, caller, path).orElseThrow(() -> {
            log.error("RO RI BI CHAN: dung bo loc rieng tu cho nhan khau {} ma nguoi goi (vai {})"
                    + " khong duoc phep biet la ton tai", person == null ? null : person.rawId(),
                    caller.role());
            return new IllegalStateException(
                    "Nhan khau nay khong hien thi duoc voi nguoi goi hien tai");
        });
    }

    /**
     * Tên hiển thị được.
     *
     * <p>Với người còn sống ngoài phạm vi chỉ trả <b>tên chính</b>. Tên húy, tên tự, tên hiệu là
     * dữ liệu lễ nghi nhạy cảm; riêng tên húy còn là căn cứ cảnh báo kỵ húy cho người khác, phơi
     * nó ra cho mọi thành viên là làm mất chính ý nghĩa của tục kiêng tên.</p>
     */
    private List<PersonName> visibleNames(Person person, PersonVisibility vis) {
        if (vis.ungroupedFieldsVisible()) {
            return person.names();
        }
        return person.primaryName().<List<PersonName>>map(List::of).orElseGet(List::of);
    }

    /**
     * Ước lượng vị thành niên từ <b>năm</b> sinh.
     *
     * <p>Chỉ dùng năm là cố ý: ngày sinh đầy đủ chính là dữ liệu mà bộ lọc đang bảo vệ (nhóm
     * {@code BIRTH_DETAIL_AND_PHOTO}), nên quyết định về mức ẩn không được phụ thuộc vào việc đọc
     * nó. Không rõ năm sinh thì không suy đoán - gia phả giấy thường thiếu năm sinh của các đời xa,
     * coi họ là trẻ em thì vô lý.</p>
     */
    private boolean isMinor(Person person) {
        if (person.birth() == null) {
            return false;
        }
        Integer year = person.birth().year().orElse(null);
        return year != null && Year.now().getValue() - year < MINOR_AGE;
    }
}
