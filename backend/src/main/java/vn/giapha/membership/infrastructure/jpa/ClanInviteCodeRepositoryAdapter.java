package vn.giapha.membership.infrastructure.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.giapha.membership.domain.ClanInviteCode;
import vn.giapha.membership.domain.ClanInviteStatus;
import vn.giapha.membership.domain.port.ClanInviteCodeRepository;

/** Hiện thực {@link ClanInviteCodeRepository} trên JPA. */
@Repository
public class ClanInviteCodeRepositoryAdapter implements ClanInviteCodeRepository {

    private final ClanInviteCodeJpaRepository jpa;

    public ClanInviteCodeRepositoryAdapter(ClanInviteCodeJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<ClanInviteCode> byId(UUID id) {
        return id == null ? Optional.empty()
                : jpa.findById(id).map(ClanInviteCodeRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<ClanInviteCode> byCodeHash(String codeHash) {
        if (codeHash == null || codeHash.isBlank()) {
            return Optional.empty();
        }
        return jpa.findByCodeHash(codeHash).map(ClanInviteCodeRepositoryAdapter::toDomain);
    }

    @Override
    public List<ClanInviteCode> all(int limit, int offset) {
        return jpa.findPage(limit, offset).stream()
                .map(ClanInviteCodeRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public ClanInviteCode save(ClanInviteCode code) {
        ClanInviteCodeJpaEntity entity = jpa.findById(code.id())
                .orElseGet(() -> new ClanInviteCodeJpaEntity(code.id(), code.codeHash(),
                        code.label(), code.issuedBy(), code.expiresAt(), code.maxUses(),
                        code.note()));
        entity.setLabel(code.label());
        entity.setStatus(code.status().name());
        entity.setRevokedAt(code.revokedAt());
        entity.setRevokedReason(code.revokedReason());
        return toDomain(jpa.saveAndFlush(entity));
    }

    /**
     * Tiêu một lượt. Xem {@link ClanInviteCodeJpaRepository#consumeOne} về việc vì sao đây là một
     * câu {@code UPDATE} chứ không phải đọc-rồi-ghi.
     */
    @Override
    public boolean tryConsume(UUID codeId, Instant now) {
        if (codeId == null) {
            return false;
        }
        return jpa.consumeOne(codeId, now == null ? Instant.now() : now) == 1;
    }

    private static ClanInviteCode toDomain(ClanInviteCodeJpaEntity entity) {
        return new ClanInviteCode(entity.getId(), entity.getCodeHash(), entity.getLabel(),
                entity.getIssuedBy(), ClanInviteStatus.of(entity.getStatus()),
                entity.getExpiresAt(), entity.getMaxUses(), entity.getUseCount(),
                entity.getRevokedAt(), entity.getRevokedReason(), entity.getNote(),
                entity.getCreatedAt(), entity.getVersion());
    }
}
