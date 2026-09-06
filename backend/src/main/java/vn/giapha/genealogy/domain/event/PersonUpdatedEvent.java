package vn.giapha.genealogy.domain.event;

import java.util.List;
import java.util.UUID;
import vn.giapha.shared.domain.BaseDomainEvent;

/**
 * Hồ sơ một nhân khẩu vừa đổi.
 *
 * <p>{@code changedFields} chỉ là <b>tên trường</b>, không kèm giá trị — sự kiện đi qua nhiều
 * người nhận, trong đó có người không đủ quyền xem dữ liệu Tầng 3.</p>
 */
public final class PersonUpdatedEvent extends BaseDomainEvent {

    private final UUID personId;
    private final List<String> changedFields;

    public PersonUpdatedEvent(UUID personId, List<String> changedFields) {
        this.personId = personId;
        this.changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
    }

    public UUID personId() {
        return personId;
    }

    public List<String> changedFields() {
        return changedFields;
    }
}
