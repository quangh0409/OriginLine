package vn.giapha.events.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.events.api.rest.dto.ReminderDispatchResultDto;
import vn.giapha.events.api.rest.dto.ReminderGenerationResultDto;
import vn.giapha.events.application.DispatchDueRemindersService;
import vn.giapha.events.application.ReminderGenerationBusyException;
import vn.giapha.events.application.ReminderGenerationResult;
import vn.giapha.events.application.ReminderGenerationTrigger;
import vn.giapha.events.application.ReminderProperties;
import vn.giapha.shared.api.ProblemTypes;

/**
 * Vận hành đường ống nhắc giỗ bằng tay — {@code /api/v1/admin/reminders}.
 *
 * <h2>Vì sao có endpoint này</h2>
 * Hai job của {@code events} chạy theo lịch: sinh lịch nhắc lúc 01:30, đẩy tin 5 phút một lần. Trên
 * môi trường demo/staging dựng lên rồi tắt trong ngày, bảng {@code reminder_job} vì thế <b>rỗng</b>
 * và cả đường ống không trình diễn được đầu-cuối. Hai nút bấm ở đây thay cho việc sửa cron rồi quên
 * trả lại — sai lầm đó sẽ theo cấu hình đi thẳng ra môi trường thật.
 *
 * <h2>Chỉ System Admin</h2>
 * {@code hasRole('ADMIN')} — vai <b>kỹ thuật toàn hệ thống</b>, không phải chức danh dòng tộc. Cố ý
 * <b>không</b> mở cho {@code COUNCIL} (Hội đồng Tộc biểu): hội đồng là thẩm quyền <i>nội dung</i>
 * của dòng họ, còn đây là bề mặt <i>vận hành</i> — cùng ranh giới mà {@code SecurityConfig} đã kẻ
 * cho {@code /actuator}. Và đây không phải phạm vi chi/ngành: job quét toàn bộ dòng họ nên không có
 * cách nào giới hạn nó theo một {@code ltree} nào cả; vì vậy nó thuộc về vai toàn cục.
 *
 * <p><b>Cảnh báo đã ghi thẳng vào hợp đồng:</b> ngày tham chiếu trong quá khứ sinh ra job có
 * {@code fire_at} đã qua, và lượt đẩy kế tiếp sẽ <b>gửi thật</b> tới người trong họ.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/reminders")
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin-reminders", description = "Van hanh duong ong nhac gio (chi System Admin)")
public class AdminReminderController {

    private static final Logger log = LoggerFactory.getLogger(AdminReminderController.class);

    private final ReminderGenerationTrigger trigger;
    private final DispatchDueRemindersService dispatcher;
    private final ReminderProperties properties;

    public AdminReminderController(ReminderGenerationTrigger trigger,
                                   DispatchDueRemindersService dispatcher,
                                   ReminderProperties properties) {
        this.trigger = trigger;
        this.dispatcher = dispatcher;
        this.properties = properties;
    }

    /**
     * Sinh {@code reminder_job} ngay lập tức thay vì chờ 01:30.
     *
     * <p><b>Idempotent.</b> Gọi lại với cùng {@code referenceDate} trả 200 và
     * {@code createdJobs = 0}; chống trùng do chỉ mục duy nhất
     * {@code ux_reminder_job_occurrence (event_id, occurrence_year, offset_days)} bảo đảm, không
     * phải do một phép kiểm tra ở tầng Java.</p>
     */
    @PostMapping("/generate")
    @Operation(summary = "Sinh lich nhac gio ngay lap tuc (idempotent, chi System Admin)",
            description = "Goi lai voi cung referenceDate se tra createdJobs=0 chu khong sinh trung."
                    + " referenceDate trong qua khu sinh ra job da toi gio -> luot day ke tiep GUI THAT.")
    public ResponseEntity<ReminderGenerationResultDto> generate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate referenceDate) {

        ReminderGenerationResult result = trigger.runNow(referenceDate);
        log.info("POST /api/v1/admin/reminders/generate -> quet {} su kien, tao moi {} job",
                result.scannedEvents(), result.createdJobs());
        return ResponseEntity.ok(ReminderGenerationResultDto.from(result));
    }

    /**
     * Đẩy các job đã tới giờ lên RabbitMQ ngay, thay vì chờ lượt quét 5 phút.
     *
     * <p>Luồng HTTP <b>không</b> chờ gửi: ở đây chỉ ghi tin vào broker rồi trả về; gateway Zalo /
     * Web Push chết cũng không giữ request nào lại.</p>
     */
    @PostMapping("/dispatch")
    @Operation(summary = "Day lich nhac da toi gio len hang doi ngay (chi System Admin)",
            description = "Chi ghi tin vao RabbitMQ roi tra ve - khong cho gateway gui xong.")
    public ResponseEntity<ReminderDispatchResultDto> dispatch() {
        Instant now = Instant.now();
        int dispatched = dispatcher.dispatchDueAt(now, LocalDate.now(properties.zone()));
        log.info("POST /api/v1/admin/reminders/dispatch -> day di {} job", dispatched);
        return ResponseEntity.ok(new ReminderDispatchResultDto(dispatched, now));
    }

    /**
     * 409 khi đã có lượt sinh khác đang chạy.
     *
     * <p>Đặt ngay trong controller chứ không thêm vào {@code GlobalExceptionHandler}: đây là tình
     * huống riêng của một endpoint vận hành, không phải một loại lỗi chung của cả hệ thống.
     * {@code @ExceptionHandler} cấp controller được ưu tiên hơn {@code @RestControllerAdvice} nên
     * ngoại lệ này không rơi vào nhánh 500.</p>
     */
    @ExceptionHandler(ReminderGenerationBusyException.class)
    public ProblemDetail handleBusy(ReminderGenerationBusyException ex, HttpServletRequest request) {
        log.info("409 {} - {}", request.getRequestURI(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(ProblemTypes.CONFLICT);
        problem.setTitle("Đang có lượt sinh lịch nhắc khác chạy");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ReminderGenerationBusyException.CODE);
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
