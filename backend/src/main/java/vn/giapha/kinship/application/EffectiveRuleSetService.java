package vn.giapha.kinship.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.kinship.domain.KinshipRule;
import vn.giapha.kinship.domain.KinshipRuleSet;
import vn.giapha.kinship.domain.RuleScope;
import vn.giapha.kinship.domain.RuleSetRepository;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Trả bộ luật danh xưng <b>đã hợp nhất</b> cho một chi — phục vụ màn hình thử luật của Hội đồng
 * Tộc biểu.
 *
 * <p>Phép hợp nhất nằm ở domain ({@code KinshipRuleSet.effectiveRules()} /
 * {@code overriddenRules()}), không lặp lại ở đây. Nếu tầng này tự hợp nhất một lần nữa thì màn
 * hình thử luật sẽ có ngày hiển thị một bộ luật khác với bộ mà engine đang dùng — đúng loại lỗi
 * làm mất niềm tin vào cả tính năng.</p>
 */
@Service
public class EffectiveRuleSetService {

    private final RuleSetRepository ruleSets;

    public EffectiveRuleSetService(RuleSetRepository ruleSets) {
        this.ruleSets = ruleSets;
    }

    /**
     * @param branchId chi/ngành cần tra; {@code null} thì rơi về bộ {@code DEFAULT}
     */
    @Transactional(readOnly = true)
    public EffectiveRuleSetView forBranch(UUID branchId) {
        KinshipRuleSet leaf = ruleSets.resolveFor(branchId);
        return toView(branchId, leaf);
    }

    /** Bộ luật cụ thể (kèm chuỗi cha) — dùng khi Hội đồng thử một bộ chưa gán cho chi nào. */
    @Transactional(readOnly = true)
    public EffectiveRuleSetView forRuleSet(UUID ruleSetId) {
        KinshipRuleSet leaf = ruleSets.byId(ruleSetId)
                .orElseThrow(() -> NotFoundException.of("KinshipRuleSet", ruleSetId));
        return toView(leaf.branchId(), leaf);
    }

    private EffectiveRuleSetView toView(UUID branchId, KinshipRuleSet leaf) {
        List<EffectiveRuleSetView.RuleSetRef> chain = new ArrayList<>();
        for (KinshipRuleSet set : leaf.chain()) {
            chain.add(new EffectiveRuleSetView.RuleSetRef(set.id(), set.scope(), set.code(),
                    set.name(), set.version()));
        }

        List<EffectiveRuleSetView.EffectiveRule> rules = new ArrayList<>();
        for (KinshipRule rule : leaf.effectiveRules()) {
            rules.add(effective(rule, false));
        }
        for (KinshipRule shadowed : leaf.overriddenRules()) {
            rules.add(effective(shadowed, true));
        }
        return new EffectiveRuleSetView(branchId, leaf.id(), chain, rules);
    }

    /**
     * {@code originScope}/{@code originRuleSetId} do {@code KinshipRuleSet} gắn khi hợp nhất; rơi
     * về {@code ruleSetId} thô nếu luật chưa qua hợp nhất (không xảy ra trên đường này, nhưng để
     * trống thì giao diện mất phần "luật này đến từ đâu").
     */
    private EffectiveRuleSetView.EffectiveRule effective(KinshipRule rule, boolean overridden) {
        RuleScope scope = rule.originScope();
        UUID setId = rule.originRuleSetId() != null ? rule.originRuleSetId() : rule.ruleSetId();
        return new EffectiveRuleSetView.EffectiveRule(rule, scope, setId, overridden);
    }
}
