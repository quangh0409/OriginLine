package vn.giapha.genealogy.domain.event;

import java.util.UUID;
import vn.giapha.shared.domain.BaseDomainEvent;

/**
 * Một nhân khẩu vừa bị <b>xoá mềm</b>. Node và cạnh trong đồ thị vẫn còn nguyên — người nhận sự
 * kiện phải hiểu đây là "ẩn khỏi mọi khung nhìn", không phải "biến mất khỏi cây".
 */
public final class PersonSoftDeletedEvent extends BaseDomainEvent {

    private final UUID personId;
    private final String reason;

    public PersonSoftDeletedEvent(UUID personId, String reason) {
        this.personId = personId;
        this.reason = reason;
    }

    public UUID personId() {
        return personId;
    }

    public String reason() {
        return reason;
    }
}
