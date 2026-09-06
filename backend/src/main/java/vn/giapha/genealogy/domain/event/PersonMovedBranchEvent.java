package vn.giapha.genealogy.domain.event;

import java.util.UUID;
import vn.giapha.shared.domain.BaseDomainEvent;

/**
 * Một nhân khẩu vừa chuyển chi/ngành.
 *
 * <p>Sự kiện này đáng quan tâm với mọi thứ bám vào phạm vi chi: quyền của Trưởng chi, người nhận
 * nhắc giỗ, và cache cây.</p>
 */
public final class PersonMovedBranchEvent extends BaseDomainEvent {

    private final UUID personId;
    private final UUID fromBranchId;
    private final UUID toBranchId;

    public PersonMovedBranchEvent(UUID personId, UUID fromBranchId, UUID toBranchId) {
        this.personId = personId;
        this.fromBranchId = fromBranchId;
        this.toBranchId = toBranchId;
    }

    public UUID personId() {
        return personId;
    }

    public UUID fromBranchId() {
        return fromBranchId;
    }

    public UUID toBranchId() {
        return toBranchId;
    }
}
