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
import vn.giapha.shared.exception.NotFoundException;

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
    public List<Event> findActivePage(int page, int size) {
        return toDomain(repository.findByDeletedFalseOrderByIdAsc(
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
            // Mot dong hong khong duoc lam sap ca lo ghi doc — xem EventMapper#toDomainOrSkip.
            mapper.toDomainOrSkip(row).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    @Override
    @Transactional
    public Event insert(Event event) {
        EventJpaEntity entity = new EventJpaEntity(event.id());
        mapper.applyToEntity(event, entity);
        return mapper.toDomain(repository.saveAndFlush(entity));
    }

    /**
     * <h2>Vì sao {@code saveAndFlush} và vì sao trả về bản vừa ghi</h2>
     * Hibernate chỉ tăng {@code @Version} lúc flush. Trả về chính đối tượng đầu vào nghĩa là nó
     * vẫn mang {@code version} cũ, và tầng API dùng giá trị ấy sinh {@code ETag} → {@code ETag} trễ
     * một nhịp. Lần {@code PATCH} kế tiếp gửi đúng {@code ETag} vừa nhận sẽ bị từ chối
     * <b>409 "có người khác vừa sửa"</b> trong khi không ai sửa cả — và lỗi này chỉ lộ ra ở lần ghi
     * <i>thứ hai</i> nên rất dễ lọt qua kiểm thủ công. Cùng một cái bẫy đã ghi ở
     * {@code PersonRepositoryAdapter.save}.
     */
    @Override
    @Transactional
    public Event update(Event event, long expectedVersion) {
        EventJpaEntity entity = repository.findById(event.id())
                .orElseThrow(() -> NotFoundException.of("Event", event.id()));
        // @Version cua Hibernate so khop gia tri nay trong menh de WHERE cua cau UPDATE.
        entity.setVersion(expectedVersion);
        mapper.applyToEntity(event, entity);
        return mapper.toDomain(repository.saveAndFlush(entity));
    }

    private List<Event> toDomain(List<EventJpaEntity> rows) {
        List<Event> result = new ArrayList<>(rows.size());
        for (EventJpaEntity row : rows) {
            mapper.toDomainOrSkip(row).ifPresent(result::add);
        }
        return List.copyOf(result);
    }
}
