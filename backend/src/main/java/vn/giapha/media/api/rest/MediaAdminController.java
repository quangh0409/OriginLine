package vn.giapha.media.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.media.api.rest.dto.MediaDtos.GcResultDto;
import vn.giapha.media.application.MediaGcService;
import vn.giapha.media.application.view.GcResult;

/**
 * Chạy tay lượt dọn tệp mồ côi — {@code POST /api/v1/admin/media/gc}.
 *
 * <h2>Vì sao có nút này khi đã có công việc đêm</h2>
 * Cùng lý do mà {@code POST /api/v1/admin/reminders/dispatch} tồn tại: <b>một đường ống chỉ chạy
 * được lúc 3 giờ sáng là một đường ống không ai kiểm được</b>. Bài kiểm tích hợp của đợt này lái
 * đúng lối này qua HTTP để chứng minh rằng gỡ một bài thật sự làm tệp thành mồ côi và đường dọn
 * thật sự dọn — không một bài kiểm nào ghi thẳng vào bảng để đi qua một cửa.
 *
 * <p>Ngoài ra nó còn phục vụ một tình huống vận hành thật: sau một sự cố kho, người quản trị muốn
 * chạy lại ngay thay vì đợi tới đêm, vì những hàng "xoá byte thất bại" của lượt trước vẫn đang giữ
 * dữ liệu mà Nghị định 13/2023 nói phải biến mất.</p>
 *
 * <h2>Chỉ Quản trị hệ thống</h2>
 * Việc này chạm vào tệp của <b>mọi</b> chi cùng lúc; không có cách nào giới hạn nó theo một
 * {@code ltree} nào cả, nên nó thuộc về vai toàn cục — đúng lập luận mà
 * {@code AdminReminderController} đã ghi.
 */
@RestController
@RequestMapping("/api/v1/admin/media")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin-media", description = "Don tep mo coi (chi System Admin)")
public class MediaAdminController {

    private static final Logger log = LoggerFactory.getLogger(MediaAdminController.class);

    private final MediaGcService gc;

    public MediaAdminController(MediaGcService gc) {
        this.gc = gc;
    }

    @PostMapping("/gc")
    @Operation(summary = "Chay mot luot don tep mo coi ngay bay gio")
    public ResponseEntity<GcResultDto> sweep() {
        GcResult result = gc.sweep();
        log.info("POST /api/v1/admin/media/gc -> {} phieu qua han, {} tep mo coi, {} loi kho",
                result.expiredTickets(), result.orphanAssets(), result.storageFailures());
        return ResponseEntity.ok(new GcResultDto(result.expiredTickets(), result.orphanAssets(),
                result.storageFailures()));
    }
}
