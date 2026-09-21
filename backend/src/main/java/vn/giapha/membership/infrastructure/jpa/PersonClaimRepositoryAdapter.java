package vn.giapha.membership.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.giapha.membership.domain.PersonClaim;
import vn.giapha.membership.domain.PersonClaimKind;
import vn.giapha.membership.domain.PersonClaimStatus;
import vn.giapha.membership.domain.RelativeKind;
import vn.giapha.membership.domain.port.PersonClaimRepository;
import vn.giapha.shared.vo.BranchPath;
import vn.giapha.shared.vo.Gender;

/** Hiện thực {@link PersonClaimRepository} trên JPA. */
@Repository
public class PersonClaimRepositoryAdapter implements PersonClaimRepository {

    private final PersonClaimJpaRepository jpa;

    public PersonClaimRepositoryAdapter(PersonClaimJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<PersonClaim> byId(UUID id) {
        return id == null ? Optional.empty()
                : jpa.findById(id).map(PersonClaimRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<PersonClaim> openForRequester(UUID appUserId) {
        if (appUserId == null) {
            return Optional.empty();
        }
        return jpa.findByRequestedByAndStatus(appUserId, PersonClaimStatus.PENDING.name())
                .map(PersonClaimRepositoryAdapter::toDomain);
    }

    @Override
    public int rejectedCountOf(UUID appUserId) {
        if (appUserId == null) {
            return 0;
        }
        return (int) jpa.countByRequestedByAndStatus(appUserId, PersonClaimStatus.REJECTED.name());
    }

    @Override
    public int totalCountOf(UUID appUserId) {
        return appUserId == null ? 0 : (int) jpa.countByRequestedBy(appUserId);
    }

    @Override
    public List<PersonClaim> pendingInScope(List<BranchPath> scopes, boolean clanWide, int limit,
                                            int offset) {
        if (!clanWide && (scopes == null || scopes.isEmpty())) {
            // Khong co pham vi nao thi khong thay gi. Tuyet doi khong hieu nguoc thanh "thay tat".
            return List.of();
        }
        return jpa.findPendingInScope(toLtreeArrayLiteral(scopes), clanWide ? 1 : 0, limit, offset)
                .stream()
                .map(PersonClaimRepositoryAdapter::toDomain)
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
    public List<PersonClaim> byRequester(UUID appUserId, int limit, int offset) {
        if (appUserId == null) {
            return List.of();
        }
        return jpa.findByRequester(appUserId, limit, offset).stream()
                .map(PersonClaimRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<PersonClaim> othersClaiming(UUID personId, UUID exceptClaimId,
                                            PersonClaimStatus status) {
        if (personId == null || exceptClaimId == null) {
            return List.of();
        }
        return jpa.findOthersClaiming(personId, exceptClaimId,
                        (status == null ? PersonClaimStatus.PENDING : status).name()).stream()
                .map(PersonClaimRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public PersonClaim save(PersonClaim claim) {
        PersonClaimJpaEntity entity = jpa.findById(claim.id())
                .orElseGet(() -> new PersonClaimJpaEntity(claim.id(), claim.kind().name(),
                        claim.requestedBy(), claim.personId(), claim.relativePersonId(),
                        claim.relativeKind() == null ? null : claim.relativeKind().name(),
                        claim.declaredName(), claim.declaredBirthYear(),
                        claim.declaredGender() == null ? null : claim.declaredGender().name(),
                        claim.targetBranchId(), claim.phone(), claim.introduction(),
                        claim.screening()));
        entity.setStatus(claim.status().name());
        entity.setReviewerId(claim.reviewerId());
        entity.setReviewNote(claim.reviewNote());
        entity.setReviewedAt(claim.reviewedAt());
        entity.setCreatedPersonId(claim.createdPersonId());
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

    private static PersonClaim toDomain(PersonClaimJpaEntity entity) {
        return new PersonClaim(entity.getId(), PersonClaimKind.of(entity.getKind()),
                entity.getRequestedBy(), entity.getPersonId(), entity.getRelativePersonId(),
                entity.getRelativeKind() == null ? null : RelativeKind.of(entity.getRelativeKind()),
                entity.getDeclaredName(), entity.getDeclaredBirthYear(),
                entity.getDeclaredGender() == null ? null
                        : Gender.valueOf(entity.getDeclaredGender()),
                entity.getTargetBranchId(), entity.getPhone(), entity.getIntroduction(),
                entity.getScreening(), PersonClaimStatus.of(entity.getStatus()),
                entity.getReviewerId(), entity.getReviewNote(), entity.getReviewedAt(),
                entity.getCreatedPersonId(), entity.getCreatedAt(), entity.getVersion());
    }
}
