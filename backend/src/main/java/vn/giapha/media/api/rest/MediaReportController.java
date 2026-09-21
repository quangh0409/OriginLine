package vn.giapha.media.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.media.api.rest.dto.MediaDtos.MediaReportDto;
import vn.giapha.media.api.rest.dto.MediaDtos.ReportMediaRequest;
import vn.giapha.media.api.rest.dto.MediaDtos.ReviewReportRequest;
import vn.giapha.media.application.MediaReportService;
import vn.giapha.media.application.view.MediaReportView;
import vn.giapha.media.domain.MediaProblemCodes;
import vn.giapha.media.domain.ReportReason;
import vn.giapha.shared.exception.DomainException;

/**
 * <b>Đường báo gỡ</b> — {@code /api/v1/media/…/reports}.
 *
 * <h2>Vì sao những điểm cuối này tồn tại</h2>
 * Chủ dự án đã chốt: ảnh trong bài đi theo quyền của <b>bài</b>, không theo bộ lọc nhóm trường của
 * từng người có mặt trong ảnh. Đó là một quyết định hợp lý cho trang của một dòng họ — nhưng nó
 * chỉ an toàn nếu có <i>lối đóng</i>. Bốn điểm cuối ở đây là lối đóng ấy: một người trong họ báo,
 * một người có thẩm quyền <b>trong đúng chi</b> gỡ hoặc giữ, và cả hai việc đều để lại tên trong
 * {@code audit_log}. Nếu một ngày các điểm cuối này bị bỏ, quyết định kia phải được xét lại cùng
 * lúc.
 *
 * <h2>Không {@code @PreAuthorize} nào ở đây, đúng như {@code PostController}</h2>
 * Quyền duyệt một đơn phụ thuộc <i>chi của bản ghi mang tấm ảnh</i>, mà controller chưa nạp đơn
 * thì chưa biết chi ấy là gì. Cửa kiểm thật là {@code MediaReportService} → {@code MediaAccessGuard}
 * → {@code BranchScopeGuard} → so {@code ltree}.
 */
@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media reports", description = "Báo gỡ tệp đính kèm và xử lý đơn")
public class MediaReportController {

    private static final Logger log = LoggerFactory.getLogger(MediaReportController.class);

    private final MediaReportService reports;

    public MediaReportController(MediaReportService reports) {
        this.reports = reports;
    }

    /** Người trong họ báo một tệp đính kèm vi phạm. */
    @PostMapping("/{mediaId}/reports")
    @Operation(summary = "Báo một tệp đính kèm vi phạm (riêng tư, sai người, không phù hợp…)")
    public ResponseEntity<MediaReportDto> report(@PathVariable UUID mediaId,
                                                 @Valid @RequestBody ReportMediaRequest body) {
        ReportReason reason = ReportReason.of(body.reason());
        if (reason == null) {
            throw new DomainException(MediaProblemCodes.VALIDATION_FAILED,
                    "Ly do khong hop le. Nhan mot trong: RIENG_TU, SAI_NGUOI, KHONG_PHU_HOP,"
                            + " BAN_QUYEN, KHAC.");
        }
        MediaReportView view = reports.report(mediaId, reason, body.note());
        log.info("POST /api/v1/media/{}/reports -> don {}", mediaId, view.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .varyBy(HttpHeaders.AUTHORIZATION)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(toDto(view));
    }

    /** Hàng đợi đơn đang mở <b>trong phạm vi chi</b> của người gọi. */
    @GetMapping("/reports")
    @Operation(summary = "Đơn báo gỡ đang mở trong phạm vi chi được giao")
    public ResponseEntity<List<MediaReportDto>> queue(
            @RequestParam(defaultValue = "20") int limit) {
        return noStore(reports.queue(limit).stream().map(MediaReportController::toDto).toList());
    }

    /**
     * Gỡ tệp.
     *
     * <p>Không có ân hạn: liên kết bị cắt và byte bị xoá khỏi kho <b>trong cùng giao dịch</b>. Ân
     * hạn 24 giờ của đường dọn áp cho tệp mất chủ vì bài bị gỡ, không áp cho tệp bị gỡ vì vi
     * phạm — một tấm ảnh đang làm lộ dữ liệu cá nhân phải ngừng được phục vụ ngay.</p>
     */
    @PostMapping("/reports/{id}/takedown")
    @Operation(summary = "Gỡ tệp theo đơn báo — xoá byte khỏi kho ngay, không ân hạn")
    public ResponseEntity<MediaReportDto> takeDown(@PathVariable UUID id,
                                                   @Valid @RequestBody(required = false)
                                                   ReviewReportRequest body) {
        MediaReportView view = reports.takeDown(id, body == null ? null : body.note());
        log.info("POST /api/v1/media/reports/{}/takedown -> tep {} da bi go", id, view.mediaId());
        return noStore(toDto(view));
    }

    /** Giữ nguyên tệp — nhưng vẫn ký tên. Người báo có quyền biết ai đã quyết. */
    @PostMapping("/reports/{id}/dismiss")
    @Operation(summary = "Xem xét rồi giữ nguyên tệp")
    public ResponseEntity<MediaReportDto> dismiss(@PathVariable UUID id,
                                                  @Valid @RequestBody(required = false)
                                                  ReviewReportRequest body) {
        return noStore(toDto(reports.dismiss(id, body == null ? null : body.note())));
    }

    private static MediaReportDto toDto(MediaReportView v) {
        return new MediaReportDto(v.id(), v.mediaId(), v.mediaKind(), v.mediaUrl(), v.reason(),
                v.note(), v.reportedBy(), v.status(), v.reviewedBy(), v.reviewedAt(),
                v.resolutionNote(), v.createdAt(), v.version());
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .varyBy(HttpHeaders.AUTHORIZATION)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(body);
    }
}
