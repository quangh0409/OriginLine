package vn.giapha.membership.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Bản chiếu JPA của {@code clan_invite_code} (V16).
 *
 * <h2>{@code useCount} là {@code insertable} nhưng KHÔNG {@code updatable}</h2>
 * Bộ đếm được tăng bằng một câu {@code UPDATE ... SET use_count = use_count + 1} có điều kiện
 * ({@code ClanInviteCodeRepository#tryConsume}), <b>không</b> bằng đọc-rồi-ghi qua entity này. Khoá
 * cột lại ở tầng ánh xạ là cách rẻ nhất để không ai vô tình mở lại lối đọc-rồi-ghi: hai người bấm
 * cùng lúc sẽ đếm thành một lượt, và một bộ đếm đếm thiếu còn tệ hơn không có bộ đếm vì nó tạo cảm
 * giác an toàn giả.
 *
 * <p>Hệ quả cần biết: sau một lần {@code tryConsume}, giá trị {@code useCount} trong persistence
 * context là <b>cũ</b>. Adapter vì thế đọc lại từ CSDL khi cần con số đúng, và
 * {@code ClanInviteRedeemer} không bao giờ dựa vào giá trị trong tay.</p>
 *
 * <p>{@code codeHash} là {@code VARCHAR(64)} chứ không {@code CHAR(64)} — xem ghi chú trong V16 về
 * {@code bpchar} và về việc {@code CHAR} đệm khoảng trắng khi so sánh.</p>
 */
@Entity
@Table(name = "clan_invite_code")
public class ClanInviteCodeJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code_hash", nullable = false, updatable = false, length = 64)
    private String codeHash;

    @Column(name = "label", length = 160)
    private String label;

    @Column(name = "issued_by", nullable = false, updatable = false)
    private UUID issuedBy;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** Xem javadoc lớp: chỉ ghi lúc chèn, tăng bằng câu UPDATE nguyên tử. */
    @Column(name = "use_count", nullable = false, updatable = false)
    private int useCount;

    @Column(name = "max_uses", updatable = false)
    private Integer maxUses;

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

    protected ClanInviteCodeJpaEntity() {
    }

    public ClanInviteCodeJpaEntity(UUID id, String codeHash, String label, UUID issuedBy,
                                   Instant expiresAt, Integer maxUses, String note) {
        this.id = id;
        this.codeHash = codeHash;
        this.label = label;
        this.issuedBy = issuedBy;
        this.expiresAt = expiresAt;
        this.maxUses = maxUses;
        this.note = note;
        this.useCount = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public UUID getIssuedBy() {
        return issuedBy;
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

    public int getUseCount() {
        return useCount;
    }

    public Integer getMaxUses() {
        return maxUses;
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
