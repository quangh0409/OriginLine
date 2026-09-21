package vn.giapha.events.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.events.api.rest.dto.EventScopeBackfillResultDto;
import vn.giapha.events.application.EventScopeBackfillResult;
import vn.giapha.events.application.EventScopeBackfillService;

/**
 * Dọn phạm vi người nhận của sự kiện cũ — {@code POST /api/v1/admin/events/backfill-scope}.
 *
 * <h2>AI chạy nó, và khi nào</h2>
 * <b>Quản trị hệ thống hoặc Hội đồng Tộc biểu</b>, <b>một lần sau khi nâng cấp</b> lên bản có lối
 * ghi sự kiện thủ công, rồi bất cứ khi nào bản báo cáo cho thấy có thông báo gửi cho cả họ mà lẽ ra
 * chỉ một chi. Thứ tự đúng: gọi với {@code dryRun=true} để đọc con số, đối chiếu với danh sách sự
 * kiện, rồi mới gọi thật.
 *
 * <p>Khác {@code AdminReminderController} (chỉ {@code ADMIN}): lượt chạy này đổi <b>ai được nhắc</b>
 * — một câu hỏi nội dung của dòng họ, không phải một nút vận hành — nên Hội đồng Tộc biểu cũng phải
 * bấm được. Nó <b>không</b> mở cho Trưởng chi: các dòng nó chạm vào hôm nay đang nhắc <i>cả họ</i>,
 * nên hậu quả vượt khỏi mọi phạm vi chi, và không có {@code ltree} nào giới hạn được nó.</p>
 *
 * <p>Chạy lại được nhiều lần: lượt thứ hai trả {@code changed = 0}. Xem
 * {@link EventScopeBackfillService}.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/events")
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'COUNCIL')")
@Tag(name = "admin-events", description = "Don du lieu su kien cu (Quan tri he thong / Hoi dong)")
public class AdminEventScopeController {

    private static final Logger log = LoggerFactory.getLogger(AdminEventScopeController.class);

    private final EventScopeBackfillService backfill;

    public AdminEventScopeController(EventScopeBackfillService backfill) {
        this.backfill = backfill;
    }

    @PostMapping("/backfill-scope")
    @Operation(summary = "Gan pham vi cho su kien cu khong co chi lan co cap dong ho",
            description = "Chay lai duoc nhieu lan: luot thu hai tra changed=0."
                    + " dryRun=true chi dem, khong ghi — hay chay no truoc.")
    public ResponseEntity<EventScopeBackfillResultDto> backfillScope(
            @RequestParam(defaultValue = "false") boolean dryRun) {

        EventScopeBackfillResult result = backfill.run(dryRun);
        log.info("POST /api/v1/admin/events/backfill-scope?dryRun={} -> quet {}, sua {}",
                dryRun, result.scanned(), result.changed());
        return ResponseEntity.ok(EventScopeBackfillResultDto.from(result));
    }
}
