package vn.giapha.events.infrastructure.jpa;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventType;
import vn.giapha.shared.vo.LunarDate;

/**
 * Chuyển đổi {@link EventJpaEntity} ⇄ {@link Event}.
 *
 * <p>Cột {@code lunar_date} là {@code jsonb} {@code {year, month, day, leap}} — cùng hình dạng với
 * {@code person.birth_lunar}/{@code death_lunar} của {@code genealogy}, để hai bên so được với nhau
 * mà không phải chuyển đổi. Ràng buộc {@code ck_event_date_source} bắt buộc có {@code day} và
 * {@code month}; {@code year} vắng nghĩa là <b>lặp lại hằng năm</b>, và {@link LunarDate} biểu diễn
 * điều đó bằng {@code year = 0}.</p>
 */
@Component
public class EventMapper {

    private static final Logger log = LoggerFactory.getLogger(EventMapper.class);

    /**
     * Bản chiếu JPA → domain, <b>ném</b> khi dòng dữ liệu không dựng được thành {@link Event}.
     *
     * <p>Dùng cho lối ghi (vừa ghi xong thì đối tượng chắc chắn hợp lệ) và cho lối đọc <i>một</i>
     * bản ghi theo khoá — ở đó im lặng nuốt lỗi sẽ trả 404 cho một dòng có thật, và người vận hành
     * không bao giờ biết có dòng hỏng. Lối đọc <b>danh sách</b> phải dùng
     * {@link #toDomainOrSkip(EventJpaEntity)}.</p>
     */
    public Event toDomain(EventJpaEntity entity) {
        return Event.builder(entity.getId())
                .personId(entity.getPersonId())
                .type(EventType.fromDbValue(entity.getEventType()))
                .title(entity.getTitle())
                .description(entity.getDescription())
                .lunarDate(toLunar(entity.getId(), entity.getLunarDate()))
                .solarDate(entity.getSolarDate())
                .lunarBased(entity.isLunarBased())
                .recurring(entity.isRecurring())
                .targetBranchId(entity.getTargetBranchId())
                .clanLevel(entity.isClanLevel())
                .location(entity.getLocation())
                .deleted(entity.isDeleted())
                .version(entity.getVersion())
                .build();
    }

    /**
     * Như {@link #toDomain}, nhưng một dòng hỏng trả {@link Optional#empty()} kèm {@code WARN}
     * thay vì ném.
     *
     * <h2>Vì sao lối đọc danh sách phải bỏ qua, không được ném</h2>
     * {@code ck_event_date_source} (V4) chỉ đòi {@code lunar_date} <b>có khoá</b> {@code day} và
     * {@code month} — không kiểm khoảng giá trị — nên {@code {"month": 13}} là một dòng hợp lệ với
     * cơ sở dữ liệu mà {@link vn.giapha.shared.vo.LunarDate} từ chối. {@link #toLunar} đã có chính
     * sách "cảnh báo rồi đi tiếp" cho đúng ca ấy, nhưng nó trả {@code null}, và constructor của
     * {@link Event} lại từ chối một sự kiện âm lịch không có ngày — nên chính sách kia bị vô hiệu
     * hoá và <b>một</b> dòng hỏng đủ làm {@code GET /api/v1/events} ném lỗi với <b>mọi</b> người,
     * đồng thời giết bộ sinh lời nhắc ban đêm (nó đọc theo lô).
     *
     * <p>Một dòng hỏng là chuyện của một dòng. Bỏ qua nó và ghi {@code WARN} kèm khoá là cách duy
     * nhất giữ được cả hai: cả họ vẫn đọc được lịch việc họ, và người vận hành vẫn có đủ thông tin
     * để đi sửa đúng dòng ấy.</p>
     */
    public Optional<Event> toDomainOrSkip(EventJpaEntity entity) {
        try {
            return Optional.of(toDomain(entity));
        } catch (IllegalArgumentException ex) {
            log.warn("event {}: dong du lieu khong dung duoc thanh Event ({}) - bo qua o loi doc"
                    + " danh sach", entity.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Chép trạng thái domain lên bản chiếu JPA — chiều ghi.
     *
     * <p><b>Không</b> chạm {@code version}: giá trị ấy do {@code EventRepositoryAdapter} đặt trước
     * khi ghi (nó là phiên bản <i>kỳ vọng</i> lấy từ {@code If-Match}, không phải một trường dữ
     * liệu thường), rồi Hibernate tự tăng lúc flush. Chép nó ở đây là vô hiệu hoá khoá lạc quan
     * theo một cách rất khó nhìn ra.</p>
     */
    public void applyToEntity(Event event, EventJpaEntity entity) {
        entity.setId(event.id());
        entity.setPersonId(event.personId());
        entity.setEventType(event.type().name());
        entity.setTitle(event.title());
        entity.setDescription(event.description());
        entity.setLunarDate(toLunarJson(event.lunarDate()));
        entity.setSolarDate(event.solarDate());
        entity.setLunarBased(event.isLunarBased());
        entity.setRecurring(event.isRecurring());
        entity.setTargetBranchId(event.targetBranchId());
        entity.setClanLevel(event.isClanLevel());
        entity.setLocation(event.location());
        entity.setDeleted(event.isDeleted());
    }

    /**
     * {@link LunarDate} → {@code jsonb}.
     *
     * <p><b>{@code year} vắng mặt khi bằng 0, không ghi số 0.</b> V4 định nghĩa "year rỗng = lặp
     * lại hằng năm" và {@code ck_event_oneoff_lunar_year} (V18) kiểm bằng
     * {@code jsonb_exists(lunar_date, 'year')}; ghi {@code "year": 0} sẽ qua được ràng buộc ấy
     * trong khi 0 không phải là một năm âm nào cả, và sự kiện một lần sẽ mang một ngày không quy
     * đổi được.</p>
     */
    public Map<String, Object> toLunarJson(LunarDate lunar) {
        if (lunar == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        if (lunar.year() > 0) {
            json.put("year", lunar.year());
        }
        json.put("month", lunar.month());
        json.put("day", lunar.day());
        json.put("leap", lunar.leapMonth());
        return json;
    }

    /**
     * Dữ liệu {@code jsonb} hỏng (thiếu ngày/tháng, hoặc tháng ngoài 1–12) trả {@code null} kèm
     * {@code WARN} thay vì ném lỗi: một dòng hỏng không được làm sập cả job sinh lịch nhắc, và
     * {@code OccurrenceResolver} đã ghi log riêng khi ngày âm hiệu lực rỗng.
     */
    private LunarDate toLunar(Object eventId, Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        Integer day = intOf(json.get("day"));
        Integer month = intOf(json.get("month"));
        if (day == null || month == null) {
            log.warn("event {}: lunar_date thieu day/month -> {}", eventId, json);
            return null;
        }
        Integer year = intOf(json.get("year"));
        boolean leap = Boolean.TRUE.equals(json.get("leap"));
        try {
            return new LunarDate(year == null ? 0 : year, month, day, leap);
        } catch (IllegalArgumentException ex) {
            log.warn("event {}: lunar_date khong hop le ({}) - {}", eventId, json, ex.getMessage());
            return null;
        }
    }

    private static Integer intOf(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}
