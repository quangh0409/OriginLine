package vn.giapha.membership.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.ChangeRequest;
import vn.giapha.membership.domain.ChangeRequestStatus;
import vn.giapha.membership.domain.port.ChangeRequestRepository;
import vn.giapha.shared.vo.BranchPath;

/**
 * Bản trong bộ nhớ của {@code change_request}.
 *
 * <p>Điểm quan trọng nhất là {@link #pendingInScope}: bản thật lọc bằng toán tử
 * {@code ltree[] @> ltree} trong SQL, nên bản giả phải lọc <b>đúng ngữ nghĩa ấy</b> — "một trong
 * các chi được giao là tổ tiên của (hoặc trùng) chi đích". Đặc biệt, danh sách phạm vi rỗng và
 * không phải vai toàn dòng họ thì trả rỗng, y hệt {@code ChangeRequestRepositoryAdapter}. Nếu bản
 * giả trả tất cả trong trường hợp đó thì test sẽ bỏ lọt đúng cái lỗi kinh điển của phân quyền theo
 * scope.</p>
 */
public final class InMemoryChangeRequestRepository implements ChangeRequestRepository {

    private final Map<UUID, ChangeRequest> rows = new LinkedHashMap<>();
    private final StubBranchLookup branches;

    public InMemoryChangeRequestRepository(StubBranchLookup branches) {
        this.branches = branches;
    }

    @Override
    public Optional<ChangeRequest> byId(UUID id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public ChangeRequest save(ChangeRequest request) {
        rows.put(request.id(), request);
        return request;
    }

    @Override
    public List<ChangeRequest> byRequester(UUID appUserId, int limit, int offset) {
        List<ChangeRequest> found = new ArrayList<>();
        for (ChangeRequest request : rows.values()) {
            if (request.requestedBy().equals(appUserId)) {
                found.add(request);
            }
        }
        return page(found, limit, offset);
    }

    @Override
    public List<ChangeRequest> pendingInScope(List<BranchPath> scopes, boolean clanWide,
                                              int limit, int offset) {
        if (!clanWide && (scopes == null || scopes.isEmpty())) {
            // Khong co pham vi nao thi khong thay gi. Tuyet doi khong hieu nguoc thanh "thay tat".
            return List.of();
        }
        List<ChangeRequest> found = new ArrayList<>();
        for (ChangeRequest request : rows.values()) {
            if (request.status() != ChangeRequestStatus.PENDING) {
                continue;
            }
            if (clanWide || coveredBy(scopes, request)) {
                found.add(request);
            }
        }
        return page(found, limit, offset);
    }

    @Override
    public long countPendingInScope(List<BranchPath> scopes, boolean clanWide) {
        if (!clanWide && (scopes == null || scopes.isEmpty())) {
            return 0L;
        }
        return pendingInScope(scopes, clanWide, Integer.MAX_VALUE, 0).size();
    }

    @Override
    public List<ChangeRequest> byPerson(UUID personId, ChangeRequestStatus status) {
        List<ChangeRequest> found = new ArrayList<>();
        for (ChangeRequest request : rows.values()) {
            if (personId != null && personId.equals(request.personId())
                    && (status == null || request.status() == status)) {
                found.add(request);
            }
        }
        return found;
    }

    public int size() {
        return rows.size();
    }

    private boolean coveredBy(List<BranchPath> scopes, ChangeRequest request) {
        Optional<BranchPath> target = branches.pathOfBranch(request.targetBranchId());
        if (target.isEmpty()) {
            // b.path IS NULL trong cau SQL that -> khong thuoc pham vi nao ngoai toan dong ho.
            return false;
        }
        return scopes.stream().anyMatch(scope -> scope.isAncestorOf(target.get()));
    }

    private static List<ChangeRequest> page(List<ChangeRequest> rows, int limit, int offset) {
        if (offset >= rows.size()) {
            return List.of();
        }
        return List.copyOf(rows.subList(offset, (int) Math.min(rows.size(), (long) offset + limit)));
    }
}
