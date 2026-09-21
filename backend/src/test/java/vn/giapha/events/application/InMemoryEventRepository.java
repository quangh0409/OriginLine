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
 * <p>Mô phỏng đúng hai điều mà bộ test quan tâm: {@code findActivePage} chỉ trả sự kiện
 * <b>chưa xoá mềm</b> (như mệnh đề {@code WHERE} của adapter thật — kể cả sự kiện một lần, xem
 * {@code EventRepository.findActivePage}), và phân trang theo offset — nhờ vậy kiểm được vòng quét
 * nhiều lô của {@link GenerateRemindersService}.</p>
 */
final class InMemoryEventRepository implements EventRepository {

    private final List<Event> events = new ArrayList<>();

    /** Số lần {@code findActivePage} bị gọi — dùng để chứng minh vòng quét dừng đúng lúc. */
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
    public List<Event> findActivePage(int page, int size) {
        pageCalls++;
        List<Event> active = events.stream()
                .filter(event -> !event.isDeleted())
                .toList();
        int from = Math.min(page * size, active.size());
        int to = Math.min(from + size, active.size());
        return List.copyOf(active.subList(from, to));
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

    @Override
    public Event insert(Event event) {
        events.add(event);
        return event;
    }

    @Override
    public Event update(Event event, long expectedVersion) {
        events.removeIf(existing -> existing.id().equals(event.id()));
        events.add(event);
        return event;
    }
}
