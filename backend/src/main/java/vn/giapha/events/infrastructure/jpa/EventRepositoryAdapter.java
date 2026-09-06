package vn.giapha.events.infrastructure.jpa;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventType;
import vn.giapha.events.domain.port.EventRepository;
import vn.giapha.events.infrastructure.jdbc.BranchScopeJdbcAdapter;

/**
 * Hiện thực {@link EventRepository} trên Spring Data JPA.
 *
 * <h2>Vì sao lọc trong bộ nhớ chứ không trong {@code WHERE}</h2>
 * Bộ lọc theo chi/ngành phải bao cả cây con ({@code ltree}), còn bộ lọc chính của endpoint chạy trên
 * ngày dương của lần xảy ra sắp tới — một giá trị tính ra. Viết một truy vấn native gánh cả hai sẽ
 * cần {@code CAST(:param AS uuid) IS NULL OR ...} rải khắp nơi (Hibernate không suy được kiểu của
 * tham số {@code null}), và vẫn không giải quyết được vế thứ hai. Bảng {@code event} ở quy mô một
 * dòng họ là hàng trăm tới hàng nghìn dòng; đây là đánh đổi đúng cho Giai đoạn 1, và lời giải khi nó
 * thành điểm nghẽn là một bảng chiếu do scheduler đêm dựng — không phải nhét công thức âm lịch vào
 * SQL.
 */
@Repository
public class EventRepositoryAdapter implements EventRepository {

    private final EventJpaRepository repository;
    private final EventMapper mapper;
    private final BranchScopeJdbcAdapter branchScope;

    public EventRepositoryAdapter(EventJpaRepository repository, EventMapper mapper,
                                  BranchScopeJdbcAdapter branchScope) {
        this.repository = repository;
        this.mapper = mapper;
        this.branchScope = branchScope;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Event> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> findRecurringPage(int page, int size) {
        return toDomain(repository.findByDeletedFalseAndRecurringTrueOrderByIdAsc(
                PageRequest.of(Math.max(0, page), Math.max(1, size))));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> search(List<EventType> types, UUID personId, UUID branchId,
                              boolean includeDeleted) {
        List<EventJpaEntity> rows = includeDeleted
                ? repository.findByOrderByIdAsc()
                : repository.findByDeletedFalseOrderByIdAsc();

        Set<EventType> typeFilter = types == null || types.isEmpty()
                ? null : EnumSet.copyOf(types);
        // Sự kiện cấp dòng họ luôn xuất hiện với mọi branchId — hợp đồng OpenAPI nói rõ điều này, và
        // nó đúng về nghiệp vụ: giỗ Tổ là của cả họ, lọc theo chi không được làm nó biến mất.
        Set<UUID> branchFilter = branchId == null ? null : branchScope.subtreeOf(branchId);

        List<Event> result = new ArrayList<>(rows.size());
        for (EventJpaEntity row : rows) {
            if (typeFilter != null && !typeFilter.contains(EventType.fromDbValue(row.getEventType()))) {
                continue;
            }
            if (personId != null && !personId.equals(row.getPersonId())) {
                continue;
            }
            if (branchFilter != null && !row.isClanLevel()
                    && (row.getTargetBranchId() == null || !branchFilter.contains(row.getTargetBranchId()))) {
                continue;
            }
            result.add(mapper.toDomain(row));
        }
        return List.copyOf(result);
    }

    private List<Event> toDomain(List<EventJpaEntity> rows) {
        List<Event> result = new ArrayList<>(rows.size());
        for (EventJpaEntity row : rows) {
            result.add(mapper.toDomain(row));
        }
        return List.copyOf(result);
    }
}
