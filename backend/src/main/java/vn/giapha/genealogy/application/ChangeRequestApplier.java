package vn.giapha.genealogy.application;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.giapha.genealogy.application.command.UpdatePersonCommand;
import vn.giapha.genealogy.domain.DatePrecision;
import vn.giapha.genealogy.domain.FieldChange;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.membership.domain.event.ChangeRequestApprovedEvent;
import vn.giapha.membership.domain.event.CorrectionPayload;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;
import vn.giapha.shared.vo.PersonId;

/**
 * <b>Áp dụng</b> một yêu cầu đính chính vừa được duyệt vào cây phả hệ.
 *
 * <p>Trước lớp này, Trưởng chi bấm Duyệt thì hệ thống ghi {@code APPROVED} và <b>gia phả không hề
 * đổi</b>. Sự kiện được phát ra nhưng không có ai nghe. Đây là người nghe đầu tiên của toàn hệ
 * thống.</p>
 *
 * <h2>Vì sao lớp này nằm ở genealogy chứ không ở membership</h2>
 * {@code membership} quyết định <i>ai được duyệt cái gì</i>. Nó không biết — và không được biết —
 * cách ghi một nhân khẩu, cách kiểm kỵ húy, hay cách giữ đồng bộ cạnh AGE với bảng
 * {@code relationship}. Nếu {@code ChangeRequestService} gọi thẳng {@code UpdatePersonService} thì
 * mọi thay đổi trong luật phả hệ trở thành thay đổi trong luồng duyệt.
 *
 * <p>Kiến trúc đã tự trả lời câu hỏi này: {@code membership.domain.event} mang
 * {@code @NamedInterface("events")} còn {@code genealogy.application} thì <b>không</b> — nên chiều
 * duy nhất hợp lệ là {@code genealogy → membership.events}. Chiều ngược lại sẽ thành chu trình và
 * {@code ModularityTests} sẽ đỏ ngay.</p>
 *
 * <h2>{@code @EventListener} thường, KHÔNG phải {@code @TransactionalEventListener}</h2>
 * Người nghe chạy đồng bộ trong ngăn xếp lời gọi của {@code ChangeRequestService.review()}, tức là
 * trong chính transaction đang mở. Đó là lựa chọn có chủ ý:
 * <ul>
 *   <li>áp dụng hỏng ⇒ ngoại lệ lan ngược ⇒ trạng thái {@code APPROVED} rollback theo ⇒ yêu cầu ở
 *       lại {@code PENDING};</li>
 *   <li>{@code AFTER_COMMIT} thì ngược lại: {@code APPROVED} đã commit rồi, lệnh ghi hỏng sau đó
 *       không cách nào rút lại — và ta có <b>đúng cái trạng thái mâu thuẫn</b> mà bộ này sinh ra để
 *       chấm dứt, chỉ khác là lần này còn khó phát hiện hơn vì có một dòng log lỗi ở đâu đó.</li>
 * </ul>
 *
 * <h2>Người ghi là người duyệt</h2>
 * Lệnh ghi đi qua {@link UpdatePersonService} với <b>nguyên ngữ cảnh bảo mật của người duyệt</b>.
 * Không có lối "ghi thay mặt người đề nghị": Trưởng chi duyệt là Trưởng chi chịu trách nhiệm, và
 * phép kiểm phạm vi {@code ltree} của {@code GenealogyAccessGuard} vẫn chạy đầy đủ — hai cửa kiểm
 * độc lập trên cùng một thao tác, chứ không phải một cửa bị bỏ qua vì "đã duyệt rồi".
 *
 * <p>Dấu vết người <i>đề nghị</i> đi vào {@code audit_log.note} — xem {@link #auditNote}. Nhờ vậy
 * đọc lại lịch sử ba năm sau vẫn phân biệt được "Trưởng chi tự sửa" với "Trưởng chi duyệt đề nghị
 * của bà Mai".</p>
 */
@Component
public class ChangeRequestApplier {

    private static final Logger log = LoggerFactory.getLogger(ChangeRequestApplier.class);

    private final UpdatePersonService updatePerson;
    private final PersonRepository persons;

    public ChangeRequestApplier(UpdatePersonService updatePerson, PersonRepository persons) {
        this.updatePerson = updatePerson;
        this.persons = persons;
    }

    /**
     * Áp dụng đề nghị vừa được duyệt.
     *
     * <p>Loại yêu cầu chưa có bộ áp dụng ({@code OTHER}, {@code ADD_RELATIONSHIP}, {@code MOVE_BRANCH}…)
     * được bỏ qua <b>một cách ồn ào</b>: ghi log mức INFO nói rõ Trưởng chi phải tự thao tác. Im
     * lặng ở đây chính là hình dạng cũ của lỗi.</p>
     */
    @EventListener
    public void onChangeRequestApproved(ChangeRequestApprovedEvent event) {
        if (!CorrectionPayload.isApplicable(event.requestType())) {
            log.info("Yeu cau dinh chinh {} loai {} khong co bo ap dung tu dong - da duyet, "
                            + "Truong chi tu thao tac tren ho so",
                    event.changeRequestId(), event.requestType());
            return;
        }

        Map<String, Object> payload = event.payload();
        requireValidPayload(event, payload);
        requireApplicableTarget(event);

        long baseVersion = CorrectionPayload.baseVersion(payload);
        UpdatePersonCommand command = toCommand(event.personId(), payload, baseVersion,
                auditNote(event));

        updatePerson.update(command);
        log.info("Da ap dung yeu cau dinh chinh {} len nhan khau {} (phien ban goc {}), "
                        + "cac truong: {}, nguoi duyet {}, nguoi de nghi {}",
                event.changeRequestId(), event.personId(), baseVersion,
                CorrectionPayload.fieldKeys(payload), event.reviewerAppUserId(),
                event.requesterAppUserId());
        // Ghi chu: person.version da nhich len sau lenh nay. Moi de nghi khac dang cho tren cung
        // ho so vi vay se lech _baseVersion va bi chan - dung y. Nguoi duyet phai doc lai roi
        // quyet lai tung cai, thay vi de chung lang le de len nhau theo thu tu bam nut.
    }

    // -------------------------------------------------------------------------------------
    // Cửa kiểm trước khi ghi
    // -------------------------------------------------------------------------------------

    /**
     * Lưới cuối của hợp đồng payload.
     *
     * <p>{@code membership} đã kiểm lúc gửi. Kiểm lại ở đây phòng ba thứ: yêu cầu tồn đọng từ
     * trước khi có hợp đồng, một lối gửi khác lọt vào sau này, và lỗi lập trình khi ai đó thêm
     * trường vào {@link CorrectionPayload#UPDATE_PERSON_FIELDS} mà quên thêm nhánh dịch ở
     * {@link #toCommand}.</p>
     */
    private void requireValidPayload(ChangeRequestApprovedEvent event, Map<String, Object> payload) {
        List<String> problems = CorrectionPayload.violations(event.requestType(), payload, true);
        if (!problems.isEmpty()) {
            throw new DomainException(GenealogyProblemCodes.VALIDATION_FAILED,
                    "Yeu cau dinh chinh " + event.changeRequestId() + " co noi dung khong ap dung "
                            + "duoc: " + String.join("; ", problems));
        }
    }

    /**
     * Nhân khẩu đích phải còn tồn tại và <b>chưa bị xoá mềm</b>.
     *
     * <p>Xoá mềm là quyết định của Hội đồng về một bản ghi trùng hoặc ghi nhầm. Âm thầm sửa hồ sơ
     * ấy rồi để nó nằm im ngoài cây là cách tạo ra dữ liệu không ai nhìn thấy mà vẫn tồn tại. Chặn
     * ở đây, {@code APPROVED} rollback, và Trưởng chi buộc phải quyết: khôi phục bản ghi trước, hay
     * từ chối đề nghị.</p>
     */
    private void requireApplicableTarget(ChangeRequestApprovedEvent event) {
        UUID personId = event.personId();
        if (personId == null) {
            throw new DomainException(GenealogyProblemCodes.VALIDATION_FAILED,
                    "Yeu cau dinh chinh " + event.changeRequestId()
                            + " loai UPDATE_PERSON nhung khong tro toi nhan khau nao");
        }
        Person person = persons.byId(PersonId.of(personId)).orElseThrow(() -> new NotFoundException(
                GenealogyProblemCodes.NOT_FOUND,
                "Khong tim thay nhan khau " + personId + " de ap dung yeu cau dinh chinh "
                        + event.changeRequestId()));
        if (person.isDeleted()) {
            throw new GenealogyConflictException(GenealogyProblemCodes.PERSON_ALREADY_DELETED,
                    "Nhan khau " + personId + " da bi xoa mem, khong ap dung duoc yeu cau dinh "
                            + "chinh " + event.changeRequestId()
                            + ". Hay khoi phuc ho so truoc, hoac tu choi yeu cau.");
        }
    }

    // -------------------------------------------------------------------------------------
    // Dịch payload sang lệnh ghi
    // -------------------------------------------------------------------------------------

    /**
     * Payload JSON → {@link UpdatePersonCommand}.
     *
     * <p><b>Khoá có mặt là "ghi đè", khoá vắng mặt là "giữ nguyên", giá trị {@code null} là "xoá
     * trắng"</b> — đúng ba trạng thái của {@link FieldChange}. Đây là lý do phải đọc
     * {@code containsKey} chứ không đọc {@code get() != null}: một đề nghị bỏ ngày mất ghi nhầm gửi
     * lên đúng {@code {"death": null}}, và nhầm nó với "không nói gì" là làm đề nghị biến mất không
     * tiếng động.</p>
     *
     * <p>{@code isAlive} cố ý <b>không</b> có trong hợp đồng: khi payload mang ngày mất,
     * {@code LifeStatusResolver} tự suy ra "đã mất" (Phương án B của Hội đồng Tộc biểu). Để người
     * đính chính khai riêng cờ sống/mất chỉ tạo thêm một cách gửi mâu thuẫn.</p>
     */
    private static UpdatePersonCommand toCommand(UUID personId, Map<String, Object> payload,
                                                 long baseVersion, String note) {
        return new UpdatePersonCommand(
                personId,
                baseVersion,
                FieldChange.keep(),                              // names - ngoai danh muc, di qua OTHER
                change(payload, "gender", ChangeRequestApplier::toGender),
                FieldChange.keep(),                              // isAlive - suy tu death
                FieldChange.keep(),                              // isDeleted - khong bao gio qua day
                change(payload, "birth", ChangeRequestApplier::toLifeDate),
                change(payload, "death", ChangeRequestApplier::toLifeDate),
                change(payload, "nativePlace", ChangeRequestApplier::toText),
                change(payload, "currentPlaceProvince", ChangeRequestApplier::toText),
                FieldChange.keep(),                              // currentPlaceFull
                change(payload, "occupation", ChangeRequestApplier::toText),
                FieldChange.keep(),                              // biography
                FieldChange.keep(),                              // avatarKey
                FieldChange.keep(),                              // primaryBranchId - chuyen chi rieng
                FieldChange.keep(),                              // contact - du lieu Tang 3
                FieldChange.keep(),                              // attributes
                FieldChange.keep(),                              // privacyConsent - y chi cua chinh chu the
                false,                                           // khong tu ghi de canh bao ky huy
                note);
    }

    /** Khoá vắng mặt ⇒ {@link FieldChange#keep()}; có mặt ⇒ ghi đè (kể cả bằng {@code null}). */
    private static <T> FieldChange<T> change(Map<String, Object> payload, String key,
                                             java.util.function.Function<Object, T> parser) {
        if (!payload.containsKey(key)) {
            return FieldChange.keep();
        }
        Object raw = payload.get(key);
        return raw == null ? FieldChange.clear() : FieldChange.set(parser.apply(raw));
    }

    private static String toText(Object raw) {
        String text = String.valueOf(raw).trim();
        // Chuoi rong tu bieu mau la y dinh "xoa truong nay", khong phai ghi mot chuoi rong.
        return text.isEmpty() ? null : text;
    }

    private static Gender toGender(Object raw) {
        return Gender.fromCode(String.valueOf(raw));
    }

    /**
     * Mốc song lịch. Hợp đồng đã bảo đảm có ít nhất một trong hai lịch và các khoảng giá trị hợp
     * lệ, nên ở đây chỉ còn việc dựng VO.
     *
     * <p>Cờ {@code leap} phải được giữ đúng: bỏ qua tháng nhuận thì ngày giỗ lệch nguyên một tháng
     * mà không có lỗi nào được ném ra.</p>
     */
    private static LifeDate toLifeDate(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new DomainException(GenealogyProblemCodes.VALIDATION_FAILED,
                    "Moc thoi gian trong de nghi dinh chinh khong dung dang DateDual");
        }
        LocalDate solar = toSolar(map.get("solar"));
        LunarDate lunar = toLunar(map.get("lunar"));
        if (solar == null && lunar == null) {
            return null;
        }
        DatePrecision precision = toPrecision(map.get("precision"));
        return LifeDate.of(solar, lunar, precision);
    }

    private static LocalDate toSolar(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(raw));
        } catch (DateTimeParseException ex) {
            throw new DomainException(GenealogyProblemCodes.VALIDATION_FAILED,
                    "Ngay duong trong de nghi dinh chinh khong phai ISO yyyy-MM-dd: " + raw, ex);
        }
    }

    private static LunarDate toLunar(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        Integer year = toInt(map.get("year"));
        Integer month = toInt(map.get("month"));
        Integer day = toInt(map.get("day"));
        if (year == null || month == null || day == null) {
            return null;
        }
        return new LunarDate(year, month, day, Boolean.TRUE.equals(map.get("leap")));
    }

    private static DatePrecision toPrecision(Object raw) {
        if (raw == null) {
            return DatePrecision.DAY;
        }
        try {
            return DatePrecision.valueOf(String.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            return DatePrecision.DAY;
        }
    }

    private static Integer toInt(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------------------
    // Nhật ký
    // -------------------------------------------------------------------------------------

    /**
     * Ghi chú đi vào {@code audit_log.note} của dòng {@code Person/UPDATE}.
     *
     * <h2>Vì sao phải nhét vào note</h2>
     * {@code audit_log} có {@code actor_user_id} — người <b>thực hiện</b> lệnh ghi, ở đây là Trưởng
     * chi duyệt — nhưng không có cột nào cho "thay mặt ai". Thêm cột là sửa V5 đã chạy trên dữ liệu
     * thật. Vì vậy dấu vết người đề nghị đi vào {@code note}, theo một khuôn cố định để về sau còn
     * lọc được bằng {@code LIKE}.
     *
     * <p>Kết quả: đọc {@code audit_log} phân biệt được ba thứ khác nhau —
     * {@code Person/UPDATE} không có khuôn này là <b>Trưởng chi tự sửa</b>; có khuôn này là
     * <b>Trưởng chi duyệt đề nghị của người khác</b>; còn dòng {@code ChangeRequest/APPROVE} cùng
     * transaction cho biết ai quyết và quyết cái gì.</p>
     *
     * <p><b>Không</b> chép giá trị Tầng 3 vào đây: {@code audit_log} là bảng chỉ ghi thêm, lọt vào
     * là không gỡ ra được. Chỉ có định danh yêu cầu và hai khoá tài khoản.</p>
     */
    private static String auditNote(ChangeRequestApprovedEvent event) {
        return "Ap dung yeu cau dinh chinh " + event.changeRequestId()
                + " | nguoi de nghi: app_user " + event.requesterAppUserId()
                + " | nguoi duyet: app_user " + event.reviewerAppUserId();
    }
}
