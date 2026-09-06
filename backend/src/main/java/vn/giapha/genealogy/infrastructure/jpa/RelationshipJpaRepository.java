package vn.giapha.genealogy.infrastructure.jpa;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy cập bản chiếu {@code relationship}. */
public interface RelationshipJpaRepository extends JpaRepository<RelationshipJpaEntity, UUID> {

    @Query("""
            select r from RelationshipJpaEntity r
            where r.deleted = false and (r.fromPersonId = :id or r.toPersonId = :id)
            """)
    List<RelationshipJpaEntity> findActiveByPerson(@Param("id") UUID personId);

    /** Cạnh có <b>cả hai</b> đầu nằm trong tập — điều kiện của {@code TreeProjection.edges}. */
    @Query("""
            select r from RelationshipJpaEntity r
            where r.deleted = false and r.fromPersonId in :ids and r.toPersonId in :ids
            """)
    List<RelationshipJpaEntity> findActiveBetween(@Param("ids") Collection<UUID> ids);

    /** Cạnh có <b>ít nhất một</b> đầu trong tập — dùng để tìm vợ/chồng cần nạp thêm vào cây. */
    @Query("""
            select r from RelationshipJpaEntity r
            where r.deleted = false and (r.fromPersonId in :ids or r.toPersonId in :ids)
            """)
    List<RelationshipJpaEntity> findActiveTouching(@Param("ids") Collection<UUID> ids);

    @Query("""
            select count(r) from RelationshipJpaEntity r
            where r.deleted = false and r.fromPersonId = :parentId and r.toPersonId = :childId
              and r.relType in ('PARENT_BIO', 'PARENT_ADOPT')
            """)
    long countParentEdge(@Param("parentId") UUID parentId, @Param("childId") UUID childId);
}
