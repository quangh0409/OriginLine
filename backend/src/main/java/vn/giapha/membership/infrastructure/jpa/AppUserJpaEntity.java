package vn.giapha.membership.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Bản chiếu bảng {@code app_user}. */
@Entity
@Table(name = "app_user")
public class AppUserJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "keycloak_sub", nullable = false, length = 64, updatable = false)
    private String keycloakSub;

    @Column(name = "person_id")
    private UUID personId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "locale", nullable = false, length = 5)
    private String locale;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AppUserJpaEntity() {
    }

    public AppUserJpaEntity(UUID id, String keycloakSub) {
        this.id = id;
        this.keycloakSub = keycloakSub;
    }

    public UUID getId() {
        return id;
    }

    public String getKeycloakSub() {
        return keycloakSub;
    }

    public UUID getPersonId() {
        return personId;
    }

    public void setPersonId(UUID personId) {
        this.personId = personId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
