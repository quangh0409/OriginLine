package vn.giapha.events.infrastructure.jpa;

import java.util.LinkedHashMap;
import java.util.Map;
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
                .build();
    }

    public Map<String, Object> toLunarJson(LunarDate lunar) {
        if (lunar == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("year", lunar.year());
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
