package vn.giapha.genealogy.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.application.view.BranchRef;
import vn.giapha.genealogy.domain.Branch;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.shared.vo.BranchPath;

/**
 * <b>Cây chi/ngành</b> cho các ô chọn phạm vi và ô lọc — nguồn của {@code Query.branches}.
 *
 * <h2>Hai câu hỏi, hai luật khác hẳn nhau</h2>
 * <ol>
 *   <li><b>"Có những chi nào?"</b> — dữ liệu tham chiếu cấp dòng họ: tên có dấu, đường dẫn
 *       {@code ltree}, vùng miền. Mọi thành viên đã đăng nhập đọc được toàn bộ, kể cả chi mình
 *       không quản. Xem {@link #list} để biết vì sao đây <b>không</b> phải một lỗ hổng phạm vi.</li>
 *   <li><b>"Trong chi ấy có bao nhiêu người?"</b> — đây là <i>dữ liệu nhân khẩu</i>, và một bộ đếm
 *       cũng tiết lộ. Xem {@link #memberCounts}.</li>
 * </ol>
 */
@Service
public class BranchQueryService {

    private static final Logger log = LoggerFactory.getLogger(BranchQueryService.class);

    /**
     * Trần số nhân khẩu nạp cho một lượt đếm.
     *
     * <p>Cùng con số và cùng lý lẽ với {@code DirectoryService.MAX_ROSTER}: rộng gấp nhiều lần quy
     * mô thật của một dòng họ đang vận hành (1.509 nhân khẩu). Chạm trần nghĩa là bộ đếm bắt đầu
     * nói thiếu — đó là lúc đổi cách làm, không phải lúc nâng hằng số.</p>
     */
    static final int MAX_ROSTER = 20_000;

    private final BranchRepository branches;
    private final GenealogyRosterPort roster;
    private final PersonRepository persons;
    private final PrivacyTierService privacy;

    public BranchQueryService(BranchRepository branches, GenealogyRosterPort roster,
                              PersonRepository persons, PrivacyTierService privacy) {
        this.branches = branches;
        this.roster = roster;
        this.persons = persons;
        this.privacy = privacy;
    }

    /**
     * Danh sách chi/ngành, sắp <b>ổn định theo {@code path}</b>.
     *
     * <p>Thứ tự {@code ltree} chính là thứ tự cây: con luôn đứng ngay sau cha, nên client chỉ cần
     * đếm dấu chấm để thụt đầu dòng, không phải dựng lại cây. Hai lượt gọi cùng tham số luôn cho
     * cùng một thứ tự.</p>
     *
     * <h2>Vì sao KHÔNG siết theo phạm vi của người gọi</h2>
     * Ở hệ thống này chưa từng có luật "đọc <i>siêu dữ liệu</i> chi phải trong phạm vi", và thêm
     * một luật như thế ở đây sẽ là luật thứ ba chứ không phải luật thứ nhất:
     * {@code BranchScopeGuard} và {@code GenealogyAccessGuard} đều chỉ nói về quyền <b>ghi</b> /
     * <b>duyệt</b> / <b>toàn dòng họ</b>, còn mọi lối đọc đang chạy (facet chi của
     * {@code GET /directory}, {@code branchId} của {@code /persons/search}, {@code GET
     * /import/branches}) đều cho mọi thành viên thấy đủ mọi chi. Tên chi và đường dẫn {@code ltree}
     * là thứ in trong gia phả giấy, không phải dữ liệu cá nhân.
     *
     * <p>Hệ quả cụ thể, và nó là <b>yêu cầu</b> chứ không phải tác dụng phụ: màn tạo việc họ tự
     * bớt các chi ngoài phạm vi bằng {@code me.managedBranches}, còn ba ô lọc kia (tìm kiếm, sự
     * kiện, vinh danh) cần đủ danh sách để lọc. Trả về danh sách đã bị cắt theo vai sẽ làm ba ô ấy
     * mất lựa chọn mà không báo lỗi gì.</p>
     *
     * <p>Thứ <b>có</b> bị siết là {@link #memberCounts}: một Trưởng chi hỏi cây con của chi khác
     * vẫn nhận đủ tên chi, nhưng con số đi kèm là con số của <i>họ</i>, không phải của Hội đồng.</p>
     *
     * @param rootPath tiền tố {@code ltree} để cắt một cây con; {@code null}/rỗng là cả dòng họ.
     *                 Chuỗi không phải {@code ltree} hợp lệ trả về danh sách rỗng (xem bên dưới)
     */
    @Transactional(readOnly = true)
    public List<BranchRef> list(String rootPath) {
        List<Branch> found;
        if (rootPath == null || rootPath.isBlank()) {
            found = branches.all();
        } else {
            BranchPath prefix = parse(rootPath);
            // Chuỗi không hợp lệ ltree: KHÔNG ném lỗi. Cổng GraphQL chưa có bộ ánh xạ lỗi nên một
            // ngoại lệ ở đây tới client dưới dạng INTERNAL_ERROR trần trụi, tệ hơn hẳn một danh
            // sách rỗng cộng một dòng log. "Không có chi nào khớp" cũng chính là sự thật.
            found = prefix == null ? List.of() : branches.subtreeOf(prefix);
        }
        List<BranchRef> result = found.stream()
                .filter(branch -> !branch.isDeleted())
                .map(BranchRef::of)
                .sorted(Comparator.comparing(BranchRef::path,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        log.debug("Cay chi/nganh duoi {}: {} chi", rootPath == null ? "(ca dong ho)" : rootPath,
                result.size());
        return result;
    }

    /**
     * Số nhân khẩu của từng chi <b>và các nhánh con</b>, đếm <b>sau</b> bộ lọc riêng tư.
     *
     * <h2>Một bộ đếm cũng tiết lộ</h2>
     * Đếm trước bộ lọc rồi mới trả ra là nói cho người gọi biết trong chi ấy có bao nhiêu người mà
     * họ <i>không</i> được thấy — đúng thứ bộ lọc sinh ra để giấu, và nó rò rỉ mà không để lại dấu
     * vết nào vì phản hồi trông hoàn toàn bình thường. Vì thế thứ tự ở đây là bắt buộc:
     * <b>nạp khoá → nạp hồ sơ → hỏi {@code canSee} → mới cộng</b>.
     *
     * <h2>Luật nằm ở đâu — và vì sao không có bản thứ ba</h2>
     * Phép hỏi "người gọi có được biết nhân khẩu này tồn tại không" là
     * {@code PrivacyTierService.canSee}, đúng một bản, đúng cái mà {@code DirectoryService} (mẫu
     * số {@code coverage}) và {@code PersonSearchService} ({@code totalElements}) đang dùng để
     * đếm. Lớp này gọi lại chính hàm ấy trên chính {@code Person} aggregate — không có mệnh đề
     * {@code WHERE} nào ở {@link GenealogyRosterPort} chép lại một nửa của nó.
     *
     * <h2>Giá phải trả, nói thẳng</h2>
     * Vì luật chạy trên aggregate chứ không trên SQL, một lượt đếm nạp <b>toàn bộ</b> nhân khẩu
     * trong phạm vi (1.509 người + 2.504 dòng tên ở dòng họ đang vận hành). Đây là cùng một đánh
     * đổi mà {@code DirectoryService} đã chọn và đã được xác nhận là đúng ở quy mô một dòng họ.
     * Khi nó không còn đúng, cách sửa là đưa {@code canSee} xuống thành cột suy dẫn có chỉ mục —
     * <b>không</b> phải lặng lẽ đổi sang đếm trước bộ lọc.
     *
     * <h2>"Và các nhánh con"</h2>
     * Một người thuộc {@code goc.chi_giap.nganh_a} được cộng vào cả ba bộ đếm {@code goc},
     * {@code goc.chi_giap} và {@code goc.chi_giap.nganh_a} — đúng ngữ nghĩa {@code <@} của
     * {@code ltree} mà cả hệ thống đang dùng, và đúng chữ trong schema.
     *
     * @param wanted các chi cần đếm; chi không có ai nhìn thấy được trả về {@code 0}, không phải
     *               vắng mặt — {@code memberCount} là {@code Int!}
     */
    @Transactional(readOnly = true)
    public Map<UUID, Integer> memberCounts(Collection<BranchRef> wanted) {
        if (wanted == null || wanted.isEmpty()) {
            return Map.of();
        }
        List<BranchRef> targets = wanted.stream().filter(ref -> ref != null && ref.path() != null).toList();
        if (targets.isEmpty()) {
            return Map.of();
        }

        List<GenealogyRosterPort.PersonBranchRef> refs =
                roster.personBranchRefs(commonPrefix(targets), MAX_ROSTER);
        if (refs.size() >= MAX_ROSTER) {
            log.warn("Bo dem chi/nganh cham tran {} nhan khau: memberCount se thap hon su that."
                    + " Day la luc doi cach lam, khong phai luc nang hang so", MAX_ROSTER);
        }

        // Path của MỌI chi, không chỉ chi được hỏi: một người có thể nằm ở nhánh con sâu hơn danh
        // sách được hỏi, và người ấy vẫn phải được cộng cho chi cha.
        Map<UUID, BranchPath> pathOfBranch = branches.pathsOf(refs.stream()
                .map(GenealogyRosterPort.PersonBranchRef::branchId).collect(Collectors.toSet()));

        CallerContext caller = privacy.caller();
        Map<UUID, UUID> branchOfPerson = new LinkedHashMap<>();
        for (GenealogyRosterPort.PersonBranchRef ref : refs) {
            branchOfPerson.putIfAbsent(ref.personId(), ref.branchId());
        }

        // Đếm theo TIỀN TỐ path: mỗi người cộng một lần cho từng tổ tiên của chi mình. O(người ×
        // độ sâu) và không cần so từng cặp (người × chi).
        Map<String, Integer> byPathPrefix = new HashMap<>();
        int visible = 0;
        for (Person person : persons.byIds(branchOfPerson.keySet())) {
            if (!privacy.canSee(person, caller)) {
                continue;
            }
            BranchPath path = pathOfBranch.get(branchOfPerson.get(person.rawId()));
            if (path == null) {
                continue;
            }
            visible++;
            for (String prefix : tienToCua(path)) {
                byPathPrefix.merge(prefix, 1, Integer::sum);
            }
        }

        Map<UUID, Integer> counts = new LinkedHashMap<>();
        for (BranchRef ref : targets) {
            counts.put(ref.id(), byPathPrefix.getOrDefault(ref.path(), 0));
        }
        log.debug("memberCount cho {} chi: {}/{} nhan khau qua duoc bo loc (vai {})",
                targets.size(), visible, branchOfPerson.size(), caller.role());
        return counts;
    }

    // =========================================================================================
    // Nội bộ
    // =========================================================================================

    /**
     * {@code "goc.chi_giap.nganh_a"} → {@code ["goc", "goc.chi_giap", "goc.chi_giap.nganh_a"]}.
     *
     * <p>Cắt chuỗi chứ không truy vấn cha: bảng {@code branch} có {@code parent_id} nhưng path
     * mới là nguồn chân lý của quan hệ bao hàm (mọi phép kiểm phạm vi trong hệ đều so path), nên
     * đi theo path thì bộ đếm không bao giờ lệch với phép kiểm quyền.</p>
     */
    private static List<String> tienToCua(BranchPath path) {
        String value = path.value();
        List<String> prefixes = new ArrayList<>();
        int from = 0;
        while (true) {
            int dot = value.indexOf('.', from);
            if (dot < 0) {
                prefixes.add(value);
                return prefixes;
            }
            prefixes.add(value.substring(0, dot));
            from = dot + 1;
        }
    }

    /**
     * Tiền tố {@code ltree} hẹp nhất còn bao đủ mọi chi được hỏi, hoặc {@code null} nếu không có.
     *
     * <p>Thuần tuý để câu SQL dùng được chỉ mục GiST thay vì quét cả bảng {@code person}. Hai ca
     * thường gặp đều khớp: {@code branches(rootPath:)} trả về một cây con (gốc của nó bao mọi chi
     * còn lại), và {@code Person.branch} chỉ có đúng một chi. Không tìm được thì rơi về cả dòng
     * họ — chậm hơn, vẫn đúng.</p>
     */
    private static String commonPrefix(List<BranchRef> targets) {
        Set<String> paths = new LinkedHashSet<>();
        for (BranchRef ref : targets) {
            paths.add(ref.path());
        }
        String best = null;
        for (String candidate : paths) {
            BranchPath prefix = parse(candidate);
            if (prefix == null) {
                continue;
            }
            boolean baoHet = paths.stream().allMatch(other -> {
                BranchPath otherPath = parse(other);
                return otherPath != null && prefix.isAncestorOf(otherPath);
            });
            if (baoHet && (best == null || candidate.length() < best.length())) {
                best = candidate;
            }
        }
        return best;
    }

    /** {@code null} thay vì ngoại lệ: chuỗi lạ từ client không được thành {@code 500}. */
    private static BranchPath parse(String value) {
        try {
            return BranchPath.of(value);
        } catch (IllegalArgumentException ex) {
            log.warn("rootPath khong phai ltree hop le, coi nhu khong co chi nao khop: {}", value);
            return null;
        }
    }
}
