package vn.giapha.genealogy.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.shared.vo.BranchPath;

/**
 * Trả lời câu <b>"bắt đầu từ đâu"</b> khi người dùng mở trang phả đồ mà chưa chọn ai.
 *
 * <h2>Đây là một quyết định thiết kế, không phải một giá trị mặc định</h2>
 * Ba ứng viên đều có lý và đều sai nếu áp cho tất cả:
 * <ul>
 *   <li><b>Thuỷ tổ (đời 1)</b> là gốc hiển nhiên của cả dòng họ — nhưng với một người chỉ muốn xem
 *       chi mình thì đó là một cây rất lớn và bản thân họ nằm ở rìa, cách gốc bảy tám đời.</li>
 *   <li><b>Hồ sơ của chính tài khoản</b> ({@code app_user.person_id}) là gốc có ý nghĩa nhất với
 *       người ấy — nhưng chiều mặc định của endpoint là {@code DESCENDANTS}, nên một thành viên ba
 *       mươi tuổi chưa có con sẽ mở trang phả đồ ra và thấy <b>đúng một node: chính mình</b>. Đó là
 *       màn hình đầu tiên tệ nhất có thể dựng được, và nó tệ với đúng nhóm người dùng đông nhất.
 *       Còn Khách thì không có hồ sơ nào để lấy.</li>
 *   <li><b>Gốc của chi</b> là phương án ở giữa: cây đủ nhỏ để đọc được, và người dùng chắc chắn
 *       nhìn thấy mình trong đó.</li>
 * </ul>
 *
 * <h2>Luật đã chọn: <i>gốc của phạm vi mà người gọi chịu trách nhiệm</i></h2>
 * Một câu duy nhất, ra bốn kết quả khác nhau vì bốn vai có bốn phạm vi khác nhau:
 * <table>
 *   <caption>Gốc mặc định theo vai</caption>
 *   <tr><th>Vai</th><th>Phạm vi</th><th>Gốc mặc định</th></tr>
 *   <tr><td>{@code ADMIN} / {@code COUNCIL}</td><td>cả dòng họ</td>
 *       <td><b>Thuỷ tổ</b> — đời nhỏ nhất trong phả</td></tr>
 *   <tr><td>{@code BRANCH_HEAD}</td><td>các chi được giao ({@code branch_assignment})</td>
 *       <td><b>Ông tổ của chi được giao</b>, lấy chi nông nhất trong {@code ltree}</td></tr>
 *   <tr><td>{@code MEMBER}</td><td>chi nhà mình ({@code person.primary_branch_id})</td>
 *       <td><b>Ông tổ chi mình</b></td></tr>
 *   <tr><td>{@code GUEST}</td><td>không có</td><td><b>Thuỷ tổ</b></td></tr>
 * </table>
 *
 * <p><b>Trưởng chi khác Thành viên ở nguồn của phạm vi, không ở công thức.</b> Một Trưởng chi được
 * giao Chi Bính nhưng bản thân sinh ra ở Chi Đinh phải mở ra Chi Bính — đó là chi họ chịu trách
 * nhiệm duyệt. Lấy chi nhà của họ là mở nhầm cây. Ngược lại, Thành viên không có
 * {@code branch_assignment} nào nên chi nhà là căn cứ duy nhất.</p>
 *
 * <p><b>Hồ sơ cá nhân vẫn được dùng — làm la bàn, không làm gốc.</b> Chuỗi
 * {@code keycloak_sub → app_user → person → primary_branch_id} chính là thứ xác định chi nhà. Nói
 * cách khác, câu trả lời <i>vẫn</i> riêng cho từng người; nó chỉ không lấy người ấy làm gốc của
 * cây. Phản hồi trả {@code rootId} đã chọn trong thân, nên giao diện biết mình đang đứng ở đâu và
 * tự làm nổi bật node của người dùng nếu muốn.</p>
 *
 * <h2>Gốc phải là người mà chính người gọi được biết là tồn tại</h2>
 * {@code TreeProjectionService} ném {@code 404} khi gốc không lọt qua bộ lọc riêng tư. Nếu ở đây
 * chọn đại một người rồi giao cho nó, thì trang phả đồ của Khách sẽ chết mỗi khi đời 1 tình cờ là
 * một người còn sống (dòng họ mới lập, hoặc dữ liệu nhập thiếu ngày mất). Vì vậy cổng trả về
 * <b>nhiều ứng viên</b> và ở đây lấy người <b>đầu tiên nhìn thấy được</b> — thứ tự do CSDL sắp đã
 * ưu tiên người đã khuất nên ca thường gặp chỉ tốn một vòng lặp.
 *
 * <h2>Không hạ quyền người gọi ở đây</h2>
 * Khác với danh bạ, phả đồ <b>được phép</b> khác nhau theo vai: đó là cây phả hệ, không phải một
 * phép đếm mà hai người buộc phải đọc ra cùng một con số. Vậy nên phép kiểm hiển thị ở đây dùng
 * đúng {@code CallerContext} thật.
 */
@Service
public class DefaultTreeRootResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultTreeRootResolver.class);

    /**
     * Số ứng viên xin về mỗi lượt.
     *
     * <p>Đủ rộng để vượt qua một cụm đời-1 còn sống bất thường, đủ hẹp để việc chọn gốc không biến
     * thành một lượt nạp hàng trăm hồ sơ.</p>
     */
    private static final int CANDIDATE_LIMIT = 16;

    private final GenealogyRosterPort roster;
    private final PersonRepository persons;
    private final PrivacyTierService privacy;

    public DefaultTreeRootResolver(GenealogyRosterPort roster, PersonRepository persons,
                                   PrivacyTierService privacy) {
        this.roster = roster;
        this.persons = persons;
        this.privacy = privacy;
    }

    /**
     * Gốc mặc định cho người gọi hiện tại.
     *
     * @return rỗng khi phả chưa có một nhân khẩu nào mà người gọi được biết là tồn tại — người gọi
     *         nên dịch thành {@code 404}, <b>không</b> phải một cây rỗng: cây rỗng nói dối rằng
     *         dòng họ không có ai
     */
    @Transactional(readOnly = true)
    public Optional<UUID> resolve(CallerContext caller) {
        for (String prefix : scopeLadder(caller)) {
            Optional<UUID> found = firstVisible(prefix, caller);
            if (found.isPresent()) {
                log.debug("Goc pha do mac dinh cho vai {} trong pham vi {} -> {}",
                        caller.role(), prefix == null ? "ca dong ho" : prefix, found.get());
                return found;
            }
        }
        log.info("Khong chon duoc goc pha do mac dinh cho vai {} - pha rong hoac khong co nhan"
                + " khau nao hien thi duoc", caller.role());
        return Optional.empty();
    }

    /**
     * <b>Thang phạm vi</b>: chi của người gọi trước, rồi nới dần lên tới cả dòng họ.
     *
     * <p>Bậc cuối luôn là {@code null} (cả dòng họ) và đó là điều kiện để trang phả đồ không bao
     * giờ chết vì dữ liệu thiếu: một chi mới lập chưa gắn nhân khẩu nào, hay một thành viên chưa
     * được ghép {@code app_user → person}, đều rơi xuống bậc cuối thay vì nhận {@code 404}.</p>
     *
     * <p>Nới lên theo <b>đường {@code ltree}</b> chứ không nhảy thẳng về gốc: một người ở
     * {@code goc.chi_binh.nganh_hai} mà ngành ấy chưa có ai thì bậc kế tiếp phải là
     * {@code goc.chi_binh}, không phải cả dòng họ.</p>
     */
    private List<String> scopeLadder(CallerContext caller) {
        Set<String> ladder = new LinkedHashSet<>();
        BranchPath start = scopeOf(caller);
        String path = start == null ? null : start.value();
        while (path != null && !path.isBlank()) {
            ladder.add(path);
            path = parentOf(path);
        }
        List<String> result = new ArrayList<>(ladder);
        result.add(null);
        return result;
    }

    /**
     * Phạm vi trách nhiệm của người gọi — chỗ duy nhất mà bốn vai rẽ nhánh.
     *
     * <p>{@code BRANCH_HEAD} có thể được giao nhiều chi; lấy chi <b>nông nhất</b> trong
     * {@code ltree} (rồi so chuỗi cho tất định) vì đó là cây bao được nhiều phạm vi của họ nhất.
     * Nếu tài khoản mang vai ấy nhưng chưa được giao chi nào thì họ đang không quản trị gì cả, nên
     * rơi về chi nhà y như một Thành viên.</p>
     */
    private BranchPath scopeOf(CallerContext caller) {
        if (caller.role().isClanWide()) {
            return null;
        }
        if (caller.role() == CallerRole.BRANCH_HEAD && !caller.managedBranches().isEmpty()) {
            return caller.managedBranches().stream()
                    .min(Comparator.comparingInt(BranchPath::depth)
                            .thenComparing(BranchPath::value))
                    .orElse(null);
        }
        return caller.homeBranch();
    }

    /** {@code goc.chi_binh.nganh_hai} → {@code goc.chi_binh} → {@code goc} → {@code null}. */
    private String parentOf(String path) {
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? null : path.substring(0, lastDot);
    }

    /**
     * Ứng viên đầu tiên mà người gọi <b>được biết là tồn tại</b>.
     *
     * <p>Dùng {@link PrivacyTierService#canSee} chứ không tự đọc {@code isAlive}/{@code isDeleted}:
     * hai luật ấy đã có một bản duy nhất và đây không phải chỗ để có bản thứ hai.</p>
     */
    private Optional<UUID> firstVisible(String branchPathPrefix, CallerContext caller) {
        List<UUID> candidates = roster.rootCandidates(branchPathPrefix, CANDIDATE_LIMIT);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Set<UUID> visible = new LinkedHashSet<>();
        for (Person person : persons.byIds(candidates)) {
            if (privacy.canSee(person, caller)) {
                visible.add(person.rawId());
            }
        }
        // Giữ nguyên thứ tự ưu tiên do CSDL sắp — byIds không hứa hẹn gì về thứ tự trả về.
        return candidates.stream().filter(visible::contains).findFirst();
    }
}
