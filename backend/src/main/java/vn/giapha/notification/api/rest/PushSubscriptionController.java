package vn.giapha.notification.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.notification.api.rest.dto.PushSubscriptionCreateRequest;
import vn.giapha.notification.api.rest.dto.PushSubscriptionDto;
import vn.giapha.notification.api.rest.dto.VapidPublicKeyDto;
import vn.giapha.notification.application.PushSubscriptionService;
import vn.giapha.notification.application.view.PushSubscriptionView;

/**
 * Đăng ký Web Push — {@code /api/v1/push}.
 *
 * <h2>Ba điều frontend phải xử lý, đừng để im lặng thất bại</h2>
 * <ul>
 *   <li>iOS chỉ cho Web Push khi PWA <b>đã được cài vào màn hình chính</b> (iOS 16.4+). Không có
 *       hướng dẫn "Thêm vào màn hình chính" thì phần lớn người dùng iPhone sẽ không bao giờ nhận
 *       push — và với dòng họ có kiều bào, đó là một phần lớn người dùng.</li>
 *   <li>Xin quyền <b>đúng lúc</b> (sau khi người dùng xem một hồ sơ hoặc một ngày giỗ), không phải
 *       ngay khi vào trang. Bị từ chối một lần là trình duyệt chặn vĩnh viễn.</li>
 *   <li>Người từ chối quyền vẫn nhận đủ thông báo in-app — push <b>không</b> là điều kiện tiên
 *       quyết của bất cứ thứ gì.</li>
 * </ul>
 *
 * <p>Khi push gateway trả {@code 404}/{@code 410}, backend <b>tự xoá</b> bản ghi và không retry.
 * Frontend có thể thấy một đăng ký biến mất mà không do mình gọi {@code DELETE}.</p>
 */
@RestController
@RequestMapping("/api/v1/push")
@Validated
@Tag(name = "push", description = "Web Push (VAPID)")
public class PushSubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(PushSubscriptionController.class);

    private final PushSubscriptionService subscriptions;

    public PushSubscriptionController(PushSubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    /**
     * Khoá công khai VAPID cho {@code PushManager.subscribe}.
     *
     * <p>Chưa cấu hình khoá thì trả 422 kèm mã lỗi ổn định, để giao diện ẩn công tắc Web Push thay
     * vì hiện một nút bấm vào là hỏng.</p>
     */
    @GetMapping("/public-key")
    @Operation(summary = "Khoa cong khai VAPID (base64url khong padding)")
    public ResponseEntity<VapidPublicKeyDto> publicKey() {
        return ResponseEntity.ok(new VapidPublicKeyDto(subscriptions.vapidPublicKey()));
    }

    /**
     * Đăng ký hoặc cập nhật một thiết bị.
     *
     * <p><b>Idempotent theo {@code endpoint}</b>: gửi lại cùng endpoint trả 200 và cập nhật bản cũ;
     * endpoint mới trả 201 kèm header {@code Location}. Trình duyệt tự cấp lại subscription sau khi
     * xoá cache hoặc cập nhật service worker, nên nếu tạo bản mới mỗi lần thì một người sẽ tích luỹ
     * hàng chục bản ghi cho cùng một máy và nhận đúng ngần ấy thông báo trùng nhau.</p>
     */
    @PostMapping("/subscriptions")
    @Operation(summary = "Dang ky thiet bi nhan Web Push (idempotent theo endpoint)")
    public ResponseEntity<PushSubscriptionDto> register(
            @Valid @RequestBody PushSubscriptionCreateRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgentHeader) {

        String userAgent = request.userAgent() == null ? userAgentHeader : request.userAgent();
        PushSubscriptionService.Registration result = subscriptions.register(
                request.endpoint(), request.keys().p256dh(), request.keys().auth(),
                userAgent, request.expirationTime());

        PushSubscriptionDto body = PushSubscriptionDto.from(result.subscription());
        log.debug("POST /api/v1/push/subscriptions -> {}", result.created() ? "201 tao moi" : "200 cap nhat");
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/push/subscriptions/" + body.id()))
                    .body(body);
        }
        return ResponseEntity.ok(body);
    }

    /** Danh sách thiết bị của chính mình — màn hình cài đặt. */
    @GetMapping("/subscriptions")
    @Operation(summary = "Cac thiet bi da dang ky cua chinh minh")
    public ResponseEntity<List<PushSubscriptionDto>> myDevices(
            @RequestParam(required = false) String currentEndpoint) {
        List<PushSubscriptionView> views = subscriptions.myDevices(currentEndpoint);
        List<PushSubscriptionDto> body = new ArrayList<>(views.size());
        for (PushSubscriptionView view : views) {
            body.add(PushSubscriptionDto.from(view));
        }
        return ResponseEntity.ok(List.copyOf(body));
    }

    /**
     * Huỷ đăng ký của chính mình.
     *
     * <p>Đây là <b>xoá cứng</b>: quy tắc "chỉ xoá mềm" áp cho nhân khẩu trong gia phả, không áp cho
     * dữ liệu kỹ thuật của thiết bị. Đăng ký không tồn tại hoặc thuộc người khác đều trả 404.</p>
     */
    @DeleteMapping("/subscriptions/{id}")
    @Operation(summary = "Huy dang ky Web Push cua chinh minh")
    public ResponseEntity<Void> unregister(@PathVariable UUID id) {
        subscriptions.unregister(id);
        return ResponseEntity.noContent().build();
    }
}
