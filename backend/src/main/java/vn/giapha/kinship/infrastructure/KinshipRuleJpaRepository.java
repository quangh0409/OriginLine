package vn.giapha.kinship.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn thô bảng {@code kinship_rule}. */
interface KinshipRuleJpaRepository extends JpaRepository<KinshipRuleEntity, UUID> {

    /**
     * Luật của nhiều bộ trong một lượt — nạp cả chuỗi kế thừa
     * {@code DEFAULT → REGION → CLAN → BRANCH} bằng một truy vấn thay vì bốn.
     */
    List<KinshipRuleEntity> findByRuleSetIdInOrderByPriorityAsc(Collection<UUID> ruleSetIds);

    List<KinshipRuleEntity> findByRuleSetIdOrderByPriorityAsc(UUID ruleSetId);

    /**
     * Dọn sạch luật của một bộ, phục vụ ngữ nghĩa <b>replace trọn vẹn</b> của
     * {@code RuleSetRepository.replace}.
     */
    void deleteByRuleSetId(UUID ruleSetId);
}
