package vn.giapha.genealogy.infrastructure.media;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.genealogy.application.BranchDirectory;
import vn.giapha.genealogy.application.CallerContext;
import vn.giapha.genealogy.application.GenealogyAccessGuard;
import vn.giapha.genealogy.application.PersonVisibility;
import vn.giapha.genealogy.application.PrivacyTierService;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PrivacyFieldGroup;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.media.domain.port.PersonAvatarAccessPort;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.vo.BranchPath;
import vn.giapha.shared.vo.PersonId;

/**
 * {@code genealogy} trả lời cho {@code media}: ai xem/đặt được ảnh chân dung của một nhân khẩu.
 *
 * <h2>Đây là chỗ QUYẾT ĐỊNH SỐ 3 CỦA CHỦ DỰ ÁN được thi hành</h2>
 * Ảnh chân dung đi qua nhóm trường <b>{@code birthDetailAndPhoto} đã có sẵn</b> — không luật riêng
 * tư mới. Vì vậy {@link #canView} không chứa một phép so nào của riêng nó: nó hỏi
 * {@code PrivacyTierService} đúng câu mà {@code PersonView.avatarKey} đã hỏi từ trước đợt này
 * ({@code vis.allows(BIRTH_DETAIL_AND_PHOTO)}), rồi trả lời theo. Chép lại phép so ở đây là cách
 * tạo ra tình huống mà {@code genealogy} đã ghi lại một lần: <b>một màn hình che, màn hình kia
 * không, và cả hai đều "đúng" theo bản luật của mình</b>.
 *
 * <h2>Hệ quả cần nhớ: KHOÁ KHÔNG PHẢI GIẤY THÔNG HÀNH</h2>
 * Một người đổi nhóm trường của mình về {@code PRIVATE} hôm nay thì từ hôm nay không ai ký được
 * URL cho ảnh của họ nữa — kể cả người hôm qua đã cầm đúng khoá đối tượng, kể cả khi khoá ấy còn
 * nằm trong lịch sử trình duyệt. Mỗi lần ký là một lần bộ lọc chạy lại với người gọi <i>hiện
 * tại</i> và cấu hình <i>hiện tại</i>.
 *
 * <h2>Người đã xoá mềm</h2>
 * {@code PrivacyTierService} đã trả lời "không thấy" cho hồ sơ đã xoá mềm với mọi vai trừ vai toàn
 * dòng họ; ta không thêm nhánh nào. Node ở lại trong cây (xoá mềm tuyệt đối), nhưng ảnh của nó thì
 * không hiện — và đó là hành vi đã có, không phải hành vi mới.
 */
@Component
public class PersonAvatarAccessAdapter implements PersonAvatarAccessPort {

    private static final Logger log = LoggerFactory.getLogger(PersonAvatarAccessAdapter.class);

    private final PersonRepository persons;
    private final BranchRepository branches;
    private final PrivacyTierService privacy;
    private final GenealogyAccessGuard guard;

    public PersonAvatarAccessAdapter(PersonRepository persons, BranchRepository branches,
                                     PrivacyTierService privacy, GenealogyAccessGuard guard) {
        this.persons = persons;
        this.branches = branches;
        this.privacy = privacy;
        this.guard = guard;
    }

    @Override
    public boolean canView(UUID personId) {
        Optional<Person> found = persons.byId(PersonId.of(personId));
        if (found.isEmpty()) {
            return false;
        }
        Person person = found.get();
        CallerContext caller = privacy.caller();
        BranchPath path = pathOf(person);
        // KHONG mot phep so rieng tu nao o day — hoi dung ban luat dang chay.
        Optional<PersonVisibility> vis = privacy.visibility(person, caller, path);
        return vis.isPresent() && vis.get().allows(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO);
    }

    /**
     * Đặt/gỡ ảnh chân dung: đúng phép kiểm ghi hồ sơ đã có
     * ({@code GenealogyAccessGuard.requireProfileWriteAccess}) — chính chủ, Trưởng chi trong phạm
     * vi {@code ltree}, hoặc vai toàn dòng họ.
     *
     * <p>Gọi phép kiểm ném ngoại lệ rồi bắt lại, thay vì viết một bản trả {@code boolean}: hai bản
     * của cùng một luật là hai bản sẽ lệch, và bản ném ngoại lệ là bản mà mọi lối ghi hồ sơ khác
     * đang dùng. Đây là lý do hiếm hoi mà bắt ngoại lệ để lấy một {@code boolean} là lựa chọn
     * đúng.</p>
     */
    @Override
    public boolean canAttach(UUID personId) {
        Optional<Person> found = persons.byId(PersonId.of(personId));
        if (found.isEmpty()) {
            return false;
        }
        Person person = found.get();
        try {
            guard.requireProfileWriteAccess(privacy.caller(), pathOf(person), person.rawId());
            return true;
        } catch (ForbiddenException ex) {
            log.debug("Tu choi dat anh chan dung cho nhan khau {}: {}", personId, ex.getCode());
            return false;
        }
    }

    /**
     * Chi <b>hiện tại</b> của nhân khẩu — không phải một giá trị chụp lại.
     *
     * <p>Khác hẳn {@code post.branch_id} (ảnh chụp lúc tạo nháp), và khác một cách có chủ ý: đây
     * là cùng ngữ nghĩa mà V17 đã chọn cho {@code honour} — "chi nào" của một con người là câu hỏi
     * về <i>hiện tại</i>, nên chụp lại sẽ làm hàng đợi sai sau một lần chuyển chi mà không ai
     * thấy.</p>
     */
    @Override
    public BranchPath branchOf(UUID personId) {
        return persons.byId(PersonId.of(personId)).map(this::pathOf).orElse(null);
    }

    private BranchPath pathOf(Person person) {
        return BranchDirectory.load(branches, List.of(person.primaryBranchId()))
                .pathOf(person.primaryBranchId());
    }
}
