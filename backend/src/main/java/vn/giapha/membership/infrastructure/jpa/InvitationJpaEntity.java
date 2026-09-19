package vn.giapha.membership.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Bản chiếu bảng {@code invitation}.
 *
 * <p>{@code code_hash} là {@code updatable = false}: mã của một lời mời không đổi. Muốn đổi mã thì
 * phát lại — và phát lại thu hồi bản cũ, để "thu hồi" còn có nghĩa.</p>
 *
 * <p>{@code created_at} là {@code insertable = false, updatable = false}: giá trị do
 * {@code DEFAULT now()} của CSDL đặt. Đồng hồ của JVM lệch nhau giữa các instance, còn thứ tự trong
 * danh sách lời mời thì phải nhất quán.</p>
 */
@Entity
@Table(name = "invitation")
public class InvitationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code_hash", nullable = false, updatable = false, length = 64)
    private String codeHash;

    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    @Column(name = "branch_id", updatable = false)
    private UUID branchId;

    @Column(name = "invited_by", nullable = false, updatable = false)
    private UUID invitedBy;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "accepted_by")
    private UUID acceptedBy;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    @Column(name = "note", updatable = false)
    private String note;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected InvitationJpaEntity() {
    }

    @SuppressWarnings("java:S107")
    public InvitationJpaEntity(UUID id, String codeHash, UUID personId, UUID branchId,
                               UUID invitedBy, Instant expiresAt, String note) {
        this.id = id;
        this.codeHash = codeHash;
        this.personId = personId;
        this.branchId = branchId;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public UUID getPersonId() {
        return personId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getAcceptedBy() {
        return acceptedBy;
    }

    public void setAcceptedBy(UUID acceptedBy) {
        this.acceptedBy = acceptedBy;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public void setRevokedReason(String revokedReason) {
        this.revokedReason = revokedReason;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
