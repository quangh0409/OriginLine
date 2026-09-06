package vn.giapha.genealogy.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.application.command.PersonSearchQuery;
import vn.giapha.genealogy.application.view.PageView;
import vn.giapha.genealogy.application.view.PersonSummaryView;
import vn.giapha.genealogy.domain.Branch;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.genealogy.domain.port.PersonSearchPort;
import vn.giapha.shared.exception.ForbiddenException;

/**
 * Tìm kiếm nhân khẩu theo tên, <b>có dấu hoặc không dấu</b> (FR-4.4).
 *
 * <h2>Thứ tự bắt buộc: lấy rộng, lọc, rồi mới cắt trang</h2>
 * Cổng tìm kiếm trả id thô chưa qua bộ lọc riêng tư, nên nếu cắt trang trước khi lọc thì một trang
 * 20 dòng có thể chỉ còn 3 dòng sau khi lọc - và người dùng thấy các trang dài ngắn thất thường mà
 * không hiểu vì sao. Vì thế: hỏi CSDL rộng hơn trang cần, lọc, rồi mới cắt.
 *
 * <p>Hệ quả phải chấp nhận: {@code totalElements} là <b>con số đã lọc trong phạm vi lấy rộng</b>,
 * không phải tổng tuyệt đối. Đây là đánh đổi có chủ ý - trả tổng tuyệt đối thì chỉ cần so hai con
 * số là biết dòng họ đang giấu bao nhiêu người còn sống, đúng thứ mà contract nói rõ là không được
 * để rò rỉ.</p>
 *
 * <p>Công cụ phía dưới là Postgres FTS cộng {@code unaccent} và {@code pg_trgm} - không phải
 * Elasticsearch, và Redis ở dự án này chỉ là cache chứ không phải máy tìm kiếm.</p>
 */
@Service
public class PersonSearchService {

    private static final Logger log = LoggerFactory.getLogger(PersonSearchService.class);

    /** Hệ số lấy rộng: xin nhiều hơn trang cần bấy nhiêu lần để bù phần bị bộ lọc bỏ đi. */
    private static final int OVER_FETCH_FACTOR = 3;

    /** Trần tuyệt đối cho một lượt lấy rộng - chặn truy vấn quét cả bảng. */
    private static final int MAX_FETCH = 1000;

    private final PersonSearchPort searchPort;
    private final PersonRepository persons;
    private final BranchRepository branches;
    private final PrivacyTierService privacy;

    public PersonSearchService(PersonSearchPort searchPort, PersonRepository persons,
                               BranchRepository branches, PrivacyTierService privacy) {
        this.searchPort = searchPort;
        this.persons = persons;
        this.branches = branches;
        this.privacy = privacy;
    }

    @Transactional(readOnly = true)
    public PageView<PersonSummaryView> search(PersonSearchQuery query) {
        CallerContext caller = privacy.caller();
        if (query.includeDeleted() && !caller.role().isClanWide()) {
            throw new ForbiddenException(GenealogyProblemCodes.FORBIDDEN,
                    "Chi Hoi dong Toc bieu hoac Quan tri he thong duoc tim trong ban ghi da xoa mem");
        }

        PersonSearchPort.SearchFilter filter = new PersonSearchPort.SearchFilter(
                query.generation(), branchPrefix(query.branchId()), query.nativePlace(),
                query.alive(), query.includeDeleted());
        int fetchLimit = Math.min(MAX_FETCH, (query.page() + 1) * query.size() * OVER_FETCH_FACTOR + 20);

        List<PersonSearchPort.Hit> hits = searchPort.search(query.q(), filter, fetchLimit);
        if (hits.isEmpty()) {
            return PageView.of(List.of(), query.page(), query.size(), 0, query.sort());
        }

        Map<UUID, PersonSearchPort.Hit> hitById = new LinkedHashMap<>();
        for (PersonSearchPort.Hit hit : hits) {
            hitById.putIfAbsent(hit.personId(), hit);
        }
        Map<UUID, Person> loaded = new LinkedHashMap<>();
        for (Person person : persons.byIds(hitById.keySet())) {
            loaded.put(person.rawId(), person);
        }

        BranchDirectory dir = BranchDirectory.load(branches, loaded.values().stream()
                .map(Person::primaryBranchId).toList());

        // Lọc trước khi đếm: con số tổng cũng không được phản ánh dữ liệu người gọi không thấy.
        List<PersonSummaryView> visible = new ArrayList<>();
        for (UUID id : hitById.keySet()) {
            Person person = loaded.get(id);
            if (person == null || !privacy.canSee(person, caller)) {
                continue;
            }
            visible.add(privacy.toSummary(person, caller, dir)
                    .withMatchedNameType(hitById.get(id).matchedNameType()));
        }
        sort(visible, query.sort());

        int from = Math.min(query.page() * query.size(), visible.size());
        int to = Math.min(from + query.size(), visible.size());
        log.debug("Tim kiem tra {} ket qua tho, con {} sau khi loc rieng tu", hits.size(), visible.size());
        return PageView.of(visible.subList(from, to), query.page(), query.size(), visible.size(),
                query.sort());
    }

    private String branchPrefix(UUID branchId) {
        if (branchId == null) {
            return null;
        }
        return branches.byId(branchId).map(Branch::path)
                .map(path -> path == null ? null : path.value()).orElse(null);
    }

    /**
     * Sắp xếp <b>sau</b> khi lọc.
     *
     * <p>Mặc định là {@code relevance} và cổng tìm kiếm đã trả đúng thứ tự đó, nên trường hợp
     * thường gặp nhất không tốn thêm phép so sánh nào.</p>
     */
    private void sort(List<PersonSummaryView> items, String sort) {
        if (sort == null || sort.isBlank()) {
            return;
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim().toLowerCase(Locale.ROOT);
        boolean descending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());

        Comparator<PersonSummaryView> comparator = switch (field) {
            case "generation" -> Comparator.comparing(PersonSummaryView::generation,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "birthyear" -> Comparator.comparing(PersonSummaryView::birthYear,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "name" -> Comparator.comparing(PersonSummaryView::displayName,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> null;
        };
        if (comparator == null) {
            return;
        }
        items.sort(descending ? comparator.reversed() : comparator);
    }
}
