package vn.giapha.notification.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.notification.api.rest.dto.WebPushConfigStatusDto;
import vn.giapha.notification.api.rest.dto.WebPushTestSendResultDto;
import vn.giapha.notification.application.WebPushConfigService;
import vn.giapha.notification.application.WebPushTestSendService;

/**
 * Chẩn đoán kênh Web Push — {@code /api/v1/admin/notifications/webpush}.
 *
 * <h2>Vì sao có endpoint này</h2>
 * Giao diện bảo thành viên "xin báo quản trị viên" khi máy chủ chưa có khoá VAPID. Lời khuyên đó
 * chỉ dùng được nếu quản trị viên có chỗ để kiểm chứng và một danh sách việc cụ thể. Trước đây
 * không có: dấu vết duy nhất là một dòng {@code WARN} lúc khởi động, còn
 * {@code GET /api/v1/push/public-key} thì trả 422 cho <i>mọi</i> nguyên nhân — thiếu khoá công
 * khai, thiếu khoá riêng, hay kênh bị tắt tay đều ra cùng một phản hồi.
 *
 * <h2>Chỉ System Admin, và không có đường ghi khoá</h2>
 * {@code hasRole('ADMIN')} — vai <b>kỹ thuật toàn hệ thống</b>, không phải chức danh dòng tộc, cùng
 * ranh giới mà {@code SecurityConfig} đã kẻ cho {@code /actuator} và
 * {@code /api/v1/admin/reminders}. Hội đồng Tộc biểu là thẩm quyền nội dung, còn đây là bề mặt vận
 * hành.
 *
 * <p><b>Không endpoint nào ở đây nhận khoá riêng VAPID</b>, và sẽ không có: nhận khoá qua HTTP thì
 * máy chủ phải lưu nó xuống một chỗ nào đó, và chỗ đó sẽ theo bản sao lưu đi khắp nơi. Khoá chỉ vào
 * hệ thống bằng biến môi trường. Endpoint duy nhất có tác dụng phụ là {@code POST /test-send}, và
 * nó chỉ gửi cho chính người đang gọi.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/notifications/webpush")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin-webpush", description = "Chan doan cau hinh Web Push (chi System Admin)")
public class AdminWebPushController {

    private static final Logger log = LoggerFactory.getLogger(AdminWebPushController.class);

    private final WebPushConfigService configService;
    private final WebPushTestSendService testSend;

    public AdminWebPushController(WebPushConfigService configService,
                                  WebPushTestSendService testSend) {
        this.configService = configService;
        this.testSend = testSend;
    }

    /**
     * Cấu hình Web Push đã sẵn sàng chưa, và nếu chưa thì phải làm gì.
     *
     * <p>Luôn trả {@code 200}: "chưa cấu hình" là một <b>câu trả lời</b>, không phải một lỗi. Trả
     * 4xx ở đây sẽ khiến chính công cụ chẩn đoán trở thành thứ cần chẩn đoán.</p>
     */
    @GetMapping
    @Operation(summary = "Tinh trang cau hinh VAPID va cac buoc can lam neu chua san sang")
    public ResponseEntity<WebPushConfigStatusDto> status() {
        WebPushConfigStatusDto body = WebPushConfigStatusDto.from(configService.status());
        log.debug("GET /api/v1/admin/notifications/webpush -> ready={}", body.ready());
        return ResponseEntity.ok(body);
    }

    /**
     * Gửi một tin thử tới <b>chính thiết bị của người đang gọi</b>.
     *
     * <p>"Đã cấu hình khoá" và "thông báo tới được máy" là hai việc khác nhau. Không có nút này thì
     * cách duy nhất để biết đường gửi còn sống là đợi tới ngày giỗ — tức là biết khi đã muộn.</p>
     *
     * <p><b>Không nhận tham số người nhận.</b> Một endpoint quản trị gửi được cho người khác sẽ sớm
     * được dùng để "thử" trên điện thoại của người trong họ lúc nửa đêm.</p>
     *
     * <p>Luôn {@code 200} kèm kết quả <b>thật</b>: {@code outcome=PERMANENT} nghĩa là push service
     * đã từ chối máy chủ này (gần như luôn là VAPID sai), {@code SKIPPED} kèm
     * {@code deviceCount=0} nghĩa là chính người gọi chưa bật công tắc trên máy nào.</p>
     */
    @PostMapping("/test-send")
    @Operation(summary = "Gui thu mot thong bao day toi thiet bi cua chinh minh")
    public ResponseEntity<WebPushTestSendResultDto> testSend() {
        WebPushTestSendResultDto body = WebPushTestSendResultDto.from(testSend.sendToSelf());
        log.info("POST /api/v1/admin/notifications/webpush/test-send -> outcome={} thiet bi={}",
                body.outcome(), body.deviceCount());
        return ResponseEntity.ok(body);
    }
}
