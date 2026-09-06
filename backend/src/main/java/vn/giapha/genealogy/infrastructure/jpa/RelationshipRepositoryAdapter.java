package vn.giapha.genealogy.infrastructure.jpa;

import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.genealogy.domain.Relationship;
import vn.giapha.genealogy.domain.port.RelationshipRepository;

/**
 * Hiện thực {@link RelationshipRepository} — <b>bản chiếu</b> của cạnh AGE.
 *
 * <p>Adapter này cố ý không tự gọi {@code TreeGraphPort}: ghép hai lệnh ghi lại với nhau là việc
 * của tầng application, nơi có ranh giới {@code @Transactional}. Nếu adapter tự gọi thì mỗi nơi
 * dùng repository sẽ vô tình ghi graph mà không biết, và ngày nào đó có nơi gọi ngoài transaction.</p>
 */
@Repository
public class RelationshipRepositoryAdapter implements RelationshipRepository {

    private final RelationshipJpaRepository relationships;

    public RelationshipRepositoryAdapter(RelationshipJpaRepository relationships) {
        this.relationships = relationships;
    }

    @Override
    public Relationship save(Relationship relationship) {
        RelationshipJpaEntity entity = relationships.findById(relationship.id())
                .orElseGet(() -> new RelationshipJpaEntity(relationship.id()));
        entity.setFromPersonId(relationship.fromPersonId());
        entity.setToPersonId(relationship.toPersonId());
        entity.setRelType(relationship.relType().name());
        entity.setSpouseOrder(relationship.spouseOrder());
        entity.setHeirType(relationship.heirKind() == null ? null : relationship.heirKind().name());
        entity.setValidFrom(relationship.validFrom());
        entity.setValidTo(relationship.validTo());
        entity.setEndReason(relationship.endReason());
        entity.setNote(relationship.note());
        entity.setDeleted(relationship.isDeleted());
        entity.setDeletedAt(relationship.deletedAt() == null
                ? null : relationship.deletedAt().atOffset(ZoneOffset.UTC));
        relationships.save(entity);
        return relationship;
    }

    @Override
    public List<Relationship> byPerson(UUID personId) {
        return relationships.findActiveByPerson(personId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Relationship> betweenAll(Collection<UUID> personIds) {
        if (personIds == null || personIds.isEmpty()) {
            return List.of();
        }
        return relationships.findActiveBetween(personIds).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Relationship> touchingAny(Collection<UUID> personIds) {
        if (personIds == null || personIds.isEmpty()) {
            return List.of();
        }
        return relationships.findActiveTouching(personIds).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsParentEdge(UUID parentId, UUID childId) {
        return relationships.countParentEdge(parentId, childId) > 0;
    }

    private Relationship toDomain(RelationshipJpaEntity entity) {
        RelType relType = RelType.valueOf(entity.getRelType());
        Relationship relationship = Relationship.of(entity.getId(), entity.getFromPersonId(),
                entity.getToPersonId(), relType);
        if (relType == RelType.SPOUSE) {
            relationship.spouseOrder(entity.getSpouseOrder());
        }
        if (relType == RelType.HEIR && entity.getHeirType() != null) {
            relationship.heirKind(HeirKind.valueOf(entity.getHeirType()));
        }
        relationship.validity(entity.getValidFrom(), entity.getValidTo());
        relationship.endReason(entity.getEndReason());
        relationship.note(entity.getNote());
        if (entity.isDeleted()) {
            relationship.softDelete();
        }
        return relationship;
    }
}
