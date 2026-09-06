package vn.giapha.events.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventType;
import vn.giapha.events.domain.port.EventRepository;

/**
 * Bảng {@code event} trong bộ nhớ.
 *
 * <p>Mô phỏng đúng hai điều mà bộ test quan tâm: {@code findRecurringPage} chỉ trả sự kiện
 * <b>lặp lại và chưa xoá mềm</b> (như mệnh đề {@code WHERE} của adapter thật), và phân trang theo
 * offset — nhờ vậy kiểm được vòng quét nhiều lô của {@link GenerateRemindersService}.</p>
 */
final class InMemoryEventRepository implements EventRepository {

    private final List<Event> events = new ArrayList<>();

    /** Số lần {@code findRecurringPage} bị gọi — dùng để chứng minh vòng quét dừng đúng lúc. */
    int pageCalls;

    InMemoryEventRepository(Event... seed) {
        Collections.addAll(events, seed);
    }

    InMemoryEventRepository add(Event event) {
        events.add(event);
        return this;
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return events.stream().filter(event -> event.id().equals(id)).findFirst();
    }

    @Override
    public List<Event> findRecurringPage(int page, int size) {
        pageCalls++;
        List<Event> recurring = events.stream()
                .filter(event -> event.isRecurring() && !event.isDeleted())
                .toList();
        int from = Math.min(page * size, recurring.size());
        int to = Math.min(from + size, recurring.size());
        return List.copyOf(recurring.subList(from, to));
    }

    @Override
    public List<Event> search(List<EventType> types, UUID personId, UUID branchId, boolean includeDeleted) {
        return events.stream()
                .filter(event -> includeDeleted || !event.isDeleted())
                .filter(event -> types == null || types.isEmpty() || types.contains(event.type()))
                .filter(event -> personId == null || personId.equals(event.personId()))
                .filter(event -> branchId == null || event.isClanLevel() || branchId.equals(event.targetBranchId()))
                .toList();
    }
}
