package vn.giapha.genealogy.domain.event;

import java.util.UUID;
import vn.giapha.shared.domain.BaseDomainEvent;

/** Một nhân khẩu vừa được thêm vào gia phả. */
public final class PersonAddedEvent extends BaseDomainEvent {

    private final UUID personId;

    public PersonAddedEvent(UUID personId) {
        this.personId = personId;
    }

    public UUID personId() {
        return personId;
    }
}
