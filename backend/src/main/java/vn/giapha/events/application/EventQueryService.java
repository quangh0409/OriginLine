package vn.giapha.events.application;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.events.application.command.EventQuery;
import vn.giapha.events.application.view.EventPageView;
import vn.giapha.events.application.view.EventView;
import vn.giapha.events.application.view.PageMetaView;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventOccurrence;
import vn.giapha.events.domain.EventSubject;
import vn.giapha.events.domain.port.EventRepository;
import vn.giapha.events.domain.port.EventSubjectPort;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.security.CurrentUserProvider;

/**
 * Danh sách giỗ/lễ — {@code GET /api/v1/events} (FR-2.3).
 *
 * <h2>Vì sao lọc và xếp trang trong bộ nhớ</h2>
 * Bộ lọc chính của endpoint này là "ngày dương của lần xảy ra sắp tới", mà giá trị ấy <b>không có
 * trong bảng</b>: nó là kết quả quy đổi âm→dương, đổi theo từng năm, và không được phép lưu (lưu là
 * bắt đầu lệch). Không thể đẩy xuống mệnh đề {@code WHERE}. Bảng {@code event} ở quy mô một dòng họ
 * là hàng trăm tới hàng nghìn dòng, không phải hàng triệu — đây là đánh đổi đúng cho Giai đoạn 1.
 * Khi nào nó thành điểm nghẽn thì lời giải là một bảng chiếu (materialized occurrence) do scheduler
 * đêm dựng, chứ không phải nhét công thức âm lịch vào SQL.
 *
 * <h2>Phân tầng riêng tư</h2>
 * Sự kiện gắn với người <b>còn sống</b> (mừng thọ, cưới hỏi) bị ẩn khỏi Khách — Nghị định 13/2023
 * và BA v2 §10: khách không thấy bất kỳ người còn sống nào, và một dòng "Mừng thọ cụ X" cũng là một
 * lần lộ. Sự kiện của người đã khuất là công khai.
 */
@Service
public class EventQueryService {

    private static final Logger log = LoggerFactory.getLogger(EventQueryService.class);

    private static final int MAX_PAGE_SIZE = 100;

    private final EventRepository events;
    private final EventSubjectPort subjects;
    private final OccurrenceResolver occurrences;
    private final ReminderProperties properties;

    public EventQueryService(EventRepository events, EventSubjectPort subjects,
                             OccurrenceResolver occurrences, ReminderProperties properties) {
        this.events = events;
        this.subjects = subjects;
        this.occurrences = occurrences;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public EventPageView list(EventQuery query) {
        LocalDate today = LocalDate.now(properties.zone());
        EventQuery.Range range = query.resolveRange(today);
        boolean guest = CurrentUserProvider.current().isEmpty();

        List<Event> candidates =
                events.search(query.types(), query.personId(), query.branchId(), false);
        Map<UUID, EventSubject> subjectsById = subjects.findPersons(personIdsOf(candidates));
        Map<UUID, EventSubject.BranchSnapshot> branchesById = subjects.findBranches(branchIdsOf(candidates));

        List<EventView> matched = new ArrayList<>();
        for (Event event : candidates) {
            // Bẫy: Map.copyOf() là map bất biến của JDK và get(null) ném NullPointerException chứ
            // không trả null. Sự kiện cấp dòng họ không gắn nhân khẩu, còn sự kiện không gắn chi thì
            // targetBranchId rỗng — cả hai đều là trường hợp thường gặp.
            EventSubject subject = event.personId() == null ? null : subjectsById.get(event.personId());
            if (guest && subject != null && subject.alive()) {
                continue;
            }
            EventSubject.BranchSnapshot branch = event.targetBranchId() == null
                    ? null : branchesById.get(event.targetBranchId());
            nextOccurrence(event, subject, today, range)
                    .map(occurrence -> toView(event, subject, branch, occurrence, today))
                    .ifPresent(matched::add);
        }

        matched.sort(comparator(query.sort()));
        int safeSize = Math.clamp(query.size(), 1, MAX_PAGE_SIZE);
        int safePage = Math.max(0, query.page());
        int fromIndex = Math.min(safePage * safeSize, matched.size());
        int toIndex = Math.min(fromIndex + safeSize, matched.size());
        log.debug("GET /events: {} ung vien -> {} khop khoang {}..{}",
                candidates.size(), matched.size(), range.start(), range.end());
        return new EventPageView(List.copyOf(matched.subList(fromIndex, toIndex)),
                PageMetaView.of(safePage, safeSize, matched.size(), query.sort()));
    }

    /**
     * Chi tiết một sự kiện — {@code GET /api/v1/events/{id}}.
     *
     * <h2>Ba quyết định</h2>
     * <ol>
     *   <li><b>Sự kiện đã xoá mềm trả 404.</b> Xoá mềm là để giữ liên kết dữ liệu và lịch sử, không
     *       phải để tiếp tục hiển thị.</li>
     *   <li><b>Khách gặp sự kiện của người còn sống cũng trả 404, không phải 403.</b> 403 xác nhận
     *       rằng id ấy có tồn tại — với người còn sống thì chính sự tồn tại đã là một lần lộ (Nghị
     *       định 13/2023, BA v2 §10).</li>
     *   <li><b>Không còn lần xảy ra nào ở phía trước thì {@code nextOccurrenceSolar} là {@code null}</b>
     *       chứ không phải một ngày trong quá khứ. Sự kiện một lần đã diễn ra vẫn xem được, nhưng
     *       không được giả vờ là sắp tới.</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public EventView findById(UUID id) {
        LocalDate today = LocalDate.now(properties.zone());
        Event event = events.findById(id)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> NotFoundException.of("Event", id));

        EventSubject subject = event.personId() == null
                ? null : subjects.findPerson(event.personId()).orElse(null);
        if (CurrentUserProvider.current().isEmpty() && subject != null && subject.alive()) {
            log.debug("Khach xem su kien {} cua nguoi con song -> tra 404", id);
            throw NotFoundException.of("Event", id);
        }
        EventSubject.BranchSnapshot branch = event.targetBranchId() == null
                ? null : subjects.findBranch(event.targetBranchId()).orElse(null);

        EventOccurrence occurrence =
                nextOccurrence(event, subject, today, new EventQuery.Range(today, null)).orElse(null);
        return toView(event, subject, branch, occurrence, today);
    }

    /**
     * Lần xảy ra <b>đầu tiên rơi vào khoảng lọc</b>.
     *
     * <p>Duyệt các năm trong tầm nhìn thay vì chỉ năm hiện tại: giỗ tháng Chạp rơi sang tháng 1–2
     * dương lịch của năm sau, và nếu chỉ xét năm nay thì nó biến mất khỏi danh sách "sắp tới" đúng
     * vào lúc người ta cần nhìn thấy nó nhất.</p>
     *
     * <p>Việc chọn năm nào đi qua {@link OccurrenceResolver#resolveAll} chứ không làm tại chỗ: đó
     * là nơi duy nhất biết "lặp hằng năm hay xảy ra một lần", và trước khi gom về đó thì màn danh
     * sách hiện một lễ khánh thành lặp lại mỗi năm.</p>
     */
    private Optional<EventOccurrence> nextOccurrence(Event event, EventSubject subject, LocalDate today,
                                                     EventQuery.Range range) {
        Integer currentLunarYear = occurrences.lunarYearOf(today);
        int firstLunarYear = currentLunarYear == null ? today.getYear() : currentLunarYear;
        // Lùi một năm: khoảng lọc có thể bắt đầu ở quá khứ (tham số `from`).
        List<Integer> lunarYears = new ArrayList<>(properties.getHorizonYears() + 2);
        List<Integer> solarYears = new ArrayList<>(properties.getHorizonYears() + 2);
        for (int i = -1; i <= properties.getHorizonYears(); i++) {
            lunarYears.add(firstLunarYear + i);
            solarYears.add(today.getYear() + i);
        }
        List<EventOccurrence> found = occurrences.resolveAll(event,
                EffectiveLunarDate.of(event, subject), lunarYears, solarYears);
        return found.stream()
                .filter(occurrence -> range.contains(occurrence.dueSolarDate()))
                .min(Comparator.comparing(EventOccurrence::dueSolarDate));
    }

    /** @param occurrence {@code null} khi sự kiện không còn lần xảy ra nào ở phía trước */
    private EventView toView(Event event, EventSubject subject, EventSubject.BranchSnapshot branch,
                             EventOccurrence occurrence, LocalDate today) {
        return new EventView(
                event.id(),
                event.type(),
                event.title(),
                subject,
                event.lunarDate(),
                event.solarDate(),
                event.isLunarBased(),
                event.isRecurring(),
                event.version(),
                occurrence == null ? null : occurrence.dueSolarDate(),
                occurrence == null || occurrence.resolvedLunar() == null
                        ? null : occurrence.resolvedLunar().year(),
                occurrence == null
                        ? null : (int) ChronoUnit.DAYS.between(today, occurrence.dueSolarDate()),
                branch,
                event.isClanLevel(),
                properties.toPlan().offsetDays(),
                event.location(),
                event.description(),
                occurrence == null ? null : occurrence.adjustment().note());
    }

    /** Hợp đồng cho phép {@code nextOccurrenceSolar} và {@code eventType}; mặc định ngày tăng dần. */
    private static Comparator<EventView> comparator(String sort) {
        String field = sort == null || sort.isBlank() ? "nextOccurrenceSolar" : sort.split(",")[0].trim();
        boolean descending = sort != null && sort.toLowerCase(java.util.Locale.ROOT).endsWith("desc");
        Comparator<EventView> comparator = switch (field) {
            case "eventType" -> Comparator.comparing(view -> view.type().name());
            default -> Comparator.comparing(EventView::nextOccurrenceSolar,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return descending ? comparator.reversed() : comparator;
    }

    private static Set<UUID> personIdsOf(List<Event> found) {
        Set<UUID> ids = new HashSet<>();
        for (Event event : found) {
            if (event.personId() != null) {
                ids.add(event.personId());
            }
        }
        return ids;
    }

    private static Set<UUID> branchIdsOf(List<Event> found) {
        Set<UUID> ids = new HashSet<>();
        for (Event event : found) {
            if (event.targetBranchId() != null) {
                ids.add(event.targetBranchId());
            }
        }
        return ids;
    }
}
