package vn.giapha.membership.infrastructure.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy cập {@code clan_invite_code}. */
public interface ClanInviteCodeJpaRepository extends JpaRepository<ClanInviteCodeJpaEntity, UUID> {

    Optional<ClanInviteCodeJpaEntity> findByCodeHash(String codeHash);

    @Query(value = """
            SELECT c.* FROM clan_invite_code c
             ORDER BY c.created_at DESC
             LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<ClanInviteCodeJpaEntity> findPage(@Param("limit") int limit, @Param("offset") int offset);

    /**
     * <b>Chốt 3</b> — tiêu một lượt, nguyên tử, có điều kiện.
     *
     * <h2>Mọi điều kiện nằm trong WHERE, không ở tầng trên</h2>
     * Còn hạn · chưa thu hồi · chưa chạm trần. Kiểm ở tầng trên rồi mới gọi thì giữa hai thời điểm
     * ấy Hội đồng có thể vừa thu hồi mã, và trần {@code max_uses} vượt được bởi đúng số người bấm
     * đồng thời — Postgres không cho hai lệnh {@code UPDATE} trên cùng một dòng chạy song song, nên
     * đặt điều kiện ở đây là đặt nó vào chỗ duy nhất tuần tự hoá được.
     *
     * <h2>Vì sao không dùng khoá lạc quan {@code @Version}</h2>
     * Nó biến lần thứ hai thành một ngoại lệ, tức một người đăng ký hợp lệ bị từ chối chỉ vì người
     * khác bấm cùng giây. Câu lệnh này thì cả hai cùng thành công và bộ đếm nhảy hai.
     *
     * <p>{@code @Modifying(clearAutomatically = true)}: giá trị {@code useCount} trong persistence
     * context trở thành cũ ngay sau câu lệnh, và một entity cũ được flush sau đó sẽ ghi đè bộ đếm
     * về con số trước. Xoá context là cách chắc chắn không ai đọc nhầm.</p>
     *
     * @return số dòng đã cập nhật: {@code 1} nếu tiêu được, {@code 0} nếu mã không còn dùng được
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE clan_invite_code
               SET use_count = use_count + 1
             WHERE id = :id
               AND status = 'ACTIVE'
               AND expires_at > :now
               AND (max_uses IS NULL OR use_count < max_uses)
            """, nativeQuery = true)
    int consumeOne(@Param("id") UUID id, @Param("now") Instant now);
}
