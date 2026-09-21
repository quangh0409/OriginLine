package vn.giapha.membership.infrastructure.jpa;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.giapha.membership.domain.ClanInviteRedemption;
import vn.giapha.membership.domain.port.ClanInviteRedemptionRepository;

/** Hiện thực {@link ClanInviteRedemptionRepository} trên JPA. */
@Repository
public class ClanInviteRedemptionRepositoryAdapter implements ClanInviteRedemptionRepository {

    private final ClanInviteRedemptionJpaRepository jpa;

    public ClanInviteRedemptionRepositoryAdapter(ClanInviteRedemptionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean record(UUID codeId, UUID appUserId, String clientKeyHash, Instant at) {
        if (codeId == null || appUserId == null) {
            return false;
        }
        return jpa.insertOnce(codeId, appUserId, clientKeyHash,
                at == null ? Instant.now() : at) == 1;
    }

    @Override
    public List<ClanInviteRedemption> byCode(UUID codeId, int limit, int offset) {
        if (codeId == null) {
            return List.of();
        }
        return jpa.findByCode(codeId, limit, offset).stream()
                .map(entity -> new ClanInviteRedemption(entity.getId(), entity.getCodeId(),
                        entity.getAppUserId(), entity.getAt()))
                .toList();
    }
}
