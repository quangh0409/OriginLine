package vn.giapha.kinship.infrastructure;

import java.util.UUID;
import vn.giapha.kinship.domain.DirectLinkType;
import vn.giapha.kinship.domain.InLawDirection;
import vn.giapha.kinship.domain.KinshipRule;
import vn.giapha.kinship.domain.RelationCode;
import vn.giapha.kinship.domain.RelationSide;
import vn.giapha.shared.vo.Gender;

/** Chuyển đổi giữa dòng CSDL và luật ở domain. Không có logic nghiệp vụ nào ở đây. */
final class KinshipRuleMapper {

    private KinshipRuleMapper() {
    }

    static KinshipRule toDomain(KinshipRuleEntity entity) {
        return new KinshipRule(
                entity.getId(),
                entity.getRuleSetId(),
                RelationCode.of(entity.getRelationCode()),
                entity.getGenDelta(),
                entity.getGenDeltaMin(),
                entity.getGenDeltaMax(),
                entity.getCollateralDegree(),
                entity.getCollateralDegreeMin(),
                entity.getCollateralDegreeMax(),
                RelationSide.fromDb(entity.getSide()),
                gender(entity.getGender()),
                entity.getIsElder(),
                RelationSide.fromDb(entity.getLinkSide()),
                gender(entity.getLinkGender()),
                InLawDirection.fromDb(entity.getInLawDirection()),
                DirectLinkType.fromDb(entity.getDirectLink()),
                entity.getDirectLinkSubtype(),
                entity.isDirectLinkReversed(),
                entity.getTitle(),
                entity.getTitleShort(),
                entity.getEgoSelfTerm(),
                entity.getDescription(),
                entity.getPriority(),
                entity.isActive(),
                null,
                entity.getRuleSetId());
    }

    static KinshipRuleEntity toEntity(KinshipRule rule, UUID ruleSetId) {
        KinshipRuleEntity entity = new KinshipRuleEntity();
        entity.setId(rule.id() != null ? rule.id() : UUID.randomUUID());
        entity.setRuleSetId(ruleSetId);
        entity.setRelationCode(rule.relationCode().value());
        entity.setGenDelta(rule.genDelta());
        entity.setGenDeltaMin(rule.genDeltaMin());
        entity.setGenDeltaMax(rule.genDeltaMax());
        entity.setCollateralDegree(rule.collateralDegree());
        entity.setCollateralDegreeMin(rule.collateralDegreeMin());
        entity.setCollateralDegreeMax(rule.collateralDegreeMax());
        entity.setSide(enumName(rule.side()));
        entity.setGender(genderName(rule.gender()));
        entity.setIsElder(rule.isElder());
        entity.setLinkSide(enumName(rule.linkSide()));
        entity.setLinkGender(genderName(rule.linkGender()));
        entity.setInLawDirection(enumName(rule.inLawDirection()));
        entity.setDirectLink(enumName(rule.directLink()));
        entity.setDirectLinkSubtype(rule.directLinkSubtype());
        entity.setDirectLinkReversed(rule.directLinkReversed());
        entity.setTitle(rule.title());
        entity.setTitleShort(rule.titleShort());
        entity.setEgoSelfTerm(rule.egoSelfTerm());
        entity.setDescription(rule.description());
        entity.setPriority(rule.priority());
        entity.setActive(rule.active());
        return entity;
    }

    /**
     * CSDL chỉ nhận MALE/FEMALE/UNKNOWN (ràng buộc {@code ck_kinship_rule_gender}), trong khi
     * {@link Gender} của shared kernel còn có OTHER. Quy OTHER về UNKNOWN khi ghi xuống.
     */
    private static String genderName(Gender gender) {
        if (gender == null) {
            return null;
        }
        return gender == Gender.OTHER ? Gender.UNKNOWN.name() : gender.name();
    }

    private static Gender gender(String value) {
        return value == null || value.isBlank() ? null : Gender.fromCode(value);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
