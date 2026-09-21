package vn.giapha.membership.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy cập {@code person_claim}.
 *
 * <h2>Lọc phạm vi nằm trong SQL, bằng {@code ltree}</h2>
 * Cùng khuôn với {@link ChangeRequestJpaRepository}, và cùng lý do — nhưng ở đây nó quan trọng hơn
 * một bậc: một lá đơn chở <b>số điện thoại</b> và vài dòng tự giới thiệu của một người đang sống,
 * nên "quên lọc" không phải một lỗi hiển thị mà là một lần lộ dữ liệu cá nhân. Lọc ở CSDL nghĩa là
 * đơn của chi khác <b>không bao giờ</b> đi vào bộ nhớ tiến trình, nên không có chỗ nào để quên.
 *
 * <p>{@code :paths} là literal mảng của Postgres ({@code {goc.chi_giap,goc.chi_at}}) rồi ép sang
 * {@code ltree[]}; {@code :clanWide} là {@code int} chứ không {@code boolean}. Cả hai vì ánh xạ
 * mảng và boolean trong native query của Hibernate phụ thuộc phiên bản driver, còn chuỗi và số thì
 * không. An toàn tiêm lệnh được bảo đảm ở tầng trên: mọi nhãn đã qua {@code BranchPath}, vốn chỉ
 * nhận {@code [A-Za-z0-9_.]}.</p>
 */
public interface PersonClaimJpaRepository extends JpaRepository<PersonClaimJpaEntity, UUID> {

    String PENDING_IN_SCOPE = """
              FROM person_claim pc
              LEFT JOIN branch b ON b.id = pc.target_branch_id AND b.is_deleted = FALSE
             WHERE pc.status = 'PENDING'
               AND (:clanWide = 1
                    OR (b.path IS NOT NULL AND CAST(:paths AS ltree[]) @> b.path))
            """;

    @Query(value = "SELECT pc.* " + PENDING_IN_SCOPE
            + " ORDER BY pc.created_at ASC LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<PersonClaimJpaEntity> findPendingInScope(@Param("paths") String paths,
                                                  @Param("clanWide") int clanWide,
                                                  @Param("limit") int limit,
                                                  @Param("offset") int offset);

    @Query(value = "SELECT count(*) " + PENDING_IN_SCOPE, nativeQuery = true)
    long countPendingInScope(@Param("paths") String paths, @Param("clanWide") int clanWide);

    /** Đơn đang chờ của một tài khoản — {@code ux_person_claim_open_requester} bảo đảm nhiều nhất một. */
    Optional<PersonClaimJpaEntity> findByRequestedByAndStatus(UUID requestedBy, String status);

    long countByRequestedByAndStatus(UUID requestedBy, String status);

    long countByRequestedBy(UUID requestedBy);

    @Query(value = """
            SELECT pc.* FROM person_claim pc
             WHERE pc.requested_by = :appUserId
             ORDER BY pc.created_at DESC
             LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<PersonClaimJpaEntity> findByRequester(@Param("appUserId") UUID appUserId,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset);

    /**
     * Các đơn <b>khác</b> cùng trỏ vào một nhân khẩu.
     *
     * <p>Không có chỉ mục duy nhất nào chặn chuyện này, và đó là chủ ý: design 07 §1.4 chốt rằng
     * hai người cùng nhận một nhân khẩu thì Trưởng chi thấy <i>cả hai</i> rồi chọn — trùng tên
     * trong dòng họ là chuyện thường, và người gửi trước chưa chắc là người đúng.</p>
     */
    @Query(value = """
            SELECT pc.* FROM person_claim pc
             WHERE pc.person_id = :personId
               AND pc.id <> :exceptId
               AND pc.status = :status
             ORDER BY pc.created_at ASC
            """, nativeQuery = true)
    List<PersonClaimJpaEntity> findOthersClaiming(@Param("personId") UUID personId,
                                                  @Param("exceptId") UUID exceptId,
                                                  @Param("status") String status);
}
