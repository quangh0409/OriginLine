package vn.giapha.membership.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import vn.giapha.membership.domain.ChangeRequest;
import vn.giapha.membership.domain.event.CorrectionPayload;

/**
 * Yêu cầu đính chính ở dạng đọc.
 *
 * <h2>{@code payload} chỉ hiện với người được phép</h2>
 * Nội dung đề nghị có thể chứa dữ liệu Tầng 3 của một người còn sống (số điện thoại, địa chỉ). Người
 * gửi thấy lại đề nghị của mình, người duyệt thấy thứ mình sắp phê — ngoài hai vai đó thì
 * {@code payload} rỗng và chỉ còn {@code payloadFields} để biết đề nghị động tới trường nào.
 * {@link #redacted()} là chỗ cắt.
 *
 * <h2>{@code payloadFields} chỉ liệt kê trường hồ sơ</h2>
 * Khoá điều khiển ({@code _baseVersion}) bị loại — xem {@link CorrectionPayload#fieldKeys}. Giao
 * diện dựng bảng so sánh "đang ghi / đề nghị" từ danh sách này, và một mốc phiên bản lọt vào đó sẽ
 * hiện lên như một trường hồ sơ không ai hiểu là gì.
 */
public record ChangeRequestView(UUID id, String type, String status, UUID personId,
                                UUID targetBranchId, Map<String, Object> payload,
                                java.util.List<String> payloadFields, String reason,
                                UUID requestedBy, UUID reviewerId, String reviewNote,
                                Instant reviewedAt, Instant createdAt, long version) {

    public static ChangeRequestView from(ChangeRequest request) {
        Map<String, Object> payload = request.payload();
        return new ChangeRequestView(request.id(), request.type().name(), request.status().name(),
                request.personId(), request.targetBranchId(), payload,
                CorrectionPayload.fieldKeys(payload), request.reason(),
                request.requestedBy(), request.reviewerId(), request.reviewNote(),
                request.reviewedAt(), request.createdAt(), request.version());
    }

    /** Bản đã bỏ {@code payload}; giữ lại tên trường để giao diện vẫn tóm tắt được đề nghị. */
    public ChangeRequestView redacted() {
        return new ChangeRequestView(id, type, status, personId, targetBranchId, Map.of(),
                payloadFields, reason, requestedBy, reviewerId, reviewNote, reviewedAt,
                createdAt, version);
    }
}
