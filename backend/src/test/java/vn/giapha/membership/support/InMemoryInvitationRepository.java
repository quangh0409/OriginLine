package vn.giapha.membership.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.Invitation;
import vn.giapha.membership.domain.InvitationStatus;
import vn.giapha.membership.domain.port.InvitationRepository;
import vn.giapha.shared.vo.BranchPath;

/**
 * Bản trong bộ nhớ của {@code invitation}, mô phỏng <b>cả hai</b> ràng buộc duy nhất của V15:
 * {@code ux_invitation_code_hash} và {@code ux_invitation_open_person}.
 *
 * <p>Mô phỏng ràng buộc chứ không chỉ lưu map: nếu bản giả cho phép hai lời mời cùng mở cho một
 * nhân khẩu thì test "phát lại thu hồi mã cũ" sẽ xanh trên một trạng thái mà CSDL thật từ chối.</p>
 */
public final class InMemoryInvitationRepository implements InvitationRepository {

    private final Map<UUID, Invitation> byId = new LinkedHashMap<>();
    private final StubBranchLookup branches;

    public InMemoryInvitationRepository(StubBranchLookup branches) {
        this.branches = branches;
    }

    public Invitation seed(Invitation invitation) {
        byId.put(invitation.id(), invitation);
        return invitation;
    }

    public List<Invitation> all() {
        return List.copyOf(byId.values());
    }

    @Override
    public Optional<Invitation> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Invitation> byCodeHash(String codeHash) {
        if (codeHash == null) {
            return Optional.empty();
        }
        return byId.values().stream()
                .filter(invitation -> codeHash.equals(invitation.codeHash()))
                .findFirst();
    }

    @Override
    public Optional<Invitation> openForPerson(UUID personId) {
        if (personId == null) {
            return Optional.empty();
        }
        return byId.values().stream()
                .filter(invitation -> personId.equals(invitation.personId()))
                .filter(invitation -> invitation.status() == InvitationStatus.PENDING)
                .findFirst();
    }

    @Override
    public List<Invitation> inScope(List<BranchPath> scopes, boolean clanWide, int limit, int offset) {
        if (!clanWide && (scopes == null || scopes.isEmpty())) {
            return List.of();
        }
        List<Invitation> matched = new ArrayList<>();
        for (Invitation invitation : byId.values()) {
            if (clanWide || coveredBy(scopes, invitation)) {
                matched.add(invitation);
            }
        }
        matched.sort(Comparator.comparing(Invitation::id));
        int from = Math.min(offset, matched.size());
        int to = Math.min(from + limit, matched.size());
        return List.copyOf(matched.subList(from, to));
    }

    @Override
    public Invitation save(Invitation invitation) {
        // ux_invitation_code_hash
        byCodeHash(invitation.codeHash())
                .filter(other -> !other.id().equals(invitation.id()))
                .ifPresent(other -> {
                    throw new IllegalStateException(
                            "Vi pham ux_invitation_code_hash: " + invitation.codeHash());
                });
        // ux_invitation_open_person
        if (invitation.status() == InvitationStatus.PENDING) {
            openForPerson(invitation.personId())
                    .filter(other -> !other.id().equals(invitation.id()))
                    .ifPresent(other -> {
                        throw new IllegalStateException(
                                "Vi pham ux_invitation_open_person: " + invitation.personId());
                    });
        }
        byId.put(invitation.id(), invitation);
        return invitation;
    }

    private boolean coveredBy(List<BranchPath> scopes, Invitation invitation) {
        Optional<BranchPath> path = branches.pathOfBranch(invitation.branchId());
        return path.isPresent() && scopes.stream().anyMatch(scope -> scope.isAncestorOf(path.get()));
    }
}
