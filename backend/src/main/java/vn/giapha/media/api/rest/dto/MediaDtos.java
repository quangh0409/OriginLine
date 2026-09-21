package vn.giapha.media.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.giapha.media.domain.MediaLimits;
import vn.giapha.media.domain.MediaReport;

/**
 * Hợp đồng trên dây của context {@code media}.
 *
 * <p>Gom vào một tệp vì chúng là <b>một</b> hợp đồng: tám record ngắn, không record nào có hành vi,
 * và chúng luôn được đọc cùng nhau khi đối chiếu với {@code contracts/openapi.yaml}. Tách thành
 * tám tệp chỉ đổi lấy tám khối {@code import} giống hệt nhau.</p>
 *
 * <p>Thân yêu cầu "đặt danh sách tệp của một bài" <b>không</b> ở đây mà ở
 * {@code content.api.rest.dto.AttachMediaRequest}: nó là hợp đồng của điểm cuối
 * {@code PUT /api/v1/posts/{id}/media}, tức của {@code content}. Đặt nó ở đây sẽ buộc
 * {@code content} phụ thuộc vào gói {@code media.api} — một gói không phải {@code @NamedInterface}
 * và không bao giờ nên là, vì mở nó ra là mở luôn mọi controller của kho tệp.</p>
 */
public final class MediaDtos {

    private MediaDtos() {
    }

    /**
     * Xin một phiếu tải lên.
     *
     * @param kind {@code IMAGE} hoặc {@code VIDEO}. Đây là <b>khai báo</b> để chọn trần; loại thật
     *        do chữ ký byte quyết ở bước xác nhận, và lệch nhau thì từ chối
     * @param sizeBytes số byte của tệp. Bắt buộc, vì nó là thứ cho phép từ chối một video quá khổ
     *        <b>trước khi</b> tốn băng thông của người dùng
     */
    public record UploadTicketRequest(
            @NotBlank(message = "Phai noi ro IMAGE hay VIDEO") String kind,
            @Positive(message = "Phai khai kich thuoc tep") long sizeBytes) {
    }

    /** Trả lời của {@code POST /api/v1/media/upload-tickets}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UploadTicketDto(String uploadUrl,
                                  UUID mediaId,
                                  String mediaKey,
                                  Instant expiresAt,
                                  long maxBytes,
                                  Integer maxDurationSeconds,
                                  List<String> acceptedContentTypes) {
    }

    /**
     * Xác nhận sau khi đã tải xong.
     *
     * @param alt chữ thay ảnh. <b>Bắt buộc với ảnh</b> (WCAG 2.2 AA 1.1.1) — ép ở tầng domain chứ
     *        không bằng {@code @NotBlank} ở đây, vì với video nó là tuỳ chọn và một ràng buộc
     *        bean-validation không nhìn thấy loại tệp
     */
    public record ConfirmUploadRequest(
            @Size(max = MediaLimits.MAX_ALT_LENGTH,
                  message = "Chu thay anh toi da 300 ky tu") String alt) {
    }

    /** Một tệp đính kèm trên dây. {@code url} là URL đã ký, hạn ngắn. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MediaAssetDto(UUID id,
                                String kind,
                                String contentType,
                                long sizeBytes,
                                Integer durationMs,
                                String alt,
                                int position,
                                String url) {
    }

    /** Xin URL đọc cho một loạt khoá (ảnh chân dung trên phả đồ). */
    public record ViewUrlRequest(
            @NotEmpty(message = "Phai co it nhat mot khoa") List<String> keys) {
    }

    /**
     * Bản đồ khoá → URL đã ký.
     *
     * <p>Khoá <b>vắng mặt</b> khi không tồn tại, đã bị gỡ, hoặc người gọi không được xem — ba
     * tình huống cố ý không phân biệt được từ ngoài, để điểm cuối này không thành công cụ dò.</p>
     */
    public record ViewUrlDto(Map<String, String> urls) {
    }

    /** Báo gỡ một tệp đính kèm. */
    public record ReportMediaRequest(
            @NotBlank(message = "Phai chon ly do") String reason,
            @Size(max = MediaReport.MAX_NOTE_LENGTH) String note) {
    }

    /** Quyết định của người duyệt trên một đơn báo gỡ. */
    public record ReviewReportRequest(@Size(max = 2000) String note) {
    }

    /** Một đơn báo gỡ trên dây. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MediaReportDto(UUID id,
                                 UUID mediaId,
                                 String mediaKind,
                                 String mediaUrl,
                                 String reason,
                                 String note,
                                 UUID reportedBy,
                                 String status,
                                 UUID reviewedBy,
                                 Instant reviewedAt,
                                 String resolutionNote,
                                 Instant createdAt,
                                 long version) {
    }

    /**
     * <b>Chính sách tải tệp mà MÁY CHỦ công bố</b> — trả lời của {@code GET /api/v1/media/policy}.
     *
     * <h2>Vì sao endpoint này tồn tại</h2>
     * Vì nếu không có nó, mỗi bên tự đặt một bộ trần — và đó không phải giả thuyết: đợt này giao
     * diện <i>đã</i> phải tự chọn (ảnh 10 MB, video 150 MB, 20 tệp một bài) đơn giản vì chưa có gì
     * để hỏi. Khi hai bộ số lệch nhau, cái giá rơi đúng vào chỗ đắt nhất: người dùng chọn một
     * video, nhìn thanh tiến trình chạy hết, <b>rồi</b> mới bị từ chối. Trên mạng di động ở quê
     * thì đó là mười phút và một lượng dữ liệu có thật.
     *
     * <p>Tiền lệ trong chính dự án này: {@code GET /api/v1/import/duplicate-policy}. Cùng hình
     * dạng, cùng lý do — luật là dữ liệu máy chủ công bố, không phải hằng số chép tay ở hai nơi.</p>
     *
     * <p>Phiếu tải lên ({@code UploadTicketDto}) vẫn chở lại các trần <i>áp dụng cho chính nó</i>;
     * hai lối không mâu thuẫn mà bổ sung: cái này để <b>dựng màn hình</b> (đặt {@code accept} của
     * ô chọn tệp, hiện dòng "tối đa …"), cái kia để <b>kiểm một tệp cụ thể</b>.</p>
     */
    public record MediaPolicyDto(long maxImageBytes,
                                 long maxVideoBytes,
                                 int maxVideoDurationSeconds,
                                 int maxMediaPerPost,
                                 int maxAltLength,
                                 List<String> imageContentTypes,
                                 List<String> videoContentTypes,
                                 int uploadUrlTtlSeconds,
                                 int viewUrlTtlSeconds) {
    }

    /** Kết quả một lượt dọn tệp mồ côi. */
    public record GcResultDto(int expiredTickets, int orphanAssets, int storageFailures) {
    }
}
