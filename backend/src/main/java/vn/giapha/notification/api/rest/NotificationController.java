package vn.giapha.notification.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.notification.api.rest.dto.NotificationCategoryApiMapper;
import vn.giapha.notification.api.rest.dto.NotificationPageDto;
import vn.giapha.notification.api.rest.dto.NotificationReadAllResultDto;
import vn.giapha.notification.api.rest.dto.NotificationReadResultDto;
import vn.giapha.notification.application.InboxQueryService;

/**
 * Trung tâm thông báo in-app — {@code /api/v1/notifications}.
 *
 * <p>Hộp thư của <b>chính người đang đăng nhập</b>, suy từ JWT. Cố ý <b>không có</b> tham số
 * {@code userId} và sẽ không bao giờ có: hộp thư người khác không phải thứ để lộ qua tham số, và
 * một tham số như vậy sẽ vĩnh viễn là chỗ để quên kiểm tra quyền.</p>
 *
 * <p>In-app là <b>nguồn chân lý</b> của kênh thông báo MVP: người từ chối quyền Web Push, người
 * dùng iPhone chưa cài PWA vào màn hình chính, người tắt thông báo hệ điều hành — tất cả vẫn nhận
 * đủ ở đây.</p>
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Validated
@Tag(name = "notifications", description = "Trung tam thong bao in-app")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final InboxQueryService inbox;

    public NotificationController(InboxQueryService inbox) {
        this.inbox = inbox;
    }

    @GetMapping
    @Operation(summary = "Hop thu thong bao in-app cua chinh minh",
            description = "unreadCount la tong so chua doc cua TOAN hop thu, khong phu thuoc bo loc.")
    public ResponseEntity<NotificationPageDto> list(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        NotificationPageDto body = NotificationPageDto.from(inbox.list(status,
                NotificationCategoryApiMapper.toDomain(category), page, size, sort));
        log.debug("GET /api/v1/notifications -> {} tin, {} chua doc",
                body.items().size(), body.unreadCount());
        return ResponseEntity.ok(body);
    }

    /**
     * Đánh dấu đã đọc — <b>idempotent</b>. Gọi lại trên tin đã đọc vẫn trả 200 với {@code readAt}
     * giữ nguyên lần đầu; giao diện cứ gọi thoải mái, không cần kiểm tra trạng thái trước.
     *
     * <p>Tin của người khác trả <b>404 chứ không 403</b>: 403 xác nhận rằng id đó có tồn tại.</p>
     */
    /**
     * Đánh dấu <b>toàn bộ</b> hộp thư của chính mình là đã đọc — <b>idempotent</b>.
     *
     * <p>Đường dẫn là {@code /read-all} chứ không phải {@code /{id}/read} với một id đặc biệt: một
     * id ma ("all") trong đường dẫn tài nguyên là thứ sớm muộn cũng va vào một UUID thật.</p>
     */
    @PostMapping("/read-all")
    @Operation(summary = "Danh dau da doc tat ca thong bao cua chinh minh (idempotent)")
    public ResponseEntity<NotificationReadAllResultDto> markAllRead() {
        NotificationReadAllResultDto body = NotificationReadAllResultDto.from(inbox.markAllRead());
        log.debug("POST /api/v1/notifications/read-all -> {} tin", body.markedCount());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Danh dau mot thong bao la da doc (idempotent)")
    public ResponseEntity<NotificationReadResultDto> markRead(@PathVariable UUID id) {
        return ResponseEntity.ok(NotificationReadResultDto.from(inbox.markRead(id)));
    }
}
