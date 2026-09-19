package vn.giapha.membership.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.giapha.membership.domain.Invitation;
import vn.giapha.membership.domain.InvitationStatus;
import vn.giapha.membership.domain.port.InvitationRepository;
import vn.giapha.shared.vo.BranchPath;

/** Hiện thực {@link InvitationRepository}. */
@Repository
public class InvitationRepositoryAdapter implements InvitationRepository {

    private final InvitationJpaRepository jpa;

    public InvitationRepositoryAdapter(InvitationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Invitation> byId(UUID id) {
        return id == null ? Optional.empty() : jpa.findById(id).map(InvitationRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Invitation> byCodeHash(String codeHash) {
        if (codeHash == null || codeHash.isBlank()) {
            return Optional.empty();
        }
        return jpa.findByCodeHash(codeHash).map(InvitationRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Invitation> openForPerson(UUID personId) {
        if (personId == null) {
            return Optional.empty();
        }
        return jpa.findByPersonIdAndStatus(personId, InvitationStatus.PENDING.name())
                .map(InvitationRepositoryAdapter::toDomain);
    }

    @Override
    public List<Invitation> inScope(List<BranchPath> scopes, boolean clanWide, int limit, int offset) {
        if (!clanWide && (scopes == null || scopes.isEmpty())) {
            // Khong co pham vi nao thi khong thay gi. Tuyet doi khong hieu nguoc thanh "thay tat".
            return List.of();
        }
        return jpa.findInScope(toLtreeArrayLiteral(scopes), clanWide ? 1 : 0, limit, offset)
                .stream()
                .map(InvitationRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Invitation save(Invitation invitation) {
        InvitationJpaEntity entity = jpa.findById(invitation.id())
                .orElseGet(() -> new InvitationJpaEntity(invitation.id(), invitation.codeHash(),
                        invitation.personId(), invitation.branchId(), invitation.invitedBy(),
                        invitation.expiresAt(), invitation.note()));
        entity.setStatus(invitation.status().name());
        entity.setAcceptedBy(invitation.acceptedBy());
        entity.setAcceptedAt(invitation.acceptedAt());
        entity.setRevokedAt(invitation.revokedAt());
        entity.setRevokedReason(invitation.revokedReason());
        return toDomain(jpa.saveAndFlush(entity));
    }

    /**
     * Literal mảng của Postgres: {@code {goc.chi_giap,goc.chi_at}}.
     *
     * <p>Không cần bọc nháy hay thoát ký tự: {@link BranchPath} đã bảo đảm mọi nhãn chỉ gồm
     * {@code [A-Za-z0-9_]} ngăn cách bởi dấu chấm.</p>
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

    private static Invitation toDomain(InvitationJpaEntity entity) {
        return new Invitation(entity.getId(), entity.getCodeHash(), entity.getPersonId(),
                entity.getBranchId(), entity.getInvitedBy(),
                InvitationStatus.of(entity.getStatus()), entity.getExpiresAt(),
                entity.getAcceptedBy(), entity.getAcceptedAt(), entity.getRevokedAt(),
                entity.getRevokedReason(), entity.getNote(), entity.getCreatedAt(),
                entity.getVersion());
    }
}
