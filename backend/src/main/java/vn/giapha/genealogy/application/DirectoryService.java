package vn.giapha.genealogy.application;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.application.command.DirectoryQuery;
import vn.giapha.genealogy.application.view.BranchRef;
import vn.giapha.genealogy.application.view.DirectoryEntryView;
import vn.giapha.genealogy.application.view.DirectoryPageView;
import vn.giapha.genealogy.application.view.DirectoryPageView.DirectoryBranchFacetView;
import vn.giapha.genealogy.application.view.DirectoryPageView.DirectoryCoverageView;
import vn.giapha.genealogy.application.view.DirectoryPageView.DirectoryFacetValueView;
import vn.giapha.genealogy.application.view.DirectoryPageView.DirectoryFacetsView;
import vn.giapha.genealogy.application.view.PageView;
import vn.giapha.genealogy.domain.Branch;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PrivacyFieldGroup;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.shared.vo.BranchPath;

/**
 * <b>Danh bạ dòng họ</b> — màn hình duy nhất trong hệ thống lấy <i>người còn sống</i> làm trung tâm.
 *
 * <h2>Vì sao không phải là một bộ lọc của {@code /persons/search}</h2>
 * Điều kiện lọt vào hai danh sách khác hẳn nhau. Tìm kiếm trả về mọi người mà người gọi
 * <i>được biết là tồn tại</i> — gồm cả người đã khuất và cả người còn sống chưa mở bất kỳ nhóm
 * trường nào. Danh bạ chỉ trả về người còn sống <b>đã tự mở</b> ít nhất một nhóm trường
 * <b>cho đúng người gọi này</b>. Nhét hai ngữ nghĩa vào một endpoint là cách chắc chắn nhất để một
 * ngày nào đó tìm kiếm rò ra một người chưa hề đồng ý. Thêm nữa, danh bạ cần {@code coverage} và
 * {@code facets}, hai khối không phải siêu dữ liệu phân trang và không có nghĩa gì ở tìm kiếm.
 *
 * <h2>Năm ràng buộc, cái nào sai cũng hỏng</h2>
 * <ol>
 *   <li><b>Chỉ người còn sống.</b> Người đã khuất là dữ liệu công khai và đã có cả cổng thông tin
 *       riêng; kéo họ vào đây thì danh bạ biến thành bản sao của tìm kiếm.</li>
 *   <li><b>Chỉ người đã tự mở.</b> Điều kiện lọt danh sách là
 *       {@code PersonVisibility.allows()} trả {@code true} cho <b>ít nhất một</b> trong năm nhóm
 *       trường. Luật nằm nguyên ở {@code PrivacyTierService}; lớp này không có một dòng nào quyết
 *       định "trường nào hiện ra".</li>
 *   <li><b>{@code coverage} do máy chủ đếm.</b> Xem {@link DirectoryPageView.DirectoryCoverageView}.</li>
 *   <li><b>Khách nhận {@code 401}</b>, không phải danh sách rỗng — xem {@link #list}.</li>
 *   <li><b>Không áp đặc quyền Hội đồng/Admin</b> — xem {@link DirectoryAudience}.</li>
 * </ol>
 *
 * <h2>Lọc trong tiến trình, không lọc bằng {@code WHERE}</h2>
 * V8 §8.4 chốt là <b>không</b> đánh chỉ mục trên {@code person.privacy_consent} và không truy vấn
 * nào lọc theo nó: "tìm người đã mở liên hệ cho cả họ" là đúng loại truy vấn hệ thống này không nên
 * làm cho dễ. Vì vậy danh bạ nạp toàn bộ người còn sống rồi lọc bằng {@code PersonVisibility}. Với
 * quy mô một dòng họ (hàng trăm tới vài nghìn người còn sống) đây là phép đánh đổi đúng, và nó còn
 * là điều kiện để {@code coverage} và {@code facets} tính được — cả hai đều cần <b>tập chưa lọc bộ
 * lọc người dùng</b> nhưng <b>đã lọc riêng tư</b>, thứ mà không câu SQL nào dựng nổi.
 *
 * <p>Nếu quy mô vượt {@link #MAX_ROSTER}, phản hồi vẫn đúng nhưng thiếu người ở cuối danh sách và
 * {@code coverage} sẽ thấp hơn sự thật. Đó là thời điểm phải đổi cách làm (đưa đồng thuận thành cột
 * suy dẫn có chỉ mục), không phải thời điểm nâng hằng số.</p>
 */
@Service
public class DirectoryService {

    private static final Logger log = LoggerFactory.getLogger(DirectoryService.class);

    /**
     * Trần số người còn sống nạp trong một lượt.
     *
     * <p>Rộng gấp nhiều lần quy mô thật của một dòng họ đang vận hành (1.509 nhân khẩu, khoảng 600
     * người còn sống). Chạm trần là tín hiệu kiến trúc, không phải tham số để chỉnh.</p>
     */
    static final int MAX_ROSTER = 20_000;

    /**
     * So tên theo <b>quy tắc tiếng Việt</b>, không so mã Unicode.
     *
     * <p>{@code "Đức"} phải đứng sau {@code "Dũng"} và trước {@code "Em"}; so bằng
     * {@code String.compareTo} thì mọi chữ có dấu bị đẩy xuống cuối bảng và danh bạ trông như chưa
     * được sắp xếp. Đây thuần tuý là <i>thứ tự trình bày</i> — khác hẳn phép <i>so khớp</i> không
     * dấu, thứ bắt buộc phải chạy bằng {@code vn_unaccent} trong CSDL (xem
     * {@code GenealogyRosterPort.livingPersonIdsMatchingName}).</p>
     */
    private static final Collator VI_COLLATOR = collatorTiengViet();

    private final GenealogyRosterPort roster;
    private final PersonRepository persons;
    private final BranchRepository branches;
    private final PrivacyTierService privacy;

    public DirectoryService(GenealogyRosterPort roster, PersonRepository persons,
                            BranchRepository branches, PrivacyTierService privacy) {
        this.roster = roster;
        this.persons = persons;
        this.branches = branches;
        this.privacy = privacy;
    }

    /**
     * Một trang danh bạ cho người gọi hiện tại.
     *
     * <h2>Khách: {@code 401}, không phải danh sách rỗng</h2>
     * Người còn sống không tồn tại đối với Khách chưa đăng nhập. Trả về một danh sách rỗng kèm
     * {@code coverage} bằng 0 sẽ là một lời nói dối <b>có hình dạng của dữ liệu thật</b> — và tệ
     * hơn, nó bảo người dùng rằng dòng họ không có ai còn sống. Đây là ranh giới pháp lý (Nghị định
     * 13/2023), không phải một tuỳ chọn giao diện.
     *
     * <p>Khác với {@code GET /persons/&#123;id&#125;}: ở đó {@code 404} là bắt buộc vì câu hỏi là
     * về <i>một</i> người cụ thể và bản thân sự tồn tại của người ấy là bí mật. Ở đây câu hỏi không
     * nhắc tới ai cả, nên không có gì để giấu ngoài chính dữ liệu.</p>
     *
     * <p>Chuỗi lọc của Spring Security đã chặn Khách từ trước; phép kiểm ở đây là lớp thứ hai, để
     * lối vào nào khác (GraphQL, một job nội bộ) cũng không thể vô tình gọi lọt.</p>
     */
    @Transactional(readOnly = true)
    public DirectoryPageView list(DirectoryQuery query) {
        CallerContext caller = privacy.caller();
        if (caller.isGuest()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Danh ba dong ho chi danh cho thanh vien da dang nhap");
        }

        // Người xem danh bạ = người gọi ĐÃ BỎ đặc quyền toàn dòng họ và đặc quyền chính chủ.
        CallerContext audience = DirectoryAudience.of(caller);

        List<UUID> livingIds = roster.livingPersonIds(MAX_ROSTER);
        if (livingIds.size() >= MAX_ROSTER) {
            log.warn("Danh ba cham tran {} nguoi con song: coverage va facets se thap hon su that."
                    + " Day la luc doi cach lam, khong phai luc nang hang so", MAX_ROSTER);
        }
        List<Person> living = persons.byIds(livingIds);
        BranchDirectory dir = BranchDirectory.load(branches, living.stream()
                .map(Person::primaryBranchId).toList());

        // Tập đã lọc riêng tư nhưng CHƯA áp bộ lọc người dùng — nguồn của cả coverage lẫn facets.
        List<DirectoryEntryView> shared = new ArrayList<>();
        long livingCount = 0;
        for (Person person : living) {
            // Mẫu số hỏi "người gọi có được biết người này TỒN TẠI không", tử số hỏi "chủ thể có
            // mở gì cho họ không". Hai câu hỏi khác nhau, và cả hai đều hỏi PrivacyTierService.
            if (!privacy.canSee(person, audience)) {
                continue;
            }
            livingCount++;
            entryOf(person, audience, dir).ifPresent(shared::add);
        }
        sort(shared, query.sort());

        List<DirectoryEntryView> matched = applyFilters(shared, query);
        int from = Math.min(query.page() * query.size(), matched.size());
        int to = Math.min(from + query.size(), matched.size());

        log.debug("Danh ba: {} nguoi con song -> {} da mo -> {} sau bo loc (vai that {})",
                livingCount, shared.size(), matched.size(), caller.role());

        return new DirectoryPageView(
                PageView.of(List.copyOf(matched.subList(from, to)), query.page(), query.size(),
                        matched.size(), query.sort()),
                new DirectoryCoverageView(shared.size(), livingCount),
                facetsOf(shared));
    }

    // =========================================================================================
    // Điều kiện lọt danh sách
    // =========================================================================================

    /**
     * Một dòng danh bạ, hoặc {@code Optional.empty()} nếu người này không lọt.
     *
     * <p>Hai cửa, theo đúng thứ tự:</p>
     * <ol>
     *   <li>{@code PrivacyTierService.visibility()} rỗng — người gọi không được biết bản ghi tồn
     *       tại. Không thể xảy ra ở đây (người xem luôn là thành viên đã đăng nhập và danh sách đã
     *       loại bản ghi xoá mềm) nhưng vẫn phải mở {@code Optional} ra, và đó chính là điều mà
     *       kiểu dữ liệu ấy sinh ra để ép.</li>
     *   <li><b>Chưa mở nhóm nào</b> cho người gọi này ⇒ không lọt. Đây là toàn bộ định nghĩa
     *       "đã tự chọn cho người khác xem", và nó được hỏi bằng chính {@code allows()} chứ không
     *       bằng một phép đọc {@code privacyConsent} nào ở đây.</li>
     * </ol>
     *
     * <p><b>Người vị thành niên không bao giờ lọt</b> — không phải nhờ một luật riêng của danh bạ,
     * mà vì {@code PersonVisibility.allows()} trả {@code false} cho mọi nhóm với họ. Đúng một bản
     * luật, và danh bạ hưởng nó miễn phí.</p>
     */
    private Optional<DirectoryEntryView> entryOf(Person person, CallerContext audience,
                                                 BranchDirectory dir) {
        BranchPath path = dir.pathOf(person.primaryBranchId());
        Optional<PersonVisibility> maybe = privacy.visibility(person, audience, path);
        if (maybe.isEmpty()) {
            return Optional.empty();
        }
        PersonVisibility vis = maybe.get();
        if (!daMoItNhatMotNhom(vis)) {
            return Optional.empty();
        }
        return Optional.of(new DirectoryEntryView(
                person.rawId(),
                person.displayName(),
                person.generation(),
                dir.refOf(person.primaryBranchId()),
                vis.allows(PrivacyFieldGroup.OCCUPATION) ? person.occupation() : null,
                vis.allows(PrivacyFieldGroup.RESIDENCE_PROVINCE) ? person.currentPlaceProvince() : null,
                vis.allows(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO) ? person.avatarKey() : null));
    }

    /**
     * Duyệt <b>toàn bộ</b> {@code PrivacyFieldGroup.values()}, không phải một danh sách chép tay.
     *
     * <p>Nhờ vậy một nhóm trường được thêm về sau tự động tính vào điều kiện lọt danh bạ mà không
     * cần ai nhớ sửa file này — và nếu nó <i>không</i> nên tính thì trình biên dịch cũng không giúp
     * được, nên hãy đọc javadoc của {@code PrivacyFieldGroup} trước khi thêm nhóm mới.</p>
     */
    private boolean daMoItNhatMotNhom(PersonVisibility vis) {
        for (PrivacyFieldGroup group : PrivacyFieldGroup.values()) {
            if (vis.allows(group)) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================================
    // Lọc · sắp xếp · facet
    // =========================================================================================

    /**
     * Bốn bộ lọc, áp <b>sau</b> bộ lọc riêng tư.
     *
     * <p>Thứ tự ấy là bắt buộc: lọc theo tỉnh trước rồi mới lọc riêng tư sẽ cho phép suy ra tỉnh
     * của một người chưa mở nhóm {@code residenceProvince} chỉ bằng cách so số dòng giữa hai lượt
     * gọi. Ở đây bộ lọc chỉ nhìn thấy đúng thứ đã hiện ra trên màn hình.</p>
     *
     * <p>{@code q} được trả lời bằng CSDL ({@code vn_unaccent} + chỉ mục trigram trên mọi lớp tên)
     * rồi <b>giao</b> với tập đã lọc riêng tư, chứ không so chuỗi trong Java: xem
     * {@code GenealogyRosterPort.livingPersonIdsMatchingName}.</p>
     */
    private List<DirectoryEntryView> applyFilters(List<DirectoryEntryView> shared,
                                                  DirectoryQuery query) {
        if (query.unfiltered()) {
            return shared;
        }
        Set<UUID> byName = null;
        if (query.q() != null) {
            byName = new LinkedHashSet<>(roster.livingPersonIdsMatchingName(query.q(), MAX_ROSTER));
        }
        BranchPath branchPrefix = query.branchId() == null ? null
                : branches.byId(query.branchId()).map(Branch::path).orElse(null);

        List<DirectoryEntryView> result = new ArrayList<>();
        for (DirectoryEntryView entry : shared) {
            if (byName != null && !byName.contains(entry.personId())) {
                continue;
            }
            if (query.province() != null && !query.province().equals(entry.currentPlaceProvince())) {
                continue;
            }
            if (query.occupation() != null && !query.occupation().equals(entry.occupation())) {
                continue;
            }
            if (branchPrefix != null && !inBranch(entry, branchPrefix)) {
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    /** Lọc theo chi lấy <b>cả cây con</b>, đúng ngữ nghĩa {@code ltree} mà cả hệ thống đang dùng. */
    private boolean inBranch(DirectoryEntryView entry, BranchPath prefix) {
        BranchRef ref = entry.primaryBranch();
        return ref != null && ref.path() != null && prefix.isAncestorOf(BranchPath.of(ref.path()));
    }

    /**
     * Sắp xếp danh bạ. Chỉ hai từ vựng, và cả hai đều có khoá phụ để thứ tự <b>tất định</b>: hai
     * lượt gọi cùng tham số phải cho cùng một trang, nếu không phân trang sẽ lặp hoặc bỏ sót người.
     */
    private void sort(List<DirectoryEntryView> items, String sort) {
        Comparator<DirectoryEntryView> theoTen = Comparator
                .comparing(DirectoryEntryView::displayName,
                        Comparator.nullsLast(VI_COLLATOR::compare))
                .thenComparing(entry -> entry.personId().toString());
        Comparator<DirectoryEntryView> comparator = DirectoryQuery.SORT_GENERATION.equals(sort)
                ? Comparator.comparing(DirectoryEntryView::generation,
                        Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(theoTen)
                : theoTen;
        items.sort(comparator);
    }

    /**
     * Ba trục facet, đếm trên tập <b>đã lọc riêng tư nhưng chưa áp bộ lọc người dùng</b>.
     *
     * <p>Vì đếm trên tập đã lọc, một tỉnh chỉ xuất hiện khi có ít nhất một người đã mở nhóm
     * {@code residenceProvince} cho chính người gọi. Đếm trên tập thô sẽ biến khối facet thành kênh
     * rò rỉ: dòng "Hà Nội (37)" bên cạnh một danh sách 4 người là đã nói ra 33 người bị ẩn.</p>
     *
     * <p>Xếp theo số lượng giảm dần rồi tên tăng dần — người dùng tìm tỉnh của mình bằng cách rà
     * mắt, nên nhóm đông phải nằm trên.</p>
     */
    private DirectoryFacetsView facetsOf(List<DirectoryEntryView> shared) {
        if (shared.isEmpty()) {
            return DirectoryFacetsView.empty();
        }
        Map<String, Long> tinh = new LinkedHashMap<>();
        Map<String, Long> nghe = new LinkedHashMap<>();
        Map<UUID, Long> chi = new LinkedHashMap<>();
        Map<UUID, BranchRef> chiRef = new LinkedHashMap<>();
        for (DirectoryEntryView entry : shared) {
            if (entry.currentPlaceProvince() != null) {
                tinh.merge(entry.currentPlaceProvince(), 1L, Long::sum);
            }
            if (entry.occupation() != null) {
                nghe.merge(entry.occupation(), 1L, Long::sum);
            }
            BranchRef ref = entry.primaryBranch();
            if (ref != null) {
                chi.merge(ref.id(), 1L, Long::sum);
                chiRef.putIfAbsent(ref.id(), ref);
            }
        }
        return new DirectoryFacetsView(
                xepFacet(tinh),
                xepFacet(nghe),
                chi.entrySet().stream()
                        .map(e -> new DirectoryBranchFacetView(e.getKey(),
                                chiRef.get(e.getKey()).name(), chiRef.get(e.getKey()).path(),
                                e.getValue()))
                        // Chi xếp theo ltree: giao diện thụt đầu dòng theo cây, nên thứ tự cây
                        // quan trọng hơn số lượng.
                        .sorted(Comparator.comparing(DirectoryBranchFacetView::path,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList());
    }

    private List<DirectoryFacetValueView> xepFacet(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(e -> new DirectoryFacetValueView(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(DirectoryFacetValueView::count).reversed()
                        .thenComparing(DirectoryFacetValueView::value, VI_COLLATOR::compare))
                .toList();
    }

    /**
     * {@code Locale.forLanguageTag("vi")} chứ không phải {@code new Locale("vi")}: bản dựng JDK nào
     * không có dữ liệu sắp xếp tiếng Việt sẽ rơi về {@code Collator} gốc, vẫn đúng hơn hẳn so mã
     * Unicode và không bao giờ ném lỗi lúc nạp lớp.
     */
    private static Collator collatorTiengViet() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi"));
        // PRIMARY se coi "Duc" va "Đức" la mot khi SAP XEP, lam thu tu khong tat dinh giua hai
        // nguoi trung ten khong dau. TERTIARY giu du khac biet dau va hoa/thuong.
        collator.setStrength(Collator.TERTIARY);
        return collator;
    }
}
