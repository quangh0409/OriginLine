package vn.giapha.membership.domain.event;

import java.util.UUID;
import vn.giapha.shared.domain.BaseDomainEvent;

/**
 * Một yêu cầu đính chính vừa bị từ chối.
 *
 * <p>Không mang {@code payload}: người nhận duy nhất được dự kiến là {@code notification}, và việc
 * báo cho người gửi biết chỉ cần lý do, không cần chép lại nội dung đề nghị — trong đó có thể có dữ
 * liệu Tầng 3.</p>
 */
public final class ChangeRequestRejectedEvent extends BaseDomainEvent {

    private final UUID changeRequestId;
    private final UUID requesterAppUserId;
    private final UUID reviewerAppUserId;
    private final String reason;

    public ChangeRequestRejectedEvent(UUID changeRequestId, UUID requesterAppUserId,
                                      UUID reviewerAppUserId, String reason) {
        this.changeRequestId = changeRequestId;
        this.requesterAppUserId = requesterAppUserId;
        this.reviewerAppUserId = reviewerAppUserId;
        this.reason = reason;
    }

    public UUID changeRequestId() {
        return changeRequestId;
    }

    public UUID requesterAppUserId() {
        return requesterAppUserId;
    }

    public UUID reviewerAppUserId() {
        return reviewerAppUserId;
    }

    public String reason() {
        return reason;
    }
}
