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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.media.api.rest.dto.MediaDtos.ConfirmUploadRequest;
import vn.giapha.media.api.rest.dto.MediaDtos.MediaAssetDto;
import vn.giapha.media.api.rest.dto.MediaDtos.MediaPolicyDto;
import vn.giapha.media.api.rest.dto.MediaDtos.UploadTicketDto;
import vn.giapha.media.api.rest.dto.MediaDtos.UploadTicketRequest;
import vn.giapha.media.api.rest.dto.MediaDtos.ViewUrlDto;
import vn.giapha.media.api.rest.dto.MediaDtos.ViewUrlRequest;
import vn.giapha.media.application.MediaUploadService;
import vn.giapha.media.application.MediaViewService;
import vn.giapha.media.domain.MediaAsset;
import vn.giapha.media.domain.MediaKind;
import vn.giapha.media.domain.MediaLimits;
import vn.giapha.media.domain.MediaSignature;

/**
 * Tải tệp lên và lấy URL đọc — {@code /api/v1/media}.
 *
 * <h2>Hai bước, và tệp KHÔNG đi qua đây</h2>
 * <ol>
 *   <li>{@code POST /upload-tickets} → nhận {@code uploadUrl} (PUT đã ký), {@code mediaKey}, và
 *       các trần đang áp dụng;</li>
 *   <li>client {@code PUT} tệp <b>thẳng lên MinIO</b>;</li>
 *   <li>{@code POST /{id}/confirm} → backend tự {@code statObject}, đọc chữ ký byte, đo thời lượng,
 *       rồi mới coi tệp là có thật.</li>
 * </ol>
 * Không có điểm cuối {@code multipart} nào ở đây, và đó là chủ ý — xem javadoc
 * {@code ObjectStoragePort}. Trần {@code spring.servlet.multipart} 10 MB của dự án cố ý khớp với
 * {@code ImportLimits.MAX_FILE_BYTES} và <b>không</b> được nâng vì video.
 *
 * <h2>Vì sao mọi phản hồi ở đây đều {@code no-store}</h2>
 * Thân phản hồi chở <b>URL đã ký</b>. Một URL đã ký nằm trong bộ nhớ đệm dùng chung — proxy công
 * ty, máy tính chung ở nhà thờ họ — là một tệp riêng tư phục vụ cho người sau, và lần đọc thứ hai
 * không bao giờ chạm tới tầng ứng dụng để mà ghi log. Đây đúng là cái bẫy mà
 * {@code PersonController.get} và {@code PostController} đã ghi lại, chỉ là ở đây hậu quả cụ thể
 * hơn.
 *
 * <h2>Mã HTTP</h2>
 * Sai chữ ký byte / quá thời lượng / không đọc được thời lượng → {@code 422}. Quá trần dung lượng
 * → {@code 413}. Xác nhận khi kho chưa có gì → {@code 409 MEDIA_NOT_UPLOADED}. Phiếu của người
 * khác → {@code 403 MEDIA_NOT_OWNED}. Phiếu hết hạn hoặc đã xử → {@code 409 MEDIA_TICKET_CLOSED}.
 */
@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media", description = "Tải ảnh/video lên kho đối tượng bằng URL đã ký")
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    private final MediaUploadService uploads;
    private final MediaViewService views;

    public MediaController(MediaUploadService uploads, MediaViewService views) {
        this.uploads = uploads;
        this.views = views;
    }

    /**
     * <b>Chính sách tải tệp mà máy chủ công bố</b> — đọc được trước khi người dùng chọn tệp nào.
     *
     * <h2>Vì sao có nó</h2>
     * Để giao diện <b>không phải chép một con số nào</b>. Không có endpoint này thì mỗi bên tự đặt
     * một bộ trần, và cái giá rơi đúng vào chỗ đắt nhất: người dùng chọn một video, nhìn thanh
     * tiến trình chạy hết, rồi mới bị từ chối. Tiền lệ cùng hình dạng trong dự án:
     * {@code GET /api/v1/import/duplicate-policy}.
     *
     * <p>Đây là <b>dữ liệu công khai của hệ thống</b>, không phải dữ liệu cá nhân — nên nó là lối
     * duy nhất trong controller này được phép vào bộ nhớ đệm, và nó nên được: màn soạn bài gọi nó
     * mỗi lần mở.</p>
     */
    @GetMapping("/policy")
    @Operation(summary = "Trần dung lượng/thời lượng và định dạng được nhận, do máy chủ công bố")
    public ResponseEntity<MediaPolicyDto> policy() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(15)).cachePublic())
                .body(new MediaPolicyDto(
                        MediaLimits.MAX_IMAGE_BYTES,
                        MediaLimits.MAX_VIDEO_BYTES,
                        MediaLimits.MAX_VIDEO_SECONDS,
                        MediaLimits.MAX_MEDIA_PER_POST,
                        MediaLimits.MAX_ALT_LENGTH,
                        List.of(MediaSignature.IMAGE_JPEG, MediaSignature.IMAGE_PNG,
                                MediaSignature.IMAGE_WEBP),
                        List.of(MediaSignature.VIDEO_MP4, MediaSignature.VIDEO_WEBM),
                        (int) MediaLimits.UPLOAD_TICKET_TTL.toSeconds(),
                        (int) MediaLimits.VIEW_URL_TTL.toSeconds()));
    }

    @PostMapping("/upload-tickets")
    @Operation(summary = "Xin một URL PUT đã ký để tải ảnh/video thẳng lên kho")
    public ResponseEntity<UploadTicketDto> ticket(@Valid @RequestBody UploadTicketRequest body) {
        var ticket = uploads.issueTicket(MediaKind.of(body.kind()), body.sizeBytes());
        log.debug("POST /api/v1/media/upload-tickets -> {}", ticket.mediaId());
        return noStore(new UploadTicketDto(ticket.uploadUrl(), ticket.mediaId(), ticket.mediaKey(),
                ticket.expiresAt(), ticket.maxBytes(), ticket.maxDurationSeconds(),
                ticket.acceptedContentTypes()));
    }

    /**
     * Xác nhận rằng tệp đã nằm trên kho.
     *
     * <p>Đây là chỗ bất biến lớn nhất của đường này được giữ: <b>tệp chỉ có thật sau khi backend
     * tự nhìn thấy nó</b>. Gọi điểm cuối này mà chưa tải gì lên sẽ nhận
     * {@code 409 MEDIA_NOT_UPLOADED}, không phải một cái gật đầu.</p>
     */
    @PostMapping("/{id}/confirm")
    @Operation(summary = "Xác nhận sau khi tải xong; alt bắt buộc với ảnh")
    public ResponseEntity<MediaAssetDto> confirm(@PathVariable UUID id,
                                                 @Valid @RequestBody ConfirmUploadRequest body) {
        MediaAsset asset = uploads.confirm(id, body == null ? null : body.alt());
        return noStore(new MediaAssetDto(asset.id(), asset.kind().name(), asset.contentType(),
                asset.sizeBytes() == null ? 0L : asset.sizeBytes(), asset.durationMs(),
                asset.altText(), 0, null));
    }

    /**
     * Ký URL đọc cho một loạt khoá.
     *
     * <p>Nhận danh sách chứ không nhận một khoá: phả đồ vẽ hàng trăm node, mỗi node một ảnh chân
     * dung, và hàng trăm vòng HTTP trên mạng di động sẽ làm hỏng NFR-1 một cách chắc chắn. Quyền
     * được <b>kiểm lại từ đầu</b> cho từng khoá — một khoá đã ra ngoài không phải giấy thông
     * hành.</p>
     */
    @PostMapping("/view-urls")
    @Operation(summary = "Đổi một loạt khoá thành URL đọc đã ký, sau khi kiểm quyền lại từng khoá")
    public ResponseEntity<ViewUrlDto> viewUrls(@Valid @RequestBody ViewUrlRequest body) {
        return noStore(new ViewUrlDto(views.signUrls(body.keys())));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .varyBy(HttpHeaders.AUTHORIZATION)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(body);
    }
}
