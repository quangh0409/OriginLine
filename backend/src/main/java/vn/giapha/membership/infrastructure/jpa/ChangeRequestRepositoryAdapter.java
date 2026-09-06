package vn.giapha.membership.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.giapha.membership.domain.ChangeRequest;
import vn.giapha.membership.domain.ChangeRequestStatus;
import vn.giapha.membership.domain.ChangeRequestType;
import vn.giapha.membership.domain.port.ChangeRequestRepository;
import vn.giapha.shared.vo.BranchPath;

/** Hiện thực {@link ChangeRequestRepository}. */
@Repository
public class ChangeRequestRepositoryAdapter implements ChangeRequestRepository {

    private final ChangeRequestJpaRepository jpa;

    public ChangeRequestRepositoryAdapter(ChangeRequestJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<ChangeRequest> byId(UUID id) {
        return id == null
                ? Optional.empty()
                : jpa.findById(id).map(ChangeRequestRepositoryAdapter::toDomain);
    }

    @Override
    public ChangeRequest save(ChangeRequest request) {
        ChangeRequestJpaEntity entity = jpa.findById(request.id())
                .orElseGet(() -> new ChangeRequestJpaEntity(request.id(), request.type().name(),
                        request.requestedBy()));
        entity.setPersonId(request.personId());
        entity.setTargetBranchId(request.targetBranchId());
        entity.setPayload(request.payload());
        entity.setReason(request.reason());
        entity.setStatus(request.status().name());
        entity.setReviewerId(request.reviewerId());
        entity.setReviewNote(request.reviewNote());
        entity.setReviewedAt(request.reviewedAt());
        return toDomain(jpa.save(entity));
    }

    @Override
    public List<ChangeRequest> byRequester(UUID appUserId, int limit, int offset) {
        if (appUserId == null) {
            return List.of();
        }
        return jpa.findByRequester(appUserId, limit, offset).stream()
                .map(ChangeRequestRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<ChangeRequest> pendingInScope(List<BranchPath> scopes, boolean clanWide,
                                              int limit, int offset) {
        if (!clanWide && (scopes == null || scopes.isEmpty())) {
            // Khong co pham vi nao thi khong thay gi. Tuyet doi khong hieu nguoc thanh "thay tat".
            return List.of();
        }
        return jpa.findPendingInScope(toLtreeArrayLiteral(scopes), clanWide ? 1 : 0, limit, offset)
                .stream()
                .map(ChangeRequestRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public long countPendingInScope(List<BranchPath> scopes, boolean clanWide) {
        if (!clanWide && (scopes == null || scopes.isEmpty())) {
            return 0L;
        }
        return jpa.countPendingInScope(toLtreeArrayLiteral(scopes), clanWide ? 1 : 0);
    }

    @Override
    public List<ChangeRequest> byPerson(UUID personId, ChangeRequestStatus status) {
        if (personId == null) {
            return List.of();
        }
        return jpa.findByPerson(personId, status == null ? null : status.name()).stream()
                .map(ChangeRequestRepositoryAdapter::toDomain)
                .toList();
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ
    // -------------------------------------------------------------------------------------

    /**
     * Dựng literal mảng của Postgres: {@code {goc.chi_giap,goc.chi_at}}.
     *
     * <p>Không cần bọc nháy hay thoát ký tự: {@link BranchPath} đã bảo đảm mọi nhãn chỉ gồm
     * {@code [A-Za-z0-9_]} ngăn cách bởi dấu chấm. Danh sách rỗng cho ra {@code {}} — một mảng
     * rỗng, và {@code x <@ ANY('{}')} là {@code false}, đúng nghĩa "không phạm vi nào".</p>
     */
    private static String toLtreeArrayLiteral(List<BranchPath> scopes) {
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        if (scopes != null) {
            for (BranchPath path : scopes) {
                if (path != null) {
                    joiner.add(path.value());
                }
            }
        }
        return joiner.toString();
    }

    private static ChangeRequest toDomain(ChangeRequestJpaEntity entity) {
        return new ChangeRequest(entity.getId(),
                ChangeRequestType.of(entity.getRequestType()),
                entity.getPersonId(),
                entity.getTargetBranchId(),
                entity.getPayload(),
                entity.getReason(),
                entity.getRequestedBy(),
                ChangeRequestStatus.of(entity.getStatus()),
                entity.getReviewerId(),
                entity.getReviewNote(),
                entity.getReviewedAt(),
                entity.getCreatedAt(),
                entity.getVersion());
    }
}
