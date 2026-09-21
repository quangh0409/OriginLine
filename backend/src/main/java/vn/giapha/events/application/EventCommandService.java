package vn.giapha.events.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.events.application.command.CreateEventCommand;
import vn.giapha.events.application.command.EventScope;
import vn.giapha.events.application.command.UpdateEventCommand;
import vn.giapha.events.application.view.EventView;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventSubject;
import vn.giapha.events.domain.EventType;
import vn.giapha.events.domain.port.EventRepository;
import vn.giapha.events.domain.port.EventSubjectPort;
import vn.giapha.membership.application.MemberScopeView;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.LunarDate;

/**
 * Tạo / sửa / xoá mềm sự kiện dòng họ — lối ghi mà {@code EventController} <b>chưa từng có</b>.
 *
 * <p>Cho tới đợt này, mọi dòng trong bảng {@code event} đều do máy sinh: giỗ dựng tự động từ
 * {@code person.death_lunar}, cộng vài dòng gieo sẵn của bản demo. Không ai cấu hình được một dịp
 * lễ, một buổi họp họ hay một ngày chạp mả — tức mười trong mười hai loại sự kiện của hợp đồng
 * không có cách nào tồn tại.</p>
 *
 * <h2>Bốn việc mà một lượt ghi phải làm trọn vẹn, trong MỘT transaction</h2>
 * <ol>
 *   <li><b>Kiểm quyền theo {@code ltree}</b> — qua {@link EventScopeGuard}, tức qua
 *       {@code BranchScopeGuard}. Trưởng chi Ất không tạo được việc cho chi Bính.</li>
 *   <li><b>Ghi bản ghi</b>, với khoá lạc quan ({@code If-Match} → {@code version}).</li>
 *   <li><b>Ghi nhật ký kiểm toán</b> — ai đổi gì, lúc nào, trước/sau.</li>
 *   <li><b>Dựng lại lịch nhắc</b>. Đây là việc dễ quên nhất và là việc người dùng thấy: sửa ngày
 *       họp họ từ 15 sang 20 mà để nguyên {@code reminder_job} đã sinh thì cả chi vẫn được nhắc
 *       theo ngày cũ — không lỗi, không log, người ta chỉ đến nhầm ngày.</li>
 * </ol>
 *
 * <h2>Ngày âm là mặc định, và phép quy đổi KHÔNG được viết lần thứ hai</h2>
 * Lớp này không chứa một dòng số học lịch nào. Mọi câu hỏi "ngày âm này rơi vào ngày dương nào" đi
 * qua {@link OccurrenceResolver} — nơi đã có sẵn chính sách cho hai ca mà gia phả thật nào cũng
 * gặp: <b>tháng nhuận biến mất</b> (ngày 3 tháng 6 nhuận năm 2017 không tồn tại năm 2028 → dùng
 * tháng 6 thường) và <b>tháng thiếu</b> (ngày 30 ở tháng chỉ có 29 ngày → lùi về 29). Giải lại lần
 * thứ hai ở đây là cách chắc chắn nhất để màn lịch và thông báo nhắc trả lời khác nhau về cùng một
 * ngày, và triệu chứng sẽ là một lời nhắc lệch nguyên một tháng.
 *
 * <h2>Giỗ vẫn lấy ngày từ hồ sơ nhân khẩu</h2>
 * {@code person.death_lunar} là nguồn chân lý của ngày giỗ (BA v2), và
 * {@link EffectiveLunarDate} thi hành điều đó ở cả màn danh sách lẫn bộ sinh lịch nhắc. Vì vậy ghi
 * một ngày <i>khác</i> vào bản ghi {@code GIO} là một thay đổi <b>không có tác dụng</b>: người sửa
 * tin rằng mình đã đổi ngày giỗ, cả họ vẫn được nhắc theo ngày cũ. Thà từ chối thẳng
 * ({@code GIO_DATE_FROM_PERSON}) và chỉ sang hồ sơ nhân khẩu.
 */
@Service
public class EventCommandService {

    private static final Logger log = LoggerFactory.getLogger(EventCommandService.class);

    private static final String AUDIT_ENTITY = "event";

    private final EventRepository events;
    private final EventSubjectPort subjects;
    private final EventScopeGuard guard;
    private final EventQueryService queries;
    private final GenerateRemindersService reminders;
    private final AuditTrailService audit;

    public EventCommandService(EventRepository events, EventSubjectPort subjects,
                               EventScopeGuard guard, EventQueryService queries,
                               GenerateRemindersService reminders, AuditTrailService audit) {
        this.events = events;
        this.subjects = subjects;
        this.guard = guard;
        this.queries = queries;
        this.reminders = reminders;
        this.audit = audit;
    }

    @Transactional
    public EventView create(CreateEventCommand command) {
        MemberScopeView caller = guard.requireProvisioned();
        EventScope scope = command.scope();
        guard.requireWriteAccess(caller, scope.clanWide(), scope.branchId());

        UUID id = UUID.randomUUID();
        Event draft = Event.builder(id)
                .personId(command.personId())
                .type(command.type())
                .title(normalize(command.title()))
                .description(normalize(command.description()))
                .lunarDate(command.lunarDate())
                .solarDate(command.solarDate())
                .lunarBased(command.lunarBased())
                .recurring(command.recurringAnnually())
                .targetBranchId(scope.branchId())
                .clanLevel(scope.clanWide())
                .location(normalize(command.location()))
                .deleted(false)
                .build();
        draft = normalizeLunar(draft);
        validate(draft);

        Event saved = events.insert(draft);
        audit.record(AUDIT_ENTITY, saved.id().toString(), AuditAction.CREATE,
                null, snapshot(saved), List.copyOf(snapshot(saved).keySet()),
                "Tao su kien dong ho");
        int created = reminders.regenerateFor(saved);
        log.info("app_user {} tao su kien {} ({}), pham vi {} -> {} lich nhac",
                caller.appUserId(), saved.id(), saved.type(),
                saved.isClanLevel() ? "ca dong ho" : "chi " + saved.targetBranchId(), created);
        return queries.findById(saved.id());
    }

    /**
     * Sửa từng phần. Trường vắng mặt trong thân yêu cầu giữ nguyên giá trị cũ — xem
     * {@link UpdateEventCommand#presentFields()}.
     */
    @Transactional
    public EventView update(UUID id, UpdateEventCommand command) {
        MemberScopeView caller = guard.requireProvisioned();
        Event current = events.findById(id).orElseThrow(() -> NotFoundException.of("Event", id));
        if (current.isDeleted()) {
            // 409 chu khong 404: nguoi goi da biet id nay ton tai (ho vua doc no truoc khi sua), va
            // "da bi xoa" la thong tin ho can de hieu vi sao thao tac khong di qua.
            throw new EventConflictException(EventProblemCodes.EVENT_DELETED,
                    "Su kien da bi xoa mem, khong sua duoc nua");
        }
        requireMatchingVersion(current, command.expectedVersion());

        boolean newClanWide = command.has(UpdateEventCommand.F_CLAN_WIDE)
                ? Boolean.TRUE.equals(command.clanWide()) : current.isClanLevel();
        UUID newBranchId = command.has(UpdateEventCommand.F_SCOPE_BRANCH)
                ? command.scopeBranchId() : current.targetBranchId();
        if (newClanWide) {
            // Chuyen len cap dong ho thi chi/nganh cu phai duoc go ra, neu khong ck_event_scope tu
            // choi ca cau UPDATE.
            newBranchId = command.has(UpdateEventCommand.F_SCOPE_BRANCH)
                    ? command.scopeBranchId() : null;
        }
        EventScope scope = new EventScope(newClanWide, newBranchId);
        guard.requireCanMove(caller, current, scope.clanWide(), scope.branchId());

        Event.Builder builder = current.toBuilder()
                .targetBranchId(scope.branchId())
                .clanLevel(scope.clanWide());
        if (command.has(UpdateEventCommand.F_TYPE) && command.type() != null) {
            builder.type(command.type());
        }
        if (command.has(UpdateEventCommand.F_TITLE)) {
            builder.title(normalize(command.title()));
        }
        if (command.has(UpdateEventCommand.F_DESCRIPTION)) {
            builder.description(normalize(command.description()));
        }
        if (command.has(UpdateEventCommand.F_LOCATION)) {
            builder.location(normalize(command.location()));
        }
        if (command.has(UpdateEventCommand.F_PERSON)) {
            builder.personId(command.personId());
        }
        boolean recurring = command.has(UpdateEventCommand.F_RECURRING)
                && command.recurringAnnually() != null
                ? command.recurringAnnually() : current.isRecurring();
        builder.recurring(recurring);
        applyDateSource(command, builder);

        Event edited = normalizeLunar(builder.build());
        validate(edited);

        Event saved = events.update(edited, command.expectedVersion());
        Map<String, Object> before = snapshot(current);
        Map<String, Object> after = snapshot(saved);
        audit.record(AUDIT_ENTITY, saved.id().toString(), AuditAction.UPDATE,
                before, after, changedFields(before, after), "Sua su kien dong ho");

        if (command.touchesSchedule()) {
            int created = reminders.regenerateFor(saved);
            log.info("Su kien {} doi ngay/pham vi -> dung lai {} lich nhac", saved.id(), created);
        }
        return queries.findById(saved.id());
    }

    /**
     * <b>Xoá mềm</b> — cờ, không phải lệnh xoá.
     *
     * <p>Bản ghi ở lại vì ba thứ còn trỏ vào nó: {@code reminder_job} đã phát,
     * {@code notification_log} của những lần đã gửi, và {@code audit_log}. Xoá cứng thì
     * {@code ON DELETE CASCADE} của {@code reminder_job} sẽ kéo theo cả lịch sử gửi, và một người
     * trong họ bấm vào thông báo cũ sẽ rơi vào trang trắng.</p>
     *
     * <p>Lịch nhắc <b>chưa bắn</b> bị dọn đi (xem
     * {@code ReminderJobRepository.deleteUnsentByEvent}); lịch đã {@code QUEUED}/{@code SENT} giữ
     * nguyên. Ngoài ra {@code DispatchDueRemindersService} còn một lớp chặn thứ hai: job của sự
     * kiện đã xoá mềm chuyển {@code CANCELLED} thay vì gửi.</p>
     */
    @Transactional
    public void softDelete(UUID id, String reason) {
        MemberScopeView caller = guard.requireProvisioned();
        Event current = events.findById(id).orElseThrow(() -> NotFoundException.of("Event", id));
        guard.requireWriteAccess(caller, current);
        if (current.isDeleted()) {
            log.debug("Su kien {} da xoa mem tu truoc - khong lam gi them", id);
            return;
        }

        Event saved = events.update(current.toBuilder().deleted(true).build(), current.version());
        audit.record(AUDIT_ENTITY, id.toString(), AuditAction.SOFT_DELETE,
                snapshot(current), snapshot(saved), List.of("isDeleted"),
                reason == null || reason.isBlank() ? "Xoa mem su kien" : reason);
        reminders.regenerateFor(saved);
        log.info("app_user {} xoa mem su kien {} ({})", caller.appUserId(), id, current.type());
    }

    // ---------------------------------------------------------------------------------------
    // Luật nghiệp vụ
    // ---------------------------------------------------------------------------------------

    /**
     * So khớp phiên bản <b>ngay khi nạp</b>, không phó mặc cho {@code @Version} của Hibernate.
     *
     * <h2>Vì sao phép kiểm này phải nằm ở đây</h2>
     * Bên dưới, {@code EventRepositoryAdapter} nạp entity rồi gọi {@code setVersion(expectedVersion)}
     * trước khi ghi. Với một entity <b>đang được quản lý trong cùng transaction</b> (và nó đang
     * được quản lý, vì ta vừa {@code findById} ở ngay trên), Hibernate dựng mệnh đề
     * {@code WHERE version = ?} từ <b>ảnh chụp lúc nạp</b> chứ không từ giá trị vừa gán — nên gán
     * một phiên bản cũ <i>không</i> làm câu {@code UPDATE} trượt, và một lượt ghi mù đi qua êm ru
     * với HTTP 200. Đây là ca đã tái hiện được bằng test, không phải suy đoán.
     *
     * <p>{@code @Version} vẫn giữ nguyên tác dụng và vẫn cần: nó là lớp chặn cho hai lượt ghi
     * <i>thật sự</i> chạy song song ở hai transaction. Phép kiểm ở đây chặn ca phổ biến hơn nhiều —
     * người dùng mở trang từ lâu rồi mới bấm Lưu. Cùng cách {@code UpdatePersonService} đã làm.</p>
     */
    private static void requireMatchingVersion(Event current, long expectedVersion) {
        if (current.version() != expectedVersion) {
            throw new EventConflictException(EventProblemCodes.OPTIMISTIC_LOCK_CONFLICT,
                    "Su kien da duoc nguoi khac cap nhat (phien ban " + current.version()
                            + ", ban gui " + expectedVersion + "). Hay tai lai va thu lai.");
        }
    }

    /**
     * Chọn nguồn ngày khi sửa: gửi {@code lunarDate} thì chuyển sang âm lịch, và ngược lại.
     *
     * <p><b>Không chuẩn hoá ở đây.</b> Hàm này chỉ chạy khi thân yêu cầu <i>có</i> một trong hai
     * trường ngày, nên mọi phép chuẩn hoá đặt bên trong nó sẽ bị bỏ qua ở đúng những lượt
     * {@code PATCH} không gửi ngày — ví dụ một lượt chỉ lật cờ lặp. Xem
     * {@link #normalizeLunar(Event)}, chạy trên sự kiện <b>đã dựng xong</b>.</p>
     */
    private static void applyDateSource(UpdateEventCommand command, Event.Builder builder) {
        boolean hasLunar = command.has(UpdateEventCommand.F_LUNAR_DATE) && command.lunarDate() != null;
        boolean hasSolar = command.has(UpdateEventCommand.F_SOLAR_DATE) && command.solarDate() != null;
        if (hasLunar && hasSolar) {
            throw new IllegalArgumentException(
                    "Chi gui mot trong hai: lunarDate (mac dinh cho viec ho) hoac solarDate."
                            + " Luu ca hai se tao ra hai ngay khong bao gio dong bo lai duoc.");
        }
        if (hasLunar) {
            builder.lunarBased(true).lunarDate(command.lunarDate()).solarDate(null);
        } else if (hasSolar) {
            builder.lunarBased(false).solarDate(command.solarDate()).lunarDate(null);
        }
    }

    /**
     * Lặp hằng năm thì <b>bỏ năm âm đi</b> ({@code year = 0}, quy ước của V4).
     *
     * <p>Giữ lại năm trên một sự kiện lặp là một sự thật nửa vời: nó không ảnh hưởng tới phép quy
     * đổi (bộ sinh lần xảy ra luôn thay bằng năm đang xét) nhưng lại hiện ra trên giao diện như
     * thể cái giỗ ấy chỉ có một năm. Với sự kiện một lần thì ngược lại, năm là bắt buộc — ràng buộc
     * ấy do constructor của {@link Event} và {@code ck_event_oneoff_lunar_year} canh.</p>
     *
     * <h2>Chạy trên sự kiện ĐÃ DỰNG XONG, không chạy bên trong một nhánh sửa</h2>
     * Bản trước nhận {@code (LunarDate, boolean)} và chỉ được gọi từ trong {@code applyDateSource},
     * mà nhánh âm lịch của hàm ấy chỉ chạy khi thân yêu cầu <b>có</b> {@code lunarDate}. Hệ quả:
     * {@code PATCH {"recurringAnnually": true}} trên một sự kiện âm lịch một-lần để nguyên
     * {@code year = 2027} trên một sự kiện nay đã lặp — đúng cái "sự thật nửa vời" mà chính javadoc
     * này nói là không được để xảy ra. Một phép chuẩn hoá phải nhìn thấy <b>trạng thái cuối</b>,
     * nếu không nó chỉ đúng với những lượt sửa mà người viết nó nghĩ tới.
     */
    private static Event normalizeLunar(Event event) {
        LunarDate lunar = event.lunarDate();
        if (lunar == null || !event.isRecurring() || lunar.year() == 0) {
            return event;
        }
        return event.toBuilder()
                .lunarDate(new LunarDate(0, lunar.month(), lunar.day(), lunar.leapMonth()))
                .build();
    }

    /**
     * Luật không diễn đạt được bằng ràng buộc CHECK, hoặc diễn đạt được nhưng phải trả lời sớm hơn
     * và rõ hơn một thông báo lỗi của Postgres.
     */
    private void validate(Event event) {
        if (event.title() == null || event.title().isBlank()) {
            throw new IllegalArgumentException("Tieu de su kien khong duoc de trong");
        }
        if (event.title().length() > 200) {
            throw new IllegalArgumentException("Tieu de su kien toi da 200 ky tu");
        }
        if (event.type() == EventType.GIO) {
            requireGioShape(event);
        }
        if (event.location() != null && event.location().length() > 255) {
            throw new IllegalArgumentException("Dia diem toi da 255 ky tu");
        }
        if (event.personId() != null) {
            EventSubject subject = subjects.findPerson(event.personId())
                    .orElseThrow(() -> NotFoundException.of("Person", event.personId()));
            requireGioMatchesPerson(event, subject);
        }
    }

    /**
     * Hình dạng bắt buộc của một cái giỗ: <b>gắn nhân khẩu · theo âm lịch · lặp hằng năm</b>.
     *
     * <h2>Vì sao hai phép kiểm sau không thể để cho {@link #requireGioMatchesPerson} lo</h2>
     * Phép canh ấy <b>tự tắt</b> với một sự kiện theo dương lịch ({@code !event.isLunarBased()}),
     * nên gửi {@code solarDate} là đi vòng qua nó trọn vẹn: ngày giỗ tách khỏi
     * {@code person.death_lunar}, lời nhắc bắn theo một ngày dương cố định và <b>trôi khoảng 11
     * ngày mỗi năm</b> khỏi ngày giỗ thật — trong khi lượt ghi gây ra chuyện đó trả 200 và để lại
     * một dòng nhật ký không có gì bất thường. Vì vậy phép kiểm phải nằm <i>trước</i>, ở chỗ không
     * có nhánh nào bỏ qua được.
     *
     * <p>Vế "lặp hằng năm" đóng ca còn lại: một cái giỗ {@code recurringAnnually: false} qua được
     * phép canh ngày, nhưng lúc đọc {@code EffectiveLunarDate.of} thay ngày bằng
     * {@code death_lunar}, mà {@code year()} của nó là <b>năm mất</b> — nên
     * {@code OccurrenceResolver} giải ra một lần xảy ra trong quá khứ và sự kiện lặng lẽ không bao
     * giờ sinh lời nhắc. "Lặng lẽ" là phần tệ nhất: không ai biết cho tới khi cái giỗ đã qua.</p>
     *
     * <p>Cả ba đều có bản sao ở CSDL ({@code ck_event_gio_has_person} V4,
     * {@code ck_event_gio_lunar_recurring} V20). Bản ở đây trả lời <i>sớm hơn và rõ hơn</i>; bản
     * dưới kia là thứ còn đỡ khi có một lối ghi mới quên gọi {@code validate}.</p>
     */
    private static void requireGioShape(Event event) {
        if (event.personId() == null) {
            throw new IllegalArgumentException(
                    "Gio ca nhan bat buoc gan mot nhan khau (ck_event_gio_has_person)");
        }
        if (!event.isLunarBased()) {
            throw new EventConflictException(EventProblemCodes.GIO_DATE_FROM_PERSON,
                    "Ngay gio lay theo ngay mat am trong ho so nhan khau, nen khong ghi duoc theo"
                            + " duong lich. Mot ngay duong co dinh se troi khoi ngay gio that"
                            + " khoang 11 ngay moi nam. Muon doi ngay gio thi sua ngay mat tren"
                            + " ho so.");
        }
        if (!event.isRecurring()) {
            throw new IllegalArgumentException(
                    "Gio lap hang nam theo ngay mat am; mot cai gio 'mot lan' se roi vao nam mat"
                            + " (qua khu) va khong bao gio sinh loi nhac"
                            + " (ck_event_gio_lunar_recurring)");
        }
    }

    /**
     * Với {@link EventType#GIO}, ngày âm ghi trong bản ghi sự kiện phải <b>trùng</b>
     * {@code person.death_lunar}.
     *
     * <p>Không tự ghi đè theo hồ sơ: sửa ngầm dữ liệu của người khác trong một lượt ghi mà họ tưởng
     * là chuyện khác là hành vi không được phép của một hệ có kiểm toán. Từ chối và chỉ đường sang
     * hồ sơ nhân khẩu — nơi lần sửa ấy có ý nghĩa và có người duyệt.</p>
     */
    private static void requireGioMatchesPerson(Event event, EventSubject subject) {
        if (event.type() != EventType.GIO) {
            return;
        }
        // KHONG them "|| !event.isLunarBased()" vao dieu kien tren. Ban dau no o day, va no lam
        // phep canh TU TAT voi moi su kien theo duong lich — tuc mo lai dung lo hong ma
        // requireGioShape vua bit. Voi GIO thi lunarBased luon dung, da duoc canh o tren.
        LunarDate fromPerson = subject.deathLunar();
        if (fromPerson == null) {
            // Da mat nhung khuyet ngay: gia pha co rat hay nhu vay. Luc nay ngay am chep trong ban
            // ghi su kien la nguon duy nhat, va no hop le. (EffectiveLunarDate xu dung the.)
            return;
        }
        LunarDate fromEvent = event.lunarDate();
        if (fromEvent != null && fromEvent.day() == fromPerson.day()
                && fromEvent.month() == fromPerson.month()
                && fromEvent.leapMonth() == fromPerson.leapMonth()) {
            return;
        }
        throw new EventConflictException(EventProblemCodes.GIO_DATE_FROM_PERSON,
                "Ngay gio lay theo ngay mat trong ho so nhan khau (" + fromPerson + ")."
                        + " Muon doi ngay gio thi sua ngay mat tren ho so, dung sua o day —"
                        + " sua o day se khong co tac dung nao.");
    }

    // ---------------------------------------------------------------------------------------
    // Nhật ký kiểm toán
    // ---------------------------------------------------------------------------------------

    /**
     * Ảnh chụp để ghi {@code audit_log}.
     *
     * <p><b>Không một trường Tầng 3 nào</b> — bảng {@code event} vốn không có số điện thoại, email
     * hay địa chỉ nhà, và {@code person_id} ở đây là một khoá chứ không phải dữ liệu cá nhân.
     * {@code SensitiveFieldRedactor} của {@code audit} là lưới cuối, không phải lý do để bên gọi
     * cẩu thả.</p>
     */
    private static Map<String, Object> snapshot(Event event) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("eventType", event.type().name());
        snapshot.put("title", event.title());
        snapshot.put("description", event.description());
        snapshot.put("lunarDate", event.lunarDate() == null ? null : event.lunarDate().toString());
        snapshot.put("solarDate", event.solarDate() == null ? null : event.solarDate().toString());
        snapshot.put("isLunarBased", event.isLunarBased());
        snapshot.put("isRecurring", event.isRecurring());
        snapshot.put("targetBranchId", event.targetBranchId() == null
                ? null : event.targetBranchId().toString());
        snapshot.put("isClanLevel", event.isClanLevel());
        snapshot.put("personId", event.personId() == null ? null : event.personId().toString());
        snapshot.put("location", event.location());
        snapshot.put("isDeleted", event.isDeleted());
        return snapshot;
    }

    private static List<String> changedFields(Map<String, Object> before, Map<String, Object> after) {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            if (!Objects.equals(entry.getValue(), before.get(entry.getKey()))) {
                changed.add(entry.getKey());
            }
        }
        return List.copyOf(changed);
    }

    /** Chuỗi rỗng và chuỗi chỉ toàn khoảng trắng là {@code null} — không phải một giá trị. */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
