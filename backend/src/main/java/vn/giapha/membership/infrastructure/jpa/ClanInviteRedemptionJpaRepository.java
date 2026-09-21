package vn.giapha.membership.infrastructure.jpa;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy cập {@code clan_invite_redemption}. */
public interface ClanInviteRedemptionJpaRepository
        extends JpaRepository<ClanInviteRedemptionJpaEntity, Long> {

    /**
     * Ghi một lượt dùng, <b>bỏ qua lặng lẽ</b> nếu tài khoản này đã dùng mã ấy rồi.
     *
     * <h2>{@code ON CONFLICT DO NOTHING} chứ không phải bắt ngoại lệ</h2>
     * Bấm hai lần, mạng chập chờn, tải lại trang — tất cả là chuyện <b>bình thường</b>, không phải
     * lỗi. Nếu để {@code ux_clan_redemption_once} ném {@code DataIntegrityViolationException} rồi
     * bắt lại thì transaction Postgres đã ở trạng thái huỷ bỏ và mọi lệnh sau đó đều chết — kể cả
     * những lệnh hoàn toàn hợp lệ của cùng lần đăng ký.
     *
     * <p>Và đây cũng là chỗ bảo vệ bộ đếm: {@code ClanInviteRedeemer} chỉ tăng {@code use_count}
     * khi câu lệnh này trả {@code 1}. Không có nó thì bộ đếm phồng lên vì những lần bấm lại vô hại,
     * và Hội đồng sẽ thu hồi một mã lành vì tưởng nó đã rò.</p>
     *
     * @return {@code 1} nếu thật sự ghi mới, {@code 0} nếu đã có
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO clan_invite_redemption (code_id, app_user_id, client_key, at)
            VALUES (:codeId, :appUserId, :clientKey, :at)
            ON CONFLICT (code_id, app_user_id) DO NOTHING
            """, nativeQuery = true)
    int insertOnce(@Param("codeId") UUID codeId, @Param("appUserId") UUID appUserId,
                   @Param("clientKey") String clientKey, @Param("at") Instant at);

    @Query(value = """
            SELECT r.* FROM clan_invite_redemption r
             WHERE r.code_id = :codeId
             ORDER BY r.at DESC
             LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<ClanInviteRedemptionJpaEntity> findByCode(@Param("codeId") UUID codeId,
                                                   @Param("limit") int limit,
                                                   @Param("offset") int offset);
}
