package vn.giapha.genealogy.domain.event;

import java.util.UUID;
import vn.giapha.shared.domain.BaseDomainEvent;

/** Một nhân khẩu đã xoá mềm vừa được khôi phục. */
public final class PersonRestoredEvent extends BaseDomainEvent {

    private final UUID personId;

    public PersonRestoredEvent(UUID personId) {
        this.personId = personId;
    }

    public UUID personId() {
        return personId;
    }
}
