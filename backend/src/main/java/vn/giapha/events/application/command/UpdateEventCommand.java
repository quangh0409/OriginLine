package vn.giapha.events.application.command;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import vn.giapha.events.domain.EventType;
import vn.giapha.shared.vo.LunarDate;

/**
 * Yêu cầu sửa một sự kiện — {@code PATCH /api/v1/events/&#123;id&#125;}.
 *
 * <h2>Vắng mặt ≠ null</h2>
 * {@code PATCH} sửa từng phần, nên phải phân biệt "không gửi trường này" (giữ nguyên) với "gửi
 * trường này bằng null" (xoá giá trị). Một record toàn trường nullable không nói được điều đó: bỏ
 * {@code location} ra khỏi thân yêu cầu và gửi {@code "location": null} sẽ cho cùng một kết quả,
 * nên mọi lần sửa tiêu đề sẽ âm thầm xoá địa điểm. Vì vậy {@link #presentFields} mang <b>tên các
 * khoá thực sự có mặt</b> ở cấp cao nhất của JSON — cùng cách {@code PersonController} đã dùng.
 *
 * @param expectedVersion phiên bản lấy từ {@code If-Match}; lệch ⇒ 409
 */
public record UpdateEventCommand(EventType type,
                                 String title,
                                 String description,
                                 LunarDate lunarDate,
                                 LocalDate solarDate,
                                 Boolean recurringAnnually,
                                 Boolean clanWide,
                                 UUID scopeBranchId,
                                 UUID personId,
                                 String location,
                                 Set<String> presentFields,
                                 long expectedVersion) {

    public static final String F_TYPE = "eventType";
    public static final String F_TITLE = "title";
    public static final String F_DESCRIPTION = "description";
    public static final String F_LUNAR_DATE = "lunarDate";
    public static final String F_SOLAR_DATE = "solarDate";
    public static final String F_RECURRING = "recurringAnnually";
    public static final String F_CLAN_WIDE = "clanWide";
    public static final String F_SCOPE_BRANCH = "scopeBranchId";
    public static final String F_PERSON = "personId";
    public static final String F_LOCATION = "location";

    public UpdateEventCommand {
        presentFields = presentFields == null ? Set.of() : Set.copyOf(presentFields);
    }

    public boolean has(String field) {
        return presentFields.contains(field);
    }

    /** Người gửi có đụng tới phạm vi hay không — quyết định việc phải kiểm quyền trên <b>cả hai</b> chi. */
    public boolean touchesScope() {
        return has(F_CLAN_WIDE) || has(F_SCOPE_BRANCH);
    }

    /**
     * Người gửi có đụng tới thứ làm <b>lệch ngày hoặc lệch người nhận</b> của các lần xảy ra phía
     * trước hay không.
     *
     * <p>Chỉ khi đó mới phải dọn và dựng lại {@code reminder_job}. Sửa mỗi tiêu đề thì không: xoá
     * rồi dựng lại lịch nhắc vô cớ là một lần ghi thừa, và nếu lượt dựng lại hỏng thì cả chi mất
     * lời nhắc vì một lần sửa chính tả.</p>
     */
    public boolean touchesSchedule() {
        return has(F_LUNAR_DATE) || has(F_SOLAR_DATE) || has(F_RECURRING)
                || has(F_PERSON) || has(F_TYPE) || touchesScope();
    }
}
