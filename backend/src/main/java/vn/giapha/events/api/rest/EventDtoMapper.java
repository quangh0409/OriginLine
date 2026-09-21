package vn.giapha.events.api.rest;

import java.util.ArrayList;
import java.util.List;
import vn.giapha.events.api.rest.dto.BranchRefDto;
import vn.giapha.events.api.rest.dto.EventDto;
import vn.giapha.events.api.rest.dto.EventPageDto;
import vn.giapha.events.api.rest.dto.EventPersonDto;
import vn.giapha.events.api.rest.dto.LunarDateDto;
import vn.giapha.events.api.rest.dto.PageMetaDto;
import vn.giapha.events.application.view.EventPageView;
import vn.giapha.events.application.view.EventView;

/**
 * {@code EventView} → {@code EventDto}, dùng chung cho <b>cả lối đọc lẫn lối ghi</b>.
 *
 * <p>Tách ra khỏi {@code EventController} khi lối ghi xuất hiện: hai bản chuyển đổi cho cùng một
 * tài nguyên sẽ lệch nhau đúng vào ngày một bên được thêm trường, và triệu chứng là màn tạo sự kiện
 * hiển thị khác màn chi tiết cho cùng một bản ghi vừa lưu xong.</p>
 */
final class EventDtoMapper {

    private EventDtoMapper() {
    }

    static EventPageDto toDto(EventPageView view) {
        List<EventDto> items = new ArrayList<>(view.items().size());
        for (EventView event : view.items()) {
            items.add(toDto(event));
        }
        return new EventPageDto(List.copyOf(items), PageMetaDto.from(view.page()));
    }

    static EventDto toDto(EventView view) {
        return new EventDto(
                view.id(),
                EventTypeApiMapper.toApi(view.type(), view.clanLevel()),
                view.title(),
                EventPersonDto.from(view.subject()),
                LunarDateDto.from(view.lunarDate()),
                view.nextOccurrenceSolar(),
                view.nextOccurrenceLunarYear(),
                view.daysUntil(),
                BranchRefDto.from(view.targetBranch()),
                view.clanLevel(),
                view.reminderOffsets(),
                view.location(),
                // graveId: mộ phần liên quan là hạng mục Giai đoạn 3 (context heritage chưa có bảng).
                null,
                view.note(),
                view.adjustmentNote(),
                view.solarDate(),
                view.lunarBased(),
                view.recurringAnnually(),
                view.version());
    }

    /** {@code ETag} = phiên bản khoá lạc quan, quay lại qua {@code If-Match} khi {@code PATCH}. */
    static String etagOf(EventView view) {
        return "\"" + view.version() + "\"";
    }
}
