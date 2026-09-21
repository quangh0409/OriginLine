package vn.giapha.membership.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Bản chiếu JPA của {@code clan_invite_redemption} (V16) — <b>chỉ dùng để đọc</b>.
 *
 * <p>Việc ghi đi qua một câu {@code INSERT ... ON CONFLICT DO NOTHING} ở
 * {@link ClanInviteRedemptionJpaRepository#insertOnce}, không qua entity này. Lý do: chỉ mục
 * {@code ux_clan_redemption_once} là thứ bảo đảm "một tài khoản đếm một lượt trên một mã", và cách
 * duy nhất để <i>hỏi</i> nó mà không làm hỏng transaction là để Postgres tự bỏ qua. Bắt
 * {@code DataIntegrityViolationException} thì transaction đã ở trạng thái huỷ bỏ và mọi lệnh sau
 * đó đều chết — trong khi bấm hai lần là chuyện bình thường, không phải lỗi.</p>
 */
@Entity
@Table(name = "clan_invite_redemption")
public class ClanInviteRedemptionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "code_id", nullable = false, updatable = false)
    private UUID codeId;

    @Column(name = "app_user_id", nullable = false, updatable = false)
    private UUID appUserId;

    @Column(name = "client_key", updatable = false, length = 64)
    private String clientKey;

    @Column(name = "at", insertable = false, updatable = false)
    private Instant at;

    protected ClanInviteRedemptionJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public UUID getCodeId() {
        return codeId;
    }

    public UUID getAppUserId() {
        return appUserId;
    }

    public String getClientKey() {
        return clientKey;
    }

    public Instant getAt() {
        return at;
    }
}
