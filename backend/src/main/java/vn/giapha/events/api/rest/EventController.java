package vn.giapha.events.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.events.api.rest.dto.BranchRefDto;
import vn.giapha.events.api.rest.dto.EventDto;
import vn.giapha.events.api.rest.dto.EventPageDto;
import vn.giapha.events.api.rest.dto.EventPersonDto;
import vn.giapha.events.api.rest.dto.LunarDateDto;
import vn.giapha.events.api.rest.dto.PageMetaDto;
import vn.giapha.events.application.EventQueryService;
import vn.giapha.events.application.command.EventQuery;
import vn.giapha.events.application.view.EventPageView;
import vn.giapha.events.application.view.EventView;

/**
 * REST danh sách giỗ/lễ — {@code GET /api/v1/events} (FR-2.3).
 *
 * <p><b>Ngày âm là dữ liệu gốc; ngày dương do máy chủ quy đổi.</b> Giao diện tuyệt đối không tự quy
 * đổi âm–dương ở client: lệch cờ tháng nhuận là bug âm thầm nhất của cả hệ thống này — không ném
 * lỗi, không ghi log, chỉ khiến cả họ đi giỗ nhầm ngày.</p>
 *
 * <p>Khoảng lọc {@code from}/{@code to} áp lên ngày dương của <b>lần xảy ra sắp tới</b>, vì đó là
 * thứ người dùng nhìn trên lịch. {@code upcomingDays} là đường tắt; gửi kèm thì {@code from}/{@code to}
 * thắng.</p>
 *
 * <p>Sự kiện gắn với người <b>còn sống</b> (mừng thọ) tuân thủ phân tầng riêng tư: Khách không
 * thấy.</p>
 */
@RestController
@RequestMapping("/api/v1/events")
@Validated
@Tag(name = "events", description = "Gio, chap, le dong ho")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    private final EventQueryService eventQuery;

    public EventController(EventQueryService eventQuery) {
        this.eventQuery = eventQuery;
    }

    @GetMapping
    @Operation(summary = "Danh sach gio, chap, le dong ho",
            description = "Ngay am la du lieu goc; nextOccurrenceSolar do backend quy doi (Ho Ngoc Duc, GMT+7).")
    public ResponseEntity<EventPageDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Min(1) @Max(400) Integer upcomingDays,
            @RequestParam(required = false) List<String> eventType,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID personId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "nextOccurrenceSolar,asc") String sort) {

        EventPageView view = eventQuery.list(new EventQuery(from, to, upcomingDays,
                EventTypeApiMapper.toDomain(eventType), branchId, personId, page, size, sort));
        log.debug("GET /api/v1/events -> {} su kien", view.items().size());
        return ResponseEntity.ok(toDto(view));
    }

    /**
     * Chi tiết một sự kiện — điểm đến của {@code deepLink} trong thông báo nhắc giỗ
     * ({@code /events/{id}}). Không có endpoint này thì bấm vào thông báo là rơi vào trang trắng.
     *
     * <p>Sự kiện đã xoá mềm, hoặc sự kiện của người còn sống khi người gọi là Khách, đều trả
     * <b>404</b> — không phải 403, vì 403 đã xác nhận id ấy có tồn tại.</p>
     */
    @GetMapping("/{id}")
    @Operation(summary = "Chi tiet mot su kien gio/le",
            description = "nextOccurrenceSolar la null neu su kien khong con lan xay ra nao phia truoc.")
    public ResponseEntity<EventDto> byId(@PathVariable UUID id) {
        return ResponseEntity.ok(toDto(eventQuery.findById(id)));
    }

    private static EventPageDto toDto(EventPageView view) {
        List<EventDto> items = new ArrayList<>(view.items().size());
        for (EventView event : view.items()) {
            items.add(toDto(event));
        }
        return new EventPageDto(List.copyOf(items), PageMetaDto.from(view.page()));
    }

    private static EventDto toDto(EventView view) {
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
                view.adjustmentNote());
    }
}
