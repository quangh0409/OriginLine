package vn.giapha.genealogy.infrastructure.jpa;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy cập bảng {@code person}. Không có phương thức xoá — nhân khẩu chỉ được xoá mềm. */
public interface PersonJpaRepository extends JpaRepository<PersonJpaEntity, UUID> {

    List<PersonJpaEntity> findByIdIn(Collection<UUID> ids);

    @Query("select p.generation from PersonJpaEntity p where p.id = :id")
    Integer findGenerationById(@Param("id") UUID id);

    /**
     * Đếm con (ruột lẫn nuôi) chưa bị xoá mềm cho từng người cha/mẹ trong danh sách.
     * Đếm cả cạnh lẫn con, vì một người con đã xoá mềm thì không được tính vào số hiển thị.
     */
    @Query(value = """
            SELECT r.from_person_id AS parent_id, COUNT(*) AS child_count
            FROM relationship r
            JOIN person c ON c.id = r.to_person_id
            WHERE r.is_deleted = FALSE
              AND r.rel_type IN ('PARENT_BIO', 'PARENT_ADOPT')
              AND c.is_deleted = FALSE
              AND r.from_person_id IN (:ids)
            GROUP BY r.from_person_id
            """, nativeQuery = true)
    List<Object[]> countChildrenByParentIds(@Param("ids") Collection<UUID> ids);
}
