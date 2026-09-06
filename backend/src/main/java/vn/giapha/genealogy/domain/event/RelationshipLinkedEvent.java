package vn.giapha.genealogy.domain.event;

import java.util.UUID;
import vn.giapha.shared.domain.BaseDomainEvent;

/**
 * Một cạnh quan hệ vừa được nối — cạnh AGE và dòng bản chiếu đã ghi xong trong cùng transaction.
 */
public final class RelationshipLinkedEvent extends BaseDomainEvent {

    private final UUID relationshipId;
    private final UUID fromPersonId;
    private final UUID toPersonId;
    private final String relType;

    public RelationshipLinkedEvent(UUID relationshipId, UUID fromPersonId, UUID toPersonId, String relType) {
        this.relationshipId = relationshipId;
        this.fromPersonId = fromPersonId;
        this.toPersonId = toPersonId;
        this.relType = relType;
    }

    public UUID relationshipId() {
        return relationshipId;
    }

    public UUID fromPersonId() {
        return fromPersonId;
    }

    public UUID toPersonId() {
        return toPersonId;
    }

    public String relType() {
        return relType;
    }
}
