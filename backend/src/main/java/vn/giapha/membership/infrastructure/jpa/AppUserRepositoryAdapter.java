package vn.giapha.membership.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.AppUserStatus;
import vn.giapha.membership.domain.port.AppUserRepository;

/**
 * Hiện thực {@link AppUserRepository}.
 *
 * <p>{@code keycloak_sub} là {@code updatable = false} ở entity: nó là khoá nối sang Keycloak và
 * đổi nó nghĩa là đổi hẳn danh tính của tài khoản — một thao tác phải đi qua nghiệp vụ có kiểm
 * duyệt, không phải qua một lần {@code save()} vô tình.</p>
 */
@Repository
public class AppUserRepositoryAdapter implements AppUserRepository {

    private final AppUserJpaRepository jpa;

    public AppUserRepositoryAdapter(AppUserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<AppUser> byId(UUID id) {
        return id == null ? Optional.empty() : jpa.findById(id).map(AppUserRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<AppUser> byKeycloakSub(String keycloakSub) {
        if (keycloakSub == null || keycloakSub.isBlank()) {
            return Optional.empty();
        }
        return jpa.findByKeycloakSub(keycloakSub).map(AppUserRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<AppUser> byPersonId(UUID personId) {
        return personId == null
                ? Optional.empty()
                : jpa.findByPersonId(personId).map(AppUserRepositoryAdapter::toDomain);
    }

    @Override
    public List<AppUser> byPersonIds(List<UUID> personIds) {
        if (personIds == null || personIds.isEmpty()) {
            return List.of();
        }
        return jpa.findByPersonIdIn(personIds).stream()
                .map(AppUserRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public AppUser save(AppUser user) {
        AppUserJpaEntity entity = jpa.findById(user.id())
                .orElseGet(() -> new AppUserJpaEntity(user.id(), user.keycloakSub()));
        entity.setPersonId(user.personId());
        entity.setEmail(user.email());
        entity.setDisplayName(user.displayName());
        entity.setStatus(user.status().name());
        entity.setLocale(user.locale());
        entity.setLastLoginAt(user.lastLoginAt());
        return toDomain(jpa.save(entity));
    }

    private static AppUser toDomain(AppUserJpaEntity entity) {
        return new AppUser(entity.getId(), entity.getKeycloakSub(), entity.getPersonId(),
                entity.getEmail(), entity.getDisplayName(), AppUserStatus.of(entity.getStatus()),
                entity.getLocale(), entity.getLastLoginAt(), entity.getVersion());
    }
}
