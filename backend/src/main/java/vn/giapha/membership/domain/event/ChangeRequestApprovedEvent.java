package vn.giapha.membership.domain.event;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.giapha.shared.domain.BaseDomainEvent;

/**
 * Một yêu cầu đính chính vừa được duyệt — tín hiệu cho {@code genealogy} <b>áp dụng</b> thay đổi.
 *
 * <h2>Vì sao là sự kiện chứ không phải lời gọi thẳng</h2>
 * {@code membership} quyết định <i>ai được duyệt cái gì</i>; nó không biết và không nên biết cách
 * ghi một nhân khẩu, cách nối một cạnh quan hệ, hay cách kiểm kỵ húy. Gọi thẳng
 * {@code UpdatePersonService} sẽ kéo {@code membership → genealogy} thành phụ thuộc cứng và biến
 * mọi thay đổi trong luật phả hệ thành thay đổi trong luồng duyệt. Phát sự kiện, để context sở hữu
 * dữ liệu tự áp dụng.
 *
 * <h2>Người nhận phải chạy TRONG cùng transaction</h2>
 * {@code @EventListener} thường (không phải {@code @TransactionalEventListener}) là đúng ở đây:
 * nếu việc áp dụng thất bại — kỵ húy, vòng quan hệ, khoá lạc quan — thì trạng thái
 * {@code APPROVED} phải rollback theo. Một yêu cầu hiện "đã duyệt" mà cây không đổi là thứ tệ hơn
 * cả không duyệt.
 *
 * <p>Ở W6 <b>chưa có người nhận</b>: đây là điểm nối để bước ghép W2×W6 gắn vào, và cho tới lúc đó
 * việc duyệt chỉ ghi nhận quyết định chứ không tự sửa cây.</p>
 */
public final class ChangeRequestApprovedEvent extends BaseDomainEvent {

    private final UUID changeRequestId;
    private final String requestType;
    private final UUID personId;
    private final UUID targetBranchId;
    private final Map<String, Object> payload;
    private final UUID reviewerAppUserId;
    private final UUID requesterAppUserId;

    public ChangeRequestApprovedEvent(UUID changeRequestId, String requestType, UUID personId,
                                      UUID targetBranchId, Map<String, Object> payload,
                                      UUID reviewerAppUserId, UUID requesterAppUserId) {
        this.changeRequestId = changeRequestId;
        this.requestType = requestType;
        this.personId = personId;
        this.targetBranchId = targetBranchId;
        // Sao chep chap nhan gia tri null — xem ghi chu o ChangeRequest.payload().
        this.payload = payload == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(payload));
        this.reviewerAppUserId = reviewerAppUserId;
        this.requesterAppUserId = requesterAppUserId;
    }

    public UUID changeRequestId() {
        return changeRequestId;
    }

    public String requestType() {
        return requestType;
    }

    public UUID personId() {
        return personId;
    }

    public UUID targetBranchId() {
        return targetBranchId;
    }

    /** Nội dung đề nghị. Người nhận tự chịu trách nhiệm không ghi Tầng 3 vào nhật ký. */
    public Map<String, Object> payload() {
        return payload;
    }

    public List<String> payloadFields() {
        return payload.keySet().stream().sorted().toList();
    }

    public UUID reviewerAppUserId() {
        return reviewerAppUserId;
    }

    public UUID requesterAppUserId() {
        return requesterAppUserId;
    }
}
