package vn.giapha.genealogy.domain.event;

import java.util.UUID;
import vn.giapha.shared.domain.BaseDomainEvent;

/**
 * Dữ liệu cá nhân của một nhân khẩu vừa được <b>ẩn danh hoá</b> theo Nghị định 13/2023.
 * Node phả hệ vẫn còn; người nhận phải dọn mọi bản sao dữ liệu Tầng 3 mà mình đang giữ.
 */
public final class PersonAnonymizedEvent extends BaseDomainEvent {

    private final UUID personId;

    public PersonAnonymizedEvent(UUID personId) {
        this.personId = personId;
    }

    public UUID personId() {
        return personId;
    }
}
