package vn.giapha.membership.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.membership.api.rest.dto.ReviewChangeRequestDto;
import vn.giapha.membership.api.rest.dto.SubmitChangeRequestDto;
import vn.giapha.membership.application.ChangeRequestService;
import vn.giapha.membership.application.ChangeRequestView;
import vn.giapha.membership.application.command.ReviewChangeRequestCommand;
import vn.giapha.membership.application.command.SubmitChangeRequestCommand;
import vn.giapha.membership.domain.ChangeRequestType;

/**
 * Luồng đề nghị – duyệt đính chính — {@code /api/v1/change-requests}.
 *
 * <h2>Kiểm quyền nằm ở tầng use case, không ở đây</h2>
 * Controller cố ý <b>không</b> mang {@code @PreAuthorize} cho việc duyệt. Lý do: quyền duyệt phụ
 * thuộc vào <i>chi đích của từng yêu cầu</i>, mà controller chưa nạp yêu cầu lên thì chưa biết chi
 * ấy là gì. Một {@code @PreAuthorize("hasRole('BRANCH_HEAD')")} ở đây sẽ cho một Trưởng chi đi qua
 * cửa rồi mới bị chặn ở trong — và tệ hơn, nó tạo cảm giác đã kiểm quyền xong.
 *
 * <p>Cửa kiểm thật là {@code ChangeRequestService.review()} → {@code BranchScopeGuard} → so
 * {@code ltree}. Đặt ở tầng use case cũng có nghĩa lối vào GraphQL hay một job nền sau này đều đi
 * qua đúng phép kiểm đó.</p>
 *
 * <h2>Mã HTTP</h2>
 * Sai vai → {@code 403 FORBIDDEN}. Đúng vai nhưng sai nhánh → {@code 403 BRANCH_SCOPE_VIOLATION} —
 * hai mã khác nhau vì hai tình huống dẫn tới hai hành động khác nhau của người dùng. Yêu cầu đã
 * chốt → {@code 422 CHANGE_REQUEST_CLOSED}. Tự duyệt đề nghị của mình →
 * {@code 403 SELF_REVIEW_FORBIDDEN}.
 */
@RestController
@RequestMapping("/api/v1/change-requests")
@Tag(name = "Change requests", description = "Yêu cầu đính chính dữ liệu phả hệ")
public class ChangeRequestController {

    private static final Logger log = LoggerFactory.getLogger(ChangeRequestController.class);

    private final ChangeRequestService changeRequests;

    public ChangeRequestController(ChangeRequestService changeRequests) {
        this.changeRequests = changeRequests;
    }

    /** Gửi đề nghị. Mọi thành viên đã có tài khoản đều gửi được, không kiểm phạm vi ở bước này. */
    @PostMapping
    @Operation(summary = "Gửi yêu cầu đính chính")
    public ResponseEntity<ChangeRequestView> submit(@Valid @RequestBody SubmitChangeRequestDto body) {
        SubmitChangeRequestCommand command = new SubmitChangeRequestCommand(
                ChangeRequestType.of(body.requestType()), body.personId(), body.targetBranchId(),
                body.payload() == null ? Map.of() : body.payload(), body.reason());
        ChangeRequestView created = changeRequests.submit(command);
        log.debug("POST /api/v1/change-requests -> {}", created.id());
        return ResponseEntity.created(URI.create("/api/v1/change-requests/" + created.id()))
                .body(created);
    }

    /** Đề nghị của chính người gọi. */
    @GetMapping("/mine")
    @Operation(summary = "Yêu cầu tôi đã gửi")
    public List<ChangeRequestView> mine(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return changeRequests.mine(page, size);
    }

    /**
     * Hàng đợi chờ duyệt <b>trong phạm vi được giao</b>.
     *
     * <p>Trưởng chi chưa có phân công nào nhận danh sách rỗng — không phải danh sách đầy đủ.</p>
     */
    @GetMapping("/pending")
    @Operation(summary = "Hàng đợi chờ duyệt trong phạm vi chi/ngành được giao")
    public List<ChangeRequestView> pending(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return changeRequests.pendingForReview(page, size);
    }

    /** Số yêu cầu chờ duyệt trong phạm vi — cho badge trên chuông thông báo. */
    @GetMapping("/pending/count")
    @Operation(summary = "Đếm yêu cầu chờ duyệt trong phạm vi")
    public Map<String, Long> pendingCount() {
        return Map.of("count", changeRequests.countPendingForReview());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết một yêu cầu")
    public ChangeRequestView byId(@PathVariable UUID id) {
        return changeRequests.byId(id);
    }

    /** Duyệt hoặc từ chối. Quyền được kiểm theo chi đích của yêu cầu, không theo vai suông. */
    @PostMapping("/{id}/review")
    @Operation(summary = "Duyệt hoặc từ chối yêu cầu đính chính")
    public ChangeRequestView review(@PathVariable UUID id,
                                    @Valid @RequestBody ReviewChangeRequestDto body) {
        return changeRequests.review(
                new ReviewChangeRequestCommand(id, body.approve(), body.note()));
    }

    /** Người gửi tự rút lại. Không phải xoá — bản ghi chuyển sang {@code CANCELLED}. */
    @DeleteMapping("/{id}")
    @Operation(summary = "Rút lại yêu cầu của chính mình")
    public ChangeRequestView cancel(@PathVariable UUID id) {
        return changeRequests.cancel(id);
    }
}
